package com.odds.scoreboard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Releases N threads at the same moment against the real {@link InMemoryScoreboard}. What decides
 * each race is the single lock, so a lost point or a second winner means the lock is broken.
 */
class ConcurrentScoreboardTest {

    private static final int THREADS = 32;

    private InMemoryScoreboard board;

    @BeforeEach
    void setUp() {
        board = new InMemoryScoreboard();
    }

    private record Result<T>(T value, RuntimeException error) {

        boolean succeeded() {
            return error == null;
        }
    }

    /** Runs one task per thread, all released together by a latch, and collects value or exception. */
    private static <T> List<Result<T>> race(int threads, IntFunction<Callable<T>> taskFor) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Result<T>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<T> task = taskFor.apply(i);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return new Result<>(task.call(), null);
                    } catch (RuntimeException e) {
                        return new Result<>(null, e);
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<Result<T>> results = new ArrayList<>();
            for (Future<Result<T>> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Score goal(String team, int seconds, String id) {
        return Score.goal(team, 9, "Player", Duration.ofSeconds(seconds), id);
    }

    @Test
    void racingStartsOfTheSamePairHaveExactlyOneWinner() throws Exception {
        List<Result<Match>> results = race(THREADS, i -> () -> board.startMatch("Spain", "Brazil"));

        assertThat(results).filteredOn(Result::succeeded).hasSize(1);
        assertThat(results).filteredOn(r -> !r.succeeded())
                .hasSize(THREADS - 1)
                .allSatisfy(r -> assertThat(r.error()).isInstanceOf(TeamAlreadyPlayingException.class));
        assertThat(board.getSummary()).containsExactly(Match.start("Spain", "Brazil"));
    }

    @Test
    void racingStartsSharingOneTeamAsHomeOrAwayHaveExactlyOneWinner() throws Exception {
        List<Result<Match>> results = race(THREADS, i -> () -> i % 2 == 0
                ? board.startMatch("Spain", "Opponent" + i)
                : board.startMatch("Opponent" + i, "Spain"));

        assertThat(results).filteredOn(Result::succeeded).hasSize(1);
        assertThat(results).filteredOn(r -> !r.succeeded())
                .allSatisfy(r -> assertThat(r.error()).isInstanceOf(TeamAlreadyPlayingException.class));
        assertThat(board.getSummary()).hasSize(1);
    }

    @Test
    void concurrentUpdatesOfOneMatchLoseNoPoints() throws Exception {
        board.startMatch("Spain", "Brazil");
        int updatesPerThread = 50;

        List<Result<Match>> results = race(THREADS, thread -> () -> {
            Match last = null;
            for (int j = 0; j < updatesPerThread; j++) {
                String team = j % 2 == 0 ? "Spain" : "Brazil";
                last = board.updateScore("Spain", "Brazil", goal(team, j, "t" + thread + "-" + j));
            }
            return last;
        });

        assertThat(results).allSatisfy(r -> assertThat(r.succeeded()).isTrue());
        Match match = board.getSummary().getFirst();
        int expectedPerSide = THREADS * updatesPerThread / 2;
        assertThat(match.homeScore()).isEqualTo(expectedPerSide);
        assertThat(match.awayScore()).isEqualTo(expectedPerSide);
        assertThat(match.scores()).hasSize(THREADS * updatesPerThread);
    }

    @Test
    void concurrentReplaysOfTheSameScoreAreCountedOnce() throws Exception {
        board.startMatch("Spain", "Brazil");
        Score score = goal("Spain", 10, "s1");

        List<Result<Match>> results = race(THREADS, i -> () -> board.updateScore("Spain", "Brazil", score));

        assertThat(results).allSatisfy(r -> assertThat(r.succeeded()).isTrue());
        Match match = board.getSummary().getFirst();
        assertThat(match.homeScore()).isEqualTo(1);
        assertThat(match.scores()).containsExactly(score);
    }

    @Test
    void racingFinishesOfTheSameMatchHaveExactlyOneWinner() throws Exception {
        board.startMatch("Spain", "Brazil");

        List<Result<Match>> results = race(THREADS, i -> () -> board.finishMatch("Spain", "Brazil"));

        assertThat(results).filteredOn(Result::succeeded).hasSize(1);
        assertThat(results).filteredOn(r -> !r.succeeded())
                .hasSize(THREADS - 1)
                .allSatisfy(r -> assertThat(r.error()).isInstanceOf(MatchNotFoundException.class));
        assertThat(board.getSummary()).isEmpty();
    }
}
