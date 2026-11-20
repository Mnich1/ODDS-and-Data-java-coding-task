package com.odds.scoreboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryScoreboardTest {

    private InMemoryScoreboard board;

    @BeforeEach
    void setUp() {
        board = new InMemoryScoreboard();
    }

    private static Score goal(String team, int minute, String id) {
        return Score.goal(team, 9, "Player", Duration.ofMinutes(minute), id);
    }

    @Nested
    class StartMatch {

        @Test
        void startsAtZeroZeroWithNoScores() {
            Match match = board.startMatch("Spain", "Brazil");

            assertThat(match).isEqualTo(Match.start("Spain", "Brazil"));
            assertThat(match.homeScore()).isZero();
            assertThat(match.awayScore()).isZero();
            assertThat(match.scores()).isEmpty();
        }

        @Test
        void stripsTeamNames() {
            assertThat(board.startMatch(" Spain ", " Brazil")).isEqualTo(Match.start("Spain", "Brazil"));
        }

        @Test
        void rejectsNullBlankAndIdenticalTeams() {
            assertThatNullPointerException().isThrownBy(() -> board.startMatch(null, "Brazil"));
            assertThatNullPointerException().isThrownBy(() -> board.startMatch("Spain", null));
            assertThatIllegalArgumentException().isThrownBy(() -> board.startMatch(" ", "Brazil"));
            assertThatIllegalArgumentException().isThrownBy(() -> board.startMatch("Spain", ""));
            assertThatIllegalArgumentException().isThrownBy(() -> board.startMatch("Spain", " Spain "));
        }

        @Test
        void rejectsAHomeTeamThatIsAlreadyPlayingAndNamesTheLiveMatch() {
            board.startMatch("Spain", "Brazil");
            board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

            assertThatThrownBy(() -> board.startMatch("Spain", "France"))
                    .isInstanceOf(TeamAlreadyPlayingException.class)
                    .hasMessage("Team 'Spain' is already playing in Spain 1 - Brazil 0");
            assertThatThrownBy(() -> board.startMatch("Brazil", "France"))
                    .isInstanceOf(TeamAlreadyPlayingException.class)
                    .hasMessageContaining("Brazil");
        }

        @Test
        void rejectsAnAwayTeamThatIsAlreadyPlaying() {
            board.startMatch("Spain", "Brazil");

            assertThatThrownBy(() -> board.startMatch("France", "Spain"))
                    .isInstanceOf(TeamAlreadyPlayingException.class)
                    .hasMessageContaining("Spain");
            assertThatThrownBy(() -> board.startMatch("France", "Brazil"))
                    .isInstanceOf(TeamAlreadyPlayingException.class)
                    .hasMessageContaining("Brazil");
        }

        @Test
        void aRejectedStartLeavesTheStateUnchanged() {
            board.startMatch("Spain", "Brazil");

            assertThatThrownBy(() -> board.startMatch("France", "Spain"))
                    .isInstanceOf(TeamAlreadyPlayingException.class);

            assertThatThrownBy(() -> board.finishMatch("France", "Spain"))
                    .isInstanceOf(MatchNotFoundException.class);
            assertThat(board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1")).homeScore()).isEqualTo(1);
        }

        @Test
        void differentMatchesWithDistinctTeamsRunSimultaneously() {
            board.startMatch("Spain", "Brazil");

            assertThat(board.startMatch("France", "Germany")).isEqualTo(Match.start("France", "Germany"));
        }

        @Test
        void thePairCanPlayAgainAfterItFinishedAsAFreshMatch() {
            board.startMatch("Spain", "Brazil");
            board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));
            board.finishMatch("Spain", "Brazil");

            assertThat(board.startMatch("Spain", "Brazil")).isEqualTo(Match.start("Spain", "Brazil"));
        }
    }

    @Nested
    class UpdateScore {

        @Test
        void returnsTheResultingMatchWithTheScoreRecorded() {
            board.startMatch("Spain", "Brazil");
            Score score = goal("Spain", 10, "s1");

            Match match = board.updateScore("Spain", "Brazil", score);

            assertThat(match.homeScore()).isEqualTo(1);
            assertThat(match.awayScore()).isZero();
            assertThat(match.scores()).containsExactly(score);
        }

        @Test
        void updatesAccumulateAcrossCallsAndSides() {
            board.startMatch("Spain", "Brazil");
            board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));
            board.updateScore("Spain", "Brazil", goal("Brazil", 20, "s2"));

            Match match = board.updateScore("Spain", "Brazil", goal("Spain", 30, "s3"));

            assertThat(match.homeScore()).isEqualTo(2);
            assertThat(match.awayScore()).isEqualTo(1);
            assertThat(match.scores()).hasSize(3);
        }

        @Test
        void aWorthManyPointsScoreAddsAllOfThem() {
            board.startMatch("Spain", "Brazil");

            Match match = board.updateScore("Spain", "Brazil",
                    new Score("Brazil", 9, "Player", 3, Duration.ofMinutes(5), "s1"));

            assertThat(match.awayScore()).isEqualTo(3);
        }

        @Test
        void stripsTheTeamNamesUsedForLookup() {
            board.startMatch("Spain", "Brazil");

            assertThat(board.updateScore(" Spain ", "Brazil ", goal("Spain", 10, "s1")).homeScore()).isEqualTo(1);
        }

        @Test
        void onlyTheTargetedMatchChanges() {
            board.startMatch("Spain", "Brazil");
            board.startMatch("France", "Germany");

            board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

            assertThat(board.updateScore("France", "Germany", goal("France", 5, "s2")).homeScore()).isEqualTo(1);
            assertThat(board.updateScore("Spain", "Brazil", goal("Brazil", 50, "s3")).homeScore()).isEqualTo(1);
        }

        @Test
        void anUnknownMatchThrowsMatchNotFound() {
            assertThatThrownBy(() -> board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1")))
                    .isInstanceOf(MatchNotFoundException.class)
                    .hasMessageContaining("Spain")
                    .hasMessageContaining("Brazil");
        }

        @Test
        void theReversedPairIsADifferentMatch() {
            board.startMatch("Spain", "Brazil");

            assertThatThrownBy(() -> board.updateScore("Brazil", "Spain", goal("Spain", 10, "s1")))
                    .isInstanceOf(MatchNotFoundException.class);
        }

        @Test
        void aFinishedMatchThrowsMatchNotFound() {
            board.startMatch("Spain", "Brazil");
            board.finishMatch("Spain", "Brazil");

            assertThatThrownBy(() -> board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1")))
                    .isInstanceOf(MatchNotFoundException.class);
        }

        @Test
        void aScoreByATeamOutsideTheMatchIsRejectedAndLeavesTheStateUnchanged() {
            board.startMatch("Spain", "Brazil");

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> board.updateScore("Spain", "Brazil", goal("France", 10, "s1")))
                    .withMessageContaining("France");

            Match match = board.updateScore("Spain", "Brazil", goal("Spain", 20, "s2"));
            assertThat(match.homeScore()).isEqualTo(1);
            assertThat(match.scores()).hasSize(1);
        }

        @Test
        void rejectsNullAndBlankArguments() {
            board.startMatch("Spain", "Brazil");
            Score score = goal("Spain", 10, "s1");

            assertThatNullPointerException().isThrownBy(() -> board.updateScore(null, "Brazil", score));
            assertThatNullPointerException().isThrownBy(() -> board.updateScore("Spain", null, score));
            assertThatNullPointerException().isThrownBy(() -> board.updateScore("Spain", "Brazil", null));
            assertThatIllegalArgumentException().isThrownBy(() -> board.updateScore(" ", "Brazil", score));
            assertThatIllegalArgumentException().isThrownBy(() -> board.updateScore("Spain", "", score));
        }
    }

    @Nested
    class FinishMatch {

        @Test
        void returnsTheFinalResult() {
            board.startMatch("Spain", "Brazil");
            Score score = goal("Spain", 10, "s1");
            board.updateScore("Spain", "Brazil", score);

            Match finalResult = board.finishMatch("Spain", "Brazil");

            assertThat(finalResult.homeScore()).isEqualTo(1);
            assertThat(finalResult.awayScore()).isZero();
            assertThat(finalResult.scores()).containsExactly(score);
        }

        @Test
        void removesTheMatchFromTheBoard() {
            board.startMatch("Spain", "Brazil");
            board.finishMatch("Spain", "Brazil");

            assertThatThrownBy(() -> board.finishMatch("Spain", "Brazil"))
                    .isInstanceOf(MatchNotFoundException.class);
            assertThatThrownBy(() -> board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1")))
                    .isInstanceOf(MatchNotFoundException.class);
        }

        @Test
        void freesBothTeamsToPlayOtherMatches() {
            board.startMatch("Spain", "Brazil");
            board.finishMatch("Spain", "Brazil");

            assertThat(board.startMatch("Spain", "France")).isEqualTo(Match.start("Spain", "France"));
            assertThat(board.startMatch("Brazil", "Germany")).isEqualTo(Match.start("Brazil", "Germany"));
        }

        @Test
        void onlyTheFinishedMatchIsRemoved() {
            board.startMatch("Spain", "Brazil");
            board.startMatch("France", "Germany");

            board.finishMatch("Spain", "Brazil");

            assertThat(board.updateScore("France", "Germany", goal("France", 10, "s1")).homeScore()).isEqualTo(1);
        }

        @Test
        void anUnknownMatchThrowsMatchNotFoundAndLeavesTheStateUnchanged() {
            board.startMatch("Spain", "Brazil");

            assertThatThrownBy(() -> board.finishMatch("France", "Germany"))
                    .isInstanceOf(MatchNotFoundException.class);
            assertThatThrownBy(() -> board.finishMatch("Brazil", "Spain"))
                    .isInstanceOf(MatchNotFoundException.class);

            assertThat(board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1")).homeScore()).isEqualTo(1);
        }

        @Test
        void rejectsNullAndBlankArguments() {
            assertThatNullPointerException().isThrownBy(() -> board.finishMatch(null, "Brazil"));
            assertThatNullPointerException().isThrownBy(() -> board.finishMatch("Spain", null));
            assertThatIllegalArgumentException().isThrownBy(() -> board.finishMatch(" ", "Brazil"));
            assertThatIllegalArgumentException().isThrownBy(() -> board.finishMatch("Spain", ""));
        }
    }

    @Nested
    class GetSummary {

        private Match play(String home, String away, int homeGoals, int awayGoals) {
            board.startMatch(home, away);
            Match match = null;
            for (int i = 1; i <= homeGoals; i++) {
                match = board.updateScore(home, away, goal(home, i, "h" + i));
            }
            for (int i = 1; i <= awayGoals; i++) {
                match = board.updateScore(home, away, goal(away, i, "a" + i));
            }
            return match;
        }

        private List<String> teams() {
            return board.getSummary().stream().map(m -> m.homeTeam() + " - " + m.awayTeam()).toList();
        }

        @Test
        void anEmptyBoardHasAnEmptySummary() {
            assertThat(board.getSummary()).isEmpty();
        }

        @Test
        void aNewMatchAppearsAtZeroZero() {
            Match started = board.startMatch("Spain", "Brazil");

            assertThat(board.getSummary()).containsExactly(started);
        }

        @Test
        void ordersByTotalScoreDescending() {
            play("Mexico", "Canada", 0, 1);
            play("Spain", "Brazil", 3, 2);
            play("Germany", "France", 1, 0);

            assertThat(teams()).containsExactly("Spain - Brazil", "Germany - France", "Mexico - Canada");
        }

        @Test
        void equalTotalsListTheMostRecentlyStartedFirst() {
            board.startMatch("A1", "A2");
            board.startMatch("B1", "B2");
            board.startMatch("C1", "C2");

            assertThat(teams()).containsExactly("C1 - C2", "B1 - B2", "A1 - A2");
        }

        @Test
        void aCombinedExampleOrdersByTotalThenRecency() {
            play("Mexico", "Canada", 0, 5);
            play("Spain", "Brazil", 10, 2);
            play("Germany", "France", 2, 2);
            play("Uruguay", "Italy", 6, 6);
            play("Argentina", "Australia", 3, 1);

            assertThat(teams()).containsExactly(
                    "Uruguay - Italy", "Spain - Brazil", "Mexico - Canada", "Argentina - Australia",
                    "Germany - France");
        }

        @Test
        void scoreUpdatesDoNotChangeStartRecency() {
            board.startMatch("A1", "A2");
            board.startMatch("B1", "B2");
            board.updateScore("B1", "B2", goal("B1", 10, "s1"));
            board.updateScore("A1", "A2", goal("A1", 10, "s2"));

            assertThat(teams()).containsExactly("B1 - B2", "A1 - A2");
        }

        @Test
        void reflectsUpdatesAndTheFinalSnapshotOfEachMatch() {
            board.startMatch("Spain", "Brazil");
            Match updated = board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

            assertThat(board.getSummary()).containsExactly(updated);
        }

        @Test
        void aFinishedMatchLeavesTheSummary() {
            play("Spain", "Brazil", 1, 0);
            play("France", "Germany", 0, 0);

            board.finishMatch("Spain", "Brazil");

            assertThat(teams()).containsExactly("France - Germany");
        }

        @Test
        void aRestartedPairCountsAsTheMostRecentStart() {
            board.startMatch("A1", "A2");
            board.startMatch("B1", "B2");
            board.finishMatch("A1", "A2");
            board.startMatch("A1", "A2");

            assertThat(teams()).containsExactly("A1 - A2", "B1 - B2");
        }

        @Test
        void theSummaryIsAnUnmodifiableSnapshot() {
            board.startMatch("Spain", "Brazil");
            List<Match> summary = board.getSummary();

            board.startMatch("France", "Germany");
            board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

            assertThat(summary).hasSize(1);
            assertThat(summary.getFirst().homeScore()).isZero();
            assertThatThrownBy(() -> summary.add(Match.start("X", "Y")))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
        @Test
        void referenceScenarioFromTheBrief() {
            play("Mexico", "Canada", 0, 5);
            play("Spain", "Brazil", 10, 2);
            play("Germany", "France", 2, 2);
            play("Uruguay", "Italy", 6, 6);
            play("Argentina", "Australia", 3, 1);

            assertThat(board.getSummary())
                    .extracting(m -> m.homeTeam() + " " + m.homeScore() + " - " + m.awayTeam() + " " + m.awayScore())
                    .containsExactly(
                            "Uruguay 6 - Italy 6",
                            "Spain 10 - Brazil 2",
                            "Mexico 0 - Canada 5",
                            "Argentina 3 - Australia 1",
                            "Germany 2 - France 2");
        }
    }

    @Nested
    class ScoreReplay {

        @Test
        void aReplayedScoreIsANoOpReturningTheCurrentMatch() {
            board.startMatch("Spain", "Brazil");
            Match first = board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

            Match replayed = board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

            assertThat(replayed).isEqualTo(first);
            assertThat(replayed.homeScore()).isEqualTo(1);
            assertThat(replayed.scores()).hasSize(1);
        }

        @Test
        void aReplayDoesNotDoubleCountPointsAndLaterScoresStillCount() {
            board.startMatch("Spain", "Brazil");
            Score threePoints = new Score("Brazil", 9, "Player", 3, Duration.ofMinutes(5), "s1");
            board.updateScore("Spain", "Brazil", threePoints);
            board.updateScore("Spain", "Brazil", threePoints);

            Match match = board.updateScore("Spain", "Brazil", goal("Brazil", 20, "s2"));

            assertThat(match.awayScore()).isEqualTo(4);
            assertThat(match.scores()).hasSize(2);
        }

        @Test
        void reusingAScoreIdWithDifferentContentIsRejectedAndLeavesTheStateUnchanged() {
            board.startMatch("Spain", "Brazil");
            board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> board.updateScore("Spain", "Brazil",
                            new Score("Spain", 9, "Player", 2, Duration.ofMinutes(10), "s1")))
                    .withMessageContaining("s1");

            Match match = board.updateScore("Spain", "Brazil", goal("Spain", 20, "s2"));
            assertThat(match.homeScore()).isEqualTo(2);
            assertThat(match.scores()).hasSize(2);
        }

        @Test
        void theSameScoreIdMayBeUsedInAnotherMatch() {
            board.startMatch("Spain", "Brazil");
            board.startMatch("France", "Germany");
            board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

            Match other = board.updateScore("France", "Germany", goal("France", 10, "s1"));

            assertThat(other.homeScore()).isEqualTo(1);
        }

        @Test
        void theSameScoreIdMayBeUsedAgainInARematchOfTheSamePair() {
            board.startMatch("Spain", "Brazil");
            board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));
            board.finishMatch("Spain", "Brazil");
            board.startMatch("Spain", "Brazil");

            Match rematch = board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

            assertThat(rematch.homeScore()).isEqualTo(1);
        }
    }

    @Test
    void stateViolationsShareTheScoreboardExceptionBaseType() {
        board.startMatch("Spain", "Brazil");

        assertThatThrownBy(() -> board.finishMatch("France", "Germany"))
                .isInstanceOf(ScoreboardException.class);
        assertThatThrownBy(() -> board.startMatch("Spain", "France"))
                .isInstanceOf(ScoreboardException.class);
        assertThat(new MatchNotFoundException("A", "B")).isInstanceOf(RuntimeException.class);
    }
}
