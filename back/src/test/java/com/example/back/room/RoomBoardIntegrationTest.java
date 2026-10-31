package com.example.back.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import java.util.Map;
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

/** US-06: attribution board, automatic attribution on join, moves and start of the reading. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RoomBoardIntegrationTest {

    private static final String PASSWORD = "secret-room";

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

    // --- Automatic attribution ---

    @Test
    void creatorGetsEveryCharacterAtCreation() throws Exception {
        String code = room(3, 4);

        Board board = board(code, creator);

        assertThat(board.players()).hasSize(1);
        assertThat(board.characters())
                .hasSize(4)
                .allSatisfy(c -> assertThat(c.player()).isEqualTo(board.meId()));
        assertThat(board.characters().getFirst().name()).isEqualTo("Narrateur");
        assertThat(board.characters().getFirst().narrator()).isTrue();
        assertThat(board.version()).isZero();
    }

    @Test
    void joiningPlayerGetsCharactersFromTheBestServed() throws Exception {
        String code = room(3, 4);
        Cookie second = guest();
        join(code, second);

        Board two = board(code, second);
        assertThat(two.version()).isEqualTo(1);
        // The newcomer gets the first characters in story order, the narrator included.
        assertThat(two.characters().stream().map(Character::player))
                .containsExactly(two.meId(), two.meId(), creatorId(two), creatorId(two));

        join(code, guest());
        Board three = board(code, creator);
        assertThat(three.counts()).containsExactly(1, 2, 1);
    }

    @Test
    void joinAfterAManualChangeOnlyFeedsTheNewcomer() throws Exception {
        String code = room(3, 4);
        Cookie second = guest();
        join(code, second);
        Board before = board(code, second);
        // The creator takes the narrator back: creator 3, second player 1.
        assign(code, before.characters().get(0).id(), creatorId(before), creator)
                .andExpect(status().isOk());

        join(code, guest());

        Board after = board(code, creator);
        assertThat(after.counts()).containsExactly(2, 1, 1);
        assertThat(after.characters().get(1).player()).isEqualTo(before.meId());
        assertThat(after.characters().get(0).player()).isEqualTo(after.players().get(2));
    }

    // --- Moves ---

    @Test
    void creatorMovesACharacterAndReloadGetsTheSameState() throws Exception {
        String code = room(2, 3);
        Cookie guest = guest();
        join(code, guest);
        Board before = board(code, guest);
        UUID character = before.characters().get(2).id();

        assign(code, character, before.meId(), creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(before.version() + 1))
                .andExpect(jsonPath("$.characters[2].playerId").value(before.meId().toString()));

        Board reloaded = board(code, guest);
        assertThat(reloaded.version()).isEqualTo(before.version() + 1);
        assertThat(reloaded.characters().get(2).player()).isEqualTo(before.meId());
    }

    @Test
    void sameMoveTwiceChangesNothing() throws Exception {
        String code = room(2, 3);
        Board before = board(code, creator);

        assign(code, before.characters().getFirst().id(), before.meId(), creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(before.version()));
    }

    @Test
    void otherPlayersCannotMoveCharacters() throws Exception {
        String code = room(2, 3);
        Cookie guest = guest();
        join(code, guest);
        Board before = board(code, guest);

        assign(code, before.characters().get(2).id(), before.meId(), guest)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_CREATOR"));

        assertThat(board(code, guest).version()).isEqualTo(before.version());
    }

    @Test
    void nonParticipantsCannotSeeNorChangeTheBoard() throws Exception {
        String code = room(2, 3);
        Board board = board(code, creator);
        Cookie outsider = guest();

        mvc.perform(get("/api/rooms/" + code + "/board").cookie(outsider))
                .andExpect(status().isNotFound());
        assign(code, board.characters().getFirst().id(), board.meId(), outsider)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void characterOfAnotherStoryIsRefused() throws Exception {
        String code = room(2, 3);
        Board board = board(code, creator);
        UUID foreign = board(room(2, 3), creator).characters().getFirst().id();

        assign(code, foreign, board.meId(), creator)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHARACTER_NOT_FOUND"));
        assign(code, UUID.randomUUID(), board.meId(), creator).andExpect(status().isNotFound());
    }

    @Test
    void playerOfAnotherRoomIsRefused() throws Exception {
        String code = room(2, 3);
        Board board = board(code, creator);
        UUID foreign = board(room(2, 3), creator).meId();

        assign(code, board.characters().getFirst().id(), foreign, creator)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAYER_NOT_FOUND"));
        mvc.perform(
                        put("/api/rooms/"
                                        + code
                                        + "/assignments/"
                                        + board.characters().getFirst().id())
                                .cookie(creator)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        put("/api/rooms/" + code + "/assignments/not-an-id")
                                .cookie(creator)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"participantId\":\"" + board.meId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /** Several tabs of the creator moving at once: each character keeps exactly one player. */
    @Test
    void concurrentMovesKeepOnePlayerPerCharacter() throws Exception {
        String code = room(2, 4);
        Cookie guest = guest();
        join(code, guest);
        Board board = board(code, creator);
        List<UUID> players = board.players();
        int moves = 16;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < moves; i++) {
                UUID character = board.characters().get(i % 4).id();
                UUID player = players.get(i % 2);
                results.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return assign(code, character, player, creator)
                                            .andReturn()
                                            .getResponse()
                                            .getStatus();
                                }));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(200);
            }
        }

        Integer rows =
                jdbc.queryForObject(
                        "SELECT count(*) FROM room_assignments a JOIN rooms r ON r.id = a.room_id"
                                + " WHERE r.code = ?",
                        Integer.class,
                        code);
        assertThat(rows).isEqualTo(4);
        Board after = board(code, creator);
        assertThat(after.characters()).allSatisfy(c -> assertThat(players).contains(c.player()));
    }

    // --- Start ---

    @Test
    void startNeedsEveryPlayer() throws Exception {
        String code = room(3, 3);
        join(code, guest());

        start(code, creator)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_READY"));
    }

    @Test
    void startNeedsACharacterForEachPlayer() throws Exception {
        String code = room(2, 3);
        Cookie guest = guest();
        join(code, guest);
        Board board = board(code, creator);
        for (Character c : board.characters()) {
            assign(code, c.id(), board.meId(), creator).andExpect(status().isOk());
        }

        start(code, creator)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_READY"));
    }

    @Test
    void onlyTheCreatorStartsTheReading() throws Exception {
        String code = room(2, 3);
        Cookie guest = guest();
        join(code, guest);

        start(code, guest)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_CREATOR"));
    }

    @Test
    void startPutsTheRoomInReadingAndMovesStayPossible() throws Exception {
        String code = room(2, 3);
        Cookie guest = guest();
        join(code, guest);
        Board board = board(code, guest);

        start(code, creator)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLAYING"));

        assertThat(board(code, guest).status()).isEqualTo("PLAYING");
        start(code, creator)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROOM_ALREADY_STARTED"));
        // The move command does not depend on the status: US-07 reuses it during the reading.
        assign(code, board.characters().get(2).id(), board.meId(), creator)
                .andExpect(status().isOk());
    }

    // --- Helpers ---

    private record Character(UUID id, String name, boolean narrator, UUID player) {}

    /** The board as seen by a player; players in arrival order. */
    private record Board(
            UUID meId,
            long version,
            String status,
            List<UUID> players,
            List<Character> characters) {

        /** Number of characters of each player, in arrival order. */
        List<Integer> counts() {
            return players.stream()
                    .map(p -> (int) characters.stream().filter(c -> c.player().equals(p)).count())
                    .toList();
        }
    }

    private static UUID creatorId(Board board) {
        return board.players().getFirst();
    }

    private Board board(String code, Cookie player) throws Exception {
        String json =
                mvc.perform(get("/api/rooms/" + code + "/board").cookie(player))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        List<Map<String, Object>> characters = JsonPath.read(json, "$.state.characters");
        List<String> players = JsonPath.read(json, "$.state.players[*].id");
        return new Board(
                UUID.fromString(JsonPath.read(json, "$.meId")),
                JsonPath.<Number>read(json, "$.state.version").longValue(),
                JsonPath.read(json, "$.state.status"),
                players.stream().map(UUID::fromString).toList(),
                characters.stream()
                        .map(
                                c ->
                                        new Character(
                                                UUID.fromString((String) c.get("id")),
                                                (String) c.get("name"),
                                                (Boolean) c.get("isNarrator"),
                                                UUID.fromString((String) c.get("playerId"))))
                        .toList());
    }

    private ResultActions assign(String code, UUID character, UUID participant, Cookie player)
            throws Exception {
        return mvc.perform(
                put("/api/rooms/" + code + "/assignments/" + character)
                        .cookie(player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"participantId\":\"" + participant + "\"}"));
    }

    private ResultActions start(String code, Cookie player) throws Exception {
        return mvc.perform(post("/api/rooms/" + code + "/start").cookie(player));
    }

    private void join(String code, Cookie player) throws Exception {
        // Distinct entry times: participants are listed by entry time.
        clock.advance(Duration.ofNanos(1_000));
        mvc.perform(
                        post("/api/rooms/" + code + "/join")
                                .cookie(player)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    /** A room created by {@link #creator}, on a story saved directly (rooms never read its PDF). */
    private String room(int playerCount, int characterCount) throws Exception {
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
        for (int i = 1; i < characterCount; i++) {
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
