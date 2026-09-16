package com.erikraft.drop;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.erikraft.drop.utils.LogUtils;


public class SnapdropApplication extends Application {

    private static SnapdropApplication instance;

    public SnapdropApplication() {
        instance = this;
    }

    public static Application getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        LogUtils.installUncaughtExceptionHandler();
        setAppTheme(getApplicationContext());
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (!(activity instanceof MainActivity)) return;

                MainActivity mainActivity = (MainActivity) activity;
                mainActivity.getOnBackPressedDispatcher().addCallback(mainActivity, new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        WebView webView = mainActivity.findViewById(R.id.webview);
                        if (webView != null && webView.canGoBack()) {
                            webView.goBack();
                            return;
                        }

                        setEnabled(false);
                        mainActivity.getOnBackPressedDispatcher().onBackPressed();
                        setEnabled(true);
                    }
                });
            }

            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
        super.onCreate();
    }

    public static void setAppTheme(final @NonNull Context context) {
        setAppTheme(getAppTheme(context));
        context.setTheme(R.style.AppTheme);
    }

    public static void setAppTheme(final DarkModeSetting setting) {
        AppCompatDelegate.setDefaultNightMode(setting.getModeId());
    }

    private static DarkModeSetting getAppTheme(final @NonNull Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return DarkModeSetting.valueOf(prefs.getString(context.getString(R.string.pref_theme_setting), DarkModeSetting.SYSTEM_DEFAULT.getPreferenceValue(context)));
    }

    private static boolean isDarkThemeActive(final @NonNull Context context, final DarkModeSetting setting) {
        if (setting == DarkModeSetting.SYSTEM_DEFAULT) {
            return isDarkThemeActive(context);
        } else {
            return setting == DarkModeSetting.DARK;
        }
    }

    private static boolean isDarkThemeActive(final @NonNull Context context) {
        final int uiMode = context.getResources().getConfiguration().uiMode;
        return (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static boolean isDarkTheme(final @NonNull Context context) {
        return isDarkThemeActive(context, getAppTheme(context));
    }
}
