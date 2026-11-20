package com.odds.scoreboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PlayerKeyTest {

    @Test
    void stripsTeamAndSurname() {
        PlayerKey key = new PlayerKey(" Spain ", 10, " Pedri ");

        assertThat(key.team()).isEqualTo("Spain");
        assertThat(key.playerSurname()).isEqualTo("Pedri");
    }

    @Test
    void equalityIsByStrippedValueAndCaseSensitive() {
        assertThat(new PlayerKey(" Spain", 10, "Pedri ")).isEqualTo(new PlayerKey("Spain", 10, "Pedri"));
        assertThat(new PlayerKey("Spain", 10, "Pedri")).isNotEqualTo(new PlayerKey("spain", 10, "Pedri"));
        assertThat(new PlayerKey("Spain", 10, "Pedri")).isNotEqualTo(new PlayerKey("Spain", 11, "Pedri"));
        assertThat(new PlayerKey("Spain", 10, "Pedri")).isNotEqualTo(new PlayerKey("Spain", 10, "Gavi"));
        assertThat(new PlayerKey("Spain", 10, "Pedri")).isNotEqualTo(new PlayerKey("Brazil", 10, "Pedri"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 99})
    void acceptsShirtNumbersFromZeroToNinetyNine(int shirtNumber) {
        assertThat(new PlayerKey("Spain", shirtNumber, "Pedri").shirtNumber()).isEqualTo(shirtNumber);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 100, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void rejectsShirtNumbersOutsideZeroToNinetyNine(int shirtNumber) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PlayerKey("Spain", shirtNumber, "Pedri"))
                .withMessageContaining(String.valueOf(shirtNumber));
    }

    @Test
    void nullTeamOrSurnameThrowsNullPointerException() {
        assertThatNullPointerException().isThrownBy(() -> new PlayerKey(null, 10, "Pedri"));
        assertThatNullPointerException().isThrownBy(() -> new PlayerKey("Spain", 10, null));
    }

    @Test
    void blankTeamOrSurnameThrowsIllegalArgumentException() {
        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerKey(" ", 10, "Pedri"));
        assertThatIllegalArgumentException().isThrownBy(() -> new PlayerKey("Spain", 10, ""));
    }
}
