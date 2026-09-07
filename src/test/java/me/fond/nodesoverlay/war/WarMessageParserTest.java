package me.fond.nodesoverlay.war;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarMessageParserTest {

    private final WarMessageParser parser = new WarMessageParser();

    @Test
    void stripsMinecraftFormattingCodes() {
        WarMessage parsed = parser.parse(
                "§c[§6War§c] §rAttacker §eis attacking §bNorthEntea §eat (§f-1140§e, §f70§e, §f-231§e)"
        ).orElseThrow();

        assertEquals(new WarMessage.Attack("Attacker", "NorthEntea", -1140, 70, -231), parsed);
    }

    @Test
    void acceptsSpacingSeparatorsAndTrailingPunctuation() {
        WarMessage attack = parser.parse(
                " [ War ]:   Player_1 is attacking North Entea at ( -17 ; 64 ; -33 )!!! "
        ).orElseThrow();
        WarMessage chunk = parser.parse(
                "[War] - Player_1 captured a chunk ( -12 ; 7 ) from North Entea..."
        ).orElseThrow();
        WarMessage territory = parser.parse(
                "[War] Player_1 captured territory ( id = 4759 ) from North Entea!"
        ).orElseThrow();

        assertEquals(new WarMessage.Attack("Player_1", "North Entea", -17, 64, -33), attack);
        assertEquals(new WarMessage.ChunkCaptured("Player_1", "North Entea", -12, 7), chunk);
        assertEquals(new WarMessage.TerritoryCaptured("Player_1", "North Entea", 4759), territory);
    }

    @Test
    void acceptsWarzoneTerritoryCaptureMessages() {
        WarMessage parsed = parser.parse(
                "[Warzone] AlpacAssassin captured territory (id=14734) from Beijing!"
        ).orElseThrow();

        assertEquals(
                new WarMessage.TerritoryCaptured("AlpacAssassin", "Beijing", 14734),
                parsed
        );
    }

    @Test
    void acceptsWarzoneCaptureAttackMessages() {
        WarMessage parsed = parser.parse(
                "[Warzone] why_not_me is capturing warzone Beijing at (2597, 65, -5790)"
        ).orElseThrow();

        assertEquals(
                new WarMessage.Attack("why_not_me", "Beijing", 2597, 65, -5790),
                parsed
        );
    }

    @Test
    void acceptsColonizationAiFlagBreakMessages() {
        WarMessage parsed = parser.parse(
                "[Colonization] AI defenders broke the flag at (15105, 77, 1075)"
        ).orElseThrow();

        assertEquals(
                new WarMessage.AttackDefended("AI defenders", 15105, 77, 1075),
                parsed
        );
    }

    @Test
    void parsesEveryDefendedAndLiberatedServerMessage() {
        assertEquals(
                new WarMessage.Attack("Player_1", "Reclaimed Town", -12, 72, 34),
                parser.parse(
                        "[War] Player_1 is liberating Reclaimed Town at (-12, 72, 34)"
                ).orElseThrow()
        );
        assertEquals(
                new WarMessage.AttackDefended("Defender", -294, 91, 766),
                parser.parse("[War] Attack at (-294, 91, 766) defended by Defender").orElseThrow()
        );
        assertEquals(
                new WarMessage.AttackDefended("Defender", -294, 91, 766),
                parser.parse("[War] Attack at (-294, 91, 766) defeated by Defender").orElseThrow()
        );
        assertEquals(
                new WarMessage.ChunkDefended("Player_1", "Occupier Town", -12, 7),
                parser.parse(
                        "[War] Player_1 defended chunk (-12, 7) against Occupier Town!"
                ).orElseThrow()
        );
        assertEquals(
                new WarMessage.ChunkLiberated("Player_1", "Occupier Town", -12, 7),
                parser.parse(
                        "[War] Player_1 liberated chunk (-12, 7) from Occupier Town!"
                ).orElseThrow()
        );
        assertEquals(
                new WarMessage.TerritoryLiberated("Player_1", "Occupier Town", 4759),
                parser.parse(
                        "[War] Player_1 liberated territory (id=4759) from Occupier Town!"
                ).orElseThrow()
        );
    }

    @Test
    void parsesTownConquestAndPlunderAsTerminalOutcomes() {
        assertEquals(
                new TownWarOutcome(
                        TownWarOutcome.Kind.CONQUERED,
                        "SouthEntea",
                        "Mauretanae"
                ),
                parser.parseTownOutcome(
                        "SouthEntea has been conquered by the alliance led by Mauretanae!"
                ).orElseThrow()
        );
        assertEquals(
                new TownWarOutcome(
                        TownWarOutcome.Kind.PLUNDERED,
                        "SouthEntea",
                        "Mauretanae"
                ),
                parser.parseTownOutcome(
                        "Town SouthEntea has been plundered by the alliance led by Mauretanae"
                ).orElseThrow()
        );
        assertEquals(
                "South Entea",
                parser.parseTownOutcome(
                        "§bTown South Entea has been plundered by the alliance led by §cMauretanae!"
                ).orElseThrow().town()
        );
    }

    @Test
    void parsesNegativeBlockAndChunkCoordinates() {
        assertEquals(
                new WarMessage.Attack("P", "Town", -1, -64, -16),
                parser.parse("[War] P is attacking Town at (-1, -64, -16)").orElseThrow()
        );
        assertEquals(
                new WarMessage.ChunkCaptured("P", "Town", -32, -1),
                parser.parse("[War] P captured chunk (-32, -1) from Town").orElseThrow()
        );
    }

    @Test
    void rejectsUnrelatedOrMalformedMessages() {
        assertTrue(parser.parse(null).isEmpty());
        assertTrue(parser.parse("   ").isEmpty());
        assertTrue(parser.parse("[Global] Player is attacking Town at (1, 2, 3)").isEmpty());
        assertTrue(parser.parse("[War] Player captured a cow from Town").isEmpty());
        assertTrue(parser.parse("[War] Player captured chunk (x, z) from Town").isEmpty());
        assertTrue(parser.parseTownOutcome("Town SouthEntea was mentioned in chat").isEmpty());
    }
}
