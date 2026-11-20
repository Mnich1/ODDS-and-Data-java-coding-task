package com.odds.scoreboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ScoreTest {

    private static final Duration MINUTE_23 = Duration.ofMinutes(23);

    @Test
    void holdsAllFieldsOfAValidScore() {
        Score score = new Score("Spain", 10, "Pedri", 2, MINUTE_23, "s-1");

        assertThat(score.team()).isEqualTo("Spain");
        assertThat(score.shirtNumber()).isEqualTo(10);
        assertThat(score.playerSurname()).isEqualTo("Pedri");
        assertThat(score.points()).isEqualTo(2);
        assertThat(score.matchTime()).isEqualTo(MINUTE_23);
        assertThat(score.scoreId()).isEqualTo("s-1");
    }

    @Test
    void goalIsAScoreWorthOnePoint() {
        assertThat(Score.goal("Spain", 10, "Pedri", MINUTE_23, "s-1"))
                .isEqualTo(new Score("Spain", 10, "Pedri", 1, MINUTE_23, "s-1"));
    }

    @Test
    void stripsTeamSurnameAndScoreId() {
        Score score = new Score(" Spain ", 10, " Pedri ", 1, MINUTE_23, " s-1 ");

        assertThat(score).isEqualTo(new Score("Spain", 10, "Pedri", 1, MINUTE_23, "s-1"));
    }

    @Test
    void equalityIsByValue() {
        Score score = Score.goal("Spain", 10, "Pedri", MINUTE_23, "s-1");

        assertThat(score).isNotEqualTo(Score.goal("Spain", 10, "Pedri", MINUTE_23, "s-2"));
        assertThat(score).isNotEqualTo(Score.goal("Spain", 10, "Pedri", Duration.ofMinutes(24), "s-1"));
        assertThat(score).isNotEqualTo(new Score("Spain", 10, "Pedri", 2, MINUTE_23, "s-1"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
    void rejectsPointsBelowOne(int points) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Score("Spain", 10, "Pedri", points, MINUTE_23, "s-1"))
                .withMessageContaining(String.valueOf(points));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 100})
    void rejectsShirtNumbersOutsideZeroToNinetyNine(int shirtNumber) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Score("Spain", shirtNumber, "Pedri", 1, MINUTE_23, "s-1"));
    }

    @Test
    void acceptsZeroAndVeryLongMatchTimes() {
        assertThat(new Score("Spain", 10, "Pedri", 1, Duration.ZERO, "s-1").matchTime()).isEqualTo(Duration.ZERO);
        assertThat(new Score("Spain", 10, "Pedri", 1, Duration.ofMinutes(200), "s-1").matchTime())
                .isEqualTo(Duration.ofMinutes(200));
    }

    @Test
    void rejectsNegativeMatchTime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Score("Spain", 10, "Pedri", 1, Duration.ofSeconds(-1), "s-1"))
                .withMessageContaining("matchTime");
    }

    @Test
    void nullArgumentsThrowNullPointerException() {
        assertThatNullPointerException().isThrownBy(() -> new Score(null, 10, "Pedri", 1, MINUTE_23, "s-1"));
        assertThatNullPointerException().isThrownBy(() -> new Score("Spain", 10, null, 1, MINUTE_23, "s-1"));
        assertThatNullPointerException().isThrownBy(() -> new Score("Spain", 10, "Pedri", 1, null, "s-1"));
        assertThatNullPointerException().isThrownBy(() -> new Score("Spain", 10, "Pedri", 1, MINUTE_23, null));
    }

    @Test
    void blankTeamSurnameOrScoreIdThrowsIllegalArgumentException() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Score(" ", 10, "Pedri", 1, MINUTE_23, "s-1"));
        assertThatIllegalArgumentException().isThrownBy(() -> new Score("Spain", 10, "", 1, MINUTE_23, "s-1"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Score("Spain", 10, "Pedri", 1, MINUTE_23, " "))
                .withMessageContaining("scoreId");
    }
}
