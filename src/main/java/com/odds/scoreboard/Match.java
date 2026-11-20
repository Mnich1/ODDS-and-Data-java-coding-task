package com.odds.scoreboard;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An immutable snapshot of a match: both teams, the summarised score and the individual scores.
 * <p>
 * The compact constructor enforces the invariants, so no instance can be inconsistent:
 * the teams differ, every score belongs to one of the two sides, the scores are ordered by
 * match time, and each total equals the sum of that side's points.
 *
 * @param homeTeam  the home team
 * @param awayTeam  the away team
 * @param homeScore total points of the home side
 * @param awayScore total points of the away side
 * @param scores    individual scores, chronological by match time; equal times keep arrival order
 */
public record Match(String homeTeam, String awayTeam, int homeScore, int awayScore, List<Score> scores) {

    public Match {
        homeTeam = Names.normalize(homeTeam, "homeTeam");
        awayTeam = Names.normalize(awayTeam, "awayTeam");
        require(!homeTeam.equals(awayTeam), "A team cannot play itself: " + homeTeam);
        scores = List.copyOf(Objects.requireNonNull(scores, "scores must not be null"));

        Map<String, Integer> totals = scores.stream()
                .collect(Collectors.groupingBy(Score::team, Collectors.summingInt(Score::points)));
        require(Set.of(homeTeam, awayTeam).containsAll(totals.keySet()),
                "Scores must belong to " + homeTeam + " - " + awayTeam + " but found teams " + totals.keySet());
        for (int i = 1; i < scores.size(); i++) {
            require(scores.get(i - 1).matchTime().compareTo(scores.get(i).matchTime()) <= 0,
                    "Scores must be ordered by matchTime");
        }
        int homeSum = totals.getOrDefault(homeTeam, 0);
        int awaySum = totals.getOrDefault(awayTeam, 0);
        require(homeSum == homeScore && awaySum == awayScore, "Totals " + homeScore + "-" + awayScore
                + " do not match the scores, which sum to " + homeSum + "-" + awaySum);
    }

    static Match start(String homeTeam, String awayTeam) {
        return new Match(homeTeam, awayTeam, 0, 0, List.of());
    }

    /**
     * Returns a new snapshot with the score added in chronological position. A score with the same
     * match time as existing ones goes after them.
     *
     * @throws IllegalArgumentException if the score's team is not one of the two sides
     */
    Match withScore(Score score) {
        Objects.requireNonNull(score, "score must not be null");
        boolean home = score.team().equals(homeTeam);
        require(home || score.team().equals(awayTeam),
                "Team '" + score.team() + "' is not playing in " + homeTeam + " - " + awayTeam);
        return createMatchWithScore(score, home, insertChronologically(score));
    }

    private List<Score> insertChronologically(Score score) {
        // sorted() is stable on an ordered stream, so an equal matchTime keeps arrival order
        return Stream.concat(scores.stream(), Stream.of(score))
                .sorted(Comparator.comparing(Score::matchTime))
                .toList();
    }

    private Match createMatchWithScore(Score score, boolean home, List<Score> updated) {
        return new Match(homeTeam, awayTeam,
                home ? homeScore + score.points() : homeScore,
                home ? awayScore : awayScore + score.points(),
                updated);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
