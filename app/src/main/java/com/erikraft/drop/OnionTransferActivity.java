package com.erikraft.drop;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
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
public class OnionTransferActivity extends AppCompatActivity {
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
    private MaterialButton stopButton;
    private MaterialButton copyButton;
    private MaterialButton shareButton;
    private String textToShare = "";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable startupTimeout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SnapdropApplication.setAppTheme(this);
        buildUi();
        registerPicker();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(getString(R.string.onion_transfer_title));
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, getResources().getDimensionPixelSize(com.google.android.material.R.dimen.mtrl_toolbar_default_height)));
        setSupportActionBar(toolbar);

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

        MaterialCardView shareCard = card();
        LinearLayout shareContent = verticalInsideCard(shareCard);
        TextView shareTitle = text("Conteúdo para compartilhar", 18);
        shareContent.addView(shareTitle);

        MaterialButton pick = button(getString(R.string.onion_transfer_select_files));
        shareContent.addView(pick);
        selectionSummary = text("Nenhum arquivo selecionado.", 14);
        shareContent.addView(selectionSummary);

        TextView sendTextLabel = text(getString(R.string.onion_transfer_send_text), 14);
        shareContent.addView(sendTextLabel, lpTop(dp(14)));

        TextInputLayout textLayout = new TextInputLayout(this);
        textLayout.setHintEnabled(false);
        textInput = new TextInputEditText(this);
        textInput.setHint(getString(R.string.onion_transfer_text_hint));
        textInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        textInput.setMinLines(4);
        textInput.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        textLayout.addView(textInput, new LinearLayout.LayoutParams(-1, -2));
        shareContent.addView(textLayout);

        MaterialButton useText = button(getString(R.string.onion_transfer_send_text));
        useText.setOnClickListener(v -> {
            textToShare = textInput.getText() == null ? "" : textInput.getText().toString();
            updateSelectionSummary();
        });
        shareContent.addView(useText, lpTop(dp(8)));
        content.addView(shareCard);

        MaterialCardView statusCard = card();
        LinearLayout statusContent = verticalInsideCard(statusCard);
        status = text("Pronto para iniciar.", 15);
        statusContent.addView(status);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        statusContent.addView(progress, lpTop(dp(12)));
        content.addView(statusCard, lpTop(dp(12)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setWeightSum(2);
        startButton = button(getString(R.string.onion_transfer_start));
        stopButton = button(getString(R.string.onion_transfer_stop));
        stopButton.setEnabled(false);
        actions.addView(startButton, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(0, -2, 1);
        stopParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(stopButton, stopParams);
        content.addView(actions, lpTop(dp(14)));

        address = text("", 15);
        address.setTextIsSelectable(true);
        content.addView(address, lpTop(dp(14)));

        qr = new ImageView(this);
        qr.setAdjustViewBounds(true);
        qr.setContentDescription("QR Code do Onion Service");
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

        pick.setOnClickListener(v -> launchPicker());
        startButton.setOnClickListener(v -> startOnion());
        stopButton.setOnClickListener(v -> stopOnion());
        copyButton.setOnClickListener(v -> copyLink());
        shareButton.setOnClickListener(v -> shareLink());
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
                updateSelectionSummary();
            } catch (IOException e) {
                Toast.makeText(this, "Falha ao preparar arquivo: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
            if (in == null) throw new IOException("Arquivo não pôde ser aberto");
            byte[] buf = new byte[64 * 1024];
            int len;
            while ((len = in.read(buf)) != -1) fos.write(buf, 0, len);
        }
        return out;
    }

    private void updateSelectionSummary() {
        if (files.isEmpty() && textToShare.isEmpty()) selectionSummary.setText(getString(R.string.onion_transfer_no_files));
        else if (!files.isEmpty() && !textToShare.isEmpty()) selectionSummary.setText(files.size() + " arquivo(s) + texto pronto para compartilhar.");
        else if (!files.isEmpty()) selectionSummary.setText(files.size() + " arquivo(s) pronto(s) para compartilhar.");
        else selectionSummary.setText("Texto pronto para compartilhar.");
    }

    private void startOnion() {
        textToShare = textInput.getText() == null ? "" : textInput.getText().toString();
        if (files.isEmpty() && textToShare.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.onion_transfer_no_files), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            if (server != null) server.stop();
            server = new OnionHttpServer(0, files, textToShare);
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
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
                    Toast.makeText(this, "O Tor não respondeu a tempo. Verifique se o dispositivo permite executar o Tor.", Toast.LENGTH_LONG).show();
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
                }

                @Override public void onError(Exception error) {
                    if (startupTimeout != null) handler.removeCallbacks(startupTimeout);
                    status.setText(getString(R.string.onion_transfer_error) + " " + (error.getMessage() == null ? "" : error.getMessage()));
                    stopOnion(true);
                }
            });
        } catch (IOException e) {
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
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        copyButton.setEnabled(false);
        shareButton.setEnabled(false);
        progress.setVisibility(View.GONE);
        address.setText("");
        qr.setVisibility(View.GONE);
        if (!preserveStatus) status.setText("Serviço Onion parado.");
    }

    private void copyLink() {
        String value = address.getText().toString().trim();
        if (value.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("ErikrafT Drop™ Onion", value));
        Toast.makeText(this, "Link copiado.", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "Não foi possível gerar o QR Code.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onDestroy() {
        if (startupTimeout != null) handler.removeCallbacks(startupTimeout);
        if (server != null) server.stop();
        TorController.shutdown(this);
        super.onDestroy();
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

    private static final class OnionHttpServer extends NanoHTTPD {
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

        private static String mime(String name) {
            String n = name.toLowerCase();
            if (n.endsWith(".png")) return "image/png";
            if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
            if (n.endsWith(".pdf")) return "application/pdf";
            if (n.endsWith(".txt")) return "text/plain; charset=utf-8";
            return "application/octet-stream";
        }

        private static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }
    }
}