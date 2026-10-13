package com.example.back.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PseudoGeneratorTest {

    private final PseudoGenerator generator = new PseudoGenerator();

    @Test
    void generatesAnimalDashSixDigits() {
        for (int i = 0; i < 1_000; i++) {
            String pseudo = generator.generate();
            assertThat(pseudo).matches(PseudoGenerator.FORMAT);
            assertThat(PseudoGenerator.ANIMALS).contains(pseudo.substring(0, pseudo.indexOf('-')));
        }
    }

    @Test
    void usesSeveralAnimals() {
        Set<String> animals = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String pseudo = generator.generate();
            animals.add(pseudo.substring(0, pseudo.indexOf('-')));
        }
        assertThat(animals.size()).isGreaterThan(50);
    }

    @Test
    void animalNamesFitTheDatabaseFormat() {
        assertThat(PseudoGenerator.ANIMALS)
                .allSatisfy(
                        animal ->
                                assertThat(animal + "-000000")
                                        .matches(PseudoGenerator.FORMAT)
                                        .hasSizeLessThanOrEqualTo(32))
                .doesNotHaveDuplicates();
    }

    @Test
    void normalizeIgnoresCaseAndSurroundingSpaces() {
        assertThat(PseudoGenerator.normalize("  LeRat-000999 \t")).isEqualTo("lerat-000999");
        assertThat(PseudoGenerator.normalize(null)).isEmpty();
    }
}
