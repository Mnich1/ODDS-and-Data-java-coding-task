package com.odds.scoreboard;

import com.odds.scoreboard.PlayerScore.MatchScores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerLookupTest {

    private InMemoryScoreboard board;

    @BeforeEach
    void setUp() {
        board = new InMemoryScoreboard();
    }

    private static Score score(String team, int shirt, String surname, int points, int minute, String id) {
        return new Score(team, shirt, surname, points, Duration.ofMinutes(minute), id);
    }

    private static Score pedri(int minute, String id) {
        return score("Spain", 10, "Pedri", 1, minute, id);
    }

    private PlayerScore pedriScore() {
        return board.getPlayerScore("Spain", 10, "Pedri");
    }

    @Test
    void anUnknownPlayerThrowsPlayerNotFound() {
        assertThatThrownBy(this::pedriScore)
                .isInstanceOf(PlayerNotFoundException.class)
                .isInstanceOf(ScoreboardException.class)
                .hasMessageContaining("Pedri")
                .hasMessageContaining("Spain")
                .hasMessageContaining("10");
    }

    @Test
    void aPlayerWhoHasNotScoredYetThrowsPlayerNotFound() {
        board.startMatch("Spain", "Brazil");

        assertThatThrownBy(this::pedriScore).isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void findsAPlayerInALiveMatchWithTheOpponent() {
        board.startMatch("Spain", "Brazil");
        Score goal = pedri(10, "s1");
        board.updateScore("Spain", "Brazil", goal);

        PlayerScore result = pedriScore();

        assertThat(result).isEqualTo(new PlayerScore("Spain", 10, "Pedri",
                List.of(new MatchScores("Spain", "Brazil", List.of(goal)))));
        assertThat(result.points()).isEqualTo(1);
    }

    @Test
    void finishedMatchesStayInTheLookup() {
        board.startMatch("Spain", "Brazil");
        Score goal = pedri(10, "s1");
        board.updateScore("Spain", "Brazil", goal);
        board.finishMatch("Spain", "Brazil");

        assertThat(pedriScore().scores())
                .containsExactly(new MatchScores("Spain", "Brazil", List.of(goal)));
    }

    @Test
    void groupsScoresPerMatchInStartOrderAcrossFinishedAndLiveMatches() {
        Score first = pedri(10, "a");
        Score second = score("Spain", 10, "Pedri", 2, 20, "b");
        Score third = pedri(30, "c");
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", first);
        board.finishMatch("Spain", "Brazil");
        board.startMatch("Italy", "Spain");
        board.updateScore("Italy", "Spain", second);
        board.finishMatch("Italy", "Spain");
        board.startMatch("Spain", "France");
        board.updateScore("Spain", "France", third);

        PlayerScore result = pedriScore();

        assertThat(result.scores()).containsExactly(
                new MatchScores("Spain", "Brazil", List.of(first)),
                new MatchScores("Italy", "Spain", List.of(second)),
                new MatchScores("Spain", "France", List.of(third)));
        assertThat(result.points()).isEqualTo(4);
    }

    @Test
    void aRematchOfTheSamePairIsAnotherGroup() {
        Score first = pedri(10, "a");
        Score second = pedri(10, "a");
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", first);
        board.finishMatch("Spain", "Brazil");
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", second);

        assertThat(pedriScore().scores()).containsExactly(
                new MatchScores("Spain", "Brazil", List.of(first)),
                new MatchScores("Spain", "Brazil", List.of(second)));
    }

    @Test
    void scoresInAMatchFollowMatchTimeNotArrivalOrder() {
        board.startMatch("Spain", "Brazil");
        Score late = pedri(60, "late");
        Score early = pedri(5, "early");
        board.updateScore("Spain", "Brazil", late);
        board.updateScore("Spain", "Brazil", early);

        assertThat(pedriScore().scores().getFirst().scores()).containsExactly(early, late);
    }

    @Test
    void otherPlayersScoresAreExcluded() {
        board.startMatch("Spain", "Brazil");
        Score pedri = pedri(10, "a");
        board.updateScore("Spain", "Brazil", pedri);
        board.updateScore("Spain", "Brazil", score("Spain", 9, "Gavi", 1, 20, "b"));
        board.updateScore("Spain", "Brazil", score("Brazil", 10, "Pedri", 1, 30, "c"));

        assertThat(pedriScore().scores()).containsExactly(new MatchScores("Spain", "Brazil", List.of(pedri)));
    }

    @Test
    void aPlayerIsTheExactTripleOfTeamNumberAndSurname() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", pedri(10, "a"));

        assertThatThrownBy(() -> board.getPlayerScore("Spain", 11, "Pedri"))
                .isInstanceOf(PlayerNotFoundException.class);
        assertThatThrownBy(() -> board.getPlayerScore("Spain", 10, "Gavi"))
                .isInstanceOf(PlayerNotFoundException.class);
        assertThatThrownBy(() -> board.getPlayerScore("Brazil", 10, "Pedri"))
                .isInstanceOf(PlayerNotFoundException.class);
        assertThatThrownBy(() -> board.getPlayerScore("Spain", 10, "pedri"))
                .isInstanceOf(PlayerNotFoundException.class);
        assertThat(board.getPlayerScore(" Spain ", 10, " Pedri ").points()).isEqualTo(1);
    }

    @Test
    void scoresAreFoundUnderTheCreditedTeamNotTheScorersOwn() {
        board.startMatch("Spain", "Brazil");
        Score ownGoal = score("Spain", 3, "Marquinhos", 1, 40, "og");
        board.updateScore("Spain", "Brazil", ownGoal);

        assertThat(board.getPlayerScore("Spain", 3, "Marquinhos").points()).isEqualTo(1);
        assertThatThrownBy(() -> board.getPlayerScore("Brazil", 3, "Marquinhos"))
                .isInstanceOf(PlayerNotFoundException.class);
    }

    @Test
    void pointsSumMultiPointScores() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", score("Spain", 10, "Pedri", 2, 10, "a"));
        board.updateScore("Spain", "Brazil", score("Spain", 10, "Pedri", 3, 20, "b"));

        assertThat(pedriScore().points()).isEqualTo(5);
    }

    @Test
    void aReplayedScoreIsCountedOnce() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", pedri(10, "a"));
        board.updateScore("Spain", "Brazil", pedri(10, "a"));

        assertThat(pedriScore().points()).isEqualTo(1);
    }

    @Test
    void argumentsAreValidatedBeforeTheLookup() {
        assertThatNullPointerException().isThrownBy(() -> board.getPlayerScore(null, 10, "Pedri"));
        assertThatNullPointerException().isThrownBy(() -> board.getPlayerScore("Spain", 10, null));
        assertThatIllegalArgumentException().isThrownBy(() -> board.getPlayerScore(" ", 10, "Pedri"));
        assertThatIllegalArgumentException().isThrownBy(() -> board.getPlayerScore("Spain", 10, ""));
        assertThatIllegalArgumentException().isThrownBy(() -> board.getPlayerScore("Spain", -1, "Pedri"));
        assertThatIllegalArgumentException().isThrownBy(() -> board.getPlayerScore("Spain", 100, "Pedri"));
    }

    @Test
    void theLookupChangesNothingAndPublishesNoEvent() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", pedri(10, "a"));
        List<ScoreboardEvent> events = new ArrayList<>();
        board.addListener(events::add);
        List<Match> before = board.getSummary();

        pedriScore();

        assertThat(events).isEmpty();
        assertThat(board.getSummary()).isEqualTo(before);
    }

    @Test
    void theResultIsUnmodifiable() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", pedri(10, "a"));
        PlayerScore result = pedriScore();

        assertThatThrownBy(() -> result.scores().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.scores().getFirst().scores().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
