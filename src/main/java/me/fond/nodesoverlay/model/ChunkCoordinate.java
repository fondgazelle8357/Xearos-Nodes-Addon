package me.fond.nodesoverlay.model;

public record ChunkCoordinate(int x, int z) {

    public long packed() {
        return pack(x, z);
    }

    public int blockX() {
        return x * 16;
    }

    public int blockZ() {
        return z * 16;
    }

    public static long pack(int x, int z) {
        return (Integer.toUnsignedLong(z) << 32) | Integer.toUnsignedLong(x);
    }

    public static int unpackX(long packed) {
        return (int) packed;
    }

    public static int unpackZ(long packed) {
        return (int) (packed >>> 32);
    }

    public static ChunkCoordinate fromBlock(int blockX, int blockZ) {
        return new ChunkCoordinate(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }
}
