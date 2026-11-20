package com.odds.scoreboard;

import java.util.Objects;

/**
 * The single place where names and identifiers are normalised, so every operation agrees on them.
 */
final class Names {

    private Names() {
    }

    /**
     * Strips surrounding whitespace (Unicode-aware) and keeps case.
     *
     * @throws NullPointerException     if {@code value} is null
     * @throws IllegalArgumentException if {@code value} is blank
     */
    static String normalize(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return stripped;
    }
}
