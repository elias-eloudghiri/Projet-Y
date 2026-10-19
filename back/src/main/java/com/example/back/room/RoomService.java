package com.example.back.room;

import com.example.back.story.Story;
import com.example.back.story.StoryService;
import com.example.back.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final Clock clock;

    public record NewRoom(UUID storyId, Integer playerCount, String password) {}

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
                        .orElseThrow(
                                () ->
                                        new RoomException(
                                                HttpStatus.UNAUTHORIZED,
                                                "UNAUTHENTICATED",
                                                "Session invalide. Reconnecte-toi."))
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
    public Room getForParticipant(String code, UUID subject, boolean guest) {
        return repository
                .findByCode(code)
                .filter(room -> room.hasParticipant(subject, guest))
                .orElseThrow(RoomException::notFound);
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
