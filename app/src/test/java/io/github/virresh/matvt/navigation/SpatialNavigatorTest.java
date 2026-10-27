package io.github.virresh.matvt.navigation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SpatialNavigatorTest {

    private static SpatialNavigator.Item item(int left, int top, int right, int bottom, String label) {
        return new SpatialNavigator.Item(left, top, right, bottom, label);
    }

    @Test
    public void rightMovesToOverlappingItemInSameRow() {
        SpatialNavigator.Item current = item(0, 0, 100, 100, "current");
        SpatialNavigator.Item sameRow = item(120, 0, 220, 100, "same row");
        SpatialNavigator.Item nextRow = item(120, 120, 220, 220, "next row");
        List<SpatialNavigator.Item> items = Arrays.asList(current, sameRow, nextRow);

        SpatialNavigator.Item result = SpatialNavigator.findNext(
                items, current, SpatialNavigator.Direction.RIGHT);

        assertEquals("same row", result.label);
    }

    @Test
    public void downRequiresHorizontalOverlap() {
        SpatialNavigator.Item current = item(0, 0, 100, 100, "current");
        SpatialNavigator.Item below = item(0, 120, 100, 220, "below");
        SpatialNavigator.Item diagonal = item(200, 120, 300, 220, "diagonal");
        List<SpatialNavigator.Item> items = Arrays.asList(current, below, diagonal);

        SpatialNavigator.Item result = SpatialNavigator.findNext(
                items, current, SpatialNavigator.Direction.DOWN);

        assertEquals("below", result.label);
    }

    @Test
    public void leftAtRowEndDoesNotJumpToAnotherRow() {
        SpatialNavigator.Item current = item(200, 0, 300, 100, "current");
        SpatialNavigator.Item left = item(0, 0, 100, 100, "left");
        SpatialNavigator.Item lowerRow = item(0, 120, 100, 220, "lower row");
        List<SpatialNavigator.Item> items = Arrays.asList(current, left, lowerRow);

        SpatialNavigator.Item result = SpatialNavigator.findNext(
                items, current, SpatialNavigator.Direction.LEFT);

        assertEquals("left", result.label);
    }

    @Test
    public void verticalFallbackAllowsSmallDiagonalOffset() {
        SpatialNavigator.Item current = item(0, 0, 100, 100, "current");
        SpatialNavigator.Item diagonal = item(120, 120, 220, 220, "diagonal");
        List<SpatialNavigator.Item> items = Arrays.asList(current, diagonal);

        SpatialNavigator.Item result = SpatialNavigator.findNext(
                items, current, SpatialNavigator.Direction.DOWN);

        assertEquals("diagonal", result.label);
    }

    @Test
    public void horizontalDirectionDoesNotFallBackDiagonally() {
        SpatialNavigator.Item current = item(0, 0, 100, 100, "current");
        SpatialNavigator.Item diagonal = item(120, 120, 220, 220, "diagonal");
        List<SpatialNavigator.Item> items = Arrays.asList(current, diagonal);

        SpatialNavigator.Item result = SpatialNavigator.findNext(
                items, current, SpatialNavigator.Direction.RIGHT);

        assertNull(result);
    }

    @Test
    public void nearestToCenterFindsCentralItem() {
        List<SpatialNavigator.Item> items = Arrays.asList(
                item(0, 0, 100, 100, "top left"),
                item(450, 250, 550, 350, "center"),
                item(900, 500, 1000, 600, "bottom right"));

        SpatialNavigator.Item result = SpatialNavigator.findNearestToCenter(items, 1000, 600);

        assertEquals("center", result.label);
    }
}
