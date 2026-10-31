package com.erikraft.drop;

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
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public class TftpServerService extends Service {
    public static final String ACTION_START = "com.erikraft.drop.action.START_TFTP";
    public static final String ACTION_STOP = "com.erikraft.drop.action.STOP_TFTP";
    public static final String EXTRA_STATUS = "tftp_status";
    public static final String EXTRA_ERROR = "tftp_error";
    private static final String CHANNEL_ID = "tftp_server";
    private static final int NOTIFICATION_ID = 42041;
    private static final int DEFAULT_PORT = 1069;
    private static final int MAX_SESSIONS = 4;
    private static final AtomicInteger ACTIVE_SESSIONS = new AtomicInteger();

    private volatile DatagramSocket listener;
    private volatile boolean running;
    private volatile boolean startRequested;
    private volatile int runningPort;

    public static Intent startIntent(android.content.Context context) {
        return new Intent(context, TftpServerService.class).setAction(ACTION_START);
    }

    public static Intent stopIntent(android.content.Context context) {
        return new Intent(context, TftpServerService.class).setAction(ACTION_STOP);
    }

    public static boolean isRunning() { return runningInstance != null && runningInstance.running; }
    public static int getRunningPort() { return runningInstance == null ? 0 : runningInstance.runningPort; }
    private static volatile TftpServerService runningInstance;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopServer();
            stopSelf();
            return START_NOT_STICKY;
        }
        synchronized (this) {
            if (running || startRequested || listener != null) return START_NOT_STICKY;
            startRequested = true;
            startForeground(NOTIFICATION_ID, notification(getString(R.string.tftp_starting)));
            new Thread(this::startServer, "ErikrafT-Drop-TFTP").start();
        }
        return START_NOT_STICKY;
    }

    private void startServer() {
        try {
            android.content.SharedPreferences prefs =
                    PreferenceManager.getDefaultSharedPreferences(this);
            int port = parsePort(prefs.getString("tftp_port", String.valueOf(DEFAULT_PORT)));
            String configuredHome = prefs.getString(
                    getString(R.string.pref_save_location), "");
            File home = configuredHome == null || configuredHome.trim().isEmpty()
                    ? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    : new File(configuredHome);
            if (!home.isDirectory() || !home.canRead()) {
                throw new IllegalStateException(getString(R.string.tftp_error_folder));
            }

            DatagramSocket socket = new DatagramSocket(null);
            socket.setReuseAddress(false);
            socket.setSoTimeout(1000);
            socket.bind(new InetSocketAddress(port));
            if (!startRequested) {
                socket.close();
                return;
            }
            listener = socket;
            runningPort = port;
            running = true;
            runningInstance = this;
            broadcastStatus(true, null);
            updateNotification(getString(R.string.tftp_running, port));

            byte[] buffer = new byte[2048];
            while (running && !socket.isClosed()) {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(request);
                    handleRequest(home, request);
                } catch (SocketTimeoutException ignored) {
                    // Allows the service to observe stop requests promptly.
                } catch (IOException e) {
                    if (running) Log.w("TftpServerService", "TFTP request failed", e);
                }
            }
        } catch (Exception e) {
            Log.e("TftpServerService", "TFTP server failed to start", e);
            String error = e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage();
            stopServer();
            broadcastStatus(false, error);
            stopSelf();
        }
    }

    private void handleRequest(File home, DatagramPacket request) {
        int length = request.getLength();
        byte[] data = request.getData();
        if (length < 4) return;
        int opcode = unsignedShort(data, request.getOffset());
        if (opcode == 2) {
            sendError(request.getAddress(), request.getPort(), 2,
                    "TFTP write requests are disabled; this server is read-only.");
            return;
        }
        if (opcode != 1) {
            sendError(request.getAddress(), request.getPort(), 4, "Illegal TFTP operation.");
            return;
        }
        if (ACTIVE_SESSIONS.incrementAndGet() > MAX_SESSIONS) {
            ACTIVE_SESSIONS.decrementAndGet();
            sendError(request.getAddress(), request.getPort(), 0, "Server is busy.");
            return;
        }
        byte[] requestBytes = new byte[length];
        System.arraycopy(data, request.getOffset(), requestBytes, 0, length);
        InetAddress client = request.getAddress();
        int clientPort = request.getPort();
        new Thread(() -> {
            try {
                serveRead(home, requestBytes, client, clientPort);
            } finally {
                ACTIVE_SESSIONS.decrementAndGet();
            }
        }, "ErikrafT-TFTP-transfer").start();
    }

    private void serveRead(File home, byte[] request, InetAddress client, int clientPort) {
        try (DatagramSocket transfer = new DatagramSocket()) {
            transfer.setSoTimeout(3000);
            String[] fields = parseRequest(request);
            if (fields == null || !"octet".equalsIgnoreCase(fields[1])) {
                sendError(transfer, client, clientPort, 4, "Only octet mode is supported.");
                return;
            }
            String name = fields[0];
            if (name.startsWith("/") || name.indexOf('\\') >= 0
                    || name.equals("..") || name.contains("../") || name.contains("/..")) {
                sendError(transfer, client, clientPort, 2, "Access violation.");
                return;
            }
            File canonicalHome = home.getCanonicalFile();
            File file = new File(canonicalHome, name).getCanonicalFile();
            String root = canonicalHome.getPath() + File.separator;
            if (!file.getPath().startsWith(root) || !file.isFile() || !file.canRead()) {
                sendError(transfer, client, clientPort, 1, "File not found or not readable.");
                return;
            }

            try (FileInputStream input = new FileInputStream(file)) {
                int block = 1;
                byte[] payload = new byte[512];
                while (true) {
                    int count = readBlock(input, payload);
                    byte[] packet = new byte[count + 4];
                    packet[0] = 0;
                    packet[1] = 3;
                    packet[2] = (byte) (block >> 8);
                    packet[3] = (byte) block;
                    System.arraycopy(payload, 0, packet, 4, count);
                    DatagramPacket dataPacket = new DatagramPacket(
                            packet, packet.length, client, clientPort);
                    boolean acknowledged = false;
                    for (int attempt = 0; attempt < 5 && !acknowledged; attempt++) {
                        transfer.send(dataPacket);
                        try {
                            byte[] ackBytes = new byte[4];
                            DatagramPacket ack = new DatagramPacket(ackBytes, ackBytes.length);
                            transfer.receive(ack);
                            if (!client.equals(ack.getAddress()) || ack.getPort() != clientPort
                                    || ack.getLength() != 4
                                    || unsignedShort(ack.getData(), ack.getOffset()) != 4) {
                                continue;
                            }
                            if (unsignedShort(ack.getData(), ack.getOffset() + 2) == block) {
                                acknowledged = true;
                            }
                        } catch (SocketTimeoutException ignored) {
                            // Retransmit the same DATA block.
                        }
                    }
                    if (!acknowledged) return;
                    if (count < 512) return;
                    block = (block + 1) & 0xffff;
                }
            }
        } catch (Exception e) {
            Log.w("TftpServerService", "TFTP read transfer failed", e);
        }
    }

    private static int readBlock(FileInputStream input, byte[] buffer) throws IOException {
        int total = 0;
        while (total < buffer.length) {
            int read = input.read(buffer, total, buffer.length - total);
            if (read < 0) break;
            total += read;
        }
        return total;
    }

    private static String[] parseRequest(byte[] request) {
        if (request.length < 4 || unsignedShort(request, 0) != 1) return null;
        int filenameEnd = findZero(request, 2);
        if (filenameEnd <= 2 || filenameEnd >= request.length - 1) return null;
        int modeEnd = findZero(request, filenameEnd + 1);
        if (modeEnd != request.length - 1) return null; // Reject unimplemented option extensions.
        String filename = new String(request, 2, filenameEnd - 2, StandardCharsets.US_ASCII);
        String mode = new String(request, filenameEnd + 1,
                modeEnd - filenameEnd - 1, StandardCharsets.US_ASCII);
        return new String[]{filename, mode};
    }

    private static int findZero(byte[] data, int start) {
        for (int i = start; i < data.length; i++) if (data[i] == 0) return i;
        return -1;
    }

    private static int unsignedShort(byte[] data, int offset) {
        return ((data[offset] & 0xff) << 8) | (data[offset + 1] & 0xff);
    }

    private void sendError(InetAddress address, int port, int code, String message) {
        try (DatagramSocket socket = new DatagramSocket()) {
            sendError(socket, address, port, code, message);
        } catch (IOException ignored) { }
    }

    private void sendError(DatagramSocket socket, InetAddress address, int port,
            int code, String message) {
        try {
            byte[] text = message.getBytes(StandardCharsets.US_ASCII);
            byte[] packet = new byte[text.length + 5];
            packet[0] = 0;
            packet[1] = 5;
            packet[2] = (byte) (code >> 8);
            packet[3] = (byte) code;
            System.arraycopy(text, 0, packet, 4, text.length);
            packet[packet.length - 1] = 0;
            socket.send(new DatagramPacket(packet, packet.length, address, port));
        } catch (IOException ignored) { }
    }

    private int parsePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port < 1025 || port > 65535) throw new IllegalArgumentException(
                    getString(R.string.tftp_error_port));
            return port;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    private android.app.Notification notification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "TFTP server", NotificationManager.IMPORTANCE_LOW));
        }
        PendingIntent stop = PendingIntent.getService(this, NOTIFICATION_ID + 1,
                stopIntent(this), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.pref_savelocation)
                .setContentTitle("ErikrafT Drop™ TFTP")
                .setContentText(text)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.tftp_stop), stop)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification(text));
    }

    private void broadcastStatus(boolean active, String error) {
        Intent intent = new Intent(EXTRA_STATUS).setPackage(getPackageName());
        intent.putExtra("running", active);
        if (error != null) intent.putExtra(EXTRA_ERROR, error);
        sendBroadcast(intent);
    }

    private void stopServer() {
        startRequested = false;
        running = false;
        DatagramSocket socket = listener;
        listener = null;
        if (socket != null) socket.close();
        runningPort = 0;
        if (runningInstance == this) runningInstance = null;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
        broadcastStatus(false, null);
    }

    @Override public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
