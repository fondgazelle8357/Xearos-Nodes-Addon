package me.fond.nodesoverlay;

/**
 * Serializes session invalidation with background publication. Once
 * {@link #advance()} returns, work holding an older generation can no longer
 * enter its publication action.
 */
final class RuntimeSessionGuard {

    private long generation;

    synchronized long current() {
        return generation;
    }

    synchronized long advance() {
        return ++generation;
    }

    synchronized boolean runIfCurrent(long expectedGeneration, Runnable action) {
        if (expectedGeneration != generation) {
            return false;
        }
        action.run();
        return true;
    }
}
