package me.fond.nodesoverlay.sync;

import java.time.Instant;

public record SyncStatus(
        State state,
        String message,
        Instant lastSuccess,
        Instant lastAttempt
) {
    public static SyncStatus idle() {
        return new SyncStatus(State.IDLE, "Waiting for map data", null, null);
    }

    public enum State {
        IDLE,
        LOADING_CACHE,
        CHECKING,
        UP_TO_DATE,
        UPDATED,
        WARNING
    }
}
