package com.erikraft.drop;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

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

/** Beta Android-only Onion Share mode. Files stay on-device; Tor exposes the local HTTP server. */
public class OnionTransferActivity extends AppCompatActivity {
    private final List<File> files = new ArrayList<>();
    private ActivityResultLauncher<Intent> picker;
    private OnionHttpServer server;
    private TextView status;
    private TextView address;
    private ImageView qr;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Transferência via Onion (Beta)");
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28, 28, 28, 28);
        TextView title = new TextView(this); title.setText("Transferência via Onion"); title.setTextSize(24); root.addView(title);
        TextView help = new TextView(this); help.setText("O Android inicia Tor, cria um Onion Service temporário e compartilha um endereço .onion. O conteúdo não passa pelo Render/Vercel."); root.addView(help);
        Button pick = new Button(this); pick.setText("Selecionar arquivos"); root.addView(pick);
        status = new TextView(this); status.setText("Nenhum arquivo selecionado."); root.addView(status);
        Button start = new Button(this); start.setText("Iniciar Onion Service"); root.addView(start);
        address = new TextView(this); address.setTextIsSelectable(true); address.setTextSize(16); root.addView(address);
        qr = new ImageView(this); root.addView(qr, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 420));
        Button copy = new Button(this); copy.setText("Copiar link"); root.addView(copy);
        Button share = new Button(this); share.setText("Compartilhar"); root.addView(share);
        setContentView(root);

        picker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
            files.clear();
            Intent data = result.getData();
            ClipData clip = data.getClipData();
            try {
                if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) files.add(copyToCache(clip.getItemAt(i).getUri()));
                else if (data.getData() != null) files.add(copyToCache(data.getData()));
                status.setText(files.size() + " arquivo(s) pronto(s) para compartilhar.");
            } catch (IOException e) { Toast.makeText(this, "Falha ao preparar arquivo: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
        });
        pick.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); i.addCategory(Intent.CATEGORY_OPENABLE); picker.launch(i); });
        start.setOnClickListener(v -> startOnion());
        copy.setOnClickListener(v -> { if (address.getText().length() > 0) { ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); cm.setPrimaryClip(ClipData.newPlainText("ErikrafT Drop Onion", address.getText())); Toast.makeText(this, "Link copiado.", Toast.LENGTH_SHORT).show(); } });
        share.setOnClickListener(v -> { if (address.getText().length() > 0) { Intent i = new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT, address.getText().toString()); startActivity(Intent.createChooser(i, "Compartilhar Onion Service")); } });
    }

    private File copyToCache(Uri uri) throws IOException {
        String name = "file-" + System.currentTimeMillis();
        android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
        if (c != null) { int n = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (c.moveToFirst() && n >= 0) name = c.getString(n); c.close(); }
        File out = new File(getCacheDir(), name.replaceAll("[^a-zA-Z0-9._-]", "_"));
        try (InputStream in = getContentResolver().openInputStream(uri); java.io.FileOutputStream fos = new java.io.FileOutputStream(out)) {
            if (in == null) throw new IOException("Arquivo não pôde ser aberto");
            byte[] buf = new byte[64 * 1024]; int len; while ((len = in.read(buf)) != -1) fos.write(buf, 0, len);
        }
        return out;
    }

    private void startOnion() {
        if (files.isEmpty()) { Toast.makeText(this, "Selecione pelo menos um arquivo.", Toast.LENGTH_LONG).show(); return; }
        try {
            if (server != null) server.stop();
            server = new OnionHttpServer(0, files); server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            status.setText("Servidor local iniciado na porta " + server.getListeningPort() + ". Iniciando Tor…");
            TorController.get(this).publishHiddenService(server.getListeningPort(), new TorController.OnionCallback() {
                @Override public void onReady(String onion) {
                    String link = "http://" + onion + "/"; address.setText(link); status.setText("Onion Service ativo. Compartilhe o link, QR Code ou código."); showQr(link);
                }
                @Override public void onError(Exception error) { status.setText("Falha ao iniciar Onion Service: " + error.getMessage()); }
            });
        } catch (IOException e) { status.setText("Falha no servidor local: " + e.getMessage()); }
    }

    private void showQr(String value) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 640, 640);
            Bitmap bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < 640; x++) for (int y = 0; y < 640; y++) bitmap.setPixel(x, y, matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            qr.setImageBitmap(bitmap);
        } catch (Exception e) { Toast.makeText(this, "Não foi possível gerar o QR Code.", Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onDestroy() { if (server != null) server.stop(); super.onDestroy(); }

    private static final class OnionHttpServer extends NanoHTTPD {
        private final List<File> files;
        OnionHttpServer(int port, List<File> files) { super(port); this.files = new ArrayList<>(files); }
        @Override public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            if ("/".equals(uri)) {
                StringBuilder html = new StringBuilder("<html><meta name='viewport' content='width=device-width'><body><h1>ErikrafT Drop™ Onion</h1><p>Arquivos disponíveis:</p><ul>");
                for (int i = 0; i < files.size(); i++) { File f = files.get(i); html.append("<li><a download href='/file/").append(i).append("'>").append(escape(f.getName())).append("</a> (").append(f.length()).append(" bytes)</li>"); }
                html.append("</ul></body></html>");
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html.toString());
            }
            if (uri.startsWith("/file/")) {
                try {
                    int index = Integer.parseInt(uri.substring(6)); File f = files.get(index);
                    return newChunkedResponse(Response.Status.OK, mime(f.getName()), new FileInputStream(f));
                } catch (Exception e) { return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "File not found"); }
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found");
        }
        private static String mime(String name) { String n = name.toLowerCase(); if (n.endsWith(".png")) return "image/png"; if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg"; if (n.endsWith(".pdf")) return "application/pdf"; return "application/octet-stream"; }
        private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    }
}
