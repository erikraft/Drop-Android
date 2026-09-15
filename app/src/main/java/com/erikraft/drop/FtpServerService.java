package com.erikraft.drop;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.DataConnectionConfigurationFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.ssl.SslConfigurationFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.SaltedPasswordEncryptor;
import org.apache.ftpserver.usermanager.UserManager;
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
import java.security.Security;
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
    private static final int NOTIFICATION_ID = 42021;
    private static final String KEYSTORE_PASSWORD = "erikraft-drop-ftps";
    private static final String KEY_ALIAS = "erikraft-drop-ftps";
    private static final int DEFAULT_PORT = 2221;
    private static final int PASSIVE_PORT_START = 50000;
    private static final int PASSIVE_PORT_END = 50010;
    private FtpServer ftpServer;

    public static Intent startIntent(android.content.Context context) {
        return new Intent(context, FtpServerService.class).setAction(ACTION_START);
    }

    public static Intent stopIntent(android.content.Context context) {
        return new Intent(context, FtpServerService.class).setAction(ACTION_STOP);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopServer();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ftpServer == null) {
            startForeground(NOTIFICATION_ID, notification("Iniciando servidor FTP…"));
            new Thread(this::startServer, "ErikrafT-Drop-FTP").start();
        }
        return START_NOT_STICKY;
    }

    private void startServer() {
        try {
            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this);
            int port = parsePort(prefs.getString(getString(R.string.pref_ftp_port), "" + DEFAULT_PORT));
            String username = valueOrDefault(prefs.getString(getString(R.string.pref_ftp_username), ""), "erikraft");
            String password = valueOrDefault(prefs.getString(getString(R.string.pref_ftp_password), ""), "erikraft");
            boolean anonymous = prefs.getBoolean(getString(R.string.pref_ftp_anonymous), false);
            boolean ftps = prefs.getBoolean(getString(R.string.pref_ftp_ftps), true);
            File home = resolveHome(prefs.getString(getString(R.string.pref_save_location), ""));
            if (!home.exists() && !home.mkdirs()) throw new IllegalStateException("Não foi possível criar a pasta base: " + home);

            PropertiesUserManagerFactory userFactory = new PropertiesUserManagerFactory();
            userFactory.setFile(new File(getFilesDir(), "ftp-users.properties"));
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
            data.setPassiveIpCheck(true);
            listener.setDataConnectionConfiguration(data.createDataConnectionConfiguration());

            if (ftps) {
                File keystore = createKeystore();
                SslConfigurationFactory ssl = new SslConfigurationFactory();
                ssl.setKeystoreFile(keystore);
                ssl.setKeystorePassword(KEYSTORE_PASSWORD);
                ssl.setKeyPassword(KEYSTORE_PASSWORD);
                ssl.setKeyAlias(KEY_ALIAS);
                ssl.setSslProtocol("TLS");
                listener.setSslConfiguration(ssl.createSslConfiguration());
                listener.setImplicitSsl(false);
            }

            serverFactory.addListener("default", listener.createListener());
            ftpServer = serverFactory.createServer();
            ftpServer.start();
            broadcastStatus(true, null);
            updateNotification((ftps ? "FTPS" : "FTP") + " ativo em " + port);
        } catch (Exception e) {
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
        File file = new File(getFilesDir(), "ftp-ftps.jks");
        if (file.exists()) return file;
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now - 60_000L);
        Date notAfter = new Date(now + 3650L * 24L * 60L * 60L * 1000L);
        X500Name subject = new X500Name("CN=ErikrafT Drop FTP");
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, BigInteger.valueOf(now), notBefore, notAfter, subject, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider.PROVIDER_NAME).build(keyPair.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate certificate = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME).getCertificate(holder);
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, KEYSTORE_PASSWORD.toCharArray());
        keyStore.setKeyEntry(KEY_ALIAS, keyPair.getPrivate(), KEYSTORE_PASSWORD.toCharArray(), new X509Certificate[]{certificate});
        try (FileOutputStream output = new FileOutputStream(file)) {
            keyStore.store(output, KEYSTORE_PASSWORD.toCharArray());
        }
        return file;
    }

    private void stopServer() {
        if (ftpServer != null) {
            try {
                ftpServer.stop();
            } catch (Exception ignored) {
            }
            ftpServer = null;
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
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_ftp)
                .setContentTitle("ErikrafT Drop™ FTP")
                .setContentText(text)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification(text));
    }

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
