package com.odds.scoreboard;

/**
 * A live scoreboard holding several simultaneous matches.
 * <p>
 * Implementations are thread-safe. A match is identified by its (home, away) pair, and a team
 * can play at most one live match at a time. Team names are case-sensitive and stripped of
 * surrounding whitespace. Arguments are validated first: null throws {@link NullPointerException},
 * a blank or otherwise invalid value throws {@link IllegalArgumentException}, and a rejected
 * operation leaves the board unchanged.
 */
public interface Scoreboard {

    /**
     * Starts a match at 0-0.
     *
     * @return the new match
     * @throws TeamAlreadyPlayingException if either team is already playing a live match
     */
    Match startMatch(String homeTeam, String awayTeam);

    /**
     * Records one scoring action in a live match.
     *
     * @return the resulting match
     * @throws MatchNotFoundException   if the match is not live
     * @throws IllegalArgumentException if the score's team is not one of the two sides
     */
    Match updateScore(String homeTeam, String awayTeam, Score score);

    /**
     * Finishes a live match and removes it from the board.
     *
     * @return the final result
     * @throws MatchNotFoundException if the match is not live
     */
    Match finishMatch(String homeTeam, String awayTeam);
}
