package me.fond.nodesoverlay.war;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerritoryInfoParserTest {

    @Test
    void parsesLiveOccupiedTerritoryResponse() {
        TerritoryInfoParser parser = new TerritoryInfoParser();

        assertFalse(parser.accept("Territory (id = 2129):").isPresent());
        assertFalse(parser.accept("- Town: Warzone_Italy_1").isPresent());
        Optional<TerritoryInfoObservation> result =
                parser.accept("- Occupier: Samnium");

        assertTrue(result.isPresent());
        TerritoryInfoObservation observation = result.orElseThrow();
        assertEquals(2129, observation.territoryId());
        assertEquals("Warzone_Italy_1", observation.ownerTown());
        assertEquals("Samnium", observation.occupierTown());
        assertTrue(observation.occupied());
    }

    @Test
    void treatsNoneAndBlankOccupiersAsUnoccupied() {
        TerritoryInfoParser parser = new TerritoryInfoParser();

        parser.accept("§6Territory (id=2129):");
        parser.accept("§b- Town: Warzone_Italy_1");
        TerritoryInfoObservation none =
                parser.accept("§b- Occupier: None").orElseThrow();

        assertNull(none.occupierTown());
        assertFalse(none.occupied());

        parser.accept("Territory (id = 2130):");
        parser.accept("- Town: Warzone_Italy_2");
        TerritoryInfoObservation blank =
                parser.accept("- Occupier: ").orElseThrow();

        assertNull(blank.occupierTown());
        assertFalse(blank.occupied());
    }
}
