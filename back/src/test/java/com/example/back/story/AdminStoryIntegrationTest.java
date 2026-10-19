package com.example.back.story;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.back.MutableClock;
import com.example.back.TestcontainersConfiguration;
import com.example.back.auth.AuthCookies;
import com.example.back.config.StorageProperties;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AdminStoryIntegrationTest {

    private static final byte[] PDF =
            "%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n"
                    .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0};

    /** Each login from its own address, away from the login rate limiter. */
    private static final AtomicInteger LOGIN_IP = new AtomicInteger();

    @Autowired private MockMvc mvc;

    @Autowired private StoryRepository storyRepository;

    @Autowired private S3Client s3;

    @Autowired private MutableClock clock;

    @Autowired private StorageProperties storage;

    private Cookie admin;

    @BeforeEach
    void loginAdmin() throws Exception {
        clock.reset();
        admin = accessCookie(login("admin-000000", "admin-test-password"));
    }

    // --- Access ---

    @Test
    void anonymousIsRejected() throws Exception {
        mvc.perform(get("/api/admin/stories")).andExpect(status().isUnauthorized());
        mvc.perform(upload(storyJson(uniqueTitle(), 2, "\"Alice\""), pdf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userIsForbidden() throws Exception {
        Cookie user =
                accessCookie(
                        mvc.perform(
                                        post("/api/auth/register")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        "{\"password\":\"correct-horse-battery\"}"))
                                .andExpect(status().isCreated())
                                .andReturn());

        assertForbidden(user);
    }

    @Test
    void guestIsForbidden() throws Exception {
        Cookie guest =
                accessCookie(
                        mvc.perform(post("/api/auth/guest"))
                                .andExpect(status().isOk())
                                .andReturn());

        assertForbidden(guest);
    }

    private void assertForbidden(Cookie access) throws Exception {
        long stories = storyRepository.count();
        int objects = objectCount();

        mvc.perform(get("/api/admin/stories").cookie(access)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/stories/limits").cookie(access))
                .andExpect(status().isForbidden());
        mvc.perform(upload(storyJson(uniqueTitle(), 2, "\"Alice\""), pdf()).cookie(access))
                .andExpect(status().isForbidden());

        assertThat(storyRepository.count()).isEqualTo(stories);
        assertThat(objectCount()).isEqualTo(objects);
    }

    // --- Creation ---

    @Test
    void adminCreatesStoryWithNarratorFirstAndPdfInBucket() throws Exception {
        String title = uniqueTitle();

        MvcResult result =
                mvc.perform(
                                upload(
                                                storyJson(
                                                        "  " + title + "  ",
                                                        3,
                                                        "\" Alice \", \"Bob\""),
                                                pdf())
                                        .cookie(admin))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.title").value(title))
                        .andExpect(jsonPath("$.description").value("Une description."))
                        .andExpect(jsonPath("$.playerCount").value(3))
                        .andExpect(jsonPath("$.characters[0]").value("Narrateur"))
                        .andExpect(jsonPath("$.characters[1]").value("Alice"))
                        .andExpect(jsonPath("$.characters[2]").value("Bob"))
                        .andExpect(jsonPath("$.pdfSize").value(PDF.length))
                        .andReturn();

        UUID id = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
        Story story =
                storyRepository.findAllByOrderByCreatedAtDesc().stream()
                        .filter(s -> s.getId().equals(id))
                        .findFirst()
                        .orElseThrow();
        assertThat(story.getCharacters())
                .extracting(StoryCharacter::getName, StoryCharacter::isNarrator)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Narrateur", true),
                        org.assertj.core.groups.Tuple.tuple("Alice", false),
                        org.assertj.core.groups.Tuple.tuple("Bob", false));
        assertThat(story.getCreatedBy()).isNotNull();

        var object = s3.getObjectAsBytes(b -> b.bucket(storage.bucket()).key(story.getPdfKey()));
        assertThat(object.asByteArray()).isEqualTo(PDF);
        assertThat(object.response().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void adminListsStoriesNewestFirst() throws Exception {
        String first = uniqueTitle();
        String second = uniqueTitle();
        create(first);
        clock.advance(Duration.ofMinutes(1));
        create(second);

        MvcResult result =
                mvc.perform(get("/api/admin/stories").cookie(admin))
                        .andExpect(status().isOk())
                        .andReturn();
        String body = result.getResponse().getContentAsString();
        java.util.List<String> titles = JsonPath.read(body, "$[*].title");
        assertThat(titles).containsSubsequence(second, first);
        java.util.List<String> characters =
                JsonPath.read(body, "$[?(@.title == '" + second + "')].characters[*]");
        assertThat(characters).containsExactly("Narrateur", "Alice");
    }

    @Test
    void limitsExposePdfMaxSize() throws Exception {
        mvc.perform(get("/api/admin/stories/limits").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfMaxSize").value(64 * 1024));
    }

    // --- Field validation ---

    @Test
    void missingTitleIsRejected() throws Exception {
        assertRejected(storyJson("   ", 2, "\"Alice\""), pdf(), 400, "Le titre est obligatoire.");
    }

    @Test
    void tooLongTitleIsRejected() throws Exception {
        assertRejected(
                storyJson("t".repeat(121), 2, "\"Alice\""),
                pdf(),
                400,
                "Le titre doit contenir au plus 120 caractères.");
    }

    @Test
    void missingDescriptionIsRejected() throws Exception {
        String json =
                "{\"title\":\"" + uniqueTitle() + "\",\"playerCount\":2,\"characters\":[\"A\"]}";
        assertRejected(json, pdf(), 400, "La description est obligatoire.");
    }

    @Test
    void playerCountOutsideTwoToFourIsRejected() throws Exception {
        for (String count : new String[] {"1", "5", "null"}) {
            assertRejected(
                    storyJson(uniqueTitle(), count, "\"A\", \"B\", \"C\", \"D\", \"E\""),
                    pdf(),
                    400,
                    "Le nombre de joueurs doit être 2, 3 ou 4.");
        }
    }

    @Test
    void fewerCharactersThanPlayersIsRejected() throws Exception {
        assertRejected(
                storyJson(uniqueTitle(), 4, "\"Alice\", \"Bob\""),
                pdf(),
                400,
                "Il faut au moins 4 personnages, narrateur compris, pour 4 joueurs : chaque"
                        + " joueur doit en incarner au moins un.");
    }

    @Test
    void blankCharacterNameIsRejected() throws Exception {
        assertRejected(
                storyJson(uniqueTitle(), 2, "\"Alice\", \" \""),
                pdf(),
                400,
                "Le nom du personnage n° 2 est vide.");
    }

    @Test
    void duplicateCharacterIgnoringCaseIsRejected() throws Exception {
        assertRejected(
                storyJson(uniqueTitle(), 2, "\"Alice\", \"alice\""),
                pdf(),
                400,
                "Le personnage « alice » apparaît plusieurs fois.");
    }

    @Test
    void narratorCannotBeAddedTwice() throws Exception {
        assertRejected(
                storyJson(uniqueTitle(), 2, "\"narrateur\""),
                pdf(),
                400,
                "Le narrateur est ajouté automatiquement : ne le saisis pas une seconde fois.");
    }

    @Test
    void duplicateTitleIgnoringCaseIsRejected() throws Exception {
        String title = uniqueTitle();
        create(title);

        assertRejected(
                storyJson(title.toUpperCase(), 2, "\"Alice\""),
                pdf(),
                409,
                "Une histoire porte déjà ce titre.");
    }

    // --- PDF upload ---

    @Test
    void missingPdfIsRejected() throws Exception {
        assertRejected(
                storyJson(uniqueTitle(), 2, "\"Alice\""),
                null,
                400,
                "Le PDF de l'histoire est obligatoire.");
    }

    @Test
    void nonPdfContentTypeIsRejected() throws Exception {
        assertRejected(
                storyJson(uniqueTitle(), 2, "\"Alice\""),
                new MockMultipartFile("pdf", "histoire.png", "image/png", PNG),
                415,
                "Le fichier « histoire.png » n'est pas un PDF.");
    }

    @Test
    void fileDeclaredAsPdfButWithOtherContentIsRejected() throws Exception {
        assertRejected(
                storyJson(uniqueTitle(), 2, "\"Alice\""),
                new MockMultipartFile("pdf", "histoire.pdf", "application/pdf", PNG),
                415,
                "Le fichier « histoire.pdf » n'est pas un PDF.");
    }

    @Test
    void pdfOverConfiguredMaxSizeIsRejected() throws Exception {
        byte[] large = Arrays.copyOf(PDF, 64 * 1024 + 1);

        assertRejected(
                storyJson(uniqueTitle(), 2, "\"Alice\""),
                new MockMultipartFile("pdf", "histoire.pdf", "application/pdf", large),
                413,
                "Le PDF dépasse la taille maximale de 64 Ko.");
    }

    // --- Helpers ---

    /** A refused import leaves neither a row nor a file behind. */
    private void assertRejected(String json, MockMultipartFile pdf, int status, String message)
            throws Exception {
        long stories = storyRepository.count();
        int objects = objectCount();

        mvc.perform(upload(json, pdf).cookie(admin))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.message").value(message));

        assertThat(storyRepository.count()).isEqualTo(stories);
        assertThat(objectCount()).isEqualTo(objects);
    }

    private void create(String title) throws Exception {
        mvc.perform(upload(storyJson(title, 2, "\"Alice\""), pdf()).cookie(admin))
                .andExpect(status().isCreated());
    }

    private static MockMultipartHttpServletRequestBuilder upload(
            String json, MockMultipartFile pdf) {
        var request =
                multipart("/api/admin/stories")
                        .file(
                                new MockMultipartFile(
                                        "story",
                                        "",
                                        MediaType.APPLICATION_JSON_VALUE,
                                        json.getBytes(StandardCharsets.UTF_8)));
        return pdf == null ? request : request.file(pdf);
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("pdf", "histoire.pdf", "application/pdf", PDF);
    }

    private static String storyJson(String title, Object playerCount, String characters) {
        return "{\"title\":\""
                + title
                + "\",\"description\":\"Une description.\",\"playerCount\":"
                + playerCount
                + ",\"characters\":["
                + characters
                + "]}";
    }

    private static String uniqueTitle() {
        return "Histoire " + UUID.randomUUID();
    }

    private int objectCount() {
        return s3.listObjectsV2(b -> b.bucket(storage.bucket())).keyCount();
    }

    private ResultActions loginRequest(String pseudo, String password) throws Exception {
        String ip = "10.9.0." + LOGIN_IP.incrementAndGet();
        return mvc.perform(
                post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"pseudo\":\"" + pseudo + "\",\"password\":\"" + password + "\"}")
                        .with(
                                request -> {
                                    request.setRemoteAddr(ip);
                                    return request;
                                }));
    }

    private MvcResult login(String pseudo, String password) throws Exception {
        return loginRequest(pseudo, password).andExpect(status().isOk()).andReturn();
    }

    private static Cookie accessCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(AuthCookies.ACCESS);
        assertThat(cookie).isNotNull();
        return new Cookie(AuthCookies.ACCESS, cookie.getValue());
    }
}
