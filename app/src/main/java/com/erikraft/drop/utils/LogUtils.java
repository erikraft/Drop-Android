package com.erikraft.drop.utils;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.erikraft.drop.BuildConfig;
import com.erikraft.drop.R;
import com.erikraft.drop.SnapdropApplication;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogUtils {
    private static String logcatLogs;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private LogUtils() { }

    public static String getLogs(final SharedPreferences prefs, final boolean refresh) {
        if (refresh) {
            logcatLogs = "--------- System Information" +
                    "\n- Device type: " + Build.MODEL + " (" + Build.PRODUCT + ", " + Build.BRAND + ')' +
                    "\n- Android version: " + Build.VERSION.RELEASE +
                    "\n- ErikrafT Drop app version: " + BuildConfig.VERSION_NAME +
                    "\n- Current time: " + sdf.format(new Date()) +
                    "\n\n" +
                    prefs.getString(SnapdropApplication.getInstance().getApplicationContext().getString(R.string.pref_last_crash), "") +
                    requestLogcatLogs();
        }
        return logcatLogs;
    }

    private static String requestLogcatLogs() {
        String logs = "Unable to read logs";
        try {
            final Process process = Runtime.getRuntime().exec("logcat -v threadtime -d");
            final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            final StringBuilder fullLogs = new StringBuilder();
            final StringBuilder relevantLogs = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                fullLogs.append(line).append("\n");
                if (isRelevantLogLine(line)) relevantLogs.append(line).append("\n");
            }
            bufferedReader.close();
            logs = "--------- Relevant Application Signals\n" +
                    (relevantLogs.length() == 0 ? "No application-specific or actionable error signals found.\n" : relevantLogs) +
                    "\n--------- Full Logcat\n" + fullLogs;
        } catch (IOException e) {
            Log.e("LogUtils", "Exception while reading logs", e);
        }
        return logs;
    }

    private static boolean isRelevantLogLine(final String line) {
        if (line == null) return false;
        final String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("androidruntime") || lower.contains("fatal exception") ||
                lower.contains("exception") || lower.contains("securityexception") ||
                lower.contains("illegalstateexception") || lower.contains("caused by") ||
                lower.contains("webview") || lower.contains("chromium") ||
                lower.contains("ftpserverservice") || lower.contains("ftpsettingsactivity") ||
                lower.contains("mainactivity") || lower.contains("javascriptinterface") ||
                lower.contains("tor") || lower.contains("lyrebird") ||
                lower.contains("logutils")) return true;

        final java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("^\\S+\\s+\\S+\\s+\\d+\\s+\\d+\\s+([VDIWEF])\\s+")
                .matcher(line);
        if (!matcher.find()) return false;
        final char priority = matcher.group(1).charAt(0);
        return (priority == 'E' || priority == 'F') && !lower.contains("openglrenderer");
    }
}
