package me.fond.nodesoverlay;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RuntimeSessionGuardTest {

    @Test
    void rejectsStalePublicationAfterDisconnectAndRejoin() {
        RuntimeSessionGuard guard = new RuntimeSessionGuard();
        AtomicReference<String> published = new AtomicReference<>();

        long firstSession = guard.current();
        assertTrue(guard.runIfCurrent(firstSession, () -> published.set("first")));

        long secondSession = guard.advance();
        assertFalse(guard.runIfCurrent(firstSession, () -> published.set("stale")));
        assertEquals("first", published.get());

        assertTrue(guard.runIfCurrent(secondSession, () -> published.set("second")));
        assertEquals("second", published.get());
    }
}
