package com.erikraft.drop;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.util.OsUtils;
import org.apache.sshd.common.util.io.PathUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;

import java.io.File;
import java.util.Collections;

public class SftpServerService extends Service {
    public static final String ACTION_START = "com.erikraft.drop.action.START_SFTP";
    public static final String ACTION_STOP = "com.erikraft.drop.action.STOP_SFTP";
    public static final String EXTRA_STATUS = "sftp_status";
    public static final String EXTRA_ERROR = "sftp_error";
    private static final String CHANNEL_ID = "sftp_server";
    private static final int NOTIFICATION_ID = 42031;
    private static final int DEFAULT_PORT = 2222;
    private static final Object LOCK = new Object();

    private SshServer server;
    private static volatile boolean running;
    private static volatile int runningPort;
    private boolean starting;

    public static Intent startIntent(android.content.Context context) {
        return new Intent(context, SftpServerService.class).setAction(ACTION_START);
    }

    public static Intent stopIntent(android.content.Context context) {
        return new Intent(context, SftpServerService.class).setAction(ACTION_STOP);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopServer();
            stopSelf();
            return START_NOT_STICKY;
        }
        synchronized (LOCK) {
            if (server != null || starting) return START_NOT_STICKY;
            starting = true;
            startForeground(NOTIFICATION_ID, notification("Iniciando servidor SFTP…"));
            new Thread(this::startServer, "ErikrafT-Drop-SFTP").start();
        }
        return START_NOT_STICKY;
    }

    private void startServer() {
        try {
            // Android does not expose a conventional user home/current directory.
            // Configure SSHD's path resolvers before touching SshServer/ServerBuilder:
            // their static initializers may resolve ~/.ssh during class initialization.
            java.nio.file.Path appFiles = getFilesDir().toPath();
            System.setProperty("user.home", appFiles.toString());
            System.setProperty("user.dir", appFiles.toString());
            PathUtils.setUserHomeFolderResolver(() -> appFiles);
            OsUtils.setCurrentWorkingDirectoryResolver(() -> appFiles);

            // Android provides its own crypto primitives; avoid SSHD's optional BC registrar.
            System.setProperty("org.apache.sshd.security.provider.BC.enabled", "false");
            android.content.SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(this);
            int port = parsePort(prefs.getString(getString(R.string.pref_sftp_port), "" + DEFAULT_PORT));
            String username = valueOrDefault(
                    prefs.getString(getString(R.string.pref_sftp_username), ""), "admin");
            String password = valueOrDefault(
                    prefs.getString(getString(R.string.pref_sftp_password), ""), "admin");
            String configuredHome = prefs.getString(getString(R.string.pref_sftp_save_location), "");
            File home = new File(configuredHome);
            if (!home.isDirectory() || !home.canRead() || !home.canWrite()) {
                throw new IllegalStateException("A pasta SFTP selecionada não pode ser acessada diretamente.");
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                throw new IllegalStateException("O servidor SFTP requer Android 8.0 ou superior.");
            }

            SshServer candidate = SshServer.setUpDefaultServer();
            candidate.setPort(port);
            candidate.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(
                    new File(getFilesDir(), "sftp-hostkey").toPath()));
            candidate.setPasswordAuthenticator(new PasswordAuthenticator() {
                @Override
                public boolean authenticate(String user, String pass, ServerSession session) {
                    return username.equals(user) && password.equals(pass);
                }
            });
            candidate.setFileSystemFactory(new VirtualFileSystemFactory(home.toPath()));
            candidate.setSubsystemFactories(Collections.singletonList(
                    new SftpSubsystemFactory.Builder().build()));

            synchronized (LOCK) {
                if (server != null) {
                    try {
                        candidate.stop();
                    } catch (Exception ignored) {
                    }
                    return;
                }
                candidate.start();
                server = candidate;
                starting = false;
            }
            Log.i("SftpServerService", "SFTP server started on port " + port
                    + " with home " + home.getAbsolutePath());
            broadcastStatus(true, null);
            running = true;
            runningPort = port;
            startForeground(NOTIFICATION_ID, notification("SFTP ativo em " + port));
            updateNotification("SFTP ativo em " + port);
        } catch (Exception e) {
            Log.e("SftpServerService", "SFTP server failed to start", e);
            broadcastStatus(false, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            synchronized (LOCK) { starting = false; }
            stopServer();
            stopSelf();
        }
    }


    private int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1025 || port > 65535) {
                throw new IllegalArgumentException("A porta deve estar entre 1025 e 65535.");
            }
            return port;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private void stopServer() {
        synchronized (LOCK) {
            if (server != null) {
                try {
                    server.stop();
                } catch (Exception ignored) {
                }
                server = null;
            }
            starting = false;
            running = false;
            runningPort = 0;
        }
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    private void broadcastStatus(boolean running, String error) {
        Intent intent = new Intent(EXTRA_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra("running", running);
        if (error != null) intent.putExtra(EXTRA_ERROR, error);
        sendBroadcast(intent);
    }

    private NotificationCompat.Builder notificationBuilder(String text) {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Servidor SFTP", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, NOTIFICATION_ID + 1, stopIntent(this),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.pref_savelocation)
                .setContentTitle("ErikrafT Drop™ SFTP")
                .setContentText(text)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(new NotificationCompat.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.ftp_stop),
                        stopPendingIntent).build());
    }

    private android.app.Notification notification(String text) {
        return notificationBuilder(text).build();
    }

    private void updateNotification(String text) {
        NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification(text));
    }

    @Override
    public void onDestroy() {
        stopServer();
        broadcastStatus(false, null);
        super.onDestroy();
    }

    public static boolean isRunning() { return running; }

    public static int getRunningPort() { return runningPort; }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
