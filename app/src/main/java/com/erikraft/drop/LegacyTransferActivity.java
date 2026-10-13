package com.erikraft.drop;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.appbar.MaterialToolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional legacy transfer compatibility diagnostics and Wi-Fi Direct text-transfer foundation.
 * NFC tag reading/writing is intentionally not part of this file-transfer-focused app.
 */
public final class LegacyTransferActivity extends DropPipActivity {
    private static final int PERMISSION_REQUEST = 7108;
    private static final int SOCKET_PORT = 38741;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private NfcAdapter nfcAdapter;
    private WifiP2pManager wifiManager;
    private WifiP2pManager.Channel wifiChannel;
    private BroadcastReceiver wifiReceiver;
    private boolean receiverRegistered;
    private WifiP2pInfo connectionInfo;
    private final List<WifiP2pDevice> peers = new ArrayList<>();
    private ArrayAdapter<String> peerAdapter;
    private EditText textInput;
    private TextView status;
    private Button beamButton;
    private Button discoverButton;
    private Button sendButton;
    private ServerSocket serverSocket;
    private Thread serverThread;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SnapdropApplication.setAppTheme(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.legacy_transfer_screen_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationContentDescription(R.string.legacy_transfer_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1,
                getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_toolbar_default_height)));
        status = new TextView(this);
        status.setText(R.string.legacy_transfer_ready);
        root.addView(status);
        textInput = new EditText(this);
        textInput.setHint(R.string.legacy_transfer_text_hint);
        textInput.setMinLines(2);
        textInput.setGravity(android.view.Gravity.TOP);
        root.addView(textInput);
        beamButton = new Button(this);
        beamButton.setText(R.string.legacy_beam_send_text);
        root.addView(beamButton);
        discoverButton = new Button(this);
        discoverButton.setText(R.string.legacy_wifi_discover);
        root.addView(discoverButton);
        ListView peerList = new ListView(this);
        peerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        peerList.setAdapter(peerAdapter);
        root.addView(peerList, new LinearLayout.LayoutParams(-1, 0, 1));
        sendButton = new Button(this);
        sendButton.setText(R.string.legacy_wifi_send_text);
        sendButton.setEnabled(false);
        root.addView(sendButton);
        setContentView(root);

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        final boolean beamAvailable = Build.VERSION.SDK_INT < 34 && nfcAdapter != null;
        beamButton.setEnabled(beamAvailable);
        beamButton.setAlpha(beamAvailable ? 1f : 0.55f);
        if (!beamAvailable) {
            beamButton.setText(nfcAdapter == null
                    ? R.string.legacy_nfc_no_hardware
                    : R.string.legacy_beam_android_14_plus);
        }
        beamButton.setOnClickListener(v -> shareWithBeam());
        discoverButton.setOnClickListener(v -> discoverPeers());
        peerList.setOnItemClickListener((parent, view, position, id) -> connectToPeer(position));
        sendButton.setOnClickListener(v -> sendWifiText());
        initWifiDirect();
        handleNfcIntent(getIntent());
        requestWifiPermissionIfNeeded();
    }

    private void initWifiDirect() {
        wifiManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        final boolean hasWifiDirect = getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT);
        if (wifiManager == null || !hasWifiDirect) {
            status.setText(R.string.legacy_wifi_unavailable);
            discoverButton.setText(R.string.legacy_wifi_unavailable);
            discoverButton.setEnabled(false);
            discoverButton.setAlpha(0.55f);
            return;
        }
        wifiChannel = wifiManager.initialize(this, getMainLooper(), null);
        wifiReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                    if (hasWifiPermission()) wifiManager.requestPeers(wifiChannel, list -> showPeers(list));
                } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                    if (hasWifiPermission()) wifiManager.requestConnectionInfo(wifiChannel, info -> {
                        connectionInfo = info;
                        boolean connected = info != null && info.groupFormed;
                        sendButton.setEnabled(connected && !info.isGroupOwner);
                        status.setText(connected
                                ? (info.isGroupOwner ? R.string.legacy_wifi_waiting_for_sender : R.string.legacy_wifi_connected)
                                : R.string.legacy_wifi_not_connected);
                        if (connected && info.isGroupOwner) startReceiverSocket();
                    });
                } else if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                    int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                    if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) status.setText(R.string.legacy_wifi_disabled);
                }
            }
        };
    }

    private void registerWifiReceiver() {
        if (receiverRegistered || wifiReceiver == null) return;
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        filter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(wifiReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(wifiReceiver, filter);
        receiverRegistered = true;
    }

    private boolean hasWifiPermission() {
        if (Build.VERSION.SDK_INT >= 33) return ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestWifiPermissionIfNeeded() {
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.NEARBY_WIFI_DEVICES : Manifest.permission.ACCESS_FINE_LOCATION;
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, PERMISSION_REQUEST);
        } else registerWifiReceiver();
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PERMISSION_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) registerWifiReceiver();
        else if (requestCode == PERMISSION_REQUEST) status.setText(R.string.legacy_wifi_permission_denied);
    }

    private void discoverPeers() {
        if (wifiManager == null || !hasWifiPermission()) {
            requestWifiPermissionIfNeeded();
            return;
        }
        registerWifiReceiver();
        wifiManager.discoverPeers(wifiChannel, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { status.setText(R.string.legacy_wifi_discovering); }
            @Override public void onFailure(int reason) { status.setText(getString(R.string.legacy_wifi_discovery_failed, reason)); }
        });
    }

    private void showPeers(WifiP2pDeviceList list) {
        peers.clear();
        peers.addAll(list.getDeviceList());
        peerAdapter.clear();
        for (WifiP2pDevice peer : peers) peerAdapter.add(peer.deviceName + " — " + peer.deviceAddress);
        if (peers.isEmpty()) status.setText(R.string.legacy_wifi_no_peers);
    }

    private void connectToPeer(int position) {
        if (!hasWifiPermission() || position < 0 || position >= peers.size()) return;
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = peers.get(position).deviceAddress;
        wifiManager.connect(wifiChannel, config, new WifiP2pManager.ActionListener() {
            @Override public void onSuccess() { status.setText(R.string.legacy_wifi_connecting); }
            @Override public void onFailure(int reason) { status.setText(getString(R.string.legacy_wifi_connect_failed, reason)); }
        });
    }

    private void sendWifiText() {
        if (connectionInfo == null || !connectionInfo.groupFormed) return;
        final String message = textInput.getText().toString();
        if (message.isEmpty()) return;
        final String host = connectionInfo.isGroupOwner ? "127.0.0.1" : connectionInfo.groupOwnerAddress.getHostAddress();
        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, SOCKET_PORT), 5000);
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                writer.println(message);
                mainHandler.post(() -> status.setText(R.string.legacy_wifi_text_sent));
            } catch (Exception e) {
                mainHandler.post(() -> status.setText(getString(R.string.legacy_wifi_transfer_failed, e.getLocalizedMessage() == null ? e.getClass().getSimpleName() : e.getLocalizedMessage())));
            }
        }).start();
    }

    private void startReceiverSocket() {
        if (serverThread != null && serverThread.isAlive()) return;
        serverThread = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(SOCKET_PORT)) {
                serverSocket = server;
                while (!server.isClosed() && !isFinishing()) {
                    try (Socket socket = server.accept();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                        StringBuilder message = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (message.length() + line.length() > 65536) throw new IllegalArgumentException("Message too large");
                            if (message.length() > 0) message.append('\n');
                            message.append(line);
                        }
                        final String received = message.toString();
                        mainHandler.post(() -> {
                            textInput.setText(received);
                            status.setText(R.string.legacy_wifi_text_received);
                        });
                    } catch (Exception ignored) { }
                }
            } catch (Exception e) {
                mainHandler.post(() -> status.setText(getString(R.string.legacy_wifi_transfer_failed, e.getMessage() == null ? "I/O error" : e.getMessage())));
            } finally { serverSocket = null; }
        }, "Drop-WiFiDirect-receiver");
        serverThread.start();
    }

    private void shareWithBeam() {
        if (nfcAdapter == null || Build.VERSION.SDK_INT >= 34) {
            status.setText(R.string.legacy_beam_unavailable);
            return;
        }
        try {
            NdefMessage message = new NdefMessage(new NdefRecord[]{createTextRecord(textInput.getText().toString())});
            // Beam APIs are deprecated/hidden on newer Android releases. Reflection is only
            // attempted before Android 14; manufacturer support is not guaranteed and failures
            // are reported rather than treated as a successful transfer.
            nfcAdapter.getClass().getMethod("setNdefPushMessage", NdefMessage.class, Activity.class, Activity[].class)
                    .invoke(nfcAdapter, message, this, new Activity[0]);
            status.setText(R.string.legacy_beam_ready_to_tap);
        } catch (Exception e) {
            status.setText(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? R.string.legacy_beam_android_10_13
                    : R.string.legacy_beam_unavailable);
        }
    }

    private NdefRecord createTextRecord(String text) {
        byte[] language = "en".getBytes(StandardCharsets.US_ASCII);
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[1 + language.length + textBytes.length];
        payload[0] = (byte) language.length;
        System.arraycopy(language, 0, payload, 1, language.length);
        System.arraycopy(textBytes, 0, payload, 1 + language.length, textBytes.length);
        return new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload);
    }

    private String readTextRecord(NdefMessage message) {
        if (message == null || message.getRecords().length == 0) return null;
        NdefRecord record = message.getRecords()[0];
        byte[] payload = record.getPayload();
        if (payload == null || payload.length == 0) return null;
        int languageLength = payload[0] & 0x3f;
        if (payload.length <= languageLength + 1) return "";
        return new String(payload, languageLength + 1, payload.length - languageLength - 1, StandardCharsets.UTF_8);
    }

    private void handleNfcIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) return;
        android.os.Parcelable[] incoming = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        if (incoming != null && incoming.length > 0 && incoming[0] instanceof NdefMessage) {
            String received = readTextRecord((NdefMessage) incoming[0]);
            if (received != null) {
                textInput.setText(received);
                status.setText(R.string.legacy_beam_text_received);
            }
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNfcIntent(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        registerWifiReceiver();
    }

    @Override protected void onPause() {
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (receiverRegistered && wifiReceiver != null) {
            try { unregisterReceiver(wifiReceiver); } catch (IllegalArgumentException ignored) { }
            receiverRegistered = false;
        }
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }
}
