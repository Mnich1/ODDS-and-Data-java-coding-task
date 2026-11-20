package com.odds.scoreboard;

import java.time.Duration;
import java.util.Objects;

/**
 * One scoring action: the credited team, the player, the points and the elapsed match time.
 *
 * @param team          team credited with the points
 * @param shirtNumber   0 to 99
 * @param playerSurname the player's known name
 * @param points        at least 1
 * @param matchTime     elapsed match time since kick-off, not negative, no upper bound
 * @param scoreId       caller-supplied id, unique within a match
 */
public record Score(String team, int shirtNumber, String playerSurname, int points, Duration matchTime,
                    String scoreId) {

    public Score {
        PlayerKey player = new PlayerKey(team, shirtNumber, playerSurname);
        team = player.team();
        playerSurname = player.playerSurname();
        if (points < 1) {
            throw new IllegalArgumentException("points must be at least 1 but was " + points);
        }
        Objects.requireNonNull(matchTime, "matchTime must not be null");
        if (matchTime.isNegative()) {
            throw new IllegalArgumentException("matchTime must not be negative but was " + matchTime);
        }
        scoreId = Names.normalize(scoreId, "scoreId");
    }

    PlayerKey playerKey() {
        return new PlayerKey(team, shirtNumber, playerSurname);
    }

    /**
     * A football goal: a {@link Score} worth one point.
     */
    public static Score goal(String team, int shirtNumber, String playerSurname, Duration matchTime,
                             String scoreId) {
        return new Score(team, shirtNumber, playerSurname, 1, matchTime, scoreId);
    }
}
