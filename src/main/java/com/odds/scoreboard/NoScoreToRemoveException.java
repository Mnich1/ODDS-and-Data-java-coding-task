package com.odds.scoreboard;

/**
 * Thrown when the last score of a live match is removed but the match has no scores.
 */
public class NoScoreToRemoveException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    NoScoreToRemoveException(String homeTeam, String awayTeam) {
        super("No score to remove in " + homeTeam + " - " + awayTeam);
    }
}
