package com.mahghuuuls.statusworks.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudLayoutTest {

    private static final HudLayout.EntryDirection AUTO = HudLayout.EntryDirection.AUTO;

    /** The x of cell {@code i} of {@code cells} in a 10-per-row grid of 18-wide cells, 400 wide, offset 4. */
    private static int cellX(HudLayout.Anchor anchor, HudLayout.EntryDirection direction, int i, int cells) {
        int columns = HudLayout.gridColumns(cells, 10);
        int x0 = HudLayout.originX(anchor, 400, 4, columns * 18);
        return HudLayout.cellX(x0, i, 10, columns, 18, HudLayout.rightToLeft(anchor, direction));
    }

    @Test
    void theShippedDefaultsAreTheTopRightWithThePotionEffectsOnlyThere() {
        assertEquals(HudLayout.Anchor.TOP_RIGHT, HudLayout.DEFAULT_ANCHOR);
        assertTrue(ClientSettings.POTION_EFFECTS_DEFAULT);
        assertTrue(ClientSettings.HIDE_INVENTORY_EFFECTS_DEFAULT);
        assertTrue(ClientSettings.SEPARATE_DEBUFFS_DEFAULT);
        assertTrue(ClientSettings.DEBUFF_BORDER_DEFAULT);
        assertTrue(ClientSettings.POTION_FRAME_DEFAULT);
    }

    @Test
    void byDefaultTheFirstEntryIsAtTheTopRightEdgeAndTheSecondToItsLeft() {
        HudLayout.Anchor topRight = HudLayout.DEFAULT_ANCHOR;
        assertEquals(400 - 4 - 18, cellX(topRight, AUTO, 0, 2));
        assertEquals(400 - 4 - 36, cellX(topRight, AUTO, 1, 2));
        assertEquals(4, HudLayout.originY(topRight, 300, 4, 30));
        assertFalse(HudLayout.upward(topRight, HudLayout.RowDirection.AUTO));
    }

    @Test
    void leftToRightAtTheTopRightPutsTheLastEntryAtTheEdge() {
        HudLayout.Anchor topRight = HudLayout.Anchor.TOP_RIGHT;
        assertEquals(400 - 4 - 18, cellX(topRight, HudLayout.EntryDirection.LEFT_TO_RIGHT, 2, 3));
        assertEquals(400 - 4 - 54, cellX(topRight, HudLayout.EntryDirection.LEFT_TO_RIGHT, 0, 3));
    }

    @Test
    void aTopCenterRowIsCenteredWithTheFirstEntryOnTheLeft() {
        HudLayout.Anchor topCenter = HudLayout.Anchor.TOP_CENTER;
        // 3 cells are 54 wide: (400 - 54) / 2 = 173.
        assertEquals(173, HudLayout.originX(topCenter, 400, 0, 54), "offset 0 is exactly centered");
        assertEquals(177, cellX(topCenter, AUTO, 0, 3), "a positive offset moves it right");
        assertEquals(177 + 36, cellX(topCenter, AUTO, 2, 3));
        assertEquals(173 - 10, HudLayout.originX(topCenter, 400, -10, 54), "a negative offset moves it left");
    }

    @Test
    void everyAnchorPlacesTheBlockOnBothAxes() {
        // A 50 by 30 block on a 400 by 300 screen, offset 4 on both axes.
        int left = 4;
        int centerX = 175 + 4;
        int right = 400 - 4 - 50;
        int top = 4;
        int middle = 135 + 4;
        int bottom = 300 - 4 - 30;
        Object[][] table = {
                {HudLayout.Anchor.TOP_LEFT, left, top}, {HudLayout.Anchor.TOP_CENTER, centerX, top},
                {HudLayout.Anchor.TOP_RIGHT, right, top}, {HudLayout.Anchor.MIDDLE_LEFT, left, middle},
                {HudLayout.Anchor.CENTER, centerX, middle}, {HudLayout.Anchor.MIDDLE_RIGHT, right, middle},
                {HudLayout.Anchor.BOTTOM_LEFT, left, bottom}, {HudLayout.Anchor.BOTTOM_CENTER, centerX, bottom},
                {HudLayout.Anchor.BOTTOM_RIGHT, right, bottom},
        };
        assertEquals(HudLayout.Anchor.values().length, table.length);
        for (Object[] row : table) {
            HudLayout.Anchor anchor = (HudLayout.Anchor) row[0];
            assertEquals(row[1], HudLayout.originX(anchor, 400, 4, 50), anchor + " x");
            assertEquals(row[2], HudLayout.originY(anchor, 300, 4, 30), anchor + " y");
        }
    }

    @Test
    void aNegativeOffsetNeverMovesTheBlockOutsideAnEdge() {
        assertEquals(0, HudLayout.originX(HudLayout.Anchor.TOP_LEFT, 400, -20, 50));
        assertEquals(400 - 50, HudLayout.originX(HudLayout.Anchor.TOP_RIGHT, 400, -20, 50));
        assertEquals(300 - 30, HudLayout.originY(HudLayout.Anchor.BOTTOM_CENTER, 300, -20, 30));
    }

    @Test
    void autoDirectionsGoAwayFromTheAnchorAndTheSettingsOverrideThem() {
        for (HudLayout.Anchor anchor : HudLayout.Anchor.values()) {
            String name = HudLayout.text(anchor);
            assertEquals(name.endsWith("right"), HudLayout.rightToLeft(anchor, AUTO), name + " entry direction");
            assertEquals(name.startsWith("bottom"), HudLayout.upward(anchor, HudLayout.RowDirection.AUTO),
                    name + " row direction");
            assertEquals(name.endsWith("right"), HudLayout.listFromRight(anchor), name + " list side");
        }
        assertTrue(HudLayout.rightToLeft(HudLayout.Anchor.TOP_LEFT, HudLayout.EntryDirection.RIGHT_TO_LEFT));
        assertFalse(HudLayout.rightToLeft(HudLayout.Anchor.TOP_RIGHT, HudLayout.EntryDirection.LEFT_TO_RIGHT));
        assertTrue(HudLayout.upward(HudLayout.Anchor.TOP_RIGHT, HudLayout.RowDirection.UP));
        assertFalse(HudLayout.upward(HudLayout.Anchor.BOTTOM_RIGHT, HudLayout.RowDirection.DOWN));
    }

    @Test
    void aShortRowFilledFromTheRightStartsAtTheBlockEdge() {
        // 12 entries, 10 per row, 10 columns, from the right, in a block starting at 0 with 18-wide cells.
        assertEquals(9 * 18, HudLayout.cellX(0, 10, 10, 10, 18, true), "entry 10 opens the second row at the right");
        assertEquals(0, HudLayout.cellX(0, 10, 10, 10, 18, false));
        // 2 entries in a 10-per-row grid: the block has 2 columns, and the first is at its right end.
        assertEquals(18, HudLayout.cellX(0, 0, 10, 2, 18, true));
        assertEquals(0, HudLayout.cellY(0, 10, 10, 2, 30, true), "upwards, the second row is the top one");
        assertEquals(30, HudLayout.cellY(0, 10, 10, 2, 30, false));
        assertEquals(30, HudLayout.cellY(0, 0, 10, 2, 30, true), "upwards, the first row is the lowest");
    }

    @Test
    void theNamedListAddsRowsDownOrUpWithTheMoreLineOnTheOuterSide() {
        // 3 rows of 18 and a +N line of 10, from y 100.
        assertEquals(100, HudLayout.listRowY(100, 0, 3, 18, 10, false));
        assertEquals(136, HudLayout.listRowY(100, 2, 3, 18, 10, false));
        assertEquals(154, HudLayout.listMoreY(100, 3, 18, false), "downwards, +N is under the rows");
        assertEquals(100 + 10 + 36, HudLayout.listRowY(100, 0, 3, 18, 10, true), "upwards, the first is lowest");
        assertEquals(100 + 10, HudLayout.listRowY(100, 2, 3, 18, 10, true));
        assertEquals(100, HudLayout.listMoreY(100, 3, 18, true), "upwards, +N is above the rows");
        assertEquals(100 + 36, HudLayout.listRowY(100, 0, 3, 18, 0, true), "no +N line, no gap");
    }

    @Test
    void aShortGridHasOnlyAsManyColumnsAsCells() {
        assertEquals(3, HudLayout.gridColumns(3, 10), "3 entries centered as 3 columns, not 10");
        assertEquals(10, HudLayout.gridColumns(12, 10));
        assertEquals(1, HudLayout.gridRows(3, 10));
        assertEquals(2, HudLayout.gridRows(12, 10));
        assertEquals(3, HudLayout.gridRows(7, 3));
        assertEquals(1, HudLayout.gridColumns(5, 0), "a row of at least one");
    }

    @Test
    void settingTextsAreExactLowercase() {
        assertEquals(HudLayout.Anchor.BOTTOM_CENTER, HudLayout.fromText(HudLayout.Anchor.class, "bottom_center"));
        assertEquals(HudLayout.EntryDirection.RIGHT_TO_LEFT,
                HudLayout.fromText(HudLayout.EntryDirection.class, "right_to_left"));
        assertNull(HudLayout.fromText(HudLayout.Anchor.class, "Top_Right"));
        assertNull(HudLayout.fromText(HudLayout.Anchor.class, "middle_center"));
        assertNull(HudLayout.fromText(HudLayout.Anchor.class, null));
        assertArrayEquals(new String[]{"auto", "down", "up"}, HudLayout.texts(HudLayout.RowDirection.class));
        assertEquals("top_right", HudLayout.text(HudLayout.Anchor.TOP_RIGHT));
    }

    /** Buffs 60 wide and 40 high, debuffs 20 wide and 20 high, on a 400 by 300 screen, offset 4. */
    private static int[][] stack(HudLayout.Anchor anchor) {
        return HudLayout.groupOrigins(anchor, 400, 300, 4, 4, new int[]{60, 20}, new int[]{40, 20});
    }

    @Test
    void theDebuffGroupIsBelowTheBuffsAtTopAndMiddleAnchorsAndAboveThemAtBottomAnchors() {
        int gap = HudLayout.GROUP_GAP;
        // Top: buffs from the edge, debuffs after the gap.
        assertArrayEquals(new int[]{4, 4 + 40 + gap}, stack(HudLayout.Anchor.TOP_LEFT)[1]);
        // Middle: the whole stack (66 high) is centered, offset 4 down; buffs first.
        int middleTop = (300 - (40 + gap + 20)) / 2 + 4;
        assertArrayEquals(new int[]{middleTop, middleTop + 40 + gap}, stack(HudLayout.Anchor.MIDDLE_RIGHT)[1]);
        // Bottom: the buffs end at the edge, the debuffs above them after the gap.
        int bottom = 300 - 4;
        assertArrayEquals(new int[]{bottom - 40, bottom - 40 - gap - 20}, stack(HudLayout.Anchor.BOTTOM_CENTER)[1]);
        for (HudLayout.Anchor anchor : HudLayout.Anchor.values()) {
            int[] ys = stack(anchor)[1];
            assertEquals(anchor.vertical > 0, ys[1] < ys[0], anchor.name());
        }
    }

    @Test
    void eachGroupSitsAtTheAnchorsSideOnItsOwnWidth() {
        for (HudLayout.Anchor anchor : HudLayout.Anchor.values()) {
            int[] xs = stack(anchor)[0];
            if (anchor.horizontal < 0) {
                assertArrayEquals(new int[]{4, 4}, xs, anchor.name());
            } else if (anchor.horizontal > 0) {
                assertArrayEquals(new int[]{400 - 4 - 60, 400 - 4 - 20}, xs, anchor.name());
            } else {
                assertArrayEquals(new int[]{(400 - 60) / 2 + 4, (400 - 20) / 2 + 4}, xs, anchor.name());
            }
        }
    }

    @Test
    void aSingleGroupIsPlacedLikeOneBlock() {
        for (HudLayout.Anchor anchor : HudLayout.Anchor.values()) {
            int[][] at = HudLayout.groupOrigins(anchor, 400, 300, 4, 4, new int[]{60}, new int[]{40});
            assertEquals(HudLayout.originX(anchor, 400, 4, 60), at[0][0], anchor.name());
            assertEquals(HudLayout.originY(anchor, 300, 4, 40), at[1][0], anchor.name());
        }
    }
}
