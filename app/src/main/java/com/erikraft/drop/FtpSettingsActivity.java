package com.erikraft.drop;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.anggrayudi.storage.file.DocumentFileUtils;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public class FtpSettingsActivity extends DropPipActivity {
    private SharedPreferences prefs;
    private TextInputEditText portInput;
    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private SwitchCompat anonymousSwitch;
    private SwitchCompat ftpsSwitch;
    private SwitchCompat stopNotificationSwitch;
    private TextView directorySummary;
    private TextView status;
    private ActivityResultLauncher<Intent> directoryPicker;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private TextView address;
    private MaterialButton serverToggleButton;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!FtpServerService.EXTRA_STATUS.equals(intent.getAction())) return;
            boolean running = intent.getBooleanExtra("running", false);
            String error = intent.getStringExtra(FtpServerService.EXTRA_ERROR);
            status.setText(running ? "Servidor ativo" : (error == null ? "Servidor parado" : "Falha: " + error));
            updateButtons(running);
            if (running) updateAddress();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SnapdropApplication.setAppTheme(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        notificationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (stopNotificationSwitch != null) stopNotificationSwitch.setChecked(granted);
            prefs.edit().putBoolean(getString(R.string.pref_ftp_stop_notification), granted).apply();
            if (FtpServerService.isRunning()) startService(new Intent(this, FtpServerService.class).setAction(FtpServerService.ACTION_REFRESH_NOTIFICATION));
        });
        directoryPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
            Uri uri = result.getData().getData();
            if (uri == null) return;
            try {
                final int flags = result.getData().getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0
                        && (flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                    getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } else if ((flags & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } else if ((flags & Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0) {
                    getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
            } catch (SecurityException ignored) { }
            DocumentFile folder = DocumentFile.fromTreeUri(this, uri);
            if (folder == null) return;
            String path = DocumentFileUtils.getAbsolutePath(folder, this);
            if (path == null || path.trim().isEmpty()) {
                Toast.makeText(this, "Não foi possível obter o diretório escolhido.", Toast.LENGTH_LONG).show();
                return;
            }
            File directPath = new File(path);
            if (!directPath.isDirectory() || !directPath.canRead() || !directPath.canWrite()) {
                Toast.makeText(this, "Esta pasta só está disponível via SAF e não pode ser usada diretamente pelo servidor FTP.", Toast.LENGTH_LONG).show();
                return;
            }
            prefs.edit().putString(getString(R.string.pref_save_location), directPath.getAbsolutePath()).apply();
            directorySummary.setText(directPath.getAbsolutePath());
        });
        buildUi();
        ContextCompat.registerReceiver(this, statusReceiver, new IntentFilter(FtpServerService.EXTRA_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Toolbar toolbar = new Toolbar(this);
        toolbar.setTitle(R.string.ftp_settings_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_toolbar_default_height)));
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(28));
        content.addView(text(getString(R.string.ftp_settings_description), 15));
        MaterialButton chooseDirectory = new MaterialButton(this);
        chooseDirectory.setText(R.string.ftp_choose_directory);
        directorySummary = text(prefs.getString(getString(R.string.pref_save_location),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).getPath()), 13);
        directorySummary.setTextIsSelectable(true);
        content.addView(chooseDirectory, lpTop(dp(12)));
        content.addView(directorySummary, lpTop(dp(4)));
        chooseDirectory.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            directoryPicker.launch(intent);
        });
        portInput = field("Porta", prefs.getString(getString(R.string.pref_ftp_port), "2221"), InputType.TYPE_CLASS_NUMBER);
        content.addView((TextInputLayout) portInput.getTag(), lpTop(dp(12)));
        usernameInput = field("Nome de usuário", prefs.getString(getString(R.string.pref_ftp_username), "admin"), InputType.TYPE_CLASS_TEXT);
        content.addView((TextInputLayout) usernameInput.getTag(), lpTop(dp(8)));
        passwordInput = field("Senha", prefs.getString(getString(R.string.pref_ftp_password), "admin"), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        content.addView((TextInputLayout) passwordInput.getTag(), lpTop(dp(8)));
        anonymousSwitch = new SwitchCompat(this);
        anonymousSwitch.setText(R.string.ftp_anonymous_title);
        anonymousSwitch.setChecked(prefs.getBoolean(getString(R.string.pref_ftp_anonymous), false));
        content.addView(anonymousSwitch, lpTop(dp(8)));
        ftpsSwitch = new SwitchCompat(this);
        ftpsSwitch.setText(R.string.ftp_ftps_title);
        ftpsSwitch.setChecked(prefs.getBoolean(getString(R.string.pref_ftp_ftps), true));
        content.addView(ftpsSwitch, lpTop(dp(4)));
        stopNotificationSwitch = new SwitchCompat(this);
        stopNotificationSwitch.setText(R.string.ftp_stop_notification_title);
        stopNotificationSwitch.setChecked(prefs.getBoolean(getString(R.string.pref_ftp_stop_notification), false));
        stopNotificationSwitch.setContentDescription(getString(R.string.ftp_stop_notification_summary));
        content.addView(stopNotificationSwitch, lpTop(dp(8)));
        TextView notificationSummary = text(getString(R.string.ftp_stop_notification_summary), 13);
        content.addView(notificationSummary, lpTop(dp(2)));
        content.addView(text(getString(R.string.ftp_passive_summary), 13), lpTop(dp(4)));
        status = text("Servidor parado", 15);
        content.addView(status, lpTop(dp(18)));
        address = text("", 14);
        address.setTextIsSelectable(true);
        content.addView(address, lpTop(dp(4)));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        serverToggleButton = new MaterialButton(this);
        serverToggleButton.setText(R.string.ftp_start);
        actions.addView(serverToggleButton, new LinearLayout.LayoutParams(-1, -2));
        content.addView(actions, lpTop(dp(12)));
        MaterialButton copy = new MaterialButton(this);
        copy.setText(R.string.ftp_copy_address);
        content.addView(copy, lpTop(dp(8)));
        serverToggleButton.setOnClickListener(v -> {
            if (FtpServerService.isRunning()) stopServer();
            else saveAndStart();
        });
        stopNotificationSwitch.setOnClickListener(v -> handleStopNotificationPreference());
        copy.setOnClickListener(v -> copyAddress());
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        updateButtons(FtpServerService.isRunning());
        status.setText(FtpServerService.isRunning() ? "Servidor ativo" : "Servidor parado");
    }

    private TextInputEditText field(String hint, String value, int inputType) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setHint(hint);
        if ("Senha".equals(hint)) {
            layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        }
        TextInputEditText edit = new TextInputEditText(this);
        edit.setSingleLine(true);
        edit.setInputType(inputType);
        edit.setText(value);
        layout.addView(edit, new LinearLayout.LayoutParams(-1, -2));
        edit.setTag(layout);
        return edit;
    }

    private void handleStopNotificationPreference() {
        if (!stopNotificationSwitch.isChecked()) {
            prefs.edit().putBoolean(getString(R.string.pref_ftp_stop_notification), false).apply();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        prefs.edit().putBoolean(getString(R.string.pref_ftp_stop_notification), true).apply();
    }

    private void saveAndStart() {
        int port;
        try {
            port = Integer.parseInt(portInput.getText() == null ? "" : portInput.getText().toString());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Informe uma porta válida.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (port < 1025 || port > 65535) {
            Toast.makeText(this, "A porta deve estar entre 1025 e 65535.", Toast.LENGTH_SHORT).show();
            return;
        }
        String username = usernameInput.getText() == null ? "" : usernameInput.getText().toString().trim();
        String password = passwordInput.getText() == null ? "" : passwordInput.getText().toString();
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Usuário e senha são obrigatórios.", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit()
                .putString(getString(R.string.pref_ftp_port), String.valueOf(port))
                .putString(getString(R.string.pref_ftp_username), username)
                .putString(getString(R.string.pref_ftp_password), password)
                .putBoolean(getString(R.string.pref_ftp_anonymous), anonymousSwitch.isChecked())
                .putBoolean(getString(R.string.pref_ftp_ftps), ftpsSwitch.isChecked())
                .putBoolean(getString(R.string.pref_ftp_stop_notification), stopNotificationSwitch.isChecked())
                .apply();
        ContextCompat.startForegroundService(this, FtpServerService.startIntent(this));
        status.setText(R.string.ftp_starting);
    }

    private void stopServer() {
        startService(FtpServerService.stopIntent(this));
    }

    private void updateButtons(boolean running) {
        serverToggleButton.setText(running ? R.string.ftp_stop : R.string.ftp_start);
        address.setText(running ? buildAddress() : "");
    }

    private void updateAddress() {
        address.setText(buildAddress());
    }

    private String buildAddress() {
        String port = prefs.getString(getString(R.string.pref_ftp_port), "2221");
        String scheme = prefs.getBoolean(getString(R.string.pref_ftp_ftps), true) ? "ftps" : "ftp";
        return scheme + "://" + getWifiAddress() + ":" + port;
    }

    private String getWifiAddress() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (!address.isLoopbackAddress() && address instanceof Inet4Address && network.isUp()) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) {
        }
        WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifi != null) {
            int ip = wifi.getConnectionInfo().getIpAddress();
            return String.format("%d.%d.%d.%d", ip & 255, (ip >> 8) & 255, (ip >> 16) & 255, (ip >> 24) & 255);
        }
        return "device-ip";
    }

    private void copyAddress() {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ErikrafT Drop FTP", address.getText()));
        Toast.makeText(this, R.string.ftp_address_copied, Toast.LENGTH_SHORT).show();
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private LinearLayout.LayoutParams lpTop(int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = margin;
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(statusReceiver);
        super.onDestroy();
    }
}
