package com.odds.scoreboard;

/**
 * A change on the board, published to {@link ScoreboardListener}s after it succeeded.
 * Exactly one event is published per successful operation.
 */
public sealed interface ScoreboardEvent {

    /**
     * A match started at 0-0.
     */
    record MatchStarted(Match match) implements ScoreboardEvent {
    }

    /**
     * A score was recorded. {@code match} is the resulting match. {@code score} is the exact score
     * recorded; it is not necessarily the last element of {@code match.scores()}, because events
     * follow arrival order while the list is chronological.
     */
    record ScoreRecorded(Match match, Score score) implements ScoreboardEvent {
    }

    /**
     * The last score of a match was removed. {@code match} is the resulting match and {@code score}
     * is the score that was removed.
     */
    record ScoreRemoved(Match match, Score score) implements ScoreboardEvent {
    }

    /**
     * A match finished. {@code finalResult} holds the final score and the full list of scores.
     */
    record MatchFinished(Match finalResult) implements ScoreboardEvent {
    }
}
