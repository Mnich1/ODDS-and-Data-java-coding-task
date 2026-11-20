package com.odds.scoreboard;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchTest {

    private static Score goal(String team, int minute, String id) {
        return Score.goal(team, 9, "Player", Duration.ofMinutes(minute), id);
    }

    private static Score points(String team, int points, int minute, String id) {
        return new Score(team, 9, "Player", points, Duration.ofMinutes(minute), id);
    }

    private static List<String> ids(Match match) {
        return match.scores().stream().map(Score::scoreId).toList();
    }

    @Test
    void startBeginsAtZeroZeroWithNoScores() {
        Match match = Match.start("Spain", "Brazil");

        assertThat(match.homeTeam()).isEqualTo("Spain");
        assertThat(match.awayTeam()).isEqualTo("Brazil");
        assertThat(match.homeScore()).isZero();
        assertThat(match.awayScore()).isZero();
        assertThat(match.scores()).isEmpty();
    }

    @Test
    void startStripsTeamNames() {
        Match match = Match.start("  Spain ", " Brazil");

        assertThat(match).isEqualTo(Match.start("Spain", "Brazil"));
    }

    @Test
    void startRejectsSameTeamTwiceAfterStripping() {
        assertThatIllegalArgumentException().isThrownBy(() -> Match.start("Spain", " Spain "))
                .withMessageContaining("Spain");
    }

    @Test
    void teamNamesAreCaseSensitive() {
        assertThat(Match.start("Spain", "spain").awayTeam()).isEqualTo("spain");
    }

    @Test
    void startRejectsNullAndBlankTeams() {
        assertThatNullPointerException().isThrownBy(() -> Match.start(null, "Brazil"));
        assertThatNullPointerException().isThrownBy(() -> Match.start("Spain", null));
        assertThatIllegalArgumentException().isThrownBy(() -> Match.start(" ", "Brazil"));
        assertThatIllegalArgumentException().isThrownBy(() -> Match.start("Spain", ""));
    }

    @Test
    void withScoreAddsPointsToTheHomeSide() {
        Match match = Match.start("Spain", "Brazil").withScore(goal("Spain", 10, "s1"));

        assertThat(match.homeScore()).isEqualTo(1);
        assertThat(match.awayScore()).isZero();
        assertThat(ids(match)).containsExactly("s1");
    }

    @Test
    void withScoreAddsPointsToTheAwaySide() {
        Match match = Match.start("Spain", "Brazil").withScore(points("Brazil", 3, 10, "s1"));

        assertThat(match.homeScore()).isZero();
        assertThat(match.awayScore()).isEqualTo(3);
    }

    @Test
    void totalsAccumulateAcrossScoresAndSides() {
        Match match = Match.start("Spain", "Brazil")
                .withScore(goal("Spain", 10, "s1"))
                .withScore(points("Brazil", 2, 20, "s2"))
                .withScore(points("Spain", 3, 30, "s3"))
                .withScore(goal("Brazil", 40, "s4"));

        assertThat(match.homeScore()).isEqualTo(4);
        assertThat(match.awayScore()).isEqualTo(3);
        assertThat(match.scores()).hasSize(4);
    }

    @Test
    void withScoreReturnsANewSnapshotAndLeavesTheOriginalUnchanged() {
        Match before = Match.start("Spain", "Brazil");

        Match after = before.withScore(goal("Spain", 10, "s1"));

        assertThat(after).isNotSameAs(before);
        assertThat(before.homeScore()).isZero();
        assertThat(before.scores()).isEmpty();
    }

    @Test
    void scoresAreOrderedByMatchTimeWhenArrivingOutOfOrder() {
        Match match = Match.start("Spain", "Brazil")
                .withScore(goal("Spain", 10, "s10"))
                .withScore(goal("Brazil", 30, "s30"))
                .withScore(goal("Spain", 20, "s20"))
                .withScore(goal("Brazil", 5, "s5"));

        assertThat(ids(match)).containsExactly("s5", "s10", "s20", "s30");
    }

    @Test
    void equalMatchTimesKeepArrivalOrder() {
        Match match = Match.start("Spain", "Brazil")
                .withScore(goal("Spain", 20, "first"))
                .withScore(goal("Brazil", 20, "second"))
                .withScore(goal("Spain", 20, "third"));

        assertThat(ids(match)).containsExactly("first", "second", "third");
    }

    @Test
    void aLateScoreWithAnEqualTimeGoesAfterExistingOnesButBeforeLaterOnes() {
        Match match = Match.start("Spain", "Brazil")
                .withScore(goal("Spain", 10, "a"))
                .withScore(goal("Spain", 20, "b"))
                .withScore(goal("Brazil", 30, "c"))
                .withScore(goal("Brazil", 20, "late"));

        assertThat(ids(match)).containsExactly("a", "b", "late", "c");
    }

    @Test
    void withScoreRejectsATeamThatIsNotInTheMatch() {
        Match match = Match.start("Spain", "Brazil");

        assertThatIllegalArgumentException().isThrownBy(() -> match.withScore(goal("France", 10, "s1")))
                .withMessageContaining("France");
        assertThatIllegalArgumentException().isThrownBy(() -> match.withScore(goal("spain", 10, "s1")));
    }

    @Test
    void withScoreRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> Match.start("Spain", "Brazil").withScore(null));
    }

    @Test
    void constructorRejectsTotalsThatDoNotMatchTheScores() {
        List<Score> scores = List.of(goal("Spain", 10, "s1"));

        assertThatIllegalArgumentException().isThrownBy(() -> new Match("Spain", "Brazil", 2, 0, scores));
        assertThatIllegalArgumentException().isThrownBy(() -> new Match("Spain", "Brazil", 1, 1, scores));
        assertThatIllegalArgumentException().isThrownBy(() -> new Match("Spain", "Brazil", 0, 0, scores));
    }

    @Test
    void constructorRejectsAScoreFromAForeignTeam() {
        List<Score> scores = List.of(goal("France", 10, "s1"));

        assertThatIllegalArgumentException().isThrownBy(() -> new Match("Spain", "Brazil", 1, 0, scores));
    }

    @Test
    void constructorRejectsScoresNotOrderedByMatchTime() {
        List<Score> scores = List.of(goal("Spain", 20, "s1"), goal("Spain", 10, "s2"));

        assertThatIllegalArgumentException().isThrownBy(() -> new Match("Spain", "Brazil", 2, 0, scores));
    }

    @Test
    void constructorRejectsNullScoresAndNullElements() {
        assertThatNullPointerException().isThrownBy(() -> new Match("Spain", "Brazil", 0, 0, null));
        List<Score> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatNullPointerException().isThrownBy(() -> new Match("Spain", "Brazil", 0, 0, withNull));
    }

    @Test
    void constructorCopiesTheListAndExposesItUnmodifiable() {
        List<Score> source = new ArrayList<>(List.of(goal("Spain", 10, "s1")));
        Match match = new Match("Spain", "Brazil", 1, 0, source);

        source.add(goal("Spain", 20, "s2"));

        assertThat(match.scores()).hasSize(1);
        assertThatThrownBy(() -> match.scores().add(goal("Spain", 30, "s3")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void replayingAnIdenticalScoreReturnsTheSameSnapshot() {
        Match match = Match.start("Spain", "Brazil").withScore(goal("Spain", 10, "s1"));

        assertThat(match.withScore(goal("Spain", 10, "s1"))).isSameAs(match);
    }

    @Test
    void aReplayIsDetectedAfterLaterScoresWithoutChangingTheOrder() {
        Match match = Match.start("Spain", "Brazil")
                .withScore(goal("Spain", 10, "s1"))
                .withScore(points("Brazil", 2, 20, "s2"));

        Match replayed = match.withScore(goal("Spain", 10, "s1"));

        assertThat(replayed).isSameAs(match);
        assertThat(ids(replayed)).containsExactly("s1", "s2");
        assertThat(replayed.homeScore()).isEqualTo(1);
        assertThat(replayed.awayScore()).isEqualTo(2);
    }

    @Test
    void aReplayWithUnstrippedValuesIsStillAReplay() {
        Match match = Match.start("Spain", "Brazil").withScore(goal("Spain", 10, "s1"));

        Score replay = Score.goal(" Spain ", 9, " Player ", Duration.ofMinutes(10), " s1 ");

        assertThat(match.withScore(replay)).isSameAs(match);
    }

    @Test
    void reusingAScoreIdWithDifferentContentIsRejected() {
        Match match = Match.start("Spain", "Brazil").withScore(goal("Spain", 10, "s1"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> match.withScore(points("Spain", 2, 10, "s1")))
                .withMessageContaining("s1");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> match.withScore(goal("Spain", 11, "s1")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> match.withScore(Score.goal("Spain", 9, "Other", Duration.ofMinutes(10), "s1")));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> match.withScore(goal("Brazil", 10, "s1")));
    }

    @Test
    void constructorRejectsDuplicateScoreIds() {
        List<Score> scores = List.of(goal("Spain", 10, "s1"), goal("Spain", 20, "s1"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Match("Spain", "Brazil", 2, 0, scores))
                .withMessageContaining("s1");
    }

    @Test
    void withoutLastScoreRemovesTheLastScoreAndLowersThatSidesTotal() {
        Match match = Match.start("Spain", "Brazil")
                .withScore(goal("Spain", 10, "s1"))
                .withScore(points("Brazil", 2, 20, "s2"));

        Match after = match.withoutLastScore();

        assertThat(after.homeScore()).isEqualTo(1);
        assertThat(after.awayScore()).isZero();
        assertThat(ids(after)).containsExactly("s1");
        assertThat(match.awayScore()).isEqualTo(2);
        assertThat(ids(match)).containsExactly("s1", "s2");
    }

    @Test
    void theLastScoreIsTheLatestByMatchTimeNotTheLastReceived() {
        Match match = Match.start("Spain", "Brazil")
                .withScore(goal("Spain", 30, "late"))
                .withScore(goal("Spain", 10, "early"));

        assertThat(ids(match.withoutLastScore())).containsExactly("early");
    }

    @Test
    void amongEqualMatchTimesTheLatestArrivalIsRemoved() {
        Match match = Match.start("Spain", "Brazil")
                .withScore(goal("Spain", 20, "first"))
                .withScore(goal("Brazil", 20, "second"));

        Match after = match.withoutLastScore();

        assertThat(ids(after)).containsExactly("first");
        assertThat(after.awayScore()).isZero();
    }

    @Test
    void removingEveryScoreOneByOneGoesBackToZeroZero() {
        Match match = Match.start("Spain", "Brazil")
                .withScore(points("Spain", 3, 10, "s1"))
                .withScore(goal("Brazil", 20, "s2"));

        assertThat(match.withoutLastScore().withoutLastScore()).isEqualTo(Match.start("Spain", "Brazil"));
    }

    @Test
    void withoutLastScoreOnAMatchWithoutScoresThrowsNamingTheTeams() {
        assertThatThrownBy(() -> Match.start("Spain", "Brazil").withoutLastScore())
                .isInstanceOf(NoScoreToRemoveException.class)
                .hasMessageContaining("Spain")
                .hasMessageContaining("Brazil");
    }

    @Test
    void aRemovedScoreIdCanBeUsedAgain() {
        Match match = Match.start("Spain", "Brazil").withScore(goal("Spain", 10, "s1")).withoutLastScore();

        Match again = match.withScore(points("Spain", 2, 15, "s1"));

        assertThat(again.homeScore()).isEqualTo(2);
        assertThat(ids(again)).containsExactly("s1");
    }

    @Test
    void equalityIsByValue() {
        Match one = Match.start("Spain", "Brazil").withScore(goal("Spain", 10, "s1"));
        Match two = Match.start("Spain", "Brazil").withScore(goal("Spain", 10, "s1"));

        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
        assertThat(one).isNotEqualTo(Match.start("Spain", "Brazil"));
    }
}
