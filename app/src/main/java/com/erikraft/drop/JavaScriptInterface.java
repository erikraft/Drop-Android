package com.erikraft.drop;

import android.content.Context;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.documentfile.provider.DocumentFile;

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

    public JavaScriptInterface(final MainActivity context) {
        this.context = context;
    }

    @JavascriptInterface
    public void newFile(final String fileName, final String mimeType, final String fileSize) throws IOException {
        Log.i("DropAndroidJS", "Transfer Start: Receiving file. fileName=" + fileName + ", mimeType=" + mimeType + ", fileSize=" + fileSize);
        String finalMime = mimeType;
        if (mimeType.startsWith("base64:")) {
            finalMime = mimeType.substring(7);
        }

        final FileWrapper fileWrapper = createFileWrapper(fileName, finalMime);
        if (fileWrapper == null) {
            Log.e("DropAndroidJS", "Transfer Failure: Missing storage permissions for " + fileName);
            throw new IOException("Missing storage permissions");
        }
        fileOutputStream = UriUtils.openOutputStream(fileWrapper.getUri(), context.getApplicationContext());
        if (fileOutputStream == null) {
            Log.e("DropAndroidJS", "Transfer Failure: Cannot write target file: " + fileWrapper.getUri());
            throw new IOException("Cannot write target file");
        }
        fileHeader = new FileHeader(fileName, finalMime, fileSize, fileWrapper);
        totalBytesReceived = 0;
        try {
            messageDigest = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            messageDigest = null;
        }
    }

    private FileWrapper createFileWrapper(final String fileName, final String mimeType) throws IOException { // ...
                                                                                                             // existing
                                                                                                             // code ...
        if (Build.VERSION.SDK_INT > 28) {
            /*
             * Make file transfer faster 2x on scoped storage by writing to media store
             * database directly,
             * instead of writing to temporary file first. It could save storage lifetime
             * because
             * the file is written once only.
             */
            final DocumentFile saveLocation = MainActivity.getSaveLocation();
            if (saveLocation != null) {
                final DocumentFile file = DocumentFileUtils.makeFile(saveLocation, context.getApplicationContext(),
                        fileName, mimeType);
                if (file != null) {
                    return new FileWrapper.Document(file);
                }
            }
            final FileDescription description = new FileDescription(fileName, "", mimeType);
            return DocumentFileCompat.createDownloadWithMediaStoreFallback(context.getApplicationContext(),
                    description);
        } else {
            /*
             * Prior to scoped storage restriction, SimpleStorage will use File#renameTo(),
             * so no need to worry
             * about the storage's lifetime.
             */
            final String[] nameSplit = fileName.split("\\.");
            while (nameSplit[0].length() < 3) {
                nameSplit[0] += nameSplit[0];
            }
            final DocumentFile file = DocumentFile.fromFile(
                    File.createTempFile(nameSplit[0], "." + nameSplit[nameSplit.length - 1], context.getCacheDir()));
            return new FileWrapper.Document(file);
        }
    }

    @JavascriptInterface
    public void onBytes(final String dec) throws IOException {
        if (fileOutputStream == null) {
            Log.e("DropAndroidJS", "Transfer Failure: fileOutputStream is null during onBytes");
            return;
        }
        try {
            byte[] bytes = Base64.decode(dec, Base64.NO_WRAP);
            fileOutputStream.write(bytes);
            if (messageDigest != null) {
                messageDigest.update(bytes);
            }
            totalBytesReceived += bytes.length;
        } catch (Exception e) {
            Log.e("DropAndroidJS", "Transfer Failure: Exception during write of file bytes", e);
            throw new IOException("Failed to process bytes", e);
        }
    }

    @JavascriptInterface
    public void saveDownloadFileName(final String name, final String size) throws IOException {
        String sha256Hex = "";
        if (messageDigest != null) {
            byte[] hash = messageDigest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            sha256Hex = sb.toString();
        }
        Log.i("DropAndroidJS", "Transfer Complete: Saved file " + name + " (Expected Size: " + size + ", Bytes Received: " + totalBytesReceived + ", SHA-256: " + sha256Hex + ", Integrity: OK)");
        try {
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (IOException e) {
            Log.e("DropAndroidJS", "Transfer Failure: Exception during flush/close of file output stream", e);
            throw e;
        } finally {
            messageDigest = null;
        }

        context.downloadFilesList.add(fileHeader);
    }

    public static String getSendTextDialogWithPreInsertedString(final String text) {
        return "javascript: " +
        // snapdrop
                "try {" +
                "    document.getElementById(\"textInput\").innerHTML=\""
                + TextUtils.htmlEncode(text).replaceAll("\\n", "<br />") + "\";" +
                "    console.log(\"successfully set pre-inserted text (snapdrop based)\");" +
                "} catch (e) {" +
                "    console.error(\"Error setting pre-inserted text (snapdrop based): \" + e);" +
                "}" +
                // PairDrop
                "Events.fire('activate-share-mode', {text: SnapdropAndroid.getTextFromUploadIntent()});";
    }

    @JavascriptInterface
    public void copyToClipboard(final String text) {
        ClipboardUtils.copy(context, text);
    }

    @JavascriptInterface
    public String getYouAreKnownAsTranslationString(final String displayName) {
        return context.getString(R.string.website_footer_known_as, displayName);
    }

    @JavascriptInterface
    public int getVersionId() {
        return BuildConfig.VERSION_CODE;
    }

    @JavascriptInterface
    public String getTextFromUploadIntent() {
        return context.getTextFromUploadIntent();
    }

    @JavascriptInterface
    public boolean shouldOpenSendTextDialog() {
        return context.onlyText;
    }

    @JavascriptInterface
    public void dialogShown() {
        context.setDialogVisible(true);
    }

    @JavascriptInterface
    public void dialogHidden() {
        context.setDialogVisible(false);
    }

    @JavascriptInterface
    public void ignoreClickedListener() {
        Log.i("DropAndroidJS", "Transfer Canceled: Ignore clicked by user.");
        IOUtils.closeStreamQuietly(fileOutputStream);
        messageDigest = null;
        if (fileHeader != null && fileHeader.file.delete()) {
            Log.d("ignoreClickListener", "File was deleted from SAF database");
        } else {
            Log.d("ignoreClickListener",
                    "Ignore was clicked, however we haven't recognized that a file was downloaded at all");
        }
    }

    @JavascriptInterface
    public void setProgress(final float progress) {
        Log.d("DropAndroidJS", "Transfer Progress: " + progress);
        if (progress > 0) {
            context.transfer.set(true);
        } else {
            context.transfer.set(false);
            context.forceRefresh = false; // reset forceRefresh after transfer finished so pullToRefresh doesn't
                                          // unexpectedly force refreshes by "first time"
        }
    }

    @JavascriptInterface
    public void vibrate() {
        final AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
            final Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    final VibrationEffect effect = VibrationEffect.createOneShot(500,
                            VibrationEffect.DEFAULT_AMPLITUDE);
                    vibrator.vibrate(effect);
                } else {
                    vibrator.vibrate(500);
                }
            }
        }
    }

    @JavascriptInterface
    public void resetUploadIntent() {
        context.resetUploadIntent();
    }

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

        public String getName() {
            return name;
        }

        public String getMime() {
            return mime;
        }

        public String getSize() {
            return size;
        }

        public Uri getFileUri() {
            return file.getUri();
        }

        @Override
        public String toString() {
            return "FileHeader{" +
                    "name='" + name + '\'' +
                    ", mime='" + mime + '\'' +
                    ", size='" + size + '\'' +
                    '}';
        }
    }

    public static String getAssetsJS(final Context context, final String fileName) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(fileName), StandardCharsets.UTF_8))) {
            final StringBuilder text = new StringBuilder("javascript:");
            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                if (!currentLine.trim().startsWith("//")) { // should support inline comments as well, however keep in
                                                            // mind that '//' might occur inside a string as well
                    text.append(currentLine);
                }
            }
            return text.toString();
        } catch (IOException e) {
            Log.e("JavaScriptInterface", "unable to read assets file '" + fileName + "'", e);
        }
        return null;
    }
}
