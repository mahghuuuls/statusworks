package com.mahghuuuls.statusworks.client;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Where the HUD block sits and in which order its cells fill it: the anchor (nine screen positions), the offset from
 * it, the direction entries follow in a row, the direction rows are added, and where each group of a stack (buffs,
 * then debuffs) sits with the gap between them. Plain numbers only, so it is
 * unit-tested without the game; {@link HudRenderer} uses it for the grid and the named list alike, and
 * {@link ClientSettings} reads the setting texts through it.
 */
final class HudLayout {

    static final Anchor DEFAULT_ANCHOR = Anchor.TOP_RIGHT;

    private HudLayout() {
    }

    /** A screen position: the top, middle, or bottom, each at the left, center, or right. */
    enum Anchor {
        TOP_LEFT(-1, -1), TOP_CENTER(0, -1), TOP_RIGHT(1, -1),
        MIDDLE_LEFT(-1, 0), CENTER(0, 0), MIDDLE_RIGHT(1, 0),
        BOTTOM_LEFT(-1, 1), BOTTOM_CENTER(0, 1), BOTTOM_RIGHT(1, 1);

        /** -1 at the left, 0 at the center, 1 at the right. */
        final int horizontal;
        /** -1 at the top, 0 in the middle, 1 at the bottom. */
        final int vertical;

        Anchor(int horizontal, int vertical) {
            this.horizontal = horizontal;
            this.vertical = vertical;
        }
    }

    /** The order of the entries inside a row. {@code AUTO} goes away from the anchor's side. */
    enum EntryDirection {
        AUTO, LEFT_TO_RIGHT, RIGHT_TO_LEFT
    }

    /** Where the next row goes. {@code AUTO} goes away from the anchor's edge. */
    enum RowDirection {
        AUTO, DOWN, UP
    }

    /** A value's setting text: its name in lowercase. */
    static String text(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /** Every setting text of an enum, in order. */
    static <E extends Enum<E>> String[] texts(Class<E> type) {
        E[] values = type.getEnumConstants();
        String[] texts = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            texts[i] = text(values[i]);
        }
        return texts;
    }

    /** The value whose setting text is exactly {@code text}, or null. */
    @Nullable
    static <E extends Enum<E>> E fromText(Class<E> type, @Nullable String text) {
        for (E e : type.getEnumConstants()) {
            if (text(e).equals(text)) {
                return e;
            }
        }
        return null;
    }

    /**
     * The block's left edge in an area {@code area} wide: the offset in from the anchored edge (never outside it),
     * or, at a center anchor, the centered position moved right by a positive offset and left by a negative one.
     */
    static int originX(Anchor anchor, double area, double offset, int width) {
        return (int) origin(anchor.horizontal, area, offset, width);
    }

    /** The block's top edge, in the same way as {@link #originX} on the vertical axis. */
    static int originY(Anchor anchor, double area, double offset, int height) {
        return (int) origin(anchor.vertical, area, offset, height);
    }

    private static double origin(int side, double area, double offset, int size) {
        if (side < 0) {
            return Math.max(0, offset);
        }
        if (side > 0) {
            return area - Math.max(0, offset) - size;
        }
        return (area - size) / 2 + offset;
    }

    /** Whether a row is filled from its right end: right-side anchors by default, or as the player set it. */
    static boolean rightToLeft(Anchor anchor, EntryDirection direction) {
        return direction == EntryDirection.AUTO ? anchor.horizontal > 0 : direction == EntryDirection.RIGHT_TO_LEFT;
    }

    /** Whether rows are added upwards: bottom anchors by default, or as the player set it. */
    static boolean upward(Anchor anchor, RowDirection direction) {
        return direction == RowDirection.AUTO ? anchor.vertical > 0 : direction == RowDirection.UP;
    }

    /** The grid's column count: a row's worth of cells, or fewer when there are fewer cells. */
    static int gridColumns(int cells, int perRow) {
        return Math.min(Math.max(1, perRow), cells);
    }

    /** The grid's row count. */
    static int gridRows(int cells, int perRow) {
        int each = Math.max(1, perRow);
        return (cells + each - 1) / each;
    }

    /**
     * The left edge of grid cell {@code i} in a block starting at {@code x0} with {@code columns} columns of
     * {@code perRow} cells each: a row filled from the right starts at the block's right edge, even when short.
     */
    static int cellX(int x0, int i, int perRow, int columns, int cellWidth, boolean rightToLeft) {
        int col = i % perRow;
        return x0 + (rightToLeft ? columns - 1 - col : col) * cellWidth;
    }

    /** The top edge of grid cell {@code i} in a block starting at {@code y0} with {@code rows} rows. */
    static int cellY(int y0, int i, int perRow, int rows, int cellHeight, boolean upward) {
        int row = i / perRow;
        return y0 + (upward ? rows - 1 - row : row) * cellHeight;
    }

    /**
     * The top edge of row {@code i} of {@code count} in the named list, starting at {@code y0}. Upwards, the first
     * row is the lowest and the {@code +N} line, when there is one, sits above the rows.
     */
    static int listRowY(int y0, int i, int count, int rowHeight, int moreHeight, boolean upward) {
        return upward ? y0 + moreHeight + (count - 1 - i) * rowHeight : y0 + i * rowHeight;
    }

    /** The top edge of the named list's {@code +N} line: under the rows, or above them upwards. */
    static int listMoreY(int y0, int count, int rowHeight, boolean upward) {
        return upward ? y0 : y0 + count * rowHeight;
    }

    /** Space between stacked groups. */
    static final int GROUP_GAP = 6;

    /**
     * The left and top edge of each group of a stack in an area of this size: {@code x[i]} and {@code y[i]}. The
     * stack as a whole is placed like one block of its total height, and each group is placed on its own width, so
     * a narrower group still sits at the anchor's side (or centered). The first group is nearest the anchor's edge:
     * at the top for a top or middle anchor, at the bottom for a bottom anchor, the next ones after it with
     * {@link #GROUP_GAP} between them.
     */
    static int[][] groupOrigins(Anchor anchor, double areaWidth, double areaHeight, double offsetX, double offsetY,
                                int[] widths, int[] heights) {
        int total = Math.max(0, heights.length - 1) * GROUP_GAP;
        for (int h : heights) {
            total += h;
        }
        int[] xs = new int[widths.length];
        int[] ys = new int[heights.length];
        int y = originY(anchor, areaHeight, offsetY, total);
        for (int k = 0; k < heights.length; k++) {
            int i = anchor.vertical > 0 ? heights.length - 1 - k : k;
            xs[i] = originX(anchor, areaWidth, offsetX, widths[i]);
            ys[i] = y;
            y += heights[i] + GROUP_GAP;
        }
        return new int[][]{xs, ys};
    }

    /** Whether the named list's rows end at the block's right edge, the icon outermost: right-side anchors. */
    static boolean listFromRight(Anchor anchor) {
        return anchor.horizontal > 0;
    }
}
