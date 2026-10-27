package io.github.virresh.matvt.helper;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes the current accessibility tree to a file for debugging Netflix's
 * (often sparse) node hierarchy. No private data is uploaded anywhere.
 */
public final class UiTreeDumper {

    private static final String TAG = "MATVT_UI_DUMP";
    private static final int MAX_NODES = 2500;
    private static final int MAX_DEPTH = 60;

    private UiTreeDumper() {
    }

    public static String dump(AccessibilityService service) {
        if (service == null) {
            return null;
        }

        StringBuilder out = new StringBuilder();
        int[] count = new int[]{0};

        List<AccessibilityWindowInfo> windows = service.getWindows();
        if (windows != null) {
            for (AccessibilityWindowInfo window : windows) {
                if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue;
                }
                out.append("WINDOW type=application active=")
                        .append(window.isActive())
                        .append(" focused=")
                        .append(window.isFocused())
                        .append('\n');
                appendNode(window.getRoot(), out, count, 1);
            }
        }

        AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root != null) {
            out.append("ACTIVE ROOT\n");
            appendNode(root, out, count, 1);
        }

        String text = out.toString();
        Log.i(TAG, text);

        File file = new File(service.getFilesDir(), "ui-tree.txt");
        try (FileOutputStream stream = new FileOutputStream(file);
             OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
            writer.write(text);
            return file.getAbsolutePath();
        } catch (IOException e) {
            Log.e(TAG, "Could not write UI tree", e);
            return null;
        }
    }

    private static void appendNode(AccessibilityNodeInfo node, StringBuilder out,
                                   int[] count, int depth) {
        if (node == null || depth > MAX_DEPTH || count[0] >= MAX_NODES) {
            return;
        }
        count[0]++;

        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);

        for (int i = 1; i < depth; i++) {
            out.append("  ");
        }
        out.append("node #").append(count[0])
                .append(" pkg=").append(value(node.getPackageName()))
                .append(" class=").append(value(node.getClassName()))
                .append(" id=").append(value(node.getViewIdResourceName()))
                .append(" text=").append(value(node.getText()))
                .append(" desc=").append(value(node.getContentDescription()))
                .append(" bounds=").append(bounds.toShortString())
                .append(" clickable=").append(node.isClickable())
                .append(" longClickable=").append(node.isLongClickable())
                .append(" focusable=").append(node.isFocusable())
                .append(" focused=").append(node.isFocused())
                .append(" scrollable=").append(node.isScrollable())
                .append(" visible=").append(node.isVisibleToUser())
                .append(" actions=").append(node.getActionList())
                .append('\n');

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount && count[0] < MAX_NODES; i++) {
            appendNode(node.getChild(i), out, count, depth + 1);
        }
    }

    private static String value(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            return "\"\"";
        }
        return '"' + text.toString().replace('\n', ' ') + '"';
    }
}
