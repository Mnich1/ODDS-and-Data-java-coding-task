package com.odds.scoreboard;

/**
 * Thrown when an operation targets a match that is not live: never started, or already finished.
 */
public class MatchNotFoundException extends ScoreboardException {

    private static final long serialVersionUID = 1L;

    MatchNotFoundException(String homeTeam, String awayTeam) {
        super("No live match " + homeTeam + " - " + awayTeam);
    }
}
