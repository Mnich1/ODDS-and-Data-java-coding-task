package com.odds.scoreboard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * In-memory {@link Scoreboard}. One lock guards all state, so check-then-act sequences such as
 * "neither team is playing, then insert" are atomic. A ReentrantLock is used instead of
 * synchronized because synchronized pins virtual threads on JDK 21.
 */
public final class InMemoryScoreboard implements Scoreboard {

    private record MatchKey(String homeTeam, String awayTeam) {

        MatchKey {
            homeTeam = Names.normalize(homeTeam, "homeTeam");
            awayTeam = Names.normalize(awayTeam, "awayTeam");
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    /** Live matches in start order. */
    private final Map<MatchKey, Match> liveMatches = new LinkedHashMap<>();

    @Override
    public Match startMatch(String homeTeam, String awayTeam) {
        Match match = Match.start(homeTeam, awayTeam);
        return locked(() -> {
            requireAvailable(match.homeTeam());
            requireAvailable(match.awayTeam());
            liveMatches.put(new MatchKey(match.homeTeam(), match.awayTeam()), match);
            return match;
        });
    }

    @Override
    public Match updateScore(String homeTeam, String awayTeam, Score score) {
        MatchKey key = new MatchKey(homeTeam, awayTeam);
        Objects.requireNonNull(score, "score must not be null");
        return locked(() -> {
            Match current = liveMatch(key);
            Match updated = current.withScore(score);
            liveMatches.put(key, updated);
            return updated;
        });
    }

    @Override
    public Match finishMatch(String homeTeam, String awayTeam) {
        MatchKey key = new MatchKey(homeTeam, awayTeam);
        return locked(() -> {
            Match finished = liveMatch(key);
            liveMatches.remove(key);
            return finished;
        });
    }

    private Match liveMatch(MatchKey key) {
        return Optional.ofNullable(liveMatches.get(key))
                .orElseThrow(() -> new MatchNotFoundException(key.homeTeam(), key.awayTeam()));
    }

    private void requireAvailable(String team) {
        liveMatches.values().stream()
                .filter(live -> live.homeTeam().equals(team) || live.awayTeam().equals(team))
                .findFirst()
                .ifPresent(live -> {
                    throw new TeamAlreadyPlayingException(team, live);
                });
    }

    private <T> T locked(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }
}
