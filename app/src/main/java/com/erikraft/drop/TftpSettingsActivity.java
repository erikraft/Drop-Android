package com.erikraft.drop;

import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;

import java.io.File;

public class TftpSettingsActivity extends DropPipActivity {
    private EditText portInput;
    private TextView status;
    private TextView address;
    private MaterialButton toggleButton;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!TftpServerService.EXTRA_STATUS.equals(intent.getAction())) return;
            boolean running = intent.getBooleanExtra("running", false);
            String error = intent.getStringExtra(TftpServerService.EXTRA_ERROR);
            status.setText(running ? getString(R.string.server_status_running)
                    : error == null ? getString(R.string.server_status_stopped)
                    : getString(R.string.server_status_error, error));
            updateUi(running);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SnapdropApplication.setAppTheme(this);
        buildUi();
        ContextCompat.registerReceiver(this, receiver,
                new IntentFilter(TftpServerService.EXTRA_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED);
        updateUi(TftpServerService.isRunning());
    }

    private void buildUi() {
        android.content.SharedPreferences prefs =
                PreferenceManager.getDefaultSharedPreferences(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Toolbar toolbar = new Toolbar(this);
        toolbar.setTitle(R.string.tftp_settings_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));
        setSupportActionBar(toolbar);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(28));

        TextView summary = text(getString(R.string.tftp_settings_summary), 15);
        content.addView(summary, top(4));
        TextView warning = text(getString(R.string.tftp_security_warning), 14);
        warning.setTextColor(0xffff9800);
        content.addView(warning, top(12));

        String configuredHome = prefs.getString(getString(R.string.pref_save_location), "");
        File home = configuredHome == null || configuredHome.trim().isEmpty()
                ? Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                : new File(configuredHome);
        TextView folder = text(getString(R.string.tftp_folder, home.getAbsolutePath()), 13);
        folder.setTextIsSelectable(true);
        content.addView(folder, top(14));

        TextView portLabel = text(getString(R.string.tftp_port_label), 14);
        content.addView(portLabel, top(18));
        portInput = new EditText(this);
        portInput.setSingleLine(true);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText(prefs.getString("tftp_port", "1069"));
        content.addView(portInput, top(4));

        status = text(getString(R.string.server_status_stopped), 15);
        content.addView(status, top(18));
        address = text("", 14);
        address.setTextIsSelectable(true);
        content.addView(address, top(4));

        toggleButton = button(getString(R.string.tftp_start));
        content.addView(toggleButton, top(14));
        MaterialButton copy = button(getString(R.string.tftp_copy_address));
        content.addView(copy, top(8));
        toggleButton.setOnClickListener(v -> {
            if (TftpServerService.isRunning()) {
                startService(TftpServerService.stopIntent(this));
            } else {
                String port = portInput.getText() == null ? "" : portInput.getText().toString().trim();
                try {
                    int value = Integer.parseInt(port);
                    if (value < 1025 || value > 65535) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, R.string.tftp_error_port, Toast.LENGTH_LONG).show();
                    return;
                }
                prefs.edit().putString("tftp_port", port).apply();
                ContextCompat.startForegroundService(this, TftpServerService.startIntent(this));
                status.setText(R.string.tftp_starting);
                toggleButton.setEnabled(false);
            }
        });
        copy.setOnClickListener(v -> {
            String value = address.getText().toString();
            if (value.isEmpty()) return;
            ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("TFTP", value));
            Toast.makeText(this, R.string.ftp_address_copied, Toast.LENGTH_SHORT).show();
        });

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void updateUi(boolean running) {
        toggleButton.setEnabled(true);
        toggleButton.setText(running ? R.string.tftp_stop : R.string.tftp_start);
        if (running) {
            int port = TftpServerService.getRunningPort();
            address.setText("tftp://" + com.erikraft.drop.utils.NetworkUtils.getIpAddress(this)
                    + ":" + port);
        } else {
            address.setText("");
        }
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private MaterialButton button(String value) {
        MaterialButton button = new MaterialButton(this);
        button.setText(value);
        return button;
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(margin);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
        super.onDestroy();
    }
}
