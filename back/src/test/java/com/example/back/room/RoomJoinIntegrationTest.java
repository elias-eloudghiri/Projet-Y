package com.example.back.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.back.MutableClock;
import com.example.back.TestcontainersConfiguration;
import com.example.back.auth.AuthCookies;
import com.example.back.story.Story;
import com.example.back.story.StoryRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** US-05: joining a room with its password. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RoomJoinIntegrationTest {

    private static final String PASSWORD = "secret-room";
    private static final String BLUE = RoomColor.BLUE.hex();
    private static final String PINK = RoomColor.PINK.hex();
    private static final String AMBER = RoomColor.AMBER.hex();

    @Autowired private MockMvc mvc;

    @Autowired private StoryRepository storyRepository;

    @Autowired private JdbcTemplate jdbc;

    @Autowired private MutableClock clock;

    private Cookie creator;

    @BeforeEach
    void registerCreator() throws Exception {
        clock.reset();
        creator = accessCookie(register());
    }

    // --- Entering ---

    @Test
    void rightPasswordEntersWithFirstFreeColour() throws Exception {
        String code = room(4);
        Cookie player = accessCookie(register());

        join(code, PASSWORD, player)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participant.color").value(PINK))
                .andExpect(jsonPath("$.participant.isMe").value(true))
                .andExpect(jsonPath("$.participant.isGuest").value(false))
                .andExpect(jsonPath("$.participant.isCreator").value(false))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andExpect(jsonPath("$.participants[0].color").value(BLUE))
                .andExpect(jsonPath("$.participants[0].isCreator").value(true))
                .andExpect(jsonPath("$.participants[0].isMe").value(false))
                .andExpect(jsonPath("$.participants[1].isMe").value(true));

        assertThat(participantCount(code)).isEqualTo(2);
        mvc.perform(get("/api/rooms/" + code).cookie(player))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants.length()").value(2));
    }

    @Test
    void codeIsCaseInsensitive() throws Exception {
        String code = room(2);

        join(code.toLowerCase(), PASSWORD, guest()).andExpect(status().isOk());

        assertThat(participantCount(code)).isEqualTo(2);
    }

    @Test
    void guestAndAccountJoinTheSameRoom() throws Exception {
        String code = room(3);
        Cookie account = accessCookie(register());
        Cookie guest = guest();

        join(code, PASSWORD, account)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participant.color").value(PINK));
        join(code, PASSWORD, guest)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participant.color").value(AMBER))
                .andExpect(jsonPath("$.participant.isGuest").value(true));

        mvc.perform(get("/api/rooms/" + code + "/participants").cookie(guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].isCreator").value(true))
                .andExpect(jsonPath("$[0].isGuest").value(false))
                .andExpect(jsonPath("$[1].isGuest").value(false))
                .andExpect(jsonPath("$[1].isMe").value(false))
                .andExpect(jsonPath("$[2].isGuest").value(true))
                .andExpect(jsonPath("$[2].isMe").value(true))
                .andExpect(jsonPath("$[2].color").value(AMBER));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM room_participants p JOIN rooms r ON r.id ="
                                        + " p.room_id WHERE r.code = ? AND p.guest_id IS NOT NULL",
                                Integer.class,
                                code))
                .isEqualTo(1);
    }

    @Test
    void rejoiningKeepsSeatAndColour() throws Exception {
        String code = room(3);
        Cookie guest = guest();
        join(code, PASSWORD, guest).andExpect(status().isOk());

        // Even without the right password: the participant is already in.
        for (String password : new String[] {PASSWORD, "wrong-password"}) {
            join(code, password, guest)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.participant.color").value(PINK))
                    .andExpect(jsonPath("$.participants.length()").value(2));
        }
        join(code, PASSWORD, creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participant.color").value(BLUE))
                .andExpect(jsonPath("$.participant.isCreator").value(true));

        assertThat(participantCount(code)).isEqualTo(2);
    }

    // --- Refusals ---

    @Test
    void wrongPasswordIsForbiddenWithClearMessage() throws Exception {
        String code = room(4);

        join(code, "wrong-password", guest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_WRONG_PASSWORD"))
                .andExpect(jsonPath("$.message").value("Mot de passe incorrect."));

        assertThat(participantCount(code)).isEqualTo(1);
    }

    @Test
    void missingPasswordIsRejectedWithoutCountingAsFailure() throws Exception {
        String code = room(4);
        Cookie guest = guest();

        for (int i = 0; i < 6; i++) {
            join(code, "", guest)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Saisis le mot de passe de la room."));
        }
        join(code, PASSWORD, guest).andExpect(status().isOk());
    }

    @Test
    void unknownRoomIsNotFound() throws Exception {
        join("ZZZZZZZZ", PASSWORD, guest())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Cette room n'existe pas."));
    }

    @Test
    void fullRoomIsRefused() throws Exception {
        String code = room(2);
        join(code, PASSWORD, guest()).andExpect(status().isOk());

        join(code, PASSWORD, guest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_FULL"))
                .andExpect(jsonPath("$.message").value("Cette room est complète."));
        // The password is checked first: a full room reveals nothing without it.
        join(code, "wrong-password", guest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_WRONG_PASSWORD"));

        assertThat(participantCount(code)).isEqualTo(2);
    }

    @Test
    void startedRoomIsRefused() throws Exception {
        String code = room(4);
        jdbc.update("UPDATE rooms SET status = 'PLAYING' WHERE code = ?", code);

        join(code, PASSWORD, guest())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_ALREADY_STARTED"))
                .andExpect(
                        jsonPath("$.message").value("La lecture a déjà commencé dans cette room."));

        assertThat(participantCount(code)).isEqualTo(1);
    }

    @Test
    void anonymousIsUnauthenticated() throws Exception {
        String code = room(4);

        mvc.perform(joinRequest(code, PASSWORD)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/rooms/" + code + "/participants"))
                .andExpect(status().isUnauthorized());
    }

    // --- Brute force ---

    @Test
    void sixthWrongPasswordWithinAMinuteIsTooManyAttempts() throws Exception {
        String code = room(4);
        Cookie guest = guest();
        for (int i = 0; i < 5; i++) {
            join(code, "wrong-" + i, guest).andExpect(status().isForbidden());
        }

        join(code, "wrong-5", guest)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.code").value("ROOM_TOO_MANY_ATTEMPTS"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Trop de mots de passe erronés. Réessaie dans 60 s."));
        // Checked before the password: the right one is refused too until the window ends.
        join(code, PASSWORD, guest).andExpect(status().isTooManyRequests());
        // Per (IP, room): another IP is not blocked.
        mvc.perform(joinRequest(code, PASSWORD).cookie(guest()).with(fromIp("10.0.0.2")))
                .andExpect(status().isOk());

        clock.advance(Duration.ofMinutes(1));
        join(code, PASSWORD, guest).andExpect(status().isOk());
        assertThat(participantCount(code)).isEqualTo(3);
    }

    // --- Concurrency ---

    @Test
    void concurrentJoinsNeverExceedCapacityNorShareAColour() throws Exception {
        String code = room(4);
        int players = 6;
        List<Cookie> guests = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            guests.add(guest());
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(players)) {
            for (Cookie guest : guests) {
                statuses.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return join(code, PASSWORD, guest)
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            }
            start.countDown();
        }

        List<Integer> results = new ArrayList<>();
        for (Future<Integer> status : statuses) {
            results.add(status.get());
        }
        assertThat(results).filteredOn(s -> s == 200).hasSize(3);
        assertThat(results).filteredOn(s -> s == 409).hasSize(3);
        List<String> colours =
                jdbc.queryForList(
                        "SELECT p.color FROM room_participants p JOIN rooms r ON r.id = p.room_id"
                                + " WHERE r.code = ?",
                        String.class,
                        code);
        assertThat(colours).hasSize(4).doesNotHaveDuplicates();
    }

    // --- Participants ---

    @Test
    void participantsAreReservedToParticipants() throws Exception {
        String code = room(4);

        mvc.perform(get("/api/rooms/" + code + "/participants").cookie(guest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_FORBIDDEN"));
        mvc.perform(get("/api/rooms/ZZZZZZZZ/participants").cookie(creator))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        mvc.perform(get("/api/rooms/" + code + "/participants").cookie(creator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].isMe").value(true))
                .andExpect(jsonPath("$[0].isCreator").value(true));
    }

    // --- Helpers ---

    private ResultActions join(String code, String password, Cookie player) throws Exception {
        // Distinct entry times: participants are listed by entry time.
        clock.advance(Duration.ofNanos(1_000));
        return mvc.perform(joinRequest(code, password).cookie(player));
    }

    private static MockHttpServletRequestBuilder joinRequest(String code, String password) {
        return post("/api/rooms/" + code + "/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + password + "\"}");
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor fromIp(
            String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    /** A room created by {@link #creator}, on a story saved directly (rooms never read its PDF). */
    private String room(int playerCount) throws Exception {
        Story story =
                new Story(
                        "Histoire " + UUID.randomUUID(),
                        "Une description.",
                        playerCount,
                        "stories/" + UUID.randomUUID() + "/story.pdf",
                        1,
                        null,
                        clock.instant());
        story.addCharacter("Narrateur", true);
        for (int i = 1; i < playerCount; i++) {
            story.addCharacter("Personnage " + i, false);
        }
        UUID storyId = storyRepository.save(story).getId();
        MvcResult result =
                mvc.perform(
                                post("/api/rooms")
                                        .cookie(creator)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"storyId\":\""
                                                        + storyId
                                                        + "\",\"playerCount\":"
                                                        + playerCount
                                                        + ",\"password\":\""
                                                        + PASSWORD
                                                        + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.code");
    }

    private int participantCount(String code) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM room_participants p JOIN rooms r ON r.id = p.room_id"
                        + " WHERE r.code = ?",
                Integer.class,
                code);
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
