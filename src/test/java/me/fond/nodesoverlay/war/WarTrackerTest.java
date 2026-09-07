package me.fond.nodesoverlay.war;

import me.fond.nodesoverlay.data.NodesOverlayDataParser;
import me.fond.nodesoverlay.data.NodesOverlaySnapshot;
import me.fond.nodesoverlay.model.RgbColor;
import me.fond.nodesoverlay.model.OwnershipState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarTrackerTest {

    @Test
    void eventViewsStayCachedAndExcludeOccupationHistoryFromAttacks() {
        WarTracker tracker = new WarTracker();
        NodesOverlaySnapshot snapshot = snapshot();
        tracker.apply(new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56), snapshot, "LocalPlayer");
        List<WarEvent> history = tracker.events();
        List<WarEvent> attacks = tracker.attackEvents();
        assertEquals(1, attacks.size());
        assertSame(history, tracker.events());
        assertSame(attacks, tracker.attackEvents());
        assertFalse(tracker.expire(300, attacks.getFirst().updatedAt()));
        assertSame(attacks, tracker.attackEvents());
        tracker.apply(new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3), snapshot, "LocalPlayer");
        assertEquals(1, tracker.events().size());
        assertTrue(tracker.attackEvents().isEmpty());
        assertEquals(1, attacks.size(), "Previously published views remain immutable");
        assertFalse(tracker.activeOccupationEvents().isEmpty());
        tracker.reconcile(snapshot);
        assertTrue(tracker.attackEvents().isEmpty());
        tracker.clearTrackedState();
        assertTrue(tracker.events().isEmpty());
        assertTrue(tracker.attackEvents().isEmpty());
    }

    @Test
    void attackViewUpdatesForDefenceTownCompletionAndExpiry() {
        WarTracker tracker = new WarTracker();
        NodesOverlaySnapshot snapshot = snapshot();
        WarMessage.Attack attack = new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56);
        tracker.apply(attack, snapshot, "LocalPlayer");
        tracker.apply(new WarMessage.AttackDefended("LocalPlayer", -24, 70, 56), snapshot, "LocalPlayer");
        assertTrue(tracker.attackEvents().isEmpty());
        tracker.apply(attack, snapshot, "LocalPlayer");
        assertTrue(tracker.completeAttacksForTown("EnemyTown", snapshot));
        assertTrue(tracker.attackEvents().isEmpty());
        WarEvent latest = tracker.apply(attack, snapshot, "LocalPlayer");
        assertTrue(tracker.expire(300, latest.updatedAt().plusSeconds(901)));
        assertTrue(tracker.attackEvents().isEmpty());
    }

    @Test
    void resolvesHostileAttackRelationAndAttackerIdentity() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();

        WarEvent event = tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );

        assertEquals(WarEvent.Kind.ATTACK, event.kind());
        assertEquals("AttackTown", event.attackingTown());
        assertEquals("AttackNation", event.attackingNation());
        assertEquals(100, event.territoryId());
        assertEquals(-2, event.chunkX());
        assertEquals(3, event.chunkZ());
        assertEquals(WarRelation.HOSTILE, event.relation());
        assertEquals(WarMarkerType.SWORD, event.marker());
        assertEquals(new RgbColor(200, 20, 20), event.color());
        assertTrue(tracker.underAttack(100));
        assertSame(event, tracker.eventAtChunk(-2, 3));
        assertEquals(1, tracker.eventsForTerritory(100).size());
    }

    @Test
    void reconcilesAnAttackThatArrivedBeforeMapDataLoaded() {
        WarTracker tracker = new WarTracker();
        WarEvent unresolved = tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                NodesOverlaySnapshot.empty(),
                "LocalPlayer"
        );

        assertEquals(-1, unresolved.territoryId());
        assertEquals(WarRelation.UNKNOWN, unresolved.relation());

        tracker.reconcile(snapshot());
        WarEvent resolved = tracker.eventAtChunk(-2, 3);
        assertNotNull(resolved);
        assertEquals(100, resolved.territoryId());
        assertEquals("AttackTown", resolved.attackingTown());
        assertEquals("AttackNation", resolved.attackingNation());
        assertEquals(WarRelation.HOSTILE, resolved.relation());
        assertEquals(WarMarkerType.SWORD, resolved.marker());
        assertTrue(tracker.underAttack(100));
    }

    @Test
    void reconcilesCaptureLocationsAndOccupierIdentityAfterDataLoads() {
        WarTracker chunkTracker = new WarTracker();
        chunkTracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                NodesOverlaySnapshot.empty(),
                "LocalPlayer"
        );
        chunkTracker.reconcile(snapshot());

        WarEvent resolvedChunk = chunkTracker.eventAtChunk(-2, 3);
        assertNotNull(resolvedChunk);
        assertEquals(100, resolvedChunk.territoryId());
        assertEquals(new RgbColor(7, 8, 9), resolvedChunk.color());
        assertEquals(1, chunkTracker.activeCaptureEvents().size());

        WarTracker territoryTracker = new WarTracker();
        territoryTracker.apply(
                new WarMessage.TerritoryCaptured("Attacker", "EnemyTown", 100),
                NodesOverlaySnapshot.empty(),
                "LocalPlayer"
        );
        territoryTracker.reconcile(snapshot());

        WarEvent resolvedTerritory = territoryTracker.events().getFirst();
        assertEquals(100, resolvedTerritory.territoryId());
        assertEquals(-24, resolvedTerritory.blockX());
        assertEquals(56, resolvedTerritory.blockZ());
        assertEquals(-2, resolvedTerritory.chunkX());
        assertEquals(3, resolvedTerritory.chunkZ());
        assertEquals("AttackTown", resolvedTerritory.attackingTown());
    }

    @Test
    void reconciliationKeepsOtherChunkAttacksAndRemovesTerritoryOutcomes() {
        WarTracker chunkTracker = new WarTracker();
        WarEvent attack = chunkTracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                NodesOverlaySnapshot.empty(),
                "LocalPlayer"
        );
        WarEvent capturedChunk = chunkTracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -1, 3),
                NodesOverlaySnapshot.empty(),
                "LocalPlayer"
        );

        assertEquals(2, chunkTracker.events().size());
        chunkTracker.reconcile(snapshot());
        assertEquals(2, chunkTracker.events().size());
        assertEquals(attack.key(), chunkTracker.eventAtChunk(-2, 3).key());
        assertEquals(
                WarEvent.Kind.ATTACK,
                chunkTracker.eventAtChunk(-2, 3).kind()
        );
        assertEquals(capturedChunk.key(), chunkTracker.eventAtChunk(-1, 3).key());

        WarTracker territoryTracker = new WarTracker();
        territoryTracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                NodesOverlaySnapshot.empty(),
                "LocalPlayer"
        );
        territoryTracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -1, 3),
                NodesOverlaySnapshot.empty(),
                "LocalPlayer"
        );
        WarEvent capturedTerritory = territoryTracker.apply(
                new WarMessage.TerritoryCaptured("Attacker", "EnemyTown", 100),
                NodesOverlaySnapshot.empty(),
                "LocalPlayer"
        );

        assertEquals(3, territoryTracker.events().size());
        territoryTracker.reconcile(snapshot());
        assertEquals(1, territoryTracker.events().size());
        assertEquals(capturedTerritory.key(), territoryTracker.events().getFirst().key());
        assertEquals(WarEvent.Kind.TERRITORY_CAPTURE, territoryTracker.events().getFirst().kind());
    }

    @Test
    void classifiesAttackByAttackerInsteadOfOccupiedTarget() {
        NodesOverlaySnapshot snapshot = partiallyCapturedSnapshot();
        assertTrue(snapshot.territory(100).occupied());

        WarEvent localPlayer = new WarTracker().apply(
                new WarMessage.Attack("LocalPlayer", "AttackTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        WarEvent nationMember = new WarTracker().apply(
                new WarMessage.Attack("NationMate", "AttackTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        WarEvent alliedPlayer = new WarTracker().apply(
                new WarMessage.Attack("AllyPlayer", "AttackTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        WarEvent hostilePlayer = new WarTracker().apply(
                new WarMessage.Attack("Attacker", "AttackTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        WarEvent unknown = new WarTracker().apply(
                new WarMessage.Attack("MissingPlayer", "AttackTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );

        assertEquals(WarRelation.FRIENDLY, localPlayer.relation());
        assertEquals(WarRelation.FRIENDLY, nationMember.relation());
        assertEquals(WarRelation.FRIENDLY, alliedPlayer.relation());
        assertEquals(WarRelation.HOSTILE, hostilePlayer.relation());
        assertEquals(WarRelation.UNKNOWN, unknown.relation());
        assertTrue(List.of(
                localPlayer,
                nationMember,
                alliedPlayer,
                hostilePlayer,
                unknown
        ).stream().allMatch(event -> event.marker() == WarMarkerType.SWORD));
    }

    @Test
    void resolvesPortOwnersByTownOrNation() {
        NodesOverlaySnapshot snapshot = snapshot();

        assertEquals(
                WarRelation.FRIENDLY,
                WarTracker.relationToOwner("LocalTown", snapshot, "LocalPlayer")
        );
        assertEquals(
                WarRelation.FRIENDLY,
                WarTracker.relationToOwner("LocalNation", snapshot, "LocalPlayer")
        );
        assertEquals(
                WarRelation.ALLIED,
                WarTracker.relationToOwner("AllyTown", snapshot, "LocalPlayer")
        );
        assertEquals(
                WarRelation.ALLIED,
                WarTracker.relationToOwner("AllyNation", snapshot, "LocalPlayer")
        );
        assertEquals(
                WarRelation.HOSTILE,
                WarTracker.relationToOwner("EnemyTown", snapshot, "LocalPlayer")
        );
        assertEquals(
                WarRelation.HOSTILE,
                WarTracker.relationToOwner("EnemyNation", snapshot, "LocalPlayer")
        );
        assertEquals(
                WarRelation.UNKNOWN,
                WarTracker.relationToOwner("MissingOwner", snapshot, "LocalPlayer")
        );
    }

    @Test
    void resolvesNodeControllerFromCurrentOccupationInsteadOfPortInfo() {
        NodesOverlaySnapshot enemyOwned = snapshot();
        WarTracker tracker = new WarTracker();
        WarTracker.TerritoryController enemy = tracker.controllerAtChunk(
                enemyOwned.territory(100),
                -2,
                3
        );
        assertEquals("EnemyTown", enemy.town());
        assertEquals(
                WarRelation.HOSTILE,
                WarTracker.relationToController(enemy, enemyOwned, "LocalPlayer")
        );

        NodesOverlaySnapshot alliedOccupied = alliedOccupiedSnapshot();
        WarTracker.TerritoryController ally = tracker.controllerAtChunk(
                alliedOccupied.territory(100),
                -2,
                3
        );
        assertEquals("AllyTown", ally.town());
        assertEquals(
                WarRelation.ALLIED,
                WarTracker.relationToController(ally, alliedOccupied, "LocalPlayer")
        );

        NodesOverlaySnapshot nationOccupied = sameNationOccupiedSnapshot();
        WarTracker.TerritoryController friendly = tracker.controllerAtChunk(
                nationOccupied.territory(100),
                -2,
                3
        );
        assertEquals("LocalTown", friendly.town());
        assertEquals(
                WarRelation.FRIENDLY,
                WarTracker.relationToController(friendly, nationOccupied, "LocalPlayer")
        );
    }

    @Test
    void alliedAttackReconcilesFromUnknownToFriendlyAfterMapDataLoads() {
        WarTracker tracker = new WarTracker();
        tracker.apply(
                new WarMessage.Attack("AllyPlayer", "AttackTown", -24, 70, 56),
                NodesOverlaySnapshot.empty(),
                "LocalPlayer"
        );

        assertEquals(WarRelation.UNKNOWN, tracker.eventAtChunk(-2, 3).relation());

        tracker.reconcile(partiallyCapturedSnapshot());

        WarEvent resolved = tracker.eventAtChunk(-2, 3);
        assertNotNull(resolved);
        assertEquals(WarRelation.FRIENDLY, resolved.relation());
        assertEquals("AllyTown", resolved.attackingTown());
        assertEquals("AllyNation", resolved.attackingNation());
    }

    @Test
    void keepsLiveTerritoryCaptureAuthoritativeAcrossMapAgreementAndDisagreement() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();

        WarEvent chunkEvent = tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        LiveCapture chunkCapture = tracker.captureAtChunk(-2, 3, 100);
        assertNotNull(chunkCapture);
        assertEquals("AttackTown", chunkCapture.occupierTown());
        assertEquals("AttackNation", chunkCapture.occupierNation());
        assertEquals(new RgbColor(7, 8, 9), chunkCapture.occupierColor());
        assertEquals(WarMarkerType.FLAG, chunkEvent.marker());
        assertEquals(-24, chunkEvent.blockX());
        assertEquals(56, chunkEvent.blockZ());

        WarEvent territoryEvent = tracker.apply(
                new WarMessage.TerritoryCaptured("Attacker", "EnemyTown", 100),
                snapshot,
                "LocalPlayer"
        );
        LiveCapture territoryCapture = tracker.captureForTerritory(100);
        assertNotNull(territoryCapture);
        assertEquals(-24, territoryEvent.blockX());
        assertEquals(56, territoryEvent.blockZ());
        assertEquals(territoryCapture, tracker.captureAtChunk(-2, 3, 100));
        assertSame(territoryCapture, tracker.captureAtChunk(20, 20, 100));

        assertFalse(tracker.expire(3600));
        assertFalse(tracker.expire(0));
        assertEquals(List.of(territoryEvent), tracker.events());
        assertEquals(territoryCapture, tracker.captureAtChunk(-2, 3, 100));
        assertEquals(territoryCapture, tracker.captureForTerritory(100));

        tracker.reconcile(snapshot(true));

        assertEquals(territoryCapture, tracker.captureAtChunk(-2, 3, 100));
        assertEquals(territoryCapture, tracker.captureForTerritory(100));
        assertFalse(tracker.expire(0));
        assertEquals(List.of(territoryEvent), tracker.events());

        tracker.reconcile(snapshot);

        assertEquals(territoryCapture, tracker.captureAtChunk(-2, 3, 100));
        assertEquals(territoryCapture, tracker.captureForTerritory(100));
    }

    @Test
    void capturedChunkStripeDoesNotExpireBeforeItsOutcomeArrives() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();
        WarEvent capture = tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        LiveCapture liveCapture = tracker.captureAtChunk(-2, 3, 100);

        assertNotNull(liveCapture);
        assertFalse(tracker.expire(0));
        assertEquals(List.of(capture), tracker.events());
        assertSame(liveCapture, tracker.captureAtChunk(-2, 3, 100));
        assertEquals(List.of(capture), tracker.activeOccupationEvents());

        WarEvent defended = tracker.apply(
                new WarMessage.ChunkDefended("LocalPlayer", "AttackTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );

        assertNull(tracker.captureAtChunk(-2, 3, 100));
        assertEquals(List.of(defended), tracker.activeOccupationEvents());

        tracker.reconcile(snapshot);
        assertEquals(List.of(defended), tracker.activeOccupationEvents());
    }

    @Test
    void friendlyChunkCaptureDoesNotOverrideAlliedSnapshotOccupation() {
        NodesOverlaySnapshot snapshot = alliedOccupiedSnapshot();
        assertTrue(snapshot.territory(100).occupied());
        assertEquals("AllyTown", snapshot.territory(100).occupierTownName());
        WarTracker tracker = new WarTracker();
        tracker.apply(
                new WarMessage.Attack("LocalPlayer", "AllyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );

        WarEvent ignoredCapture = tracker.apply(
                new WarMessage.ChunkCaptured("LocalPlayer", "AllyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );

        assertEquals(WarEvent.Kind.CHUNK_CAPTURE, ignoredCapture.kind());
        assertTrue(tracker.events().isEmpty());
        assertTrue(tracker.activeOccupationEvents().isEmpty());
        assertNull(tracker.captureAtChunk(-2, 3, 100));
        assertFalse(tracker.underAttack(100));
        assertEquals("AllyTown", snapshot.territory(100).occupierTownName());
    }

    @Test
    void nationMemberChunkCaptureDoesNotOverrideSameNationSnapshotOccupation() {
        NodesOverlaySnapshot snapshot = sameNationOccupiedSnapshot();
        assertTrue(snapshot.territory(100).occupied());
        assertEquals("LocalTown", snapshot.territory(100).occupierTownName());
        WarTracker tracker = new WarTracker();

        tracker.apply(
                new WarMessage.ChunkCaptured("NationMate", "LocalTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );

        assertTrue(tracker.events().isEmpty());
        assertTrue(tracker.activeOccupationEvents().isEmpty());
        assertNull(tracker.captureAtChunk(-2, 3, 100));
        assertEquals("LocalTown", snapshot.territory(100).occupierTownName());
    }

    @Test
    void friendlyChunkCapturePreservesLiveAlliedTerritoryCapture() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();
        WarEvent territoryCapture = tracker.apply(
                new WarMessage.TerritoryCaptured("AllyPlayer", "EnemyTown", 100),
                snapshot,
                "LocalPlayer"
        );
        LiveCapture alliedOccupation = tracker.captureForTerritory(100);
        assertNotNull(alliedOccupation);
        assertEquals("AllyNation", alliedOccupation.occupierNation());
        tracker.apply(
                new WarMessage.Attack("LocalPlayer", "AllyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );

        tracker.apply(
                new WarMessage.ChunkCaptured("LocalPlayer", "AllyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );

        assertSame(alliedOccupation, tracker.captureForTerritory(100));
        assertSame(alliedOccupation, tracker.captureAtChunk(-2, 3, 100));
        assertEquals(List.of(territoryCapture), tracker.events());
        assertEquals(List.of(territoryCapture), tracker.activeOccupationEvents());
        assertFalse(tracker.underAttack(100));
    }

    @Test
    void friendlyChunkCapturePreservesAlliedTerritoryInfoBaseline() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();
        assertTrue(tracker.observeTerritory(
                new TerritoryInfoObservation(
                        100,
                        "EnemyTown",
                        "AllyTown",
                        Instant.now()
                ),
                snapshot
        ));
        LiveCapture alliedOccupation = tracker.captureAtChunk(-2, 3, 100);
        assertNotNull(alliedOccupation);

        tracker.apply(
                new WarMessage.ChunkCaptured("LocalPlayer", "AllyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );

        assertEquals(alliedOccupation, tracker.captureAtChunk(-2, 3, 100));
        assertEquals(1, tracker.activeOccupationEvents().size());
        assertEquals(
                WarEvent.Kind.TERRITORY_CAPTURE,
                tracker.activeOccupationEvents().getFirst().kind()
        );
    }

    @Test
    void chunkCaptureStillOverridesWhenEitherSideIsHostile() {
        NodesOverlaySnapshot snapshot = snapshot();

        WarTracker hostileAttackerTracker = new WarTracker();
        LiveCapture alliedTerritory = liveTerritoryCapture(
                hostileAttackerTracker,
                snapshot,
                "AllyPlayer"
        );
        hostileAttackerTracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "AllyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        LiveCapture hostileChunk = hostileAttackerTracker.captureAtChunk(-2, 3, 100);
        assertNotNull(hostileChunk);
        assertEquals("AttackNation", hostileChunk.occupierNation());
        assertSame(alliedTerritory, hostileAttackerTracker.captureForTerritory(100));

        WarTracker hostileOccupationTracker = new WarTracker();
        LiveCapture hostileTerritory = liveTerritoryCapture(
                hostileOccupationTracker,
                snapshot,
                "Attacker"
        );
        hostileOccupationTracker.apply(
                new WarMessage.ChunkCaptured("LocalPlayer", "AttackTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        LiveCapture friendlyChunk = hostileOccupationTracker.captureAtChunk(-2, 3, 100);
        assertNotNull(friendlyChunk);
        assertEquals("LocalNation", friendlyChunk.occupierNation());
        assertSame(hostileTerritory, hostileOccupationTracker.captureForTerritory(100));
    }

    @Test
    void territoryLevelCapturedFeedDoesNotDiscardExactChunkCaptures() {
        WarTracker tracker = new WarTracker();
        WarEvent capture = tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot(),
                "LocalPlayer"
        );

        NodesOverlaySnapshot partialCapture = partiallyCapturedSnapshot();
        assertTrue(partialCapture.territory(100).occupied());

        tracker.reconcile(partialCapture);

        assertNotNull(tracker.captureAtChunk(-2, 3, 100));
        assertEquals(List.of(capture.key()), tracker.activeCaptureEvents().stream()
                .map(WarEvent::key)
                .toList());
    }

    @Test
    void keepsAttacksForDifferentChunksEvenWhenTerritoryOrTownMatches() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();

        WarEvent firstKnown = tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        WarEvent secondKnown = tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -8, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        WarEvent firstUnknown = tracker.apply(
                new WarMessage.Attack("Attacker", "MissingTown", 5000, 70, 5000),
                snapshot,
                "LocalPlayer"
        );
        WarEvent secondUnknown = tracker.apply(
                new WarMessage.Attack("Attacker", "MissingTown", 5100, 70, 5000),
                snapshot,
                "LocalPlayer"
        );

        assertEquals(2, tracker.eventsForTerritory(100).size());
        assertSame(firstKnown, tracker.eventAtChunk(-2, 3));
        assertSame(secondKnown, tracker.eventAtChunk(-1, 3));
        assertSame(firstUnknown, tracker.eventAtChunk(312, 312));
        assertSame(secondUnknown, tracker.eventAtChunk(318, 312));
        assertEquals(4, tracker.events().size());
    }

    @Test
    void townConquestClearsEveryAttackFlagForThatTownOnly() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();
        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -8, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        WarEvent unrelated = tracker.apply(
                new WarMessage.Attack("Attacker", "AttackTown", 328, 70, 328),
                snapshot,
                "LocalPlayer"
        );

        assertTrue(tracker.completeAttacksForTown("enemytown", snapshot));
        assertEquals(List.of(unrelated), tracker.events());
        assertFalse(tracker.completeAttacksForTown("EnemyTown", snapshot));
    }

    @Test
    void activeAttackResolvesImmediatelyBeforeTimeout() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();
        WarEvent attack = tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );

        tracker.expire(0);

        assertEquals(List.of(attack), tracker.events());
        assertTrue(tracker.underAttack(100));
        assertTrue(tracker.hasAutomaticWaypointForTerritory(100));

        tracker.apply(
                new WarMessage.AttackDefended("LocalPlayer", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );

        assertTrue(tracker.events().isEmpty());
        assertFalse(tracker.underAttack(100));
        assertFalse(tracker.hasAutomaticWaypointForTerritory(100));
    }

    @Test
    void activeAttackExpiresOnlyAfterFifteenMinutes() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();
        WarEvent attack = tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );

        assertFalse(tracker.expire(
                3_600,
                attack.updatedAt().plusSeconds(
                        WarTracker.ACTIVE_ATTACK_EXPIRATION_SECONDS
                )
        ));
        assertEquals(List.of(attack), tracker.events());
        assertTrue(tracker.underAttack(100));

        assertTrue(tracker.expire(
                3_600,
                attack.updatedAt()
                        .plusSeconds(WarTracker.ACTIVE_ATTACK_EXPIRATION_SECONDS)
                        .plusMillis(1)
        ));
        assertTrue(tracker.events().isEmpty());
        assertFalse(tracker.underAttack(100));
        assertFalse(tracker.hasAutomaticWaypointForTerritory(100));
    }

    @Test
    void chunkCaptureReplacesOnlyTheAttackInThatExactChunk() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();

        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -8, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        WarEvent captured = tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );

        assertSame(captured, tracker.eventAtChunk(-2, 3));
        assertEquals(WarEvent.Kind.ATTACK, tracker.eventAtChunk(-1, 3).kind());
        assertTrue(tracker.underAttack(100));
        assertEquals(2, tracker.events().size());
    }

    @Test
    void chunkCaptureKeepsUnrelatedWarEvents() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();

        WarEvent unrelated = tracker.apply(
                new WarMessage.Attack("Attacker", "MissingTown", 5000, 70, 5000),
                snapshot,
                "LocalPlayer"
        );
        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        WarEvent captured = tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );

        assertSame(unrelated, tracker.eventAtChunk(312, 312));
        assertSame(captured, tracker.eventAtChunk(-2, 3));
        assertEquals(2, tracker.events().size());
    }

    @Test
    void defendedAttackRemovesTheAttackWithoutLeavingAnIcon() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();

        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        WarEvent defended = tracker.apply(
                new WarMessage.AttackDefended("LocalPlayer", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );

        assertTrue(tracker.events().isEmpty());
        assertNull(tracker.eventAtChunk(-2, 3));
        assertEquals(WarEvent.Kind.ATTACK_DEFENDED, defended.kind());
        assertFalse(tracker.underAttack(100));
        assertFalse(tracker.hasAutomaticWaypointForTerritory(100));
    }

    @Test
    void defendedAndLiberatedChunksRemoveProvisionalCaptureState() {
        NodesOverlaySnapshot snapshot = snapshot();

        WarTracker defendedTracker = new WarTracker();
        defendedTracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        WarEvent defended = defendedTracker.apply(
                new WarMessage.ChunkDefended("LocalPlayer", "AttackTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        assertNull(defendedTracker.captureAtChunk(-2, 3, 100));
        assertEquals(1, defendedTracker.events().size());
        assertSame(defended, defendedTracker.events().getFirst());
        assertEquals(WarEvent.Kind.CHUNK_DEFENDED, defended.kind());

        WarTracker liberatedTracker = new WarTracker();
        liberatedTracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        WarEvent liberated = liberatedTracker.apply(
                new WarMessage.ChunkLiberated("LocalPlayer", "AttackTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        assertNull(liberatedTracker.captureAtChunk(-2, 3, 100));
        assertEquals(1, liberatedTracker.events().size());
        assertSame(liberated, liberatedTracker.events().getFirst());
        assertEquals(WarEvent.Kind.CHUNK_LIBERATED, liberated.kind());
        assertEquals(List.of(liberated), liberatedTracker.activeOccupationEvents());

        liberatedTracker.reconcile(partiallyCapturedSnapshot());
        assertEquals(List.of(liberated), liberatedTracker.activeOccupationEvents());

        liberatedTracker.reconcile(snapshot);
        assertEquals(List.of(liberated), liberatedTracker.activeOccupationEvents());
    }

    @Test
    void exactChunkLiberationOverridesButDoesNotEraseLiveTerritoryCapture() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();

        WarEvent captured = tracker.apply(
                new WarMessage.TerritoryCaptured("Attacker", "EnemyTown", 100),
                snapshot,
                "LocalPlayer"
        );
        WarEvent liberated = tracker.apply(
                new WarMessage.ChunkLiberated("LocalPlayer", "AttackTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );

        LiveCapture territoryCapture = tracker.captureForTerritory(100);
        assertNotNull(territoryCapture);
        assertNull(tracker.captureAtChunk(-2, 3, 100));
        assertSame(territoryCapture, tracker.captureAtChunk(-1, 3, 100));
        assertEquals(2, tracker.events().size());
        assertTrue(tracker.events().contains(captured));
        assertTrue(tracker.events().contains(liberated));
    }

    @Test
    void exactChunkCaptureOverridesButDoesNotEraseLiveTerritoryLiberation() {
        NodesOverlaySnapshot snapshot = snapshot(true);
        WarTracker tracker = new WarTracker();

        WarEvent liberated = tracker.apply(
                new WarMessage.TerritoryLiberated("LocalPlayer", "AttackTown", 100),
                snapshot,
                "LocalPlayer"
        );
        WarEvent captured = tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );

        assertNotNull(tracker.captureAtChunk(-2, 3, 100));
        assertNull(tracker.captureAtChunk(-1, 3, 100));
        assertEquals(2, tracker.events().size());
        assertTrue(tracker.events().contains(liberated));
        assertTrue(tracker.events().contains(captured));
    }

    @Test
    void territoryLiberationReplacesEveryEarlierStateForThatTerritory() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();

        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        tracker.apply(
                new WarMessage.TerritoryCaptured("Attacker", "EnemyTown", 100),
                snapshot,
                "LocalPlayer"
        );
        WarEvent liberated = tracker.apply(
                new WarMessage.TerritoryLiberated("LocalPlayer", "AttackTown", 100),
                snapshot,
                "LocalPlayer"
        );

        assertNull(tracker.captureForTerritory(100));
        assertNull(tracker.captureAtChunk(-2, 3, 100));
        assertFalse(tracker.underAttack(100));
        assertEquals(1, tracker.events().size());
        assertSame(liberated, tracker.events().getFirst());
        assertEquals(WarEvent.Kind.TERRITORY_LIBERATED, liberated.kind());
        assertEquals(WarMarkerType.SHIELD, liberated.marker());
    }

    @Test
    void exactDebugChatRunsThroughTheCompleteMapLifecycle() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();
        WarMessageParser parser = new WarMessageParser();
        List<WarEvent.Kind> expectedKinds = List.of(
                WarEvent.Kind.ATTACK,
                WarEvent.Kind.ATTACK_DEFENDED,
                WarEvent.Kind.ATTACK,
                WarEvent.Kind.CHUNK_CAPTURE,
                WarEvent.Kind.CHUNK_DEFENDED,
                WarEvent.Kind.CHUNK_CAPTURE,
                WarEvent.Kind.CHUNK_LIBERATED,
                WarEvent.Kind.ATTACK,
                WarEvent.Kind.TERRITORY_CAPTURE,
                WarEvent.Kind.TERRITORY_LIBERATED
        );
        List<WarDebugMessages.TimedMessage> messages = WarDebugMessages.fullLifecycle(
                "Attacker",
                "EnemyTown",
                "AttackTown",
                100,
                -24,
                70,
                56,
                -2,
                3,
                10
        );

        for (int index = 0; index < messages.size(); index++) {
            WarMessage message = parser.parse(messages.get(index).text()).orElseThrow();
            WarEvent event = tracker.apply(message, snapshot, "LocalPlayer");

            assertEquals(expectedKinds.get(index), event.kind());
            if (event.kind() == WarEvent.Kind.ATTACK_DEFENDED) {
                assertTrue(tracker.events().isEmpty());
            } else if (index == 7) {
                assertEquals(2, tracker.events().size());
                assertTrue(tracker.events().contains(event));
                assertEquals(
                        List.of(WarEvent.Kind.CHUNK_LIBERATED),
                        tracker.activeOccupationEvents().stream().map(WarEvent::kind).toList()
                );
            } else {
                assertEquals(1, tracker.events().size());
                assertSame(event, tracker.events().getFirst());
            }
        }

        assertFalse(tracker.underAttack(100));
        assertNull(tracker.captureAtChunk(-2, 3, 100));
        assertNull(tracker.captureForTerritory(100));
    }

    @Test
    void territoryCaptureOverridesOlderCapturedChunksAndCompletesItsAttacks() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();

        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -8, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        LiveCapture olderChunkCapture = tracker.captureAtChunk(-2, 3, 100);

        WarEvent territoryEvent = tracker.apply(
                new WarMessage.TerritoryCaptured("Attacker", "EnemyTown", 100),
                snapshot,
                "LocalPlayer"
        );
        LiveCapture territoryCapture = tracker.captureForTerritory(100);

        assertNotNull(olderChunkCapture);
        assertNotNull(territoryCapture);
        assertSame(territoryCapture, tracker.captureAtChunk(-2, 3, 100));
        assertFalse(tracker.underAttack(100));
        assertEquals(1, tracker.events().size());
        assertSame(territoryEvent, tracker.events().getFirst());
    }

    @Test
    void mapReconcileDoesNotRemoveLiveTerritoryCaptureView() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();

        WarEvent event = tracker.apply(
                new WarMessage.TerritoryCaptured("Attacker", "EnemyTown", 100),
                snapshot,
                "LocalPlayer"
        );

        assertEquals(1, tracker.activeCaptureEvents().size());
        assertSame(event, tracker.activeCaptureEvents().getFirst());

        tracker.reconcile(snapshot(true));

        assertEquals(1, tracker.activeCaptureEvents().size());
        assertSame(event, tracker.activeCaptureEvents().getFirst());
        assertEquals(1, tracker.events().size());
        assertSame(event, tracker.events().getFirst());
    }

    @Test
    void waypointSuppressionEndsWhenTheAttackStateIsReplaced() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();

        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -8, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        tracker.suppressWaypointForTerritory(100);

        tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        assertTrue(tracker.hasActiveAttackForTerritory(100));

        tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -1, 3),
                snapshot,
                "LocalPlayer"
        );
        assertFalse(tracker.hasActiveAttackForTerritory(100));
        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );

        assertTrue(tracker.hasAutomaticWaypointForTerritory(100));

        tracker.suppressWaypointForTerritory(100);
        assertFalse(tracker.expire(0));
        assertTrue(tracker.hasActiveAttackForTerritory(100));
        assertFalse(tracker.hasAutomaticWaypointForTerritory(100));
        tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        assertTrue(tracker.hasAutomaticWaypointForTerritory(100));

        tracker.suppressWaypointForTerritory(100);
        tracker.apply(
                new WarMessage.TerritoryCaptured("Attacker", "EnemyTown", 100),
                snapshot,
                "LocalPlayer"
        );
        tracker.apply(
                new WarMessage.Attack("Attacker", "EnemyTown", -24, 70, 56),
                snapshot,
                "LocalPlayer"
        );
        assertTrue(tracker.hasAutomaticWaypointForTerritory(100));
    }

    @Test
    void inGameTerritoryInfoBecomesLiveBaselineAndAcceptsLaterChunkOutcomes() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();
        tracker.apply(
                new WarMessage.ChunkCaptured("Attacker", "EnemyTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );
        LiveCapture exactChunkCapture = tracker.captureAtChunk(-2, 3, 100);
        assertNotNull(exactChunkCapture);
        TerritoryInfoObservation occupied = new TerritoryInfoObservation(
                100,
                "EnemyTown",
                "AttackTown",
                Instant.now()
        );

        assertTrue(tracker.observeTerritory(occupied, snapshot));

        LiveCapture territoryCapture = tracker.captureForTerritory(100);
        assertNotNull(territoryCapture);
        assertEquals("AttackTown", territoryCapture.occupierTown());
        assertEquals("AttackNation", territoryCapture.occupierNation());
        assertEquals(new RgbColor(7, 8, 9), territoryCapture.occupierColor());
        assertSame(exactChunkCapture, tracker.captureAtChunk(-2, 3, 100));
        assertEquals(List.of("chunk:-2:3"), tracker.events().stream()
                .map(WarEvent::key)
                .toList());
        assertEquals(
                Set.of(WarEvent.Kind.CHUNK_CAPTURE, WarEvent.Kind.TERRITORY_CAPTURE),
                tracker.activeOccupationEvents().stream()
                        .map(WarEvent::kind)
                        .collect(java.util.stream.Collectors.toSet())
        );

        TerritoryInfoObservation unchanged = new TerritoryInfoObservation(
                100,
                "EnemyTown",
                "AttackTown",
                Instant.now()
        );
        assertFalse(tracker.observeTerritory(unchanged, snapshot));
        assertEquals(2, tracker.activeOccupationEvents().size());

        tracker.reconcile(snapshot);
        tracker.reconcile(partiallyCapturedSnapshot());

        assertNotNull(tracker.captureForTerritory(100));
        assertEquals(
                new RgbColor(7, 8, 9),
                tracker.activeOccupationEvents().getFirst().color()
        );

        WarEvent chunkLiberated = tracker.apply(
                new WarMessage.ChunkLiberated("LocalPlayer", "AttackTown", -2, 3),
                snapshot,
                "LocalPlayer"
        );

        assertNull(tracker.captureAtChunk(-2, 3, 100));
        assertNotNull(tracker.captureAtChunk(-1, 3, 100));
        assertEquals(2, tracker.activeOccupationEvents().size());
        assertTrue(tracker.activeOccupationEvents().contains(chunkLiberated));

        TerritoryInfoObservation unoccupied = new TerritoryInfoObservation(
                100,
                "EnemyTown",
                null,
                Instant.now()
        );
        assertTrue(tracker.observeTerritory(unoccupied, snapshot));

        assertNull(tracker.captureForTerritory(100));
        assertNull(tracker.captureAtChunk(-1, 3, 100));
        assertEquals(
                Set.of(WarEvent.Kind.CHUNK_LIBERATED, WarEvent.Kind.TERRITORY_LIBERATED),
                tracker.activeOccupationEvents().stream()
                        .map(WarEvent::kind)
                        .collect(java.util.stream.Collectors.toSet())
        );
    }

    @Test
    void territoryInfoMatchingDownloadedStateDoesNotReportAChange() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();
        TerritoryInfoObservation matching = new TerritoryInfoObservation(
                100,
                "EnemyTown",
                null,
                Instant.now()
        );

        assertFalse(tracker.observeTerritory(matching, snapshot));
        assertFalse(tracker.observeTerritory(
                new TerritoryInfoObservation(
                        100,
                        "enemytown",
                        null,
                        Instant.now()
                ),
                snapshot
        ));
        assertEquals(1, tracker.activeOccupationEvents().size());
        assertEquals(
                WarEvent.Kind.TERRITORY_LIBERATED,
                tracker.activeOccupationEvents().getFirst().kind()
        );
    }

    @Test
    void territoryTownRowOverridesDownloadedOwnerStatusAndSurvivesRefresh() {
        NodesOverlaySnapshot snapshot = snapshot();
        WarTracker tracker = new WarTracker();
        TerritoryInfoObservation observation = new TerritoryInfoObservation(
                100,
                "LocalTown",
                null,
                Instant.now()
        );

        assertTrue(tracker.observeTerritory(observation, snapshot));
        NodesOverlaySnapshot projected = tracker.applyTerritoryOwnership(snapshot);

        assertEquals("LocalTown", projected.territory(100).ownerTownName());
        assertEquals("LocalNation", projected.territory(100).ownerNationName());
        assertEquals(OwnershipState.OWNED, projected.territory(100).state());
        assertEquals(new RgbColor(20, 40, 60), projected.territory(100).ownerColor());
        assertFalse(projected.territory(100).occupied());

        tracker.reconcile(snapshot);
        NodesOverlaySnapshot refreshed = tracker.applyTerritoryOwnership(snapshot);
        assertEquals("LocalTown", refreshed.territory(100).ownerTownName());
        assertEquals(OwnershipState.OWNED, refreshed.territory(100).state());
    }

    private static NodesOverlaySnapshot snapshot() {
        return snapshot(false);
    }

    private static NodesOverlaySnapshot snapshot(boolean capturedByAttacker) {
        return snapshot(
                capturedByAttacker ? "[100]" : "[]",
                "[]",
                capturedByAttacker ? "[]" : "[100]"
        );
    }

    private static NodesOverlaySnapshot partiallyCapturedSnapshot() {
        return snapshot("[100]", "[100]", "[100]");
    }

    private static NodesOverlaySnapshot alliedOccupiedSnapshot() {
        return snapshot("[]", "[]", "[100]", "[]", "[100]");
    }

    private static NodesOverlaySnapshot sameNationOccupiedSnapshot() {
        return snapshot("[]", "[]", "[100]", "[100]", "[]");
    }

    private static LiveCapture liveTerritoryCapture(
            WarTracker tracker,
            NodesOverlaySnapshot snapshot,
            String player
    ) {
        tracker.apply(
                new WarMessage.TerritoryCaptured(player, "EnemyTown", 100),
                snapshot,
                "LocalPlayer"
        );
        LiveCapture capture = tracker.captureForTerritory(100);
        assertNotNull(capture);
        return capture;
    }

    private static NodesOverlaySnapshot snapshot(
            String attackerTerritories,
            String attackerCaptured,
            String enemyTerritories
    ) {
        return snapshot(
                attackerTerritories,
                attackerCaptured,
                enemyTerritories,
                "[]",
                "[]"
        );
    }

    private static NodesOverlaySnapshot snapshot(
            String attackerTerritories,
            String attackerCaptured,
            String enemyTerritories,
            String localCaptured,
            String allyCaptured
    ) {
        String towns = """
                {
                  "meta": {"type": "towns"},
                  "residents": {
                    "local": {"name": "LocalPlayer", "town": "LocalTown", "nation": "LocalNation"},
                    "nationmate": {"name": "NationMate", "town": "LocalSiblingTown", "nation": "LocalNation"},
                    "ally": {"name": "AllyPlayer", "town": "AllyTown", "nation": "AllyNation"},
                    "attacker": {"name": "Attacker", "town": "AttackTown", "nation": "AttackNation"}
                  },
                  "towns": {
                    "LocalTown": {
                      "color": [10, 20, 30],
                      "territories": [],
                      "annexed": [],
                      "captured": %s,
                      "claimed": [],
                      "cores": [],
                      "sphered": [],
                      "allies": ["AllyTown"],
                      "enemies": ["EnemyTown"]
                    },
                    "LocalSiblingTown": {
                      "color": [12, 24, 36],
                      "territories": [],
                      "annexed": [],
                      "captured": [],
                      "claimed": [],
                      "cores": [],
                      "sphered": [],
                      "allies": [],
                      "enemies": []
                    },
                    "AttackTown": {
                      "color": [1, 2, 3],
                      "territories": %s,
                      "annexed": [],
                      "captured": %s,
                      "claimed": [],
                      "cores": [],
                      "sphered": [],
                      "allies": [],
                      "enemies": []
                    },
                    "EnemyTown": {
                      "color": [100, 0, 0],
                      "territories": %s,
                      "annexed": [],
                      "captured": [],
                      "claimed": [],
                      "cores": [],
                      "sphered": [],
                      "allies": [],
                      "enemies": ["LocalTown"]
                    },
                    "AllyTown": {
                      "color": [0, 100, 0],
                      "territories": [],
                      "annexed": [],
                      "captured": %s,
                      "claimed": [],
                      "cores": [],
                      "sphered": [],
                      "allies": ["LocalTown"],
                      "enemies": []
                    }
                  },
                  "nations": {
                    "LocalNation": {
                      "color": [20, 40, 60],
                      "towns": ["LocalTown", "LocalSiblingTown"],
                      "allies": ["AllyNation"],
                      "enemies": ["EnemyNation", "AttackNation"]
                    },
                    "AttackNation": {
                      "color": [7, 8, 9],
                      "towns": ["AttackTown"],
                      "allies": [],
                      "enemies": ["LocalNation"]
                    },
                    "EnemyNation": {
                      "color": [200, 20, 20],
                      "towns": ["EnemyTown"],
                      "allies": [],
                      "enemies": ["LocalNation"]
                    },
                    "AllyNation": {
                      "color": [20, 200, 20],
                      "towns": ["AllyTown"],
                      "allies": ["LocalNation"],
                      "enemies": []
                    }
                  },
                  "plots": {}
                }
                """.formatted(
                localCaptured,
                attackerTerritories,
                attackerCaptured,
                enemyTerritories,
                allyCaptured
        );
        String world = """
                {
                  "meta": {"type": "world"},
                  "nodes": {},
                  "territories": {
                    "100": {
                      "name": "",
                      "core": [-24, 56],
                      "coreChunk": [-2, 3],
                      "chunks": [-2, 3, -1, 3],
                      "size": 2,
                      "neighbors": [],
                      "isEdge": false,
                      "nodes": [],
                      "color": 0
                    }
                  }
                }
                """;
        return new NodesOverlayDataParser().parse(towns, world);
    }
}
