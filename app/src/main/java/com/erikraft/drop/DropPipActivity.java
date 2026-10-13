package com.erikraft.drop;

import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

/**
 * Shared PiP lifecycle for ErikrafT Drop activities.
 *
 * PiP is opt-in per Activity in AndroidManifest.xml. Check the actual Activity
 * declaration before calling the framework APIs: older or device-specific
 * merged manifests may omit supportsPictureInPicture even when the device
 * itself supports PiP.
 */
public abstract class DropPipActivity extends AppCompatActivity {
    private final SharedPreferences.OnSharedPreferenceChangeListener pipPreferenceListener =
            (sharedPreferences, key) -> {
                if (getString(R.string.pref_picture_in_picture).equals(key)) {
                    updatePictureInPictureParams();
                }
            };

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(pipPreferenceListener);
        updatePictureInPictureParams();
    }

    static boolean isPictureInPictureEnabled(final SharedPreferences prefs, final String key) {
        return prefs.getBoolean(key, true);
    }

    private boolean isPictureInPictureEnabled() {
        return isPictureInPictureEnabled(
                PreferenceManager.getDefaultSharedPreferences(this),
                getString(R.string.pref_picture_in_picture));
    }

    private boolean isPictureInPictureAvailable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return false;
        }

        try {
            final ActivityInfo activityInfo = getPackageManager().getActivityInfo(
                    new ComponentName(this, getClass()), 0);
            return activityInfo.supportsPictureInPicture;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // Fail closed rather than crashing an Activity that is not PiP-enabled.
            return false;
        }
    }

    private void updatePictureInPictureParams() {
        if (!isPictureInPictureAvailable()) {
            return;
        }

        final PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(isPictureInPictureEnabled());
        }

        try {
            setPictureInPictureParams(builder.build());
        } catch (IllegalStateException ignored) {
            // Some ROMs can report PiP support inconsistently. PiP is optional;
            // never let a parameter update crash the screen.
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();

        // Android 8–11 do not support setAutoEnterEnabled().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                && isPictureInPictureAvailable()
                && isPictureInPictureEnabled()
                && !isFinishing()
                && !isInPictureInPictureMode()) {
            try {
                enterPictureInPictureMode(new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9))
                        .build());
            } catch (IllegalStateException ignored) {
                // Keep navigation safe on ROMs with incomplete PiP implementations.
            }
        }
    }

    @Override
    protected void onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(pipPreferenceListener);
        super.onDestroy();
    }
}
