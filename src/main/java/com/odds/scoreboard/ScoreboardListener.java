package com.odds.scoreboard;

/**
 * Receives the changes of a {@link Scoreboard}, so consumers react instead of polling.
 * <p>
 * Contract:
 * <ul>
 *   <li>Delivery is synchronous, on the thread that made the change, while the board's lock is held.
 *       Every listener therefore sees the events in exactly the order the state changed.</li>
 *   <li>Implementations must be fast and non-blocking, and must not call the board's mutating
 *       methods. A slow listener slows every writer. Hand the event to a queue if work is slow.</li>
 *   <li>A {@link RuntimeException} thrown by a listener is logged at WARNING and isolated: the other
 *       listeners are still notified and the operation still succeeds. Delivery is best-effort,
 *       at-most-once. Errors are not caught.</li>
 * </ul>
 */
@FunctionalInterface
public interface ScoreboardListener {

    void onEvent(ScoreboardEvent event);
}
