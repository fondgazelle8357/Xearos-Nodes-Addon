package me.fond.nodesoverlay.port;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortInfoParserTest {

    @Test
    void collectsMultilinePortInfoResponse() {
        PortInfoParser parser = new PortInfoParser();

        assertFalse(parser.accept("Port rome:").isPresent());
        assertFalse(parser.accept("- (x,z): (-766, 532)").isPresent());
        assertFalse(parser.accept("- Groups:").isPresent());
        assertFalse(parser.accept("  - 1").isPresent());
        assertFalse(parser.accept("- Owner: Roma").isPresent());
        assertFalse(parser.accept("- Access:").isPresent());
        assertFalse(parser.accept("  - Ally: true (Cost: 0 GOLD_INGOT)").isPresent());
        assertFalse(parser.accept("  - Neutral: false").isPresent());
        Optional<PortObservation> result = parser.accept("  - Enemy: false");

        assertTrue(result.isPresent());
        PortObservation port = result.orElseThrow();
        assertEquals("rome", port.name());
        assertEquals(-766, port.x());
        assertEquals(532, port.z());
        assertEquals(java.util.Set.of("1"), port.groups());
        assertEquals("Roma", port.owner());
        assertEquals(Boolean.TRUE, port.allyAccess());
        assertEquals(Boolean.FALSE, port.neutralAccess());
        assertEquals(Boolean.FALSE, port.enemyAccess());
        assertEquals("0 GOLD_INGOT", port.allyCost());
    }
}
