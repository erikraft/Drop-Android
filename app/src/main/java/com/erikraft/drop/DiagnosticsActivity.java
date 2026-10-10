package com.erikraft.drop;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.preference.PreferenceManager;

import com.erikraft.drop.utils.LogUtils;
import com.google.android.material.button.MaterialButton;

import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiagnosticsActivity extends DropPipActivity {
    private TextView logs;
    private String currentLogs = "";
    private ActivityResultLauncher<String> saver;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SnapdropApplication.setAppTheme(this);

        saver = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
            if (uri == null) return;
            final String content = currentLogs;
            executor.execute(() -> {
                try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new java.io.IOException("No output stream");
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                    mainHandler.post(() -> Toast.makeText(this, R.string.diagnostics_saved, Toast.LENGTH_SHORT).show());
                } catch (Exception e) {
                    mainHandler.post(() -> Toast.makeText(this, "Falha ao salvar: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
        });

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        Toolbar bar = new Toolbar(this);
        bar.setTitle(R.string.diagnostics_title);
        root.addView(bar, new LinearLayout.LayoutParams(-1, getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_toolbar_default_height)));
        setSupportActionBar(bar);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            actionBar.setHomeActionContentDescription(R.string.home_as_up_indicator_about);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        addActionButton(actions, iconButton(R.string.diagnostics_refresh, android.R.drawable.ic_popup_sync), v -> refreshLogs());
        addActionButton(actions, iconButton(R.string.diagnostics_copy, R.drawable.ic_content_copy), v -> copyLogs());
        addActionButton(actions, iconButton(R.string.diagnostics_save, android.R.drawable.ic_menu_save), v -> saver.launch("erikraft-drop-diagnostics.txt"));
        addActionButton(actions, iconButton(R.string.diagnostics_clear, android.R.drawable.ic_menu_delete), v -> clearLogs());
        addActionButton(actions, iconButton(R.string.diagnostics_share, android.R.drawable.ic_menu_share), v -> shareLogs());
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        logs = new TextView(this);
        logs.setTextIsSelectable(true);
        logs.setTypeface(android.graphics.Typeface.MONOSPACE);
        logs.setTextSize(12);
        int padding = dp(8);
        logs.setPadding(padding, padding, padding, padding);
        scroll.addView(logs);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        refreshLogs();
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    private MaterialButton button(int id) {
        MaterialButton b = new MaterialButton(this);
        b.setText(id);
        b.setMinHeight(dp(48));
        b.setMinWidth(0);
        b.setMaxLines(1);
        return b;
    }

    private MaterialButton iconButton(int label, int icon) {
        MaterialButton button = button(label);
        button.setText("");
        button.setContentDescription(getString(label));
        button.setIconResource(icon);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        return button;
    }

    private void addActionButton(LinearLayout parent, MaterialButton button, View.OnClickListener listener) {
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(dp(3), dp(2), dp(3), dp(2));
        parent.addView(button, params);
    }

    private void refreshLogs() {
        final boolean enabled = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.pref_diagnostics_enabled), false);
        if (!enabled) {
            currentLogs = getString(R.string.diagnostics_disabled);
            logs.setText(currentLogs);
            return;
        }

        logs.setText(getString(R.string.diagnostics_loading));
        executor.execute(() -> {
            String result = LogUtils.getLogs(PreferenceManager.getDefaultSharedPreferences(this), true);
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                currentLogs = result == null ? "" : result;
                logs.setText(currentLogs);
            });
        });
    }

    private void copyLogs() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("ErikrafT Drop diagnostics", currentLogs));
        Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareLogs() {
        try {
            File file = new File(getCacheDir(), "erikraft-drop-diagnostics.txt");
            try (FileOutputStream out = new FileOutputStream(file, false)) {
                out.write(currentLogs.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.diagnostics_title));
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.setClipData(android.content.ClipData.newRawUri("ErikrafT Drop diagnostics", uri));
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, getString(R.string.diagnostics_share)));
        } catch (Exception e) {
            Toast.makeText(this, "Falha ao compartilhar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void clearLogs() {
        currentLogs = LogUtils.getEmptyLogHeader();
        logs.setText(currentLogs);
        logs.scrollTo(0, 0);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        super.onDestroy();
    }
}
