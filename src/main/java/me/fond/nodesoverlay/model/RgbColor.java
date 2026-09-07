package me.fond.nodesoverlay.model;

public record RgbColor(int red, int green, int blue) {

    public static final RgbColor NEUTRAL = new RgbColor(112, 112, 112);

    public RgbColor {
        red = clamp(red);
        green = clamp(green);
        blue = clamp(blue);
    }

    public int rgb() {
        return (red << 16) | (green << 8) | blue;
    }

    public int argb(int alpha) {
        return (clamp(alpha) << 24) | rgb();
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
