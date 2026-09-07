package me.fond.nodesoverlay.port;

import me.fond.nodesoverlay.model.PortRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortTrackerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void liveObservationOverridesBaseAndSurvivesRestart() {
        PortTracker tracker = new PortTracker();
        tracker.activate(temporaryDirectory, NOPLogger.NOP_LOGGER);
        tracker.setBase(Map.of("rome", new PortRecord(
                "rome",
                "Rome",
                Set.of("1"),
                -700,
                500,
                null,
                null,
                null,
                null,
                null,
                false,
                Instant.EPOCH
        )));
        tracker.apply(new PortObservation(
                "rome",
                Set.of("1"),
                -766,
                532,
                "Roma",
                true,
                false,
                false,
                "0 GOLD_INGOT",
                Instant.ofEpochMilli(1234L)
        ));

        assertEquals(-766, tracker.ports().get("rome").x());
        assertTrue(tracker.ports().get("rome").observedInGame());

        PortTracker restored = new PortTracker();
        restored.activate(temporaryDirectory, NOPLogger.NOP_LOGGER);
        restored.setBase(Map.of("rome", new PortRecord(
                "rome",
                "Rome",
                Set.of("1"),
                -700,
                500,
                null,
                null,
                null,
                null,
                null,
                false,
                Instant.EPOCH
        )));

        assertEquals(-766, restored.ports().get("rome").x());
        assertEquals("Roma", restored.ports().get("rome").owner());
        assertEquals(Instant.ofEpochMilli(1234L), restored.ports().get("rome").updatedAt());
    }
}
