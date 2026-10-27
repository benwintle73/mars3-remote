package io.github.virresh.matvt.navigation;

import java.util.List;

/**
 * Pure geometry helper for moving between selectable rectangles with a D-pad.
 * Kept free of Android classes so it can be unit tested on the JVM.
 */
public final class SpatialNavigator {

    private SpatialNavigator() {
    }

    public enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    public static final class Item {
        public final int left;
        public final int top;
        public final int right;
        public final int bottom;
        public final String label;

        public Item(int left, int top, int right, int bottom, String label) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.label = label == null ? "" : label;
        }

        public int centerX() {
            return (left + right) / 2;
        }

        public int centerY() {
            return (top + bottom) / 2;
        }

        public int width() {
            return Math.max(0, right - left);
        }

        public int height() {
            return Math.max(0, bottom - top);
        }

        public int area() {
            return width() * height();
        }

        public boolean contains(int x, int y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }

        public boolean sameBounds(Item other) {
            return other != null
                    && left == other.left
                    && top == other.top
                    && right == other.right
                    && bottom == other.bottom;
        }

        @Override
        public String toString() {
            return "Item{" + left + "," + top + "," + right + "," + bottom + " '" + label + "'}";
        }
    }

    public static Item findNext(List<Item> items, Item current, Direction direction) {
        if (items == null || items.isEmpty() || current == null || direction == null) {
            return null;
        }

        Item strict = findBest(items, current, direction, true);
        if (strict != null) {
            return strict;
        }

        // Diagonal fallback is only safe for vertical movement. Horizontally it
        // would jump into the next row when the user is at the end of a row.
        if (direction == Direction.UP || direction == Direction.DOWN) {
            return findBest(items, current, direction, false);
        }
        return null;
    }

    private static Item findBest(List<Item> items, Item current, Direction direction, boolean requireOverlap) {
        Item best = null;
        double bestScore = Double.MAX_VALUE;

        for (Item candidate : items) {
            if (candidate == null || candidate.sameBounds(current)) {
                continue;
            }

            int dx = candidate.centerX() - current.centerX();
            int dy = candidate.centerY() - current.centerY();

            int primary;
            int perpendicular;
            boolean overlaps;

            switch (direction) {
                case RIGHT:
                    if (dx <= 0) continue;
                    primary = dx;
                    perpendicular = Math.abs(dy);
                    overlaps = overlapLength(current.top, current.bottom, candidate.top, candidate.bottom) > 0;
                    break;
                case LEFT:
                    if (dx >= 0) continue;
                    primary = -dx;
                    perpendicular = Math.abs(dy);
                    overlaps = overlapLength(current.top, current.bottom, candidate.top, candidate.bottom) > 0;
                    break;
                case DOWN:
                    if (dy <= 0) continue;
                    primary = dy;
                    perpendicular = Math.abs(dx);
                    overlaps = overlapLength(current.left, current.right, candidate.left, candidate.right) > 0;
                    break;
                case UP:
                    if (dy >= 0) continue;
                    primary = -dy;
                    perpendicular = Math.abs(dx);
                    overlaps = overlapLength(current.left, current.right, candidate.left, candidate.right) > 0;
                    break;
                default:
                    continue;
            }

            if (requireOverlap && !overlaps) {
                continue;
            }

            if (!requireOverlap) {
                // Stay within roughly 50 degrees of the requested direction.
                if (perpendicular > primary * 1.2 + 24) {
                    continue;
                }
            }

            double score = primary + (overlaps ? 0.35 : 1.2) * perpendicular;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best;
    }

    public static Item findNearestToPoint(List<Item> items, int x, int y) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        Item best = null;
        long bestDistance = Long.MAX_VALUE;
        for (Item item : items) {
            if (item == null) continue;
            long dx = item.centerX() - x;
            long dy = item.centerY() - y;
            long distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = item;
            }
        }
        return best;
    }

    public static Item findNearestToCenter(List<Item> items, int width, int height) {
        return findNearestToPoint(items, width / 2, height / 2);
    }

    private static int overlapLength(int startA, int endA, int startB, int endB) {
        return Math.max(0, Math.min(endA, endB) - Math.max(startA, startB));
    }
}
