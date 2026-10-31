package com.example.back.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.back.MutableClock;
import com.example.back.TestcontainersConfiguration;
import com.example.back.auth.AuthCookies;
import com.example.back.story.Story;
import com.example.back.story.StoryRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Live room state: joins, moves, presence and start are pushed over STOMP to the players of the
 * room, and to them only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class RoomLiveIntegrationTest {

    private static final String PASSWORD = "secret-room";
    private static final long TIMEOUT_S = 5;

    @LocalServerPort private int port;

    @Autowired private MockMvc mvc;

    @Autowired private StoryRepository storyRepository;

    @Autowired private MutableClock clock;

    @Autowired private SimpUserRegistry userRegistry;

    private final WebSocketStompClient stomp =
            new WebSocketStompClient(new StandardWebSocketClient());

    private Cookie creator;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        stomp.setMessageConverter(new SimpleMessageConverter());
        creator = accessCookie(register());
    }

    @AfterEach
    void tearDown() {
        stomp.stop();
    }

    @Test
    void creatorSeesNewParticipantWithoutReloading() throws Exception {
        String code = room(3);
        Live live = connect(creator);
        BlockingQueue<String> events = subscribed(live, RoomBroadcaster.topic(code));

        join(code, PASSWORD, guest());

        String event =
                awaitState(events, e -> JsonPath.<Integer>read(e, "$.state.players.length()") == 2);
        assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("STATE");
        List<Map<String, Object>> participants = JsonPath.read(event, "$.state.players");
        assertThat(participants).hasSize(2);
        assertThat(participants.get(0))
                .containsEntry("color", RoomColor.BLUE.hex())
                .containsEntry("isCreator", true)
                .containsEntry("isGuest", false);
        assertThat(participants.get(1))
                .containsEntry("color", RoomColor.PINK.hex())
                .containsEntry("isCreator", false)
                .containsEntry("isGuest", true)
                // Same message for everyone: no per-player field.
                .doesNotContainKey("isMe");
        assertThat(live.errors).isEmpty();
    }

    @Test
    void refusedOrRepeatedJoinsAreNotBroadcast() throws Exception {
        String code = room(3);
        Cookie guest = guest();
        join(code, PASSWORD, guest);
        Live live = connect(creator);
        BlockingQueue<String> events = subscribed(live, RoomBroadcaster.topic(code));

        long version = version(code, creator);

        join(code, "wrong-password", guest());
        join(code, PASSWORD, guest);

        assertNoChange(events, version);
    }

    @Test
    void moveIsPushedToOtherPlayersWithinASecond() throws Exception {
        String code = room(2);
        Cookie guest = guest();
        join(code, PASSWORD, guest);
        Live live = connect(guest);
        BlockingQueue<String> events = subscribed(live, RoomBroadcaster.topic(code));
        String board = board(code, guest);
        long version = JsonPath.<Number>read(board, "$.state.version").longValue();
        String guestId = JsonPath.read(board, "$.meId");
        // The creator kept the second character, the newcomer got the narrator.
        String character = JsonPath.read(board, "$.state.characters[1].id");

        long sent = System.nanoTime();
        mvc.perform(
                        put("/api/rooms/" + code + "/assignments/" + character)
                                .cookie(creator)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"participantId\":\"" + guestId + "\"}"))
                .andExpect(status().isOk());

        String event = awaitState(events, e -> stateVersion(e) > version);
        assertThat(Duration.ofNanos(System.nanoTime() - sent)).isLessThan(Duration.ofSeconds(1));
        assertThat(stateVersion(event)).isEqualTo(version + 1);
        assertThat((String) JsonPath.read(event, "$.state.characters[1].playerId"))
                .isEqualTo(guestId);
    }

    @Test
    void presenceOfPlayersIsPushed() throws Exception {
        String code = room(2);
        Cookie guest = guest();
        join(code, PASSWORD, guest);
        Live live = connect(creator);
        BlockingQueue<String> events = subscribed(live, RoomBroadcaster.topic(code));

        Live other = connect(guest);
        other.subscribe(RoomBroadcaster.topic(code));
        awaitState(events, e -> guestConnected(e));
        other.session.disconnect();

        awaitState(events, e -> !guestConnected(e));
    }

    @Test
    void startIsPushedToAllPlayers() throws Exception {
        String code = room(2);
        Cookie guest = guest();
        join(code, PASSWORD, guest);
        Live live = connect(guest);
        BlockingQueue<String> events = subscribed(live, RoomBroadcaster.topic(code));

        mvc.perform(post("/api/rooms/" + code + "/start").cookie(creator))
                .andExpect(status().isOk());

        awaitState(events, e -> "PLAYING".equals(JsonPath.read(e, "$.state.status")));
    }

    @Test
    void nonParticipantCannotFollowRoom() throws Exception {
        String code = room(3);
        Live outsider = connect(guest());
        BlockingQueue<String> events = outsider.subscribe(RoomBroadcaster.topic(code));

        assertThat(outsider.errors.poll(TIMEOUT_S, TimeUnit.SECONDS)).isNotNull();
        join(code, PASSWORD, guest());
        assertThat(events.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void onlyRoomTopicsCanBeFollowed() throws Exception {
        Live live = connect(creator);

        live.subscribe("/topic/rooms");

        assertThat(live.errors.poll(TIMEOUT_S, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void clientsCannotPublishToRoom() throws Exception {
        String code = room(3);
        Cookie guest = guest();
        join(code, PASSWORD, guest);
        Live listener = connect(creator);
        BlockingQueue<String> events = subscribed(listener, RoomBroadcaster.topic(code));
        Live sender = connect(guest);

        sender.session.send(
                RoomBroadcaster.topic(code),
                "{\"type\":\"STATE\",\"state\":{}}".getBytes(StandardCharsets.UTF_8));

        assertThat(sender.errors.poll(TIMEOUT_S, TimeUnit.SECONDS)).isNotNull();
        assertNoChange(events, version(code, creator));
    }

    @Test
    void anonymousHandshakeIsRefused() {
        assertThatThrownBy(() -> connect(new WebSocketHttpHeaders()))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    void foreignOriginIsRefused() {
        WebSocketHttpHeaders headers = handshakeHeaders(creator);
        headers.setOrigin("https://evil.example");

        assertThatThrownBy(() -> connect(headers)).isInstanceOf(ExecutionException.class);
    }

    // --- Helpers ---

    /**
     * Next state matching, skipping the others: a subscription also pushes the presence of its
     * player, at a moment the test does not control.
     */
    private static String awaitState(BlockingQueue<String> events, Predicate<String> matching)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S);
        while (true) {
            long left = deadline - System.nanoTime();
            String event = events.poll(Math.max(0, left), TimeUnit.NANOSECONDS);
            assertThat(event).as("expected state").isNotNull();
            if (matching.test(event)) {
                return event;
            }
        }
    }

    /** Only presence updates for a second: the version did not change. */
    private static void assertNoChange(BlockingQueue<String> events, long version)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        String event;
        while ((event =
                        events.poll(
                                Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
                != null) {
            assertThat(stateVersion(event)).isEqualTo(version);
        }
    }

    private static long stateVersion(String event) {
        return JsonPath.<Number>read(event, "$.state.version").longValue();
    }

    /** In a room of two, the guest is the second player. */
    private static boolean guestConnected(String event) {
        return JsonPath.<Boolean>read(event, "$.state.players[1].connected");
    }

    private String board(String code, Cookie player) throws Exception {
        return mvc.perform(get("/api/rooms/" + code + "/board").cookie(player))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private long version(String code, Cookie player) throws Exception {
        return JsonPath.<Number>read(board(code, player), "$.state.version").longValue();
    }

    /** A STOMP session, with the ERROR frames it received. */
    private record Live(StompSession session, BlockingQueue<String> errors) {

        BlockingQueue<String> subscribe(String destination) {
            BlockingQueue<String> events = new LinkedBlockingQueue<>();
            session.subscribe(destination, collector(events));
            return events;
        }
    }

    /** Subscribes and waits until the server has registered it, before anything is broadcast. */
    private BlockingQueue<String> subscribed(Live live, String destination) throws Exception {
        BlockingQueue<String> events = live.subscribe(destination);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_S);
        while (userRegistry
                .findSubscriptions(s -> destination.equals(s.getDestination()))
                .isEmpty()) {
            assertThat(System.nanoTime()).as("subscription registered").isLessThan(deadline);
            Thread.sleep(20);
        }
        return events;
    }

    private Live connect(Cookie cookie) throws Exception {
        return connect(handshakeHeaders(cookie));
    }

    private Live connect(WebSocketHttpHeaders headers) throws Exception {
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session =
                stomp.connectAsync(
                                "ws://localhost:" + port + "/ws",
                                headers,
                                new StompSessionHandlerAdapter() {
                                    @Override
                                    public Type getPayloadType(StompHeaders headers) {
                                        return byte[].class;
                                    }

                                    @Override
                                    public void handleFrame(StompHeaders headers, Object payload) {
                                        // Only ERROR frames reach the session handler.
                                        errors.add(String.valueOf(headers.get("message")));
                                    }
                                })
                        .get(TIMEOUT_S, TimeUnit.SECONDS);
        return new Live(session, errors);
    }

    private static WebSocketHttpHeaders handshakeHeaders(Cookie cookie) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        if (cookie != null) {
            headers.add("Cookie", cookie.getName() + "=" + cookie.getValue());
        }
        return headers;
    }

    private static StompFrameHandler collector(BlockingQueue<String> events) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                events.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        };
    }

    private void join(String code, String password, Cookie player) throws Exception {
        // Distinct entry times: participants are listed by entry time.
        clock.advance(Duration.ofNanos(1_000));
        mvc.perform(
                post("/api/rooms/" + code + "/join")
                        .cookie(player)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + password + "\"}"));
    }

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
