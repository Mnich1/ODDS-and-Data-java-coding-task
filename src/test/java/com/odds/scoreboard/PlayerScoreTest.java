package com.odds.scoreboard;

import com.odds.scoreboard.PlayerScore.MatchScores;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerScoreTest {

    private static Score pedri(int points, int minute, String id) {
        return new Score("Spain", 10, "Pedri", points, Duration.ofMinutes(minute), id);
    }

    @Test
    void pointsIsTheSumOfTheScoresAcrossAllMatches() {
        PlayerScore player = new PlayerScore("Spain", 10, "Pedri", List.of(
                new MatchScores("Spain", "Brazil", List.of(pedri(1, 10, "a"), pedri(2, 20, "b"))),
                new MatchScores("Spain", "France", List.of(pedri(3, 5, "c")))));

        assertThat(player.points()).isEqualTo(6);
    }

    @Test
    void stripsTeamAndSurnameAndValidatesTheShirtNumber() {
        assertThat(new PlayerScore(" Spain ", 10, " Pedri ", List.of()))
                .isEqualTo(new PlayerScore("Spain", 10, "Pedri", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerScore("Spain", 100, "Pedri", List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerScore(" ", 10, "Pedri", List.of()));
        assertThatNullPointerException().isThrownBy(() -> new PlayerScore("Spain", 10, null, List.of()));
        assertThatNullPointerException().isThrownBy(() -> new PlayerScore("Spain", 10, "Pedri", null));
    }

    @Test
    void rejectsAScoreOfAnotherPlayer() {
        Score gavi = new Score("Spain", 9, "Gavi", 1, Duration.ofMinutes(10), "a");

        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerScore("Spain", 10, "Pedri",
                List.of(new MatchScores("Spain", "Brazil", List.of(gavi)))))
                .withMessageContaining("Gavi");
    }

    @Test
    void matchScoresStripsTheTeamNamesAndRejectsNulls() {
        assertThat(new MatchScores(" Spain ", " Brazil ", List.of()))
                .isEqualTo(new MatchScores("Spain", "Brazil", List.of()));
        assertThatNullPointerException().isThrownBy(() -> new MatchScores(null, "Brazil", List.of()));
        assertThatNullPointerException().isThrownBy(() -> new MatchScores("Spain", "Brazil", null));
        assertThatIllegalArgumentException().isThrownBy(() -> new MatchScores("Spain", " ", List.of()));
    }

    @Test
    void theListsAreCopiedAndUnmodifiable() {
        List<Score> source = new ArrayList<>(List.of(pedri(1, 10, "a")));
        MatchScores matchScores = new MatchScores("Spain", "Brazil", source);
        List<MatchScores> matches = new ArrayList<>(List.of(matchScores));
        PlayerScore player = new PlayerScore("Spain", 10, "Pedri", matches);

        source.add(pedri(1, 20, "b"));
        matches.clear();

        assertThat(matchScores.scores()).hasSize(1);
        assertThat(player.scores()).containsExactly(matchScores);
        assertThatThrownBy(() -> player.scores().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> matchScores.scores().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
