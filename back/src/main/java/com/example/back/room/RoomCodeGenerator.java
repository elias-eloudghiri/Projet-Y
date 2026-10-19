package com.example.back.room;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Random, non-sequential room codes, easy to read aloud: no 0/O nor 1/I/l. */
@Component
public class RoomCodeGenerator {

    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int LENGTH = 8;

    /** Same rule as the rooms_code_format constraint. */
    public static final String CODE_PATTERN = "[" + ALPHABET + "]{" + LENGTH + "}";

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
