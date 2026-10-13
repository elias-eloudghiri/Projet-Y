package com.example.back.user;

public enum Role {
    USER,
    ADMIN,
    /** Visitor without an account. Only lives in a JWT: never persisted in {@code users}. */
    GUEST
}
