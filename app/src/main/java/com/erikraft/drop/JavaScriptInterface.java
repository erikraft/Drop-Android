package com.erikraft.drop;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;

import androidx.documentfile.provider.DocumentFile;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.anggrayudi.storage.FileWrapper;
import com.anggrayudi.storage.extension.IOUtils;
import com.anggrayudi.storage.extension.UriUtils;
import com.anggrayudi.storage.file.DocumentFileCompat;
import com.anggrayudi.storage.file.DocumentFileUtils;
import com.anggrayudi.storage.media.FileDescription;
import com.erikraft.drop.utils.ClipboardUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class JavaScriptInterface {
    private final MainActivity context;

    private OutputStream fileOutputStream;
    private FileHeader fileHeader;
    private java.security.MessageDigest messageDigest;
    private long totalBytesReceived = 0;
    private long expectedBytes = -1;
    private boolean transferActive = false;

    public JavaScriptInterface(final MainActivity context) {
        this.context = context;
    }

    @JavascriptInterface
    public synchronized void newFile(final String fileName, final String mimeType, final String fileSize) throws IOException {
        Log.i("DropAndroidJS", "Transfer Start: Receiving file. fileName=" + fileName + ", mimeType=" + mimeType + ", fileSize=" + fileSize);
        final String safeName = sanitizeDownloadName(fileName);
        String finalMime = TextUtils.isEmpty(mimeType) ? "application/octet-stream" : mimeType;
        if (finalMime.startsWith("base64:")) finalMime = finalMime.substring(7);

        IOUtils.closeStreamQuietly(fileOutputStream);
        fileOutputStream = null;
        fileHeader = null;
        totalBytesReceived = 0;
        expectedBytes = parseExpectedBytes(fileSize);
        transferActive = false;

        final FileWrapper fileWrapper = createFileWrapper(safeName, finalMime);
        if (fileWrapper == null) throw new IOException("Missing storage permissions");
        fileOutputStream = UriUtils.openOutputStream(fileWrapper.getUri(), context.getApplicationContext());
        if (fileOutputStream == null) throw new IOException("Cannot write target file");
        fileHeader = new FileHeader(safeName, finalMime, fileSize, fileWrapper);
        try {
            messageDigest = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            messageDigest = null;
        }
    }

    private long parseExpectedBytes(final String fileSize) {
        if (TextUtils.isEmpty(fileSize)) return -1;
        try {
            return Long.parseLong(fileSize.trim());
        } catch (NumberFormatException e) {
            Log.w("DropAndroidJS", "Unable to parse expected file size: " + fileSize);
            return -1;
        }
    }

    private FileWrapper createFileWrapper(final String fileName, final String mimeType) throws IOException {
        if (Build.VERSION.SDK_INT > 28) {
            final DocumentFile saveLocation = MainActivity.getSaveLocation();
            if (saveLocation != null) {
                final DocumentFile file = DocumentFileUtils.makeFile(saveLocation, context.getApplicationContext(), fileName, mimeType);
                if (file != null) return new FileWrapper.Document(file);
            }
            final FileDescription description = new FileDescription(fileName, "", mimeType);
            return DocumentFileCompat.createDownloadWithMediaStoreFallback(context.getApplicationContext(), description);
        }
        final String[] nameSplit = fileName.split("\\.");
        while (nameSplit[0].length() < 3) nameSplit[0] += nameSplit[0];
        final DocumentFile file = DocumentFile.fromFile(File.createTempFile(nameSplit[0], "." + nameSplit[nameSplit.length - 1], context.getCacheDir()));
        return new FileWrapper.Document(file);
    }

    @JavascriptInterface
    public void downloadBase64File(final String requestedName, final String requestedMime, final String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) return;
        final String name = sanitizeDownloadName(requestedName);
        final String mime = TextUtils.isEmpty(requestedMime) ? "application/octet-stream" : requestedMime;
        try {
            final byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
            final DocumentFile saveLocation = MainActivity.getSaveLocation();
            if (saveLocation == null) throw new IOException("Unable to access Android Downloads location");
            final DocumentFile target = DocumentFileUtils.makeFile(saveLocation, context.getApplicationContext(), name, mime);
            if (target == null) throw new IOException("Unable to create Android download file");
            final OutputStream outputStream = UriUtils.openOutputStream(target.getUri(), context.getApplicationContext());
            if (outputStream == null) throw new IOException("Unable to open Android download file");
            try {
                outputStream.write(bytes);
                outputStream.flush();
            } finally {
                IOUtils.closeStreamQuietly(outputStream);
            }
            context.runOnUiThread(() -> android.widget.Toast.makeText(context, "Baixado: " + name, android.widget.Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            Log.e("DropAndroidJS", "Web download failed: " + name, e);
            context.runOnUiThread(() -> android.widget.Toast.makeText(context, "Falha ao baixar " + name, android.widget.Toast.LENGTH_LONG).show());
        }
    }

    private String sanitizeDownloadName(final String requestedName) {
        String name = TextUtils.isEmpty(requestedName) ? "download" : requestedName;
        name = name.replace('\\', '_').replace('/', '_').replace('\n', '_').replace('\r', '_');
        return name.equals(".") || name.equals("..") ? "download" : name;
    }

    @JavascriptInterface
    public synchronized void onBytes(final String dec) throws IOException {
        if (fileOutputStream == null || fileHeader == null || !transferActive && totalBytesReceived > 0) return;
        try {
            final byte[] bytes = Base64.decode(dec, Base64.NO_WRAP);
            fileOutputStream.write(bytes);
            if (messageDigest != null) messageDigest.update(bytes);
            totalBytesReceived += bytes.length;
            transferActive = true;
        } catch (Exception e) {
            throw new IOException("Failed to process bytes", e);
        }
    }

    @JavascriptInterface
    public synchronized void saveDownloadFileName(final String name, final String size) throws IOException {
        if (fileOutputStream == null || fileHeader == null) return;
        final long completedExpectedBytes = parseExpectedBytes(size);
        final long expected = completedExpectedBytes >= 0 ? completedExpectedBytes : expectedBytes;
        final boolean sizeMatches = expected < 0 || expected == totalBytesReceived;

        String sha256Hex = "";
        if (messageDigest != null) {
            final byte[] hash = messageDigest.digest();
            final StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            sha256Hex = sb.toString();
        }

        try {
            fileOutputStream.flush();
            fileOutputStream.close();
        } finally {
            fileOutputStream = null;
            messageDigest = null;
            transferActive = false;
        }

        if (!sizeMatches) {
            Log.e("DropAndroidJS", "Transfer Failure: Size mismatch for " + name + " (Expected: " + expected + ", Received: " + totalBytesReceived + ", SHA-256: " + sha256Hex + ")");
            if (fileHeader.file.delete()) Log.w("DropAndroidJS", "Corrupted/incomplete file deleted: " + name);
            fileHeader = null;
            expectedBytes = -1;
            totalBytesReceived = 0;
            context.runOnUiThread(() -> android.widget.Toast.makeText(context, "Transferência incompleta: " + name, android.widget.Toast.LENGTH_LONG).show());
            return;
        }

        context.downloadFilesList.add(fileHeader);
        fileHeader = null;
        expectedBytes = -1;
        totalBytesReceived = 0;
    }

    public static String getSendTextDialogWithPreInsertedString(final String text) {
        return "javascript: try { document.getElementById(\"textInput\").innerHTML=\""
                + TextUtils.htmlEncode(text).replaceAll("\\n", "<br />") + "\";"
                + " Events.fire('activate-share-mode', {text: SnapdropAndroid.getTextFromUploadIntent()}); } catch (e) { console.error(e); }";
    }

    @JavascriptInterface
    public void copyToClipboard(final String text) {
        ClipboardUtils.copy(context, text);
    }

    /** Returns the current Android clipboard text to the WebView without leaving the WebView UI. */
    @JavascriptInterface
    public String getClipboardText() {
        final ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) return "";
        final ClipData data = clipboard.getPrimaryClip();
        if (data == null || data.getItemCount() == 0) return "";
        final CharSequence text = data.getItemAt(0).coerceToText(context);
        return text == null ? "" : text.toString();
    }

    /** Keeps the Android window awake for WebView features such as Animated QR. */
    @JavascriptInterface
    public void setKeepScreenOn(final boolean keepOn) {
        context.runOnUiThread(() -> {
            if (keepOn) context.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else if (!context.transfer.get()) context.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    /** Disables the native pull-to-refresh container while an in-WebView overlay is being scrolled. */
    @JavascriptInterface
    public void setDialogVisible(final boolean visible) {
        context.setDialogVisible(visible);
        context.runOnUiThread(() -> setSwipeRefreshEnabled(!visible));
    }

    private void setSwipeRefreshEnabled(final boolean enabled) {
        final View root = context.getWindow().getDecorView();
        final ListHolder holder = new ListHolder();
        findSwipeRefreshLayouts(root, holder);
        for (SwipeRefreshLayout swipe : holder.items) {
            swipe.setRefreshing(false);
            swipe.setEnabled(enabled);
        }
    }

    private void findSwipeRefreshLayouts(final View view, final ListHolder holder) {
        if (view instanceof SwipeRefreshLayout) holder.items.add((SwipeRefreshLayout) view);
        if (view instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) findSwipeRefreshLayouts(group.getChildAt(i), holder);
        }
    }

    private static final class ListHolder {
        final java.util.ArrayList<SwipeRefreshLayout> items = new java.util.ArrayList<>();
    }

    @JavascriptInterface
    public String getYouAreKnownAsTranslationString(final String displayName) {
        return context.getString(R.string.website_footer_known_as, displayName);
    }

    @JavascriptInterface
    public int getVersionId() { return BuildConfig.VERSION_CODE; }

    @JavascriptInterface
    public String getTextFromUploadIntent() { return context.getTextFromUploadIntent(); }

    @JavascriptInterface
    public boolean shouldOpenSendTextDialog() { return context.onlyText; }

    @JavascriptInterface
    public void dialogShown() { setDialogVisible(true); }

    @JavascriptInterface
    public void dialogHidden() { setDialogVisible(false); }

    @JavascriptInterface
    public synchronized void ignoreClickedListener() {
        IOUtils.closeStreamQuietly(fileOutputStream);
        fileOutputStream = null;
        messageDigest = null;
        transferActive = false;
        expectedBytes = -1;
        totalBytesReceived = 0;
        if (fileHeader != null && fileHeader.file.delete()) Log.d("ignoreClickListener", "File was deleted from SAF database");
        fileHeader = null;
    }

    @JavascriptInterface
    public void setProgress(final float progress) {
        Log.d("DropAndroidJS", "Transfer Progress: " + progress);
        if (progress > 0) context.transfer.set(true);
        else {
            context.transfer.set(false);
            context.forceRefresh = false;
        }
    }

    @JavascriptInterface
    public void vibrate() {
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
            final Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                else vibrator.vibrate(500);
            }
        }
    }

    @JavascriptInterface
    public void resetUploadIntent() { context.resetUploadIntent(); }

    public static class FileHeader {
        private final String name;
        private final String mime;
        private final String size;
        private final FileWrapper file;

        public FileHeader(final String name, final String mime, final String size, final FileWrapper file) {
            this.name = name;
            this.mime = mime;
            this.size = size;
            this.file = file;
        }

        public String getName() { return name; }
        public String getMime() { return mime; }
        public String getSize() { return size; }
        public Uri getFileUri() { return file.getUri(); }

        @Override
        public String toString() {
            return "FileHeader{" + "name='" + name + '\'' + ", mime='" + mime + '\'' + ", size='" + size + '\'' + '}';
        }
    }

    public static String getAssetsJS(final Context context, final String fileName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open(fileName), StandardCharsets.UTF_8))) {
            final StringBuilder text = new StringBuilder("javascript:");
            String currentLine;
            while ((currentLine = reader.readLine()) != null) if (!currentLine.trim().startsWith("//")) text.append(currentLine);
            return text.toString();
        } catch (IOException e) {
            Log.e("JavaScriptInterface", "unable to read assets file '" + fileName + "'", e);
        }
        return null;
    }
}
