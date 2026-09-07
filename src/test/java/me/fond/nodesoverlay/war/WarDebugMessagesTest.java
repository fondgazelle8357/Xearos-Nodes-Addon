package me.fond.nodesoverlay.war;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarDebugMessagesTest {

    @Test
    void fullLifecycleUsesRealParsableChatWithRequestedTiming() {
        List<WarDebugMessages.TimedMessage> messages = WarDebugMessages.fullLifecycle(
                "Player_1",
                "Target Town",
                "Occupier Town",
                4759,
                -294,
                91,
                766,
                -19,
                47,
                10
        );

        assertEquals(
                List.of(0, 10, 20, 30, 40, 50, 60, 70, 80, 90),
                messages.stream().map(WarDebugMessages.TimedMessage::delaySeconds).toList()
        );
        assertEquals(
                List.of(
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
                ),
                messages.stream()
                        .map(WarDebugMessages.TimedMessage::text)
                        .map(text -> new WarMessageParser().parse(text).orElseThrow())
                        .map(WarDebugMessagesTest::kind)
                        .toList()
        );
    }

    @Test
    void emitsExactServerStylePunctuation() {
        assertEquals(
                "[War] Player_1 captured territory (id=4759) from Target Town!",
                WarDebugMessages.territoryCaptured("Player_1", "Target Town", 4759)
        );
        assertEquals(
                "[War] Player_1 defended chunk (-19, 47) against Occupier Town!",
                WarDebugMessages.chunkDefended("Player_1", "Occupier Town", -19, 47)
        );
        assertEquals(
                "[War] Attack at (-294, 91, 766) defended by Player_1",
                WarDebugMessages.attackDefended("Player_1", -294, 91, 766)
        );
    }

    private static WarEvent.Kind kind(WarMessage message) {
        return switch (message) {
            case WarMessage.Attack ignored -> WarEvent.Kind.ATTACK;
            case WarMessage.AttackDefended ignored -> WarEvent.Kind.ATTACK_DEFENDED;
            case WarMessage.ChunkCaptured ignored -> WarEvent.Kind.CHUNK_CAPTURE;
            case WarMessage.ChunkDefended ignored -> WarEvent.Kind.CHUNK_DEFENDED;
            case WarMessage.ChunkLiberated ignored -> WarEvent.Kind.CHUNK_LIBERATED;
            case WarMessage.TerritoryCaptured ignored -> WarEvent.Kind.TERRITORY_CAPTURE;
            case WarMessage.TerritoryLiberated ignored -> WarEvent.Kind.TERRITORY_LIBERATED;
        };
    }
}
