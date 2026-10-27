package com.example.back.room;

import com.example.back.auth.AccessTokenService;
import com.example.back.story.Story;
import com.example.back.story.StoryService;
import com.example.back.user.Role;
import com.example.back.user.UserRepository;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class RoomService {

    public static final int MIN_PLAYERS = StoryService.MIN_PLAYERS;
    public static final int PASSWORD_MIN_LENGTH = 4;
    public static final int PASSWORD_MAX_LENGTH = 64;

    /** With 31^8 possible codes, a second collision in a row is already unlikely. */
    private static final int MAX_CODE_ATTEMPTS = 10;

    private final RoomRepository repository;
    private final StoryService storyService;
    private final UserRepository userRepository;
    private final RoomCodeGenerator codeGenerator;
    private final PasswordEncoder passwordEncoder;
    private final RoomJoinAttemptService attemptService;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public record NewRoom(UUID storyId, Integer playerCount, String password) {}

    /**
     * Identity of the caller, from its access token.
     *
     * @param subject user id for accounts, guest id for guests
     */
    public record Player(UUID subject, boolean guest, String pseudo) {

        public static Player from(Jwt jwt) {
            return new Player(
                    UUID.fromString(jwt.getSubject()),
                    Role.GUEST.name().equals(jwt.getClaimAsString(AccessTokenService.CLAIM_ROLE)),
                    jwt.getClaimAsString(AccessTokenService.CLAIM_PSEUDO));
        }
    }

    /** The room (participants loaded) and the caller's participation in it. */
    public record JoinResult(Room room, RoomParticipant participant) {}

    /**
     * Creates a waiting room whose first participant is its creator. Not transactional: a code
     * taken by a concurrent creation is retried with a new one.
     */
    public Room create(NewRoom request, UUID creatorId) {
        if (request.storyId() == null) {
            throw RoomException.invalid("Choisis une histoire.");
        }
        Story story = storyService.get(request.storyId());
        int playerCount = validatePlayerCount(request.playerCount(), story.getMaxPlayers());
        String passwordHash = passwordEncoder.encode(validatePassword(request.password()));
        String pseudo =
                userRepository
                        .findById(creatorId)
                        .orElseThrow(RoomService::unauthenticated)
                        .getPseudo();

        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = codeGenerator.generate();
            if (repository.existsByCode(code)) {
                continue;
            }
            Room room =
                    new Room(code, story, creatorId, passwordHash, playerCount, clock.instant());
            room.addCreator(creatorId, pseudo);
            try {
                return repository.saveAndFlush(room);
            } catch (DataIntegrityViolationException e) {
                // Same code saved by a concurrent creation since the check above.
                if (!repository.existsByCode(code)) {
                    throw e;
                }
            }
        }
        throw new IllegalStateException("Could not generate a unique room code");
    }

    /**
     * The room as seen by one of its participants. Unknown room and room the caller is not in get
     * the same answer, so that a code cannot be probed.
     */
    @Transactional(readOnly = true)
    public Room getForParticipant(String code, Player player) {
        return repository
                .findByCode(code)
                .filter(room -> room.hasParticipant(player.subject(), player.guest()))
                .orElseThrow(RoomException::notFound);
    }

    /** Whether the player may follow the room's live updates. */
    @Transactional(readOnly = true)
    public boolean isParticipant(String code, Player player) {
        return repository
                .findByCode(code)
                .map(room -> room.hasParticipant(player.subject(), player.guest()))
                .orElse(false);
    }

    /** Same as {@link #getForParticipant}, but tells a non-participant apart (403) from 404. */
    @Transactional(readOnly = true)
    public Room getParticipants(String code, Player player) {
        Room room = repository.findByCode(code).orElseThrow(RoomException::notFound);
        if (!room.hasParticipant(player.subject(), player.guest())) {
            throw RoomException.notParticipant();
        }
        return room;
    }

    /**
     * Enters a room, or returns the existing participation (no new seat taken). Checks, in order:
     * unknown room, already in, too many wrong passwords, password, room full, reading started. The
     * password is checked before the capacity so that nothing is revealed without it.
     *
     * <p>The costly password hash is checked before taking the lock; the room row is then locked
     * (SELECT … FOR UPDATE) so that concurrent joins neither exceed the capacity nor share a
     * colour. The unique constraints of room_participants are the safety net.
     */
    public JoinResult join(String code, String password, Player player, String ip) {
        Room room = repository.findByCode(code).orElseThrow(RoomException::notFound);
        Optional<RoomParticipant> existing = room.findParticipant(player.subject(), player.guest());
        if (existing.isPresent()) {
            return new JoinResult(room, existing.get());
        }
        attemptService
                .blockedFor(ip, code)
                .ifPresent(
                        retryAfter -> {
                            throw RoomException.tooManyAttempts(retryAfter);
                        });
        if (password == null || password.isEmpty()) {
            throw RoomException.invalid("Saisis le mot de passe de la room.");
        }
        if (!passwordEncoder.matches(password, room.getPasswordHash())) {
            attemptService.recordFailure(ip, code);
            throw RoomException.wrongPassword();
        }
        attemptService.reset(ip, code);
        if (!player.guest() && !userRepository.existsById(player.subject())) {
            throw unauthenticated();
        }
        return transactions.execute(tx -> enter(code, player));
    }

    private JoinResult enter(String code, Player player) {
        Room room = repository.lockByCode(code).orElseThrow(RoomException::notFound);
        // A concurrent request of the same player (double click) may have won the lock.
        Optional<RoomParticipant> existing = room.findParticipant(player.subject(), player.guest());
        if (existing.isPresent()) {
            return new JoinResult(room, existing.get());
        }
        if (room.isFull()) {
            throw RoomException.full();
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw RoomException.alreadyStarted();
        }
        RoomParticipant participant =
                room.addPlayer(player.subject(), player.guest(), player.pseudo(), clock.instant());
        repository.flush();
        // Sent to the room's subscribers once committed (see RoomBroadcaster).
        events.publishEvent(new RoomBroadcaster.ParticipantsChanged(code));
        return new JoinResult(room, participant);
    }

    private static RoomException unauthenticated() {
        return new RoomException(
                HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Session invalide. Reconnecte-toi.");
    }

    private static int validatePlayerCount(Integer playerCount, int maxPlayers) {
        if (playerCount == null) {
            throw RoomException.invalid("Le nombre de joueurs est obligatoire.");
        }
        if (playerCount < MIN_PLAYERS || playerCount > maxPlayers) {
            throw RoomException.invalid(
                    maxPlayers == MIN_PLAYERS
                            ? "Cette histoire se joue à " + MIN_PLAYERS + " joueurs exactement."
                            : "Le nombre de joueurs doit être compris entre "
                                    + MIN_PLAYERS
                                    + " et "
                                    + maxPlayers
                                    + " pour cette histoire.");
        }
        return playerCount;
    }

    private static String validatePassword(String password) {
        if (password == null
                || password.length() < PASSWORD_MIN_LENGTH
                || password.length() > PASSWORD_MAX_LENGTH) {
            throw RoomException.invalid(
                    "Le mot de passe de la room doit contenir entre "
                            + PASSWORD_MIN_LENGTH
                            + " et "
                            + PASSWORD_MAX_LENGTH
                            + " caractères.");
        }
        return password;
    }
}
