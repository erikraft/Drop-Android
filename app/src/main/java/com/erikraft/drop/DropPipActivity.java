package com.erikraft.drop;

import android.app.PictureInPictureParams;
import android.content.SharedPreferences;
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
 * The existing PiP behavior lived only in MainActivity. Keeping the lifecycle
 * here lets every real app screen use the same persisted preference without
 * duplicating the enter/exit logic.
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
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    private void updatePictureInPictureParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return;
        }

        final PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(isPictureInPictureEnabled());
        }

        setPictureInPictureParams(builder.build());
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
            enterPictureInPictureMode(new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .build());
        }
    }

    @Override
    protected void onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(pipPreferenceListener);
        super.onDestroy();
    }
}
