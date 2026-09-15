package com.erikraft.drop;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiagnosticsActivity extends AppCompatActivity {
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
        bar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        bar.setNavigationContentDescription(R.string.home_as_up_indicator_about);
        bar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        root.addView(bar, new LinearLayout.LayoutParams(-1, getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_toolbar_default_height)));
        setSupportActionBar(bar);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        int margin = dp(4);
        addActionButton(actions, button(R.string.diagnostics_refresh), v -> refreshLogs());
        addActionButton(actions, button(R.string.diagnostics_copy), v -> copyLogs());
        addActionButton(actions, button(R.string.diagnostics_save), v -> saver.launch("erikraft-drop-diagnostics.txt"));
        addActionButton(actions, button(R.string.diagnostics_clear), v -> clearLogs());
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

    private MaterialButton button(int id) {
        MaterialButton b = new MaterialButton(this);
        b.setText(id);
        b.setMinHeight(dp(48));
        b.setMinWidth(0);
        b.setMaxLines(1);
        return b;
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

    private void clearLogs() {
        currentLogs = "";
        logs.setText("");
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
