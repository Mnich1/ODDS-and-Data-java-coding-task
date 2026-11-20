package com.odds.scoreboard;

import com.odds.scoreboard.ScoreboardEvent.MatchFinished;
import com.odds.scoreboard.ScoreboardEvent.MatchStarted;
import com.odds.scoreboard.ScoreboardEvent.ScoreRecorded;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoreboardListenerTest {

    /** Hand-written fake that records every event it receives. */
    private static final class RecordingListener implements ScoreboardListener {

        private final List<ScoreboardEvent> events = new ArrayList<>();

        @Override
        public void onEvent(ScoreboardEvent event) {
            events.add(event);
        }
    }

    private InMemoryScoreboard board;
    private RecordingListener listener;

    @BeforeEach
    void setUp() {
        board = new InMemoryScoreboard();
        listener = new RecordingListener();
        board.addListener(listener);
    }

    private static Score goal(String team, int minute, String id) {
        return Score.goal(team, 9, "Player", Duration.ofMinutes(minute), id);
    }

    private static void assertRejected(ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOf(RuntimeException.class);
    }

    @Test
    void nullListenersAreRejectedAndABoardWithoutListenersWorks() {
        assertThatNullPointerException().isThrownBy(() -> board.addListener(null));
        assertThat(new InMemoryScoreboard().startMatch("Spain", "Brazil")).isNotNull();
    }

    @Test
    void startMatchPublishesExactlyOneMatchStarted() {
        Match started = board.startMatch("Spain", "Brazil");

        assertThat(listener.events).containsExactly(new MatchStarted(started));
    }

    @Test
    void updateScorePublishesTheResultingMatchAndTheExactScore() {
        board.startMatch("Spain", "Brazil");
        listener.events.clear();
        Score score = goal("Spain", 10, "s1");

        Match updated = board.updateScore("Spain", "Brazil", score);

        assertThat(listener.events).containsExactly(new ScoreRecorded(updated, score));
    }

    @Test
    void finishMatchPublishesTheFinalResultWithItsScores() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));
        listener.events.clear();

        Match finalResult = board.finishMatch("Spain", "Brazil");

        assertThat(listener.events).containsExactly(new MatchFinished(finalResult));
        assertThat(finalResult.scores()).hasSize(1);
    }

    @Test
    void aWholeMatchPublishesOneEventPerOperationInOrder() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));
        board.updateScore("Spain", "Brazil", goal("Brazil", 20, "s2"));
        board.finishMatch("Spain", "Brazil");

        assertThat(listener.events).extracting(event -> event.getClass().getSimpleName())
                .containsExactly("MatchStarted", "ScoreRecorded", "ScoreRecorded", "MatchFinished");
    }

    @Test
    void aReplayedScorePublishesNothing() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));
        listener.events.clear();

        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

        assertThat(listener.events).isEmpty();
    }

    @Test
    void rejectedOperationsPublishNothing() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));
        listener.events.clear();

        assertRejected(() -> board.startMatch("Spain", "France"));
        assertRejected(() -> board.startMatch(null, "France"));
        assertRejected(() -> board.startMatch("France", "France"));
        assertRejected(() -> board.updateScore("France", "Germany", goal("France", 10, "s2")));
        assertRejected(() -> board.updateScore("Spain", "Brazil", goal("Italy", 10, "s2")));
        assertRejected(() -> board.updateScore("Spain", "Brazil", goal("Spain", 11, "s1")));
        assertRejected(() -> board.updateScore("Spain", "Brazil", null));
        assertRejected(() -> board.finishMatch("France", "Germany"));

        assertThat(listener.events).isEmpty();
    }

    @Test
    void scoreRecordedCarriesTheRecordedScoreEvenWhenItIsNotTheLastInTheList() {
        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", goal("Spain", 30, "late"));
        listener.events.clear();
        Score early = goal("Spain", 10, "early");

        Match updated = board.updateScore("Spain", "Brazil", early);

        assertThat(listener.events).containsExactly(new ScoreRecorded(updated, early));
        assertThat(updated.scores().getLast().scoreId()).isEqualTo("late");
    }

    @Test
    void everyListenerSeesEveryEventInTheSameOrder() {
        RecordingListener second = new RecordingListener();
        board.addListener(second);

        board.startMatch("Spain", "Brazil");
        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

        assertThat(second.events).hasSize(2).isEqualTo(listener.events);
    }

    @Test
    void aListenerOnlySeesEventsAfterItWasRegistered() {
        board.startMatch("Spain", "Brazil");
        RecordingListener late = new RecordingListener();
        board.addListener(late);

        board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

        assertThat(late.events).hasSize(1).first().isInstanceOf(ScoreRecorded.class);
    }

    @Test
    void eventsAreDeliveredAfterTheStateChangedOnTheCallersThread() {
        Thread caller = Thread.currentThread();
        List<Thread> threads = new ArrayList<>();
        List<List<Match>> summaries = new ArrayList<>();
        board.addListener(event -> {
            threads.add(Thread.currentThread());
            summaries.add(board.getSummary());
        });

        Match started = board.startMatch("Spain", "Brazil");
        Match updated = board.updateScore("Spain", "Brazil", goal("Spain", 10, "s1"));

        assertThat(threads).containsExactly(caller, caller);
        assertThat(summaries).containsExactly(List.of(started), List.of(updated));
    }

    @Test
    void aFailingListenerIsIsolatedAndTheOperationStillSucceeds() {
        RecordingListener after = new RecordingListener();
        board.addListener(event -> {
            throw new IllegalStateException("listener failure");
        });
        board.addListener(after);

        Match started = board.startMatch("Spain", "Brazil");

        assertThat(board.getSummary()).containsExactly(started);
        assertThat(listener.events).containsExactly(new MatchStarted(started));
        assertThat(after.events).containsExactly(new MatchStarted(started));
    }

    @Test
    void errorsFromAListenerAreNotCaughtButTheLockIsReleasedAndTheStateKept() throws Exception {
        board.addListener(event -> {
            throw new AssertionError("fatal");
        });

        assertThatThrownBy(() -> board.startMatch("Spain", "Brazil")).isInstanceOf(AssertionError.class);

        List<Match> summary = CompletableFuture.supplyAsync(board::getSummary).get(10, TimeUnit.SECONDS);
        assertThat(summary).containsExactly(Match.start("Spain", "Brazil"));
    }
}
