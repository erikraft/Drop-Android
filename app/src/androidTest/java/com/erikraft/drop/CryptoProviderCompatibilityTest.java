package com.erikraft.drop;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Build;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.apache.sshd.common.util.OsUtils;
import org.apache.sshd.common.util.io.PathUtils;
import org.apache.sshd.server.SshServer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.ServerSocket;
import java.security.AlgorithmParameters;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class CryptoProviderCompatibilityTest {

    private static final String KEYSTORE_PASSWORD = "erikraft-drop-ftps";

    @Test
    public void bundledBouncyCastleSupportsFtpsRsaSignature() throws Exception {
        Provider provider = new BouncyCastleProvider();
        assertNotNull(Signature.getInstance("SHA256withRSA", provider));
    }

    @Test
    public void androidPlatformSupportsSshNistP384Parameters() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp384r1"));
        assertNotNull(parameters.getParameterSpec(ECParameterSpec.class));
    }

    @Test
    public void ftpsListenerStartsWithGeneratedPkcs12Keystore() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, Object> previous = capturePreferences(preferences,
                context.getString(R.string.pref_ftp_port),
                context.getString(R.string.pref_ftp_username),
                context.getString(R.string.pref_ftp_password),
                context.getString(R.string.pref_ftp_anonymous),
                context.getString(R.string.pref_ftp_ftps),
                context.getString(R.string.pref_save_location));
        int port = findAvailablePort();
        File home = context.getFilesDir();
        try {
            preferences.edit()
                    .putString(context.getString(R.string.pref_ftp_port), Integer.toString(port))
                    .putString(context.getString(R.string.pref_ftp_username), "test-user")
                    .putString(context.getString(R.string.pref_ftp_password), "test-password")
                    .putBoolean(context.getString(R.string.pref_ftp_anonymous), false)
                    .putBoolean(context.getString(R.string.pref_ftp_ftps), true)
                    .putString(context.getString(R.string.pref_save_location), home.getAbsolutePath())
                    .commit();

            startService(context, FtpServerService.startIntent(context));
            awaitFtpRunning(true);
            // Allow asynchronous listener startup to fail rather than accepting its
            // early running-instance assignment as proof that start() succeeded.
            Thread.sleep(500);
            assertTrue("FTPS listener did not remain running", FtpServerService.isRunning());

            File generatedFile = new File(context.getFilesDir(), "ftp-ftps.p12");
            assertTrue("Generated PKCS12 keystore is missing", generatedFile.isFile());
            KeyStore generated = KeyStore.getInstance("PKCS12");
            try (FileInputStream input = new FileInputStream(generatedFile)) {
                generated.load(input, KEYSTORE_PASSWORD.toCharArray());
            }
            KeyStore roundTrip = KeyStore.getInstance("PKCS12");
            roundTrip.load(null, KEYSTORE_PASSWORD.toCharArray());
            roundTrip.setKeyEntry("round-trip", generated.getKey(
                    "erikraft-drop-ftps", KEYSTORE_PASSWORD.toCharArray()),
                    KEYSTORE_PASSWORD.toCharArray(),
                    generated.getCertificateChain("erikraft-drop-ftps"));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            roundTrip.store(output, KEYSTORE_PASSWORD.toCharArray());
            KeyStore reloaded = KeyStore.getInstance("PKCS12");
            reloaded.load(new ByteArrayInputStream(output.toByteArray()),
                    KEYSTORE_PASSWORD.toCharArray());
            assertNotNull(reloaded.getKey("round-trip", KEYSTORE_PASSWORD.toCharArray()));
        } finally {
            try {
                startService(context, FtpServerService.stopIntent(context));
                awaitFtpRunning(false);
            } finally {
                restorePreferences(preferences, previous);
            }
        }
    }

    @Test
    public void sftpServiceStartsWithValidHomeDirectory() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        Map<String, Object> previous = capturePreferences(preferences,
                context.getString(R.string.pref_sftp_port),
                context.getString(R.string.pref_sftp_username),
                context.getString(R.string.pref_sftp_password),
                context.getString(R.string.pref_sftp_save_location));
        String previousUserHome = System.getProperty("user.home");
        String previousUserDir = System.getProperty("user.dir");
        String providerProperty = "org.apache.sshd.security.provider.BC.enabled";
        String previousProviderProperty = System.getProperty(providerProperty);
        File home = new File(context.getFilesDir(), "sftp-instrumentation-home");
        assertTrue("Could not create SFTP test home", home.isDirectory() || home.mkdirs());
        try {
            preferences.edit()
                    .putString(context.getString(R.string.pref_sftp_port),
                            Integer.toString(findAvailablePort()))
                    .putString(context.getString(R.string.pref_sftp_username), "test-user")
                    .putString(context.getString(R.string.pref_sftp_password), "test-password")
                    .putString(context.getString(R.string.pref_sftp_save_location), home.getAbsolutePath())
                    .commit();

            startService(context, SftpServerService.startIntent(context));
            awaitSftpRunning(true);
            assertTrue("SFTP service did not remain running", SftpServerService.isRunning());
        } finally {
            try {
                startService(context, SftpServerService.stopIntent(context));
                awaitSftpRunning(false);
            } finally {
                PathUtils.setUserHomeFolderResolver(null);
                OsUtils.setCurrentWorkingDirectoryResolver(null);
                restoreProperty("user.home", previousUserHome);
                restoreProperty("user.dir", previousUserDir);
                restoreProperty(providerProperty, previousProviderProperty);
                restorePreferences(preferences, previous);
            }
        }
    }

    @Test
    public void sshdInitializesWithAndroidHomeAndWithoutItsBcRegistrar() throws Exception {
        String providerProperty = "org.apache.sshd.security.provider.BC.enabled";
        String previousProviderProperty = System.getProperty(providerProperty);
        String previousUserHome = System.getProperty("user.home");
        String previousUserDir = System.getProperty("user.dir");
        Context context = ApplicationProvider.getApplicationContext();
        java.nio.file.Path appFiles = context.getFilesDir().toPath();

        try {
            System.setProperty("user.home", appFiles.toString());
            System.setProperty("user.dir", appFiles.toString());
            PathUtils.setUserHomeFolderResolver(() -> appFiles);
            OsUtils.setCurrentWorkingDirectoryResolver(() -> appFiles);
            System.setProperty(providerProperty, "false");
            SshServer server = SshServer.setUpDefaultServer();
            assertNotNull(server);
            server.stop();
        } finally {
            PathUtils.setUserHomeFolderResolver(null);
            OsUtils.setCurrentWorkingDirectoryResolver(null);
            restoreProperty("user.home", previousUserHome);
            restoreProperty("user.dir", previousUserDir);
            restoreProperty(providerProperty, previousProviderProperty);
        }
    }

    private static Map<String, Object> capturePreferences(
            SharedPreferences preferences, String... keys) {
        Map<String, Object> values = new HashMap<>();
        Map<String, ?> all = preferences.getAll();
        for (String key : keys) {
            values.put(key, all.containsKey(key) ? all.get(key) : null);
        }
        return values;
    }

    private static void restorePreferences(
            SharedPreferences preferences, Map<String, Object> values) {
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                editor.remove(entry.getKey());
            } else if (value instanceof String) {
                editor.putString(entry.getKey(), (String) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            }
        }
        editor.commit();
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static int findAvailablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private static void awaitFtpRunning(boolean expected) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (FtpServerService.isRunning() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertTrue("FTP/FTPS service running state did not become " + expected,
                FtpServerService.isRunning() == expected);
    }

    private static void awaitSftpRunning(boolean expected) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (SftpServerService.isRunning() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertTrue("SFTP service running state did not become " + expected,
                SftpServerService.isRunning() == expected);
    }
}
