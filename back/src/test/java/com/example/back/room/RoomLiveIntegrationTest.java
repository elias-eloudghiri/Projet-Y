package com.example.back.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Live waiting room: a join is pushed over STOMP to the players already in the room. */
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

        String event = events.poll(TIMEOUT_S, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("PARTICIPANTS");
        List<Map<String, Object>> participants = JsonPath.read(event, "$.participants");
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

        join(code, "wrong-password", guest());
        join(code, PASSWORD, guest);

        assertThat(events.poll(1, TimeUnit.SECONDS)).isNull();
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
                "{\"type\":\"PARTICIPANTS\",\"participants\":[]}".getBytes(StandardCharsets.UTF_8));

        assertThat(sender.errors.poll(TIMEOUT_S, TimeUnit.SECONDS)).isNotNull();
        assertThat(events.poll(1, TimeUnit.SECONDS)).isNull();
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
