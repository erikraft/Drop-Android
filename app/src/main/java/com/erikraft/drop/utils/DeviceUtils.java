package com.erikraft.drop.utils;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;

/**
 * Helper methods for adapting the experience depending on the device form factor.
 */
public final class DeviceUtils {

    private static final String FEATURE_LEANBACK_ONLY = "android.software.leanback_only";
    private static final String FEATURE_VR_MODE = "android.software.vr.mode";
    private static final String FEATURE_VR_HIGH_PERFORMANCE = "android.hardware.vr.high_performance";
    private static final String FEATURE_XR_TYPE = "android.hardware.type.xr";

    private DeviceUtils() {
        // Utility class
    }

    public static boolean isTvOrXr(final Context context) {
        return isTelevision(context) || isExtendedReality(context);
    }

    public static boolean isTelevision(final Context context) {
        final PackageManager packageManager = context.getPackageManager();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                || packageManager.hasSystemFeature(FEATURE_LEANBACK_ONLY)) {
            return true;
        }

        final UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    public static boolean isExtendedReality(final Context context) {
        final PackageManager packageManager = context.getPackageManager();
        if (packageManager.hasSystemFeature(FEATURE_XR_TYPE)
                || packageManager.hasSystemFeature(FEATURE_VR_MODE)
                || packageManager.hasSystemFeature(FEATURE_VR_HIGH_PERFORMANCE)) {
            return true;
        }

        final UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        if (uiModeManager == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_VR_HEADSET;
        }
        return false;
    }
}
