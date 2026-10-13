package com.erikraft.drop;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.appcompat.app.ActionBar;

import com.google.android.material.appbar.MaterialToolbar;
import androidx.core.app.NotificationCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import fi.iki.elonen.NanoHTTPD;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Android-only Onion Service sharing mode. Files and text are served from the device through Tor. */
public class OnionTransferActivity extends DropPipActivity {
    private final List<File> files = new ArrayList<>();
    private ActivityResultLauncher<Intent> picker;
    private OnionHttpServer server;
    private TextView status;
    private TextView address;
    private TextView selectionSummary;
    private ProgressBar progress;
    private ImageView qr;
    private TextInputEditText textInput;
    private MaterialButton startButton;
    private MaterialButton textToggleButton;
    private TextView fileToggle;
    private MaterialButton copyButton;
    private MaterialButton shareButton;
    private androidx.appcompat.widget.SwitchCompat stopNotificationSwitch;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private String textToShare = "";
    private boolean textIncluded;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable startupTimeout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SnapdropApplication.setAppTheme(this);
        notificationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (stopNotificationSwitch != null) {
                stopNotificationSwitch.setChecked(granted);
                PreferenceManager.getDefaultSharedPreferences(this)
                        .edit().putBoolean(getString(R.string.pref_onion_stop_notification), granted).apply();
                if (granted && server != null) showNotification();
            }
        });
        buildUi();
        registerPicker();
        handleNotificationIntent(getIntent());
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getString(R.string.onion_transfer_title));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_toolbar_default_height)));
        setSupportActionBar(toolbar);

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            actionBar.setHomeActionContentDescription(R.string.home_as_up_indicator_about);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        content.setPadding(pad, dp(12), pad, dp(28));

        TextView title = text(getString(R.string.onion_transfer_title), 26);
        content.addView(title);
        TextView description = text(getString(R.string.onion_transfer_description), 15);
        description.setPadding(0, dp(8), 0, dp(16));
        content.addView(description);
        stopNotificationSwitch = new androidx.appcompat.widget.SwitchCompat(this);
        stopNotificationSwitch.setText(R.string.onion_stop_notification_title);
        stopNotificationSwitch.setChecked(PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.pref_onion_stop_notification), false));
        content.addView(stopNotificationSwitch, lpTop(dp(8)));
        content.addView(text(getString(R.string.onion_stop_notification_summary), 13), lpTop(dp(2)));
        stopNotificationSwitch.setOnClickListener(v -> handleStopNotificationPreference());

        MaterialCardView shareCard = card();
        LinearLayout shareContent = verticalInsideCard(shareCard);
        TextView shareTitle = text(getString(R.string.onion_transfer_content_title), 18);
        shareContent.addView(shareTitle);

        fileToggle = text(getString(R.string.onion_transfer_select_files), 15);
        fileToggle.setGravity(android.view.Gravity.CENTER);
        fileToggle.setMinHeight(dp(52));
        fileToggle.setPadding(dp(12), dp(10), dp(12), dp(10));
        fileToggle.setFocusable(true);
        fileToggle.setClickable(true);
        fileToggle.setBackground(createDashedSelectorBackground());
        fileToggle.setOnClickListener(v -> {
            if (files.isEmpty()) {
                launchPicker();
            } else {
                files.clear();
                updateSelectionSummary();
                updateActionLabels();
            }
        });
        shareContent.addView(fileToggle, lpTop(dp(10)));

        selectionSummary = text(getString(R.string.onion_transfer_no_files), 14);
        shareContent.addView(selectionSummary, lpTop(dp(6)));

        TextInputLayout textLayout = new TextInputLayout(this);
        textLayout.setHintEnabled(false);
        textInput = new TextInputEditText(this);
        textInput.setHint(getString(R.string.onion_transfer_text_hint));
        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        textInput.setMinLines(4);
        textInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        textInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (textIncluded) {
                    textToShare = value.toString();
                    updateSelectionSummary();
                }
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        textLayout.addView(textInput, new LinearLayout.LayoutParams(-1, -2));
        shareContent.addView(textLayout, lpTop(dp(14)));

        TextView textPreview = text("", 15);
        textPreview.setTextIsSelectable(true);
        textPreview.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        textPreview.setPadding(dp(12), dp(12), dp(12), dp(12));
        textPreview.setMinHeight(dp(120));
        textPreview.setBackground(new DiagonalPreviewDrawable(dp(10), getResources().getDisplayMetrics().density));
        textPreview.setVisibility(View.GONE);
        shareContent.addView(textPreview, lpTop(dp(14)));

        textToggleButton = button(getString(R.string.onion_transfer_send_text));
        textToggleButton.setOnClickListener(v -> {
            if (textIncluded) {
                textIncluded = false;
                textToShare = "";
                textInput.setText("");
                textInput.setError(null);
                textPreview.setText("");
                textPreview.setVisibility(View.GONE);
                textLayout.setVisibility(View.VISIBLE);
            } else {
                String enteredText = textInput.getText() == null ? "" : textInput.getText().toString();
                if (enteredText.trim().isEmpty()) {
                    textInput.setError(getString(R.string.onion_transfer_text_hint));
                    return;
                }
                textIncluded = true;
                textToShare = enteredText;
                textInput.setError(null);
                textPreview.setText(enteredText);
                textLayout.setVisibility(View.GONE);
                textPreview.setVisibility(View.VISIBLE);
            }
            updateActionLabels();
            updateSelectionSummary();
        });
        shareContent.addView(textToggleButton, lpTop(dp(8)));
        content.addView(shareCard);

        MaterialCardView statusCard = card();
        LinearLayout statusContent = verticalInsideCard(statusCard);
        status = text(getString(R.string.onion_transfer_ready_to_start), 15);
        statusContent.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        statusContent.addView(progress, lpTop(dp(12)));
        content.addView(statusCard, lpTop(dp(12)));

        startButton = button(getString(R.string.onion_transfer_start));
        content.addView(startButton, lpTop(dp(14)));

        address = text("", 15);
        address.setTextIsSelectable(true);
        content.addView(address, lpTop(dp(14)));

        qr = new ImageView(this);
        qr.setAdjustViewBounds(true);
        qr.setContentDescription(getString(R.string.onion_transfer_qr_content_description));
        qr.setVisibility(View.GONE);
        content.addView(qr, lpTop(dp(12)));

        LinearLayout linkActions = new LinearLayout(this);
        linkActions.setGravity(android.view.Gravity.CENTER);
        copyButton = button(getString(R.string.onion_transfer_copy));
        shareButton = button(getString(R.string.onion_transfer_share));
        copyButton.setEnabled(false);
        shareButton.setEnabled(false);
        linkActions.addView(copyButton);
        linkActions.addView(shareButton, lpLeft(dp(8)));
        content.addView(linkActions, lpTop(dp(10)));

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        startButton.setOnClickListener(v -> {
            if (server != null) stopOnion();
            else startOnion();
        });
        updateActionLabels();
        copyButton.setOnClickListener(v -> copyLink());
        shareButton.setOnClickListener(v -> shareLink());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    private void handleNotificationIntent(Intent intent) {
        if (intent != null && ACTION_STOP_NOTIFICATION.equals(intent.getAction())) {
            stopOnion(false);
            finish();
        }
    }

    private void handleStopNotificationPreference() {
        if (!stopNotificationSwitch.isChecked()) {
            PreferenceManager.getDefaultSharedPreferences(this)
                    .edit().putBoolean(getString(R.string.pref_onion_stop_notification), false).apply();
            cancelNotification();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putBoolean(getString(R.string.pref_onion_stop_notification), true).apply();
        if (server != null) showNotification();
    }

    private void showNotification() {
        if (server == null || !PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.pref_onion_stop_notification), false)) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, getString(R.string.onion_transfer_title), NotificationManager.IMPORTANCE_LOW));
        }
        Intent stopIntent = new Intent(this, OnionTransferActivity.class)
                .setAction(ACTION_STOP_NOTIFICATION)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_onion)
                .setContentTitle(getString(R.string.onion_transfer_title))
                .setContentText(getString(R.string.onion_stop_notification_summary))
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(new NotificationCompat.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        getString(R.string.onion_transfer_stop),
                        pendingIntent).build());
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void cancelNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    private void registerPicker() {
        picker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            files.clear();
            Intent data = result.getData();
            ClipData clip = data.getClipData();
            try {
                if (clip != null) {
                    for (int i = 0; i < clip.getItemCount(); i++) files.add(copyToCache(clip.getItemAt(i).getUri()));
                } else if (data.getData() != null) {
                    files.add(copyToCache(data.getData()));
                }
            } catch (IOException e) {
                Toast.makeText(this, getString(R.string.onion_transfer_prepare_file_error, e.getMessage()), Toast.LENGTH_LONG).show();
            } finally {
                updateSelectionSummary();
                updateActionLabels();
            }
        });
    }

    private void launchPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        picker.launch(i);
    }

    private File copyToCache(Uri uri) throws IOException {
        String name = "file-" + System.currentTimeMillis();
        android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null) {
            int n = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
            if (c.moveToFirst() && n >= 0) name = c.getString(n);
            c.close();
        }
        File out = new File(getCacheDir(), name.replaceAll("[^a-zA-Z0-9._-]", "_"));
        try (InputStream in = getContentResolver().openInputStream(uri); java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            if (in == null) throw new IOException(getString(R.string.onion_error_file_open));
            byte[] buf = new byte[64 * 1024];
            int len;
            while ((len = in.read(buf)) != -1) fos.write(buf, 0, len);
        }
        return out;
    }

    private void updateActionLabels() {
        if (fileToggle != null) {
            fileToggle.setBackground(createDashedSelectorBackground());
            fileToggle.setText(files.isEmpty()
                    ? getString(R.string.onion_transfer_select_files)
                    : getString(R.string.onion_transfer_clear_files));
        }
        if (textToggleButton != null) {
            textToggleButton.setText(textIncluded
                    ? getString(R.string.onion_transfer_clear_text)
                    : getString(R.string.onion_transfer_send_text));
        }
        if (startButton != null) {
            startButton.setText(server != null
                    ? getString(R.string.onion_transfer_stop)
                    : getString(R.string.onion_transfer_start));
        }
    }

    private GradientDrawable createDashedSelectorBackground() {
        // Fixed brand colors keep the selector consistent across light and dark themes.
        int strokeColor = files.isEmpty()
                ? android.graphics.Color.parseColor("#4285F4")
                : android.graphics.Color.parseColor("#FF2200");
        GradientDrawable background = new GradientDrawable();
        background.setColor(android.graphics.Color.TRANSPARENT);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), strokeColor, dp(5), dp(4));
        fileToggle.setTextColor(strokeColor);
        return background;
    }

    private void updateSelectionSummary() {
        if (files.isEmpty() && textToShare.isEmpty()) {
            selectionSummary.setText(getString(R.string.onion_transfer_no_files));
            return;
        }

        String summary;
        if (!files.isEmpty() && !textToShare.isEmpty()) {
            summary = getString(R.string.onion_transfer_summary_files_and_text, files.size());
        } else if (!files.isEmpty()) {
            summary = getString(R.string.onion_transfer_summary_files, files.size());
        } else {
            summary = getString(R.string.onion_transfer_summary_text);
        }

        if (!files.isEmpty()) {
            StringBuilder fileNames = new StringBuilder(summary);
            for (File file : files) {
                fileNames.append("\n").append(file.getName());
            }
            selectionSummary.setText(fileNames);
        } else {
            selectionSummary.setText(summary);
        }
    }

    private void startOnion() {
        textToShare = textIncluded && textInput.getText() != null
                ? textInput.getText().toString() : "";
        if (files.isEmpty() && textToShare.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.onion_transfer_no_files), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            if (server != null) server.stop();
            server = new OnionHttpServer(0, files, textToShare);
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            updateActionLabels();
            copyButton.setEnabled(false);
            shareButton.setEnabled(false);
            address.setText("");
            qr.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(0);
            status.setText(getString(R.string.onion_transfer_server_ready));

            startupTimeout = () -> {
                if (server != null && address.getText().length() == 0) {
                    status.setText(getString(R.string.onion_transfer_error));
                    Toast.makeText(this, getString(R.string.onion_error_tor_timeout), Toast.LENGTH_LONG).show();
                    stopOnion(true);
                }
            };
            handler.postDelayed(startupTimeout, 120000);

            TorController.get(this).publishHiddenService(server.getListeningPort(), new TorController.OnionCallback() {
                @Override public void onProgress(int percentage) {
                    progress.setVisibility(View.VISIBLE);
                    progress.setProgress(percentage);
                    status.setText(getString(R.string.onion_transfer_progress, percentage));
                }

                @Override public void onReady(String onion) {
                    if (startupTimeout != null) handler.removeCallbacks(startupTimeout);
                    String link = "http://" + onion + "/";
                    address.setText(link);
                    progress.setProgress(100);
                    status.setText(getString(R.string.onion_transfer_ready));
                    copyButton.setEnabled(true);
                    shareButton.setEnabled(true);
                    showQr(link);
                    showNotification();
                }

                @Override public void onError(Exception error) {
                    if (startupTimeout != null) handler.removeCallbacks(startupTimeout);
                    status.setText(getString(R.string.onion_transfer_error) + " " + (error.getMessage() == null ? "" : error.getMessage()));
                    stopOnion(true);
                }
            });
        } catch (IOException e) {
            server = null;
            updateActionLabels();
            status.setText(getString(R.string.onion_transfer_error) + " " + e.getMessage());
        }
    }

    private void stopOnion() {
        stopOnion(false);
    }

    private void stopOnion(boolean preserveStatus) {
        if (startupTimeout != null) handler.removeCallbacks(startupTimeout);
        if (server != null) {
            server.stop();
            server = null;
        }
        TorController.shutdown(this);
        updateActionLabels();
        copyButton.setEnabled(false);
        shareButton.setEnabled(false);
        progress.setVisibility(View.GONE);
        address.setText("");
        qr.setVisibility(View.GONE);
        cancelNotification();
        if (!preserveStatus) status.setText(getString(R.string.server_status_stopped));
    }

    private void copyLink() {
        String value = address.getText().toString().trim();
        if (value.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("ErikrafT Drop™ Onion", value));
        Toast.makeText(this, R.string.onion_transfer_link_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareLink() {
        String value = address.getText().toString().trim();
        if (value.isEmpty()) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, value);
        startActivity(Intent.createChooser(i, getString(R.string.onion_transfer_share)));
    }

    private void showQr(String value) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 640, 640);
            Bitmap bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < 640; x++) for (int y = 0; y < 640; y++) bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            qr.setImageBitmap(bitmap);
            qr.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.onion_error_qr), Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onDestroy() {
        if (startupTimeout != null) handler.removeCallbacks(startupTimeout);
        cancelNotification();
        if (server != null) server.stop();
        TorController.shutdown(this);
        super.onDestroy();
    }

    private static final class DiagonalPreviewDrawable extends Drawable {
        private final float cornerRadius;
        private final float density;
        private final Paint stripePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        DiagonalPreviewDrawable(float cornerRadius, float density) {
            this.cornerRadius = cornerRadius;
            this.density = density;
            stripePaint.setColor(0x26888888);
            stripePaint.setStyle(Paint.Style.STROKE);
            stripePaint.setStrokeWidth(Math.max(1f, density));
            borderPaint.setColor(0xFF888888);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(Math.max(1f, density));
        }

        @Override
        public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            Path clip = new Path();
            clip.addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clip);
            float spacing = 12f * density;
            for (float x = bounds.left - bounds.height(); x < bounds.right + bounds.height(); x += spacing) {
                canvas.drawLine(x, bounds.bottom, x + bounds.height(), bounds.top, stripePaint);
            }
            canvas.restoreToCount(save);
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, borderPaint);
        }

        @Override public void setAlpha(int alpha) {
            stripePaint.setAlpha(Math.round(0x26 * alpha / 255f));
            borderPaint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            stripePaint.setColorFilter(colorFilter);
            borderPaint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18));
        card.setCardElevation(dp(2));
        return card;
    }

    private LinearLayout verticalInsideCard(MaterialCardView card) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(content);
        return content;
    }

    private TextView text(String value, float size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        return view;
    }

    private MaterialButton button(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        return button;
    }

    private LinearLayout.LayoutParams lpTop(int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = margin;
        return p;
    }

    private LinearLayout.LayoutParams lpLeft(int margin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.leftMargin = margin;
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static final String ACTION_STOP_NOTIFICATION = "com.erikraft.drop.action.STOP_ONION_NOTIFICATION";
    private static final int NOTIFICATION_ID = 42022;
    private static final String CHANNEL_ID = "onion_transfer";
 class OnionHttpServer extends NanoHTTPD {
        private final List<File> files;
        private final String text;

        OnionHttpServer(int port, List<File> files, String text) {
            super(port);
            this.files = new ArrayList<>(files);
            this.text = text == null ? "" : text;
        }

        @Override public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if ("/".equals(uri)) {
                StringBuilder html = new StringBuilder("<!doctype html><html><meta name='viewport' content='width=device-width,initial-scale=1'><meta charset='utf-8'><title>ErikrafT Drop™ Onion</title><style>body{font-family:sans-serif;max-width:760px;margin:40px auto;padding:0 18px;line-height:1.5}a{display:block;margin:10px 0}pre{white-space:pre-wrap;background:#f4f4f4;padding:14px;border-radius:12px}</style><h1>ErikrafT Drop™</h1>");
                if (!text.isEmpty()) html.append("<h2>Texto</h2><pre>").append(escape(text)).append("</pre>");
                html.append("<h2>Arquivos</h2><ul>");
                if (files.isEmpty()) html.append("<li>Nenhum arquivo.</li>");
                for (int i = 0; i < files.size(); i++) {
                    File f = files.get(i);
                    html.append("<li><a download href='/file/").append(i).append("'>").append(escape(f.getName())).append("</a> (").append(f.length()).append(" bytes)</li>");
                }
                html.append("</ul></html>");
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html.toString());
            }
            if (uri.startsWith("/file/")) {
                try {
                    int index = Integer.parseInt(uri.substring(6));
                    File f = files.get(index);
                    return newChunkedResponse(Response.Status.OK, mime(f.getName()), new FileInputStream(f));
                } catch (Exception e) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found");
                }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
        }

        private String mime(String name) {
            String n = name.toLowerCase();
            if (n.endsWith(".png")) return "image/png";
            if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
            if (n.endsWith(".pdf")) return "application/pdf";
            if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
            return "application/octet-stream";
        }

        private String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }
    }
}