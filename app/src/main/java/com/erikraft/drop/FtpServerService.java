package com.erikraft.drop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfiguration;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.SaltedPasswordEncryptor;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Provider;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FtpServerService extends Service {
    public static final String ACTION_START = "com.erikraft.drop.action.START_FTP";
    public static final String ACTION_STOP = "com.erikraft.drop.action.STOP_FTP";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_ERROR = "error";
    public static final String CHANNEL_ID = "ftp_server";
    public static final String EXTRA_STOP_NOTIFICATION = "stop_notification";
    public static final String ACTION_REFRESH_NOTIFICATION = "com.erikraft.drop.action.REFRESH_FTP_NOTIFICATION";
    private static final int NOTIFICATION_ID = 42021;
    private static final String KEYSTORE_PASSWORD = "erikraft-drop-ftps";
    private static final String KEY_ALIAS = "erikraft-drop-ftps";
    private static final int DEFAULT_PORT = 2221;
    private static final int PASSIVE_PORT_START = 50000;
    private static final int PASSIVE_PORT_END = 50010;
    private FtpServer ftpServer;
    private final Object serverLock = new Object();
    private long startGeneration;

    public static Intent startIntent(android.content.Context context) {
        return new Intent(context, FtpServerService.class).setAction(ACTION_START);
    }

    public static Intent stopIntent(android.content.Context context) {
        return new Intent(context, FtpServerService.class).setAction(ACTION_STOP);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REFRESH_NOTIFICATION.equals(intent.getAction())) {
            synchronized (serverLock) {
                if (ftpServer != null) updateNotification("Servidor FTP/FTPS ativo");
            }
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopServer();
            stopSelf();
            return START_NOT_STICKY;
        }
        synchronized (serverLock) {
            if (ftpServer != null) return START_NOT_STICKY;
            final long generation = ++startGeneration;
            startForeground(NOTIFICATION_ID, notification("Iniciando servidor FTP…"));
            new Thread(() -> startServer(generation), "ErikrafT-Drop-FTP").start();
        }
        return START_NOT_STICKY;
    }

    private void startServer(long generation) {
        try {
            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
            int port = parsePort(prefs.getString(getString(R.string.pref_ftp_port), "" + DEFAULT_PORT));
            String username = valueOrDefault(prefs.getString(getString(R.string.pref_ftp_username), ""), "admin");
            String password = valueOrDefault(prefs.getString(getString(R.string.pref_ftp_password), ""), "erikraft");
            boolean anonymous = prefs.getBoolean(getString(R.string.pref_ftp_anonymous), false);
            boolean ftps = prefs.getBoolean(getString(R.string.pref_ftp_ftps), true);
            File home = resolveHome(prefs.getString(getString(R.string.pref_save_location), ""));
            if (!home.exists() && !home.mkdirs()) throw new IllegalStateException("Não foi possível criar a pasta base: " + home);

            File userFile = new File(getFilesDir(), "ftp-users.properties");
            if (!userFile.exists() && !userFile.createNewFile()) {
                throw new IllegalStateException("Não foi possível criar o arquivo de usuários FTP: " + userFile);
            }

            PropertiesUserManagerFactory userFactory = new PropertiesUserManagerFactory();
            userFactory.setFile(userFile);
            userFactory.setPasswordEncryptor(new SaltedPasswordEncryptor());
            UserManager userManager = userFactory.createUserManager();
            BaseUser user = new BaseUser();
            user.setName(username);
            user.setPassword(password);
            user.setHomeDirectory(home.getAbsolutePath());
            List<org.apache.ftpserver.ftplet.Authority> authorities = new ArrayList<>();
            authorities.add(new WritePermission());
            user.setAuthorities(authorities);
            userManager.save(user);

            FtpServerFactory serverFactory = new FtpServerFactory();
            serverFactory.setUserManager(userManager);
            ConnectionConfigFactory connectionConfig = new ConnectionConfigFactory();
            connectionConfig.setAnonymousLoginEnabled(anonymous);
            connectionConfig.setMaxAnonymousLogins(1);
            serverFactory.setConnectionConfig(connectionConfig.createConnectionConfig());

            ListenerFactory listener = new ListenerFactory();
            listener.setPort(port);
            DataConnectionConfigurationFactory data = new DataConnectionConfigurationFactory();
            data.setPassivePorts(PASSIVE_PORT_START + "-" + PASSIVE_PORT_END);
            // Android devices frequently sit behind NAT/VPN interfaces; strict passive-IP
            // validation can reject legitimate data connections even when the control
            // connection is established successfully.
            data.setPassiveIpCheck(false);

            if (ftps) {
                SslConfigurationFactory sslFactory = new SslConfigurationFactory();
                File keystore = createKeystore();
                sslFactory.setKeystoreFile(keystore);
                sslFactory.setKeystoreType("PKCS12");
                sslFactory.setKeystorePassword(KEYSTORE_PASSWORD);
                sslFactory.setKeyPassword(KEYSTORE_PASSWORD);
                sslFactory.setKeyAlias(KEY_ALIAS);
                sslFactory.setSslProtocol("TLS");
                SslConfiguration ssl = sslFactory.createSslConfiguration();
                listener.setSslConfiguration(ssl);
                listener.setImplicitSsl(false);
                data.setSslConfiguration(ssl);
                data.setImplicitSsl(false);
            }

            listener.setDataConnectionConfiguration(data.createDataConnectionConfiguration());
            serverFactory.addListener("default", listener.createListener());
            FtpServer candidate = serverFactory.createServer();
            synchronized (serverLock) {
                if (generation != startGeneration) {
                    try {
                        candidate.stop();
                    } catch (Exception ignored) {
                    }
                    return;
                }
                ftpServer = candidate;
                runningInstance = candidate;
                candidate.start();
            }
            Log.i("FtpServerService", "FTP/FTPS server started on port " + port + " with home " + home.getAbsolutePath());
            broadcastStatus(true, null);
            updateNotification((ftps ? "FTPS" : "FTP") + " ativo em " + port);
        } catch (Exception e) {
            Log.e("FtpServerService", "FTP/FTPS server failed to start", e);
            synchronized (serverLock) {
                if (generation != startGeneration) return;
            }
            broadcastStatus(false, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            stopServer();
            stopSelf();
        }
    }

    private File resolveHome(String configured) {
        if (configured != null && !configured.trim().isEmpty()) return new File(configured);
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    private int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1025 || port > 65535) throw new IllegalArgumentException("A porta deve estar entre 1025 e 65535.");
            return port;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private File createKeystore() throws Exception {
        // Android does not guarantee the desktop JKS keystore implementation.
        // Use PKCS12, which is supported by Android's platform crypto providers.
        File file = new File(getFilesDir(), "ftp-ftps.p12");
        if (file.exists()) return file;
        // Do not use Android's platform provider named "BC". Android ships an
        // older, internal Bouncy Castle provider under the same name, which can
        // shadow the bundled provider and make SHA256withRSA unavailable.
        // Keep the bundled provider instance local instead of registering it
        // globally.
        Provider bcProvider = new BouncyCastleProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", bcProvider);
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 60_000L);
        Date notAfter = new Date(now + 3650L * 24L * 60L * 60L * 1000L);
        X500Name subject = new X500Name("CN=ErikrafT Drop FTP");
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(now), notBefore, notAfter, subject, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(bcProvider)
                .build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider(bcProvider)
                .getCertificate(holder);
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, KEYSTORE_PASSWORD.toCharArray());
        keyStore.setKeyEntry(KEY_ALIAS, keyPair.getPrivate(), KEYSTORE_PASSWORD.toCharArray(), new X509Certificate[]{certificate});
        File temporary = File.createTempFile("ftp-ftps-", ".tmp", getFilesDir());
        boolean published = false;
        try {
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                keyStore.store(output, KEYSTORE_PASSWORD.toCharArray());
            }
            if (!temporary.renameTo(file)) {
                throw new IllegalStateException("Não foi possível finalizar o keystore FTPS.");
            }
            published = true;
            return file;
        } finally {
            if (!published && temporary.exists() && !temporary.delete()) {
                Log.w("FtpServerService", "Could not delete temporary FTPS keystore: "
                        + temporary.getAbsolutePath());
            }
        }
    }

    private void stopServer() {
        synchronized (serverLock) {
            startGeneration++;
            if (ftpServer != null) {
                try {
                    ftpServer.stop();
                } catch (Exception ignored) {
                }
                ftpServer = null;
                runningInstance = null;
            }
        }
    }

    private void broadcastStatus(boolean running, String error) {
        Intent intent = new Intent(EXTRA_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra("running", running);
        if (error != null) intent.putExtra(EXTRA_ERROR, error);
        sendBroadcast(intent);
    }

    private Notification notification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Servidor FTP", NotificationManager.IMPORTANCE_LOW));
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.pref_savelocation)
                .setContentTitle("ErikrafT Drop™ FTP")
                .setContentText(text)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE);
        boolean stopAction = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.pref_ftp_stop_notification), false);
        if (stopAction) {
            PendingIntent stopPendingIntent = PendingIntent.getService(
                    this,
                    NOTIFICATION_ID + 1,
                    stopIntent(this),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new NotificationCompat.Action.Builder(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    getString(R.string.ftp_stop),
                    stopPendingIntent).build());
        }
        return builder.build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification(text));
    }

    public static boolean isRunning() { return runningInstance != null; }

    private static volatile FtpServer runningInstance;

    @Override
    public void onDestroy() {
        stopServer();
        broadcastStatus(false, null);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
