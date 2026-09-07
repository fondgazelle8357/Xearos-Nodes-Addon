package me.fond.nodesoverlay.war;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import me.fond.nodesoverlay.config.ServerSettings;
import me.fond.nodesoverlay.model.RgbColor;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AttackWaypointManagerTest {

    @Test
    void removesRestoredWaypointsThatHaveNoLiveAttack() {
        Int2ObjectOpenHashMap<String> collection = new Int2ObjectOpenHashMap<>();
        collection.put(11, "resolved attack");
        collection.put(12, "active attack");

        AttackWaypointManager.retainOnly(collection, Set.of(12));

        assertEquals(1, collection.size());
        assertEquals("active attack", collection.get(12));
    }

    @Test
    void clearsRestoredCollectionWhenNoAttacksRemain() {
        Int2ObjectOpenHashMap<String> collection = new Int2ObjectOpenHashMap<>();
        collection.put(21, "previous session");
        collection.put(22, "previous session");

        AttackWaypointManager.retainOnly(collection, Set.of());

        assertTrue(collection.isEmpty());
    }

    @Test
    void relationshipAwareWaypointDoesNotReuseRedOccupiedTargetColor() {
        ServerSettings settings = new ServerSettings();
        RgbColor occupiedTargetColor = new RgbColor(225, 20, 20);
        WarEvent friendlyAttack = attack(WarRelation.FRIENDLY, occupiedTargetColor);

        assertEquals(
                settings.friendlyMarkerColor,
                AttackWaypointManager.waypointColor(friendlyAttack, settings)
        );

        settings.relationshipAwareWarMarkers = false;
        assertEquals(
                occupiedTargetColor,
                AttackWaypointManager.waypointColor(friendlyAttack, settings)
        );
    }

    @Test
    void relationshipAwareWaypointUsesEveryConfiguredRelationColor() {
        ServerSettings settings = new ServerSettings();

        assertEquals(
                settings.friendlyMarkerColor,
                AttackWaypointManager.waypointColor(
                        attack(WarRelation.FRIENDLY, RgbColor.NEUTRAL),
                        settings
                )
        );
        assertEquals(
                settings.alliedMarkerColor,
                AttackWaypointManager.waypointColor(
                        attack(WarRelation.ALLIED, RgbColor.NEUTRAL),
                        settings
                )
        );
        assertEquals(
                settings.hostileMarkerColor,
                AttackWaypointManager.waypointColor(
                        attack(WarRelation.HOSTILE, RgbColor.NEUTRAL),
                        settings
                )
        );
        assertEquals(
                settings.unknownMarkerColor,
                AttackWaypointManager.waypointColor(
                        attack(WarRelation.UNKNOWN, RgbColor.NEUTRAL),
                        settings
                )
        );
    }

    private static WarEvent attack(WarRelation relation, RgbColor color) {
        return new WarEvent(
                "attack:1:2",
                WarEvent.Kind.ATTACK,
                "Player",
                "Town",
                "Nation",
                "OccupiedTown",
                100,
                24,
                70,
                40,
                1,
                2,
                relation,
                WarMarkerType.SWORD,
                color,
                Instant.EPOCH
        );
    }
}
