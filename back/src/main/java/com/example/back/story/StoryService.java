package com.example.back.story;

import com.example.back.config.StoryProperties;
import com.example.back.storage.ObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class StoryService {

    public static final String NARRATOR_NAME = "Narrateur";
    public static final int TITLE_MAX_LENGTH = 120;
    public static final int DESCRIPTION_MAX_LENGTH = 2000;
    public static final int CHARACTER_NAME_MAX_LENGTH = 60;

    /** Characters besides the narrator. */
    public static final int MAX_CHARACTERS = 50;

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 4;

    static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final StoryRepository repository;
    private final ObjectStorage storage;
    private final StoryProperties properties;
    private final Clock clock;

    /**
     * @param characters the characters besides the narrator, who is always added first
     */
    public record NewStory(
            String title, String description, Integer playerCount, List<String> characters) {}

    /**
     * Validates the story, uploads its PDF then saves its metadata. The PDF is removed again if the
     * metadata cannot be saved, so no orphan file stays in the bucket.
     */
    public Story create(NewStory request, MultipartFile pdf, UUID adminId) {
        String title = strip(request.title());
        String description = strip(request.description());
        int playerCount = validatePlayerCount(request.playerCount());
        validateTitle(title);
        validateDescription(description);
        List<String> characters = validateCharacters(request.characters(), playerCount);
        validatePdf(pdf);
        if (repository.existsByTitleIgnoreCase(title)) {
            throw titleTaken();
        }

        Story story =
                new Story(
                        title,
                        description,
                        playerCount,
                        "stories/" + UUID.randomUUID() + "/story.pdf",
                        pdf.getSize(),
                        adminId,
                        clock.instant());
        story.addCharacter(NARRATOR_NAME, true);
        characters.forEach(name -> story.addCharacter(name, false));

        try (InputStream content = pdf.getInputStream()) {
            storage.put(story.getPdfKey(), content, pdf.getSize(), PDF_CONTENT_TYPE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try {
            return repository.save(story);
        } catch (RuntimeException e) {
            storage.deleteQuietly(story.getPdfKey());
            // Same title saved by a concurrent import since the check above.
            throw e instanceof DataIntegrityViolationException ? titleTaken() : e;
        }
    }

    @Transactional(readOnly = true)
    public List<Story> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    private static int validatePlayerCount(Integer playerCount) {
        if (playerCount == null || playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS) {
            throw StoryValidationException.invalid("Le nombre de joueurs doit être 2, 3 ou 4.");
        }
        return playerCount;
    }

    private static void validateTitle(String title) {
        if (title.isEmpty()) {
            throw StoryValidationException.invalid("Le titre est obligatoire.");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw StoryValidationException.invalid(
                    "Le titre doit contenir au plus " + TITLE_MAX_LENGTH + " caractères.");
        }
    }

    private static void validateDescription(String description) {
        if (description.isEmpty()) {
            throw StoryValidationException.invalid("La description est obligatoire.");
        }
        if (description.length() > DESCRIPTION_MAX_LENGTH) {
            throw StoryValidationException.invalid(
                    "La description doit contenir au plus "
                            + DESCRIPTION_MAX_LENGTH
                            + " caractères.");
        }
    }

    private static List<String> validateCharacters(List<String> input, int playerCount) {
        List<String> names = input == null ? List.of() : input;
        if (names.size() > MAX_CHARACTERS) {
            throw StoryValidationException.invalid(
                    "Une histoire compte au plus "
                            + MAX_CHARACTERS
                            + " personnages en plus du"
                            + " narrateur.");
        }
        Set<String> seen = new HashSet<>(Set.of(NARRATOR_NAME.toLowerCase(Locale.ROOT)));
        List<String> result = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = strip(names.get(i));
            if (name.isEmpty()) {
                throw StoryValidationException.invalid(
                        "Le nom du personnage n° " + (i + 1) + " est vide.");
            }
            if (name.length() > CHARACTER_NAME_MAX_LENGTH) {
                throw StoryValidationException.invalid(
                        "Le nom du personnage « "
                                + name
                                + " » doit contenir au plus "
                                + CHARACTER_NAME_MAX_LENGTH
                                + " caractères.");
            }
            if (name.equalsIgnoreCase(NARRATOR_NAME)) {
                throw StoryValidationException.invalid(
                        "Le narrateur est ajouté automatiquement : ne le saisis pas une seconde"
                                + " fois.");
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw StoryValidationException.invalid(
                        "Le personnage « " + name + " » apparaît plusieurs fois.");
            }
            result.add(name);
        }
        // Each player must embody at least one character.
        if (result.size() + 1 < playerCount) {
            throw StoryValidationException.invalid(
                    "Il faut au moins "
                            + playerCount
                            + " personnages, narrateur compris, pour "
                            + playerCount
                            + " joueurs : chaque joueur doit en incarner au moins un.");
        }
        return result;
    }

    private void validatePdf(MultipartFile pdf) {
        if (pdf == null || pdf.isEmpty()) {
            throw StoryValidationException.invalid("Le PDF de l'histoire est obligatoire.");
        }
        long maxSize = properties.pdfMaxSize().toBytes();
        if (pdf.getSize() > maxSize) {
            throw tooLarge(maxSize);
        }
        if (!PDF_CONTENT_TYPE.equalsIgnoreCase(pdf.getContentType()) || !hasPdfSignature(pdf)) {
            throw new StoryValidationException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "INVALID_FILE_TYPE",
                    "Le fichier « " + pdf.getOriginalFilename() + " » n'est pas un PDF.");
        }
    }

    /** The declared type comes from the browser: the file content must confirm it. */
    private static boolean hasPdfSignature(MultipartFile pdf) {
        try (InputStream content = pdf.getInputStream()) {
            return Arrays.equals(content.readNBytes(PDF_SIGNATURE.length), PDF_SIGNATURE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static StoryValidationException tooLarge(long maxSize) {
        return new StoryValidationException(
                HttpStatus.CONTENT_TOO_LARGE,
                "FILE_TOO_LARGE",
                "Le PDF dépasse la taille maximale de " + formatSize(maxSize) + ".");
    }

    private static StoryValidationException titleTaken() {
        return new StoryValidationException(
                HttpStatus.CONFLICT, "TITLE_TAKEN", "Une histoire porte déjà ce titre.");
    }

    static String formatSize(long bytes) {
        long mega = 1024 * 1024;
        if (bytes >= mega) {
            return bytes % mega == 0
                    ? bytes / mega + " Mo"
                    : String.format(Locale.FRANCE, "%.1f Mo", (double) bytes / mega);
        }
        return Math.max(1, bytes / 1024) + " Ko";
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
