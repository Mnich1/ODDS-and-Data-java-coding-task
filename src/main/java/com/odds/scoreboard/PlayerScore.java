package com.odds.scoreboard;

import java.util.List;
import java.util.Objects;

/**
 * Everything a player scored across the tournament, grouped per match so each score shows its
 * opponent. A player is the exact triple of team, shirt number and surname; team is the team
 * credited with the points.
 *
 * @param team          team credited with the points
 * @param shirtNumber   0 to 99
 * @param playerSurname the player's known name
 * @param scores        one entry per match the player scored in, in the order the matches were played
 */
public record PlayerScore(String team, int shirtNumber, String playerSurname, List<MatchScores> scores) {

    public PlayerScore {
        PlayerKey player = new PlayerKey(team, shirtNumber, playerSurname);
        team = player.team();
        playerSurname = player.playerSurname();
        scores = List.copyOf(Objects.requireNonNull(scores, "scores must not be null"));
        scores.stream()
                .flatMap(matchScores -> matchScores.scores().stream())
                .filter(score -> !score.playerKey().equals(player))
                .findFirst()
                .ifPresent(score -> {
                    throw new IllegalArgumentException("Score by " + score.playerSurname() + " (#"
                            + score.shirtNumber() + ", " + score.team() + ") is not by "
                            + player.playerSurname() + " (#" + player.shirtNumber() + ", " + player.team() + ")");
                });
    }

    /**
     * Total points over all matches.
     */
    public int points() {
        return scores.stream()
                .flatMap(matchScores -> matchScores.scores().stream())
                .mapToInt(Score::points)
                .sum();
    }

    /**
     * The scores a player recorded in one match, chronological by match time.
     */
    public record MatchScores(String homeTeam, String awayTeam, List<Score> scores) {

        public MatchScores {
            homeTeam = Names.normalize(homeTeam, "homeTeam");
            awayTeam = Names.normalize(awayTeam, "awayTeam");
            scores = List.copyOf(Objects.requireNonNull(scores, "scores must not be null"));
        }
    }
}
