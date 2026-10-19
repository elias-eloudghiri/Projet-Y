package com.example.back.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.back.MutableClock;
import com.example.back.TestcontainersConfiguration;
import com.example.back.auth.AuthCookies;
import com.example.back.story.Story;
import com.example.back.story.StoryRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RoomIntegrationTest {

    private static final String PASSWORD = "secret-room";

    @Autowired private MockMvc mvc;

    @Autowired private RoomRepository roomRepository;

    @Autowired private StoryRepository storyRepository;

    @Autowired private PasswordEncoder passwordEncoder;

    @Autowired private TransactionTemplate transactions;

    @Autowired private MutableClock clock;

    @MockitoSpyBean private RoomCodeGenerator codeGenerator;

    private Cookie creator;
    private String creatorPseudo;

    @BeforeEach
    void registerCreator() throws Exception {
        clock.reset();
        MvcResult result = register();
        creator = accessCookie(result);
        creatorPseudo = JsonPath.read(result.getResponse().getContentAsString(), "$.pseudo");
    }

    // --- Access ---

    @Test
    void anonymousIsRejected() throws Exception {
        UUID story = story(4);
        long rooms = roomRepository.count();

        mvc.perform(createRequest(roomJson(story, 2, PASSWORD)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rooms/ABCDEFGH")).andExpect(status().isUnauthorized());

        assertThat(roomRepository.count()).isEqualTo(rooms);
    }

    @Test
    void guestCannotCreateRoom() throws Exception {
        UUID story = story(4);
        long rooms = roomRepository.count();

        mvc.perform(createRequest(roomJson(story, 2, PASSWORD)).cookie(guest()))
                .andExpect(status().isForbidden());

        assertThat(roomRepository.count()).isEqualTo(rooms);
    }

    // --- Creation ---

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4})
    void creatorPicksPlayerCountFromTwoToStoryMaximum(int playerCount) throws Exception {
        UUID story = story(4);

        create(roomJson(story, playerCount, PASSWORD))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(matchesCode()))
                .andExpect(jsonPath("$.playerCount").value(playerCount))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.story.id").value(story.toString()))
                .andExpect(jsonPath("$.story.maxPlayers").value(4));
    }

    @Test
    void creatorIsFirstParticipantWithFirstColour() throws Exception {
        UUID story = story(3);

        String body =
                create(roomJson(story, 3, PASSWORD))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.participants.length()").value(1))
                        .andExpect(jsonPath("$.participants[0].pseudo").value(creatorPseudo))
                        .andExpect(jsonPath("$.participants[0].color").value("#60A5FA"))
                        .andExpect(jsonPath("$.participants[0].isCreator").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String code = JsonPath.read(body, "$.code");

        assertThat((String) JsonPath.read(body, "$.shareUrl"))
                .isEqualTo("http://localhost:5173/room/" + code);
        transactions.executeWithoutResult(
                tx -> {
                    Room room = roomRepository.findByCode(code).orElseThrow();
                    assertThat(room.getStatus()).isEqualTo(RoomStatus.WAITING);
                    assertThat(room.getParticipants())
                            .singleElement()
                            .satisfies(
                                    p -> {
                                        assertThat(p.isCreator()).isTrue();
                                        assertThat(p.getUserId()).isEqualTo(room.getCreatorId());
                                        assertThat(p.getGuestId()).isNull();
                                    });
                });
    }

    @Test
    void passwordIsHashedWithAccountEncoder() throws Exception {
        String code = code(create(roomJson(story(2), 2, PASSWORD)).andExpect(status().isCreated()));

        Room room = roomRepository.findByCode(code).orElseThrow();
        assertThat(room.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$argon2id$");
        assertThat(passwordEncoder.matches(PASSWORD, room.getPasswordHash())).isTrue();
    }

    @Test
    void passwordNeverAppearsInResponses() throws Exception {
        MvcResult created = create(roomJson(story(2), 2, PASSWORD)).andReturn();
        String code = code(created);
        String hash = roomRepository.findByCode(code).orElseThrow().getPasswordHash();
        String fetched =
                mvc.perform(get("/api/rooms/" + code).cookie(creator))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        for (String body : new String[] {created.getResponse().getContentAsString(), fetched}) {
            assertThat(body)
                    .doesNotContain(PASSWORD)
                    .doesNotContain(hash)
                    .doesNotContainIgnoringCase("password");
            // Only the story id is exposed: neither the room nor the participant ids.
            assertThat(body).doesNotContain("\"id\":\"" + roomIdOf(code));
        }
    }

    @Test
    void takenCodeIsReplacedByANewOne() throws Exception {
        String taken = code(create(roomJson(story(2), 2, PASSWORD)));
        doReturn(taken).doCallRealMethod().when(codeGenerator).generate();

        String code = code(create(roomJson(story(2), 2, PASSWORD)).andExpect(status().isCreated()));

        assertThat(code).isNotEqualTo(taken).matches(RoomCodeGenerator.CODE_PATTERN);
    }

    // --- Validation ---

    @Test
    void playerCountBelowTwoIsRejected() throws Exception {
        assertRejected(
                roomJson(story(4), 1, PASSWORD),
                400,
                "Le nombre de joueurs doit être compris entre 2 et 4 pour cette histoire.");
    }

    @Test
    void playerCountAboveStoryMaximumIsRejected() throws Exception {
        assertRejected(
                roomJson(story(3), 4, PASSWORD),
                400,
                "Le nombre de joueurs doit être compris entre 2 et 3 pour cette histoire.");
    }

    @Test
    void playerCountAboveTwoPlayerStoryIsRejected() throws Exception {
        assertRejected(
                roomJson(story(2), 3, PASSWORD),
                400,
                "Cette histoire se joue à 2 joueurs exactement.");
    }

    @Test
    void missingPlayerCountIsRejected() throws Exception {
        assertRejected(
                roomJson(story(4), null, PASSWORD), 400, "Le nombre de joueurs est obligatoire.");
    }

    @Test
    void passwordLengthLimitsAreEnforced() throws Exception {
        String message = "Le mot de passe de la room doit contenir entre 4 et 64 caractères.";
        UUID story = story(2);

        assertRejected(roomJson(story, 2, "abc"), 400, message);
        assertRejected(roomJson(story, 2, "a".repeat(65)), 400, message);
        assertRejected(roomJson(story, 2, null), 400, message);

        create(roomJson(story, 2, "abcd")).andExpect(status().isCreated());
        create(roomJson(story, 2, "a".repeat(64))).andExpect(status().isCreated());
    }

    @Test
    void unknownStoryIsNotFound() throws Exception {
        assertRejected(
                roomJson(UUID.randomUUID(), 2, PASSWORD), 404, "Cette histoire n'existe pas.");
    }

    @Test
    void missingStoryIsRejected() throws Exception {
        assertRejected(
                "{\"playerCount\":2,\"password\":\"" + PASSWORD + "\"}",
                400,
                "Choisis une histoire.");
    }

    @Test
    void malformedBodyIsRejected() throws Exception {
        assertRejected(
                "{\"storyId\":\"" + story(2) + "\",\"playerCount\":\"deux\"}",
                400,
                "Requête invalide.");
    }

    // --- Reading a room ---

    @Test
    void creatorReadsRoomByCodeIgnoringCase() throws Exception {
        UUID story = story(3);
        String code = code(create(roomJson(story, 2, PASSWORD)));

        mvc.perform(get("/api/rooms/" + code.toLowerCase()).cookie(creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.shareUrl").value("http://localhost:5173/room/" + code))
                .andExpect(jsonPath("$.story.id").value(story.toString()))
                .andExpect(jsonPath("$.story.maxPlayers").value(3))
                .andExpect(jsonPath("$.playerCount").value(2))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.participants[0].pseudo").value(creatorPseudo))
                .andExpect(jsonPath("$.participants[0].isCreator").value(true));
    }

    @Test
    void nonParticipantsGetSameAnswerAsUnknownRoom() throws Exception {
        String code = code(create(roomJson(story(2), 2, PASSWORD)));

        for (Cookie other : new Cookie[] {accessCookie(register()), guest()}) {
            mvc.perform(get("/api/rooms/" + code).cookie(other))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").value("Cette room n'existe pas."));
        }
        mvc.perform(get("/api/rooms/ZZZZZZZZ").cookie(creator))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cette room n'existe pas."));
    }

    // --- Catalogue ---

    @Test
    void catalogueIsOpenToAccountsAndGuestsWithoutStorageDetails() throws Exception {
        UUID story = story(3);

        for (Cookie reader : new Cookie[] {creator, guest()}) {
            String body =
                    mvc.perform(get("/api/stories").cookie(reader))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
            assertThat(body).doesNotContain("pdf");
            mvc.perform(get("/api/stories/" + story).cookie(reader))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.maxPlayers").value(3))
                    .andExpect(jsonPath("$.characters[0]").value("Narrateur"))
                    .andExpect(jsonPath("$.pdfKey").doesNotExist())
                    .andExpect(jsonPath("$.pdfSize").doesNotExist());
        }
        mvc.perform(get("/api/stories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/stories/" + UUID.randomUUID()).cookie(creator))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORY_NOT_FOUND"));
    }

    // --- Helpers ---

    /** A refused creation leaves no room behind. */
    private void assertRejected(String json, int status, String message) throws Exception {
        long rooms = roomRepository.count();

        create(json).andExpect(status().is(status)).andExpect(jsonPath("$.message").value(message));

        assertThat(roomRepository.count()).isEqualTo(rooms);
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(createRequest(json).cookie(creator));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            createRequest(String json) {
        return post("/api/rooms").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static String roomJson(UUID storyId, Integer playerCount, String password) {
        return "{\"storyId\":\""
                + storyId
                + "\",\"playerCount\":"
                + playerCount
                + ",\"password\":"
                + (password == null ? "null" : "\"" + password + "\"")
                + "}";
    }

    /** A story with as many characters as players, saved directly: rooms never read its PDF. */
    private UUID story(int maxPlayers) {
        Story story =
                new Story(
                        "Histoire " + UUID.randomUUID(),
                        "Une description.",
                        maxPlayers,
                        "stories/" + UUID.randomUUID() + "/story.pdf",
                        1,
                        null,
                        clock.instant());
        story.addCharacter("Narrateur", true);
        for (int i = 1; i < maxPlayers; i++) {
            story.addCharacter("Personnage " + i, false);
        }
        return storyRepository.save(story).getId();
    }

    private UUID roomIdOf(String code) {
        return roomRepository.findByCode(code).orElseThrow().getId();
    }

    private static String code(ResultActions result) throws Exception {
        return code(result.andReturn());
    }

    private static String code(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
    }

    private static org.hamcrest.Matcher<String> matchesCode() {
        return org.hamcrest.Matchers.matchesPattern(RoomCodeGenerator.CODE_PATTERN);
    }

    private MvcResult register() throws Exception {
        return mvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private Cookie guest() throws Exception {
        return accessCookie(
                mvc.perform(post("/api/auth/guest")).andExpect(status().isOk()).andReturn());
    }

    private static Cookie accessCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(AuthCookies.ACCESS);
        assertThat(cookie).isNotNull();
        return new Cookie(AuthCookies.ACCESS, cookie.getValue());
    }
}
