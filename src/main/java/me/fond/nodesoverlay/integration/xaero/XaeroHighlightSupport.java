package me.fond.nodesoverlay.integration.xaero;

import me.fond.nodesoverlay.model.NationRecord;
import me.fond.nodesoverlay.model.RgbColor;
import me.fond.nodesoverlay.model.TerritoryView;
import me.fond.nodesoverlay.model.TownRecord;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.world.World;

final class XaeroHighlightSupport {

    private XaeroHighlightSupport() {
    }

    static boolean shouldRender(RegistryKey<World> dimension, boolean enabled) {
        if (!enabled || dimension == null) {
            return false;
        }
        // Most servers use minecraft:overworld, but several map providers
        // expose their main world under a namespaced key (for example
        // server:overworld or server:world). The node feeds describe one
        // primary surface world, so do not silently disable the overlay just
        // because the namespace differs. Nether/end dimensions remain out of
        // scope unless they are explicitly named as a surface world.
        if (World.OVERWORLD.equals(dimension)) {
            return true;
        }
        String path = dimension.getValue().getPath().toLowerCase(java.util.Locale.ROOT);
        return !path.contains("nether") && !path.contains("end");
    }

    static int[] territoryBaseColors(
            XaeroOverlayRuntime.State state,
            int chunkX,
            int chunkZ,
            int[] output
    ) {
        TerritoryView current = state.territoryAtChunk(chunkX, chunkZ);
        if (current == null) {
            return null;
        }

        XaeroOverlayRuntime.OverlayStyle style = state.style();
        TerritoryView top = state.territoryAtChunk(chunkX, chunkZ - 1);
        TerritoryView right = state.territoryAtChunk(chunkX + 1, chunkZ);
        TerritoryView bottom = state.territoryAtChunk(chunkX, chunkZ + 1);
        TerritoryView left = state.territoryAtChunk(chunkX - 1, chunkZ);
        boolean topBorder = differentTerritory(current, top);
        boolean rightBorder = differentTerritory(current, right);
        boolean bottomBorder = differentTerritory(current, bottom);
        boolean leftBorder = differentTerritory(current, left);
        int topAlpha = sideAlpha(
                current,
                top,
                style
        );
        int rightAlpha = sideAlpha(
                current,
                right,
                style
        );
        int bottomAlpha = sideAlpha(
                current,
                bottom,
                style
        );
        int leftAlpha = sideAlpha(
                current,
                left,
                style
        );
        int thickness = style.borderThickness();
        RgbColor baseColor = state.baseColor(current);

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int alpha = style.fillAlpha();
                boolean borderApplied = false;
                if (topBorder && localZ < thickness) {
                    alpha = topAlpha;
                    borderApplied = true;
                }
                if (rightBorder && localX >= 16 - thickness) {
                    alpha = borderApplied ? Math.max(alpha, rightAlpha) : rightAlpha;
                    borderApplied = true;
                }
                if (bottomBorder && localZ >= 16 - thickness) {
                    alpha = borderApplied ? Math.max(alpha, bottomAlpha) : bottomAlpha;
                    borderApplied = true;
                }
                if (leftBorder && localX < thickness) {
                    alpha = borderApplied ? Math.max(alpha, leftAlpha) : leftAlpha;
                    borderApplied = true;
                }
                output[(localZ << 4) | localX] = xaeroColor(
                        borderApplied ? style.borderColor() : baseColor,
                        alpha
                );
            }
        }
        return output;
    }

    /**
     * Produces the final territory fill, border and occupation stripes in one
     * array. Xaero otherwise blends each registered highlighter over all 256
     * pixels separately, which doubled the cost of the minimap overlay.
     */
    static int[] territoryOverlayColors(
            XaeroOverlayRuntime.State state,
            int chunkX,
            int chunkZ,
            int[] output
    ) {
        TerritoryView current = state.territoryAtChunk(chunkX, chunkZ);
        if (current == null) {
            return null;
        }

        XaeroOverlayRuntime.OverlayStyle style = state.style();
        TerritoryView top = state.territoryAtChunk(chunkX, chunkZ - 1);
        TerritoryView right = state.territoryAtChunk(chunkX + 1, chunkZ);
        TerritoryView bottom = state.territoryAtChunk(chunkX, chunkZ + 1);
        TerritoryView left = state.territoryAtChunk(chunkX - 1, chunkZ);
        boolean topBorder = differentTerritory(current, top);
        boolean rightBorder = differentTerritory(current, right);
        boolean bottomBorder = differentTerritory(current, bottom);
        boolean leftBorder = differentTerritory(current, left);
        int topAlpha = sideAlpha(current, top, style);
        int rightAlpha = sideAlpha(current, right, style);
        int bottomAlpha = sideAlpha(current, bottom, style);
        int leftAlpha = sideAlpha(current, left, style);
        int thickness = style.borderThickness();
        RgbColor baseColor = state.baseColor(current);
        RgbColor occupationColor = state.occupationColor(current, chunkX, chunkZ);
        int stripeColor = occupationColor == null
                ? 0
                : xaeroColor(occupationColor, style.occupationStripeAlpha());
        int period = style.occupationStripePeriod();
        int width = style.occupationStripeWidth();
        int chunkBlockX = chunkX << 4;
        int chunkBlockZ = chunkZ << 4;

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                int alpha = style.fillAlpha();
                boolean borderApplied = false;
                if (topBorder && localZ < thickness) {
                    alpha = topAlpha;
                    borderApplied = true;
                }
                if (rightBorder && localX >= 16 - thickness) {
                    alpha = borderApplied ? Math.max(alpha, rightAlpha) : rightAlpha;
                    borderApplied = true;
                }
                if (bottomBorder && localZ >= 16 - thickness) {
                    alpha = borderApplied ? Math.max(alpha, bottomAlpha) : bottomAlpha;
                    borderApplied = true;
                }
                if (leftBorder && localX < thickness) {
                    alpha = borderApplied ? Math.max(alpha, leftAlpha) : leftAlpha;
                    borderApplied = true;
                }

                int color = xaeroColor(
                        borderApplied ? style.borderColor() : baseColor,
                        alpha
                );
                if (!borderApplied
                        && occupationColor != null
                        && Math.floorMod(
                        chunkBlockX + localX + chunkBlockZ + localZ,
                        period
                ) < width) {
                    color = compositeXaero(color, stripeColor);
                }
                output[(localZ << 4) | localX] = color;
            }
        }
        return output;
    }

    static int[] occupationStripeColors(
            XaeroOverlayRuntime.State state,
            int chunkX,
            int chunkZ,
            int[] output
    ) {
        TerritoryView current = state.territoryAtChunk(chunkX, chunkZ);
        RgbColor occupationColor = current == null
                ? null
                : state.occupationColor(current, chunkX, chunkZ);
        if (occupationColor == null) {
            return null;
        }

        for (int index = 0; index < output.length; index++) {
            output[index] = 0;
        }

        XaeroOverlayRuntime.OverlayStyle style = state.style();
        int stripeColor = xaeroColor(occupationColor, style.occupationStripeAlpha());
        int period = style.occupationStripePeriod();
        int width = style.occupationStripeWidth();
        int borderThickness = style.borderThickness();
        boolean topBorder = differentTerritory(
                current,
                state.territoryAtChunk(chunkX, chunkZ - 1)
        );
        boolean rightBorder = differentTerritory(
                current,
                state.territoryAtChunk(chunkX + 1, chunkZ)
        );
        boolean bottomBorder = differentTerritory(
                current,
                state.territoryAtChunk(chunkX, chunkZ + 1)
        );
        boolean leftBorder = differentTerritory(
                current,
                state.territoryAtChunk(chunkX - 1, chunkZ)
        );
        int chunkBlockX = chunkX << 4;
        int chunkBlockZ = chunkZ << 4;

        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                if (isTerritoryBorderPixel(
                        localX,
                        localZ,
                        topBorder,
                        rightBorder,
                        bottomBorder,
                        leftBorder,
                        borderThickness
                )) {
                    continue;
                }
                int globalX = chunkBlockX + localX;
                int globalZ = chunkBlockZ + localZ;
                if (Math.floorMod(globalX + globalZ, period) < width) {
                    output[(localZ << 4) | localX] = stripeColor;
                }
            }
        }
        return output;
    }

    static Text tooltip(TerritoryView territory) {
        if (territory == null) {
            return null;
        }

        StringBuilder text = new StringBuilder("Territory ")
                .append(territory.territory().id())
                .append(" | ")
                .append(territory.ownerTownName());
        if (territory.ownerNation() != null) {
            text.append(" (").append(territory.ownerNationName()).append(')');
        }
        if (territory.occupied()) {
            text.append(" | Occupied by ").append(territory.occupierTownName());
            if (territory.occupierNation() != null) {
                text.append(" (").append(territory.occupierNationName()).append(')');
            }
        }
        return Text.literal(text.toString());
    }

    private static int sideAlpha(
            TerritoryView current,
            TerritoryView neighbor,
            XaeroOverlayRuntime.OverlayStyle style
    ) {
        if (!differentTerritory(current, neighbor)) {
            return style.fillAlpha();
        }
        int borderAlpha = sameEffectiveOwner(current, neighbor)
                ? style.internalBorderAlpha()
                : style.strongBorderAlpha();
        if (!current.townCore()) {
            return borderAlpha;
        }

        // Town-core outlines are 1.5x as opaque as ordinary territory borders.
        return Math.min(255, (borderAlpha * 3 + 1) / 2);
    }

    private static boolean differentTerritory(TerritoryView current, TerritoryView neighbor) {
        return neighbor == null
                || current.territory().id() != neighbor.territory().id();
    }

    private static boolean sameEffectiveOwner(TerritoryView first, TerritoryView second) {
        if (second == null) {
            return false;
        }

        NationRecord firstNation = first.ownerNation();
        NationRecord secondNation = second.ownerNation();
        if (firstNation != null || secondNation != null) {
            return firstNation != null
                    && secondNation != null
                    && firstNation.name().equalsIgnoreCase(secondNation.name());
        }

        TownRecord firstTown = first.owner();
        TownRecord secondTown = second.owner();
        return firstTown != null
                && secondTown != null
                && firstTown.name().equalsIgnoreCase(secondTown.name());
    }

    private static boolean isTerritoryBorderPixel(
            int localX,
            int localZ,
            boolean top,
            boolean right,
            boolean bottom,
            boolean left,
            int thickness
    ) {
        return localZ < thickness && top
                || localX >= 16 - thickness && right
                || localZ >= 16 - thickness && bottom
                || localX < thickness && left;
    }

    /**
     * Xaero highlight pixels use BBGGRRAA, with alpha in the low byte.
     */
    private static int xaeroColor(RgbColor color, int alpha) {
        int rgb = color == null ? RgbColor.NEUTRAL.rgb() : color.rgb();
        return (rgb & 0xFF) << 24
                | ((rgb >>> 8) & 0xFF) << 16
                | ((rgb >>> 16) & 0xFF) << 8
                | alpha;
    }

    /**
     * Alpha-composites {@code over} onto {@code under}. Both colors use
     * Xaero's BBGGRRAA layout.
     */
    private static int compositeXaero(int under, int over) {
        int underAlpha = under & 0xFF;
        int overAlpha = over & 0xFF;
        int inverseOver = 255 - overAlpha;
        int outAlpha = overAlpha + (underAlpha * inverseOver + 127) / 255;
        if (outAlpha == 0) {
            return 0;
        }
        int denominator = outAlpha * 255;
        int red = compositeChannel(
                under >>> 8 & 0xFF,
                underAlpha,
                over >>> 8 & 0xFF,
                overAlpha,
                inverseOver,
                denominator
        );
        int green = compositeChannel(
                under >>> 16 & 0xFF,
                underAlpha,
                over >>> 16 & 0xFF,
                overAlpha,
                inverseOver,
                denominator
        );
        int blue = compositeChannel(
                under >>> 24 & 0xFF,
                underAlpha,
                over >>> 24 & 0xFF,
                overAlpha,
                inverseOver,
                denominator
        );
        return blue << 24 | green << 16 | red << 8 | outAlpha;
    }

    private static int compositeChannel(
            int under,
            int underAlpha,
            int over,
            int overAlpha,
            int inverseOver,
            int denominator
    ) {
        int numerator = over * overAlpha * 255
                + under * underAlpha * inverseOver;
        return (numerator + denominator / 2) / denominator;
    }
}
