package com.odds.scoreboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class NamesTest {

    @Test
    void stripsSurroundingWhitespaceIncludingUnicodeSpaces() {
        assertThat(Names.normalize("  Spain ", "team")).isEqualTo("Spain");
        assertThat(Names.normalize(" Spain ", "team")).isEqualTo("Spain");
    }

    @Test
    void keepsCaseAndInnerWhitespace() {
        assertThat(Names.normalize("Costa Rica", "team")).isEqualTo("Costa Rica");
        assertThat(Names.normalize("spain", "team")).isNotEqualTo(Names.normalize("Spain", "team"));
    }

    @Test
    void nullThrowsNullPointerExceptionNamingTheField() {
        assertThatNullPointerException()
                .isThrownBy(() -> Names.normalize(null, "team"))
                .withMessageContaining("team");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t\n", " "})
    void blankThrowsIllegalArgumentExceptionNamingTheField(String blank) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Names.normalize(blank, "team"))
                .withMessageContaining("team");
    }
}
