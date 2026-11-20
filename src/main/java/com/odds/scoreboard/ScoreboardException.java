package com.odds.scoreboard;

/**
 * Base type for scoreboard state violations. Invalid arguments are reported with
 * {@link NullPointerException} and {@link IllegalArgumentException} instead.
 */
public abstract class ScoreboardException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ScoreboardException(String message) {
        super(message);
    }
}
