package com.example.back.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomCodeGeneratorTest {

    private final RoomCodeGenerator generator = new RoomCodeGenerator();

    @Test
    void codesHaveEightUnambiguousCharacters() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.generate()).matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}");
        }
    }

    @Test
    void alphabetExcludesAmbiguousCharacters() {
        assertThat(RoomCodeGenerator.ALPHABET).doesNotContain("0", "O", "1", "I", "l", "L");
    }

    @Test
    void codesAreRandom() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generate());
        }
        assertThat(codes).hasSize(1000);
    }
}
