package com.erikraft.drop;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.preference.PreferenceManager;

import java.security.SecureRandom;
import java.util.Locale;

/** Secure one-time migration and credential checks for the local FTP/FTPS/SFTP servers. */
public final class FtpSecurityDefaults {
    private static final String MIGRATION_KEY = "security_ftp_sftp_defaults_v1";
    private static final SecureRandom RANDOM = new SecureRandom();

    private FtpSecurityDefaults() { }

    public static synchronized void ensureSecureDefaults(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(MIGRATION_KEY, false)) return;

        String ftpUserKey = context.getString(R.string.pref_ftp_username);
        String ftpPasswordKey = context.getString(R.string.pref_ftp_password);
        String sftpUserKey = context.getString(R.string.pref_sftp_username);
        String sftpPasswordKey = context.getString(R.string.pref_sftp_password);

        String ftpUser = prefs.getString(ftpUserKey, "android");
        String ftpPassword = prefs.getString(ftpPasswordKey, "");
        String sftpUser = prefs.getString(sftpUserKey, "android");
        String sftpPassword = prefs.getString(sftpPasswordKey, "");

        SharedPreferences.Editor editor = prefs.edit();
        if (ftpUser == null || ftpUser.trim().isEmpty()) editor.putString(ftpUserKey, "android");
        if (!hasStrongCredentials(ftpUser, ftpPassword)) {
            editor.putString(ftpPasswordKey, generatePassword());
        }
        if (sftpUser == null || sftpUser.trim().isEmpty()) editor.putString(sftpUserKey, "android");
        if (!hasStrongCredentials(sftpUser, sftpPassword)) {
            editor.putString(sftpPasswordKey, generatePassword());
        }

        // These settings were previously persisted with insecure defaults. Migrate once;
        // users can still deliberately change them again from the settings screen.
        editor.putBoolean(context.getString(R.string.pref_ftp_anonymous), false);
        editor.putBoolean(context.getString(R.string.pref_ftp_ftps), true);
        editor.putBoolean(MIGRATION_KEY, true);
        editor.apply();
    }

    public static boolean hasStrongCredentials(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().length() < 12) {
            return false;
        }
        String trimmedPassword = password.trim();
        if (trimmedPassword.equalsIgnoreCase(username.trim())) return false;
        String normalized = trimmedPassword.toLowerCase(Locale.ROOT);
        if (normalized.equals("android")
                || normalized.equals("admin")
                || normalized.equals("erikraft")
                || normalized.equals("password")
                || normalized.equals("123456789012")
                || normalized.equals("abcdefghijkl")
                || normalized.equals("qwertyuiopas")) return false;

        boolean lower = false, upper = false, digit = false, symbol = false;
        char previous = 0;
        boolean allSame = true;
        for (int i = 0; i < trimmedPassword.length(); i++) {
            char c = trimmedPassword.charAt(i);
            lower |= Character.isLowerCase(c);
            upper |= Character.isUpperCase(c);
            digit |= Character.isDigit(c);
            symbol |= !Character.isLetterOrDigit(c);
            if (i > 0 && c != previous) allSame = false;
            previous = c;
        }
        int characterClasses = (lower ? 1 : 0) + (upper ? 1 : 0)
                + (digit ? 1 : 0) + (symbol ? 1 : 0);
        return !allSame && characterClasses >= 3;
    }

    private static String generatePassword() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }
}
