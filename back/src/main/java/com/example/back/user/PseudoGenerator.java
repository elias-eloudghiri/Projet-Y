package com.example.back.user;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Generates random pseudos such as {@code lerat-000999}: an animal name and six digits.
 * The pseudo is both the login identifier and the display name.
 */
@Component
public class PseudoGenerator {

    public static final Pattern FORMAT = Pattern.compile("^[a-z]+-[0-9]{6}$");

    static final List<String> ANIMALS = List.of(
            "abeille", "albatros", "alpaga", "antilope", "autruche", "baleine", "belette", "biche",
            "bison", "blaireau", "bouquetin", "bourdon", "buffle", "caille", "calmar", "cameleon",
            "campagnol", "canard", "castor", "cerf", "chameau", "chamois", "chouette", "cigale",
            "cigogne", "coccinelle", "colibri", "corbeau", "crabe", "crapaud", "criquet", "cygne",
            "dauphin", "dromadaire", "ecureuil", "escargot", "faucon", "fennec", "flamant", "fouine",
            "fourmi", "gazelle", "gecko", "geai", "girafe", "gorille", "grenouille", "grillon",
            "guepard", "hamster", "hermine", "heron", "herisson", "hibou", "homard", "iguane",
            "jaguar", "koala", "lama", "lapin", "lemurien", "leopard", "lerat", "lezard",
            "libellule", "lion", "loir", "loup", "loutre", "lynx", "manchot", "marmotte",
            "merle", "mesange", "moineau", "morse", "mouette", "mulot", "narval", "okapi",
            "orque", "ours", "panda", "panthere", "papillon", "paon", "pelican", "phoque",
            "pie", "pingouin", "pinson", "poulpe", "putois", "raton", "renard", "requin",
            "rhino", "sanglier", "scarabee", "suricate", "tapir", "taupe", "tigre", "tortue",
            "toucan", "vautour", "wombat", "yak", "zebre");

    private static final int NUMBER_BOUND = 1_000_000;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        String animal = ANIMALS.get(random.nextInt(ANIMALS.size()));
        return animal + "-" + String.format(Locale.ROOT, "%06d", random.nextInt(NUMBER_BOUND));
    }

    /** Login input is case-insensitive and ignores surrounding whitespace. */
    public static String normalize(String input) {
        return input == null ? "" : input.strip().toLowerCase(Locale.ROOT);
    }
}
