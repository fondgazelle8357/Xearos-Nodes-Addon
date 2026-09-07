package me.fond.nodesoverlay.war;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingWarMessageBufferTest {

    @Test
    void retainsStandaloneCaptureReceivedBeforeAccessFinishes() {
        PendingWarMessageBuffer buffer = new PendingWarMessageBuffer(8);
        String capture =
                "[War] MetroX_GameS captured chunk (-12, 60) from Warzone_Italy_1!";

        assertTrue(buffer.offerIfWarMessage(capture));
        assertEquals(1, buffer.size());
        assertEquals(List.of(capture), buffer.drain());
        assertEquals(0, buffer.size());

        WarMessage parsed = new WarMessageParser().parse(capture).orElseThrow();
        assertEquals(
                new WarMessage.ChunkCaptured(
                        "MetroX_GameS",
                        "Warzone_Italy_1",
                        -12,
                        60
                ),
                parsed
        );
    }

    @Test
    void ignoresNonWarMessagesDeduplicatesAndBoundsPendingMessages() {
        PendingWarMessageBuffer buffer = new PendingWarMessageBuffer(2);
        String first = "[War] A captured chunk (1, 1) from Town!";
        String second = "[War] B captured chunk (2, 2) from Town!";
        String third = "[War] C captured chunk (3, 3) from Town!";

        assertFalse(buffer.offerIfWarMessage("[Global] hello"));
        assertTrue(buffer.offerIfWarMessage(first));
        assertTrue(buffer.offerIfWarMessage(first));
        assertEquals(1, buffer.size());
        assertTrue(buffer.offerIfWarMessage(second));
        assertTrue(buffer.offerIfWarMessage(third));

        assertEquals(List.of(second, third), buffer.drain());
    }

    @Test
    void retainsTownOutcomeReceivedBeforeAccessFinishes() {
        PendingWarMessageBuffer buffer = new PendingWarMessageBuffer(8);
        String conquered =
                "SouthEntea has been conquered by the alliance led by Mauretanae!";

        assertTrue(buffer.offerIfWarMessage(conquered));
        assertEquals(List.of(conquered), buffer.drain());
    }
}
