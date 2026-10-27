package io.github.virresh.matvt.helper;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import java.util.List;

import io.github.virresh.matvt.services.MouseEventService;

public class AccessibilityUtils {
    private static final String FIRE_TV_SETTINGS_SERVICE_ID = "com.wolf.firetvsettings/.main.services.HomeService";
    private static final String BUTTON_MAPPER_SERVICE_ID = "flar2.homebutton/.a.i";

    public static boolean isAccessibilityDisabled(Context ctx) {
        ComponentName component = new ComponentName(ctx, MouseEventService.class);
        String fullId = component.flattenToString();
        String shortId = component.flattenToShortString();
        String serviceClass = MouseEventService.class.getName();
        String relativeId = ctx.getPackageName() + "/.services.MouseEventService";
        String namespaceId = "io.github.virresh.matvt/" + serviceClass;
        String namespaceRelativeId = "io.github.virresh.matvt/.services.MouseEventService";
        return !isAccServiceInstalled(fullId, ctx)
                && !isAccServiceInstalled(shortId, ctx)
                && !isAccServiceInstalled(ctx.getPackageName() + "/" + serviceClass, ctx)
                && !isAccServiceInstalled(relativeId, ctx)
                && !isAccServiceInstalled(namespaceId, ctx)
                && !isAccServiceInstalled(namespaceRelativeId, ctx);
    }

    public static boolean isAnotherServiceInstalled(Context ctx) {
        return isAccServiceInstalled(FIRE_TV_SETTINGS_SERVICE_ID, ctx) || isAccServiceInstalled(BUTTON_MAPPER_SERVICE_ID, ctx);
    }

    public static boolean isAccServiceInstalled(String serviceId, Context ctx) {
        AccessibilityManager am = (AccessibilityManager) ctx.getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> runningServices = null;
        if (am != null) {
            runningServices = am.getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK);
        }
        if (runningServices != null) {
            for (AccessibilityServiceInfo service : runningServices) {
                if (serviceId.equals(service.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isOverlayDisabled(Context ctx) {
        return !Settings.canDrawOverlays(ctx);
    }
}