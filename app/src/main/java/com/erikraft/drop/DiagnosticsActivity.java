package com.erikraft.drop;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;

import java.nio.charset.StandardCharsets;

public class DiagnosticsActivity extends AppCompatActivity {
    private TextView logsView;
    private ActivityResultLauncher<String> createDocumentLauncher;
    private String currentLogs = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SnapdropApplication.setAppTheme(this);

        createDocumentLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/plain"), uri -> {
            if (uri == null) return;
            try (java.io.OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new java.io.IOException("Output stream unavailable");
                out.write(currentLogs.getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, R.string.diagnostics_saved, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Falha ao salvar diagnósticos: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        buildUi();
        refreshLogs();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        Toolbar toolbar = new Toolbar(this);
        toolbar.setTitle(R.string.diagnostics_title);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_toolbar_default_height)));
        setSupportActionBar(toolbar);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(12), dp(8), dp(12), dp(8));
        actions.setGravity(android.view.Gravity.CENTER_VERTICAL);

        MaterialButton refresh = button(R.string.diagnostics_refresh);
        MaterialButton copy = button(R.string.diagnostics_copy);
        MaterialButton save = button(R.string.diagnostics_save);
        actions.addView(refresh);
        actions.addView(copy, marginLeft(dp(8)));
        actions.addView(save, marginLeft(dp(8)));
        root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        logsView = new TextView(this);
        logsView.setTextIsSelectable(true);
        logsView.setTextSize(12);
        logsView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logsView.setPadding(dp(12), dp(12), dp(12), dp(24));
        scroll.addView(logsView);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        refresh.setOnClickListener(v -> refreshLogs());
        copy.setOnClickListener(v -> copyLogs());
        save.setOnClickListener(v -> createDocumentLauncher.launch("erikraft-drop-diagnostics.txt"));
    }

    private void refreshLogs() {
        boolean enabled = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.pref_diagnostics_enabled), false);
        if (!enabled) {
            currentLogs = getString(R.string.diagnostics_disabled);
            logsView.setText(currentLogs);
            return;
        }
        currentLogs = com.erikraft.drop.utils.LogUtils.getLogs(PreferenceManager.getDefaultSharedPreferences(this), true);
        logsView.setText(currentLogs);
    }

    private void copyLogs() {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("ErikrafT Drop™ diagnostics", currentLogs));
        Toast.makeText(this, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show();
    }

    private MaterialButton button(int label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        return button;
    }

    private LinearLayout.LayoutParams marginLeft(int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.leftMargin = margin;
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
