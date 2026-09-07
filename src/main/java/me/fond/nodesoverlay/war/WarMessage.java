package me.fond.nodesoverlay.war;

public sealed interface WarMessage permits
        WarMessage.Attack,
        WarMessage.AttackDefended,
        WarMessage.ChunkCaptured,
        WarMessage.ChunkDefended,
        WarMessage.ChunkLiberated,
        WarMessage.TerritoryCaptured,
        WarMessage.TerritoryLiberated {

    String player();

    String reportedTown();

    record Attack(
            String player,
            String reportedTown,
            int blockX,
            int blockY,
            int blockZ
    ) implements WarMessage {
    }

    record AttackDefended(
            String player,
            int blockX,
            int blockY,
            int blockZ
    ) implements WarMessage {

        @Override
        public String reportedTown() {
            return "";
        }
    }

    record ChunkCaptured(
            String player,
            String reportedTown,
            int chunkX,
            int chunkZ
    ) implements WarMessage {
    }

    record ChunkDefended(
            String player,
            String reportedTown,
            int chunkX,
            int chunkZ
    ) implements WarMessage {
    }

    record ChunkLiberated(
            String player,
            String reportedTown,
            int chunkX,
            int chunkZ
    ) implements WarMessage {
    }

    record TerritoryCaptured(
            String player,
            String reportedTown,
            int territoryId
    ) implements WarMessage {
    }

    record TerritoryLiberated(
            String player,
            String reportedTown,
            int territoryId
    ) implements WarMessage {
    }
}
