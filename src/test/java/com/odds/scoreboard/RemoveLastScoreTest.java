package com.odds.scoreboard;

import com.odds.scoreboard.ScoreboardEvent.ScoreRecorded;
import com.odds.scoreboard.ScoreboardEvent.ScoreRemoved;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoveLastScoreTest {

    private InMemoryScoreboard board;
    private final List<ScoreboardEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        board = new InMemoryScoreboard();
        board.addListener(events::add);
    }

    private static Score goal(String team, int minute, String id) {
        return Score.goal(team, 9, "Player", Duration.ofMinutes(minute), id);
    }

    private static void assertRejected(ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(RuntimeException.class);
    }

    @Test
    void returnsTheResultingMatchWithTheLastScoreGone() {
        board.startMatch("Spain", "Brazil");
        Score first = goal("Spain", 10, "s1");
        board.updateScore("Spain", "Brazil", first);
        board.updateScore("Spain", "Brazil", goal("Brazil", 20, "s2"));

        Match match = board.removeLastScore("Spain", "Brazil");

        assertThat(match.homeScore()).isEqualTo(1);
        assertThat(match.awayScore()).isZero();
        assertThat(match.scores()).containsExactly(first);
    }

    @Test
    void repeatedRemovalWalksBackToZeroZero() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));
        board.updateScore("Spain", "Brazil", goal("Brazil", 20, "s2"));

        board.removeLastScore("Spain", "Brazil");

        assertThat(board.removeLastScore("Spain", "Brazil")).isEqualTo(Match.start("Spain", "Brazil"));
    }

    @Test
    void stripsTheTeamNamesUsedForLookup() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

        assertThat(board.removeLastScore(" Spain ", "Brazil ").homeScore()).isZero();
    }

    @Test
    void aMatchWithoutScoresThrowsNoScoreToRemoveAndStaysUnchanged() {
        board.startMatch("Spain", "Brazil");

        assertThatThrownBy(() -> board.removeLastScore("Spain", "Brazil"))
                .isInstanceOf(NoScoreToRemoveException.class)
                .isInstanceOf(ScoreboardException.class)
                .hasMessageContaining("Spain")
                .hasMessageContaining("Brazil");

        assertThat(board.getSummary()).containsExactly(Match.start("Spain", "Brazil"));
    }

    @Test
    void anUnknownReversedOrFinishedMatchThrowsMatchNotFound() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

        assertThatThrownBy(() -> board.removeLastScore("France", "Germany"))
                .isInstanceOf(MatchNotFoundException.class);
        assertThatThrownBy(() -> board.removeLastScore("Brazil", "Spain"))
                .isInstanceOf(MatchNotFoundException.class);

        board.finishMatch("Spain", "Brazil");
        assertThatThrownBy(() -> board.removeLastScore("Spain", "Brazil"))
                .isInstanceOf(MatchNotFoundException.class);
    }

    @Test
    void rejectsNullAndBlankArguments() {
        assertThatNullPointerException().isThrownBy(() -> board.removeLastScore(null, "Brazil"));
        assertThatNullPointerException().isThrownBy(() -> board.removeLastScore("Spain", null));
        assertThatIllegalArgumentException().isThrownBy(() -> board.removeLastScore(" ", "Brazil"));
        assertThatIllegalArgumentException().isThrownBy(() -> board.removeLastScore("Spain", ""));
    }

    @Test
    void theSummaryShowsTheLowerTotalAndTheStartOrderIsUnchanged() {
        board.startMatch("A1", "A2");
        board.startMatch("B1", "B2");
        board.updateScore("A1", "A2", goal("A1", 10, "s1"));
        assertThat(board.getSummary().getFirst().homeTeam()).isEqualTo("A1");

        board.removeLastScore("A1", "A2");

        assertThat(board.getSummary()).extracting(Match::homeTeam).containsExactly("B1", "A1");
        assertThat(board.getSummary()).extracting(Match::homeScore).containsExactly(0, 0);
    }

    @Test
    void thePlayerLookupReflectsTheRemoval() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", Score.goal("Spain", 10, "Pedri", Duration.ofMinutes(10), "s1"));
        assertThat(board.getPlayerScore("Spain", 10, "Pedri").points()).isEqualTo(1);

        board.removeLastScore("Spain", "Brazil");

        assertThatThrownBy(() -> board.getPlayerScore("Spain", 10, "Pedri"))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void aRemovedScoreIdCanBeRecordedAgainAsANewScore() {
        board.startMatch("Spain", "Brazil");
        Score score = goal("Spain", 10, "s1");
        board.updateScore("Spain", "Brazil", score);
        board.removeLastScore("Spain", "Brazil");
        events.clear();

        Match match = board.updateScore("Spain", "Brazil", score);

        assertThat(match.homeScore()).isEqualTo(1);
        assertThat(events).containsExactly(new ScoreRecorded(match, score));
    }

    @Test
    void publishesExactlyOneScoreRemovedWithTheResultingMatchAndTheRemovedScore() {
        board.startMatch("Spain", "Brazil");
        Score removed = goal("Brazil", 20, "s2");
        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));
        board.updateScore("Spain", "Brazil", removed);
        events.clear();

        Match match = board.removeLastScore("Spain", "Brazil");

        assertThat(events).containsExactly(new ScoreRemoved(match, removed));
    }

    @Test
    void rejectedRemovalsPublishNothing() {
        board.startMatch("Spain", "Brazil");
        events.clear();

        assertRejected(() -> board.removeLastScore("Spain", "Brazil"));
        assertRejected(() -> board.removeLastScore("France", "Germany"));
        assertRejected(() -> board.removeLastScore(null, "Brazil"));
        assertRejected(() -> board.removeLastScore("Spain", " "));

        assertThat(events).isEmpty();
    }
}
