package com.erikraft.drop.qr;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.erikraft.drop.R;
import com.erikraft.drop.utils.ClipboardUtils;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class QRTransferActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_URI = "uri";
    private static final int PERMISSION_REQUEST_CAMERA = 101;

    private ImageView qrImageView;
    private SurfaceView cameraSurfaceView;
    private TextView titleTextView;
    private TextView statusTextView;
    private TextView progressTextView;
    private TextView symbolsTextView;
    private ProgressBar progressBar;
    private Button pauseButton;
    private Button cancelButton;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isPaused = false;
    private boolean isCancelled = false;

    private byte[][] sendChunks;
    private ErikrafTQRProtocol.Frame baseFrame;
    private int currentChunkIndex = 0;
    private Runnable animationRunnable;

    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private QRDecoder qrDecoder = new QRDecoder();
    private MultiFormatReader multiFormatReader = new MultiFormatReader();
    private boolean isReceiving = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_transfer);

        qrImageView = findViewById(R.id.qr_image_view);
        cameraSurfaceView = findViewById(R.id.camera_surface_view);
        titleTextView = findViewById(R.id.title_text_view);
        statusTextView = findViewById(R.id.status_text_view);
        progressTextView = findViewById(R.id.progress_text_view);
        symbolsTextView = findViewById(R.id.symbols_text_view);
        progressBar = findViewById(R.id.progress_bar);
        pauseButton = findViewById(R.id.pause_button);
        cancelButton = findViewById(R.id.cancel_button);

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) mode = "receive";

        cancelButton.setOnClickListener(v -> cancelTransfer());
        pauseButton.setOnClickListener(v -> togglePause());

        if ("send_text".equals(mode)) {
            String text = getIntent().getStringExtra(EXTRA_TEXT);
            setupSendText(text != null ? text : "");
        } else if ("send_file".equals(mode)) {
            Uri uri = getIntent().getParcelableExtra(EXTRA_URI);
            setupSendFile(uri);
        } else {
            setupReceiveMode();
        }
    }

    private void setupSendText(String text) {
        titleTextView.setText(R.string.qr_transfer_send_title);
        statusTextView.setText(R.string.qr_transfer_instruction);

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        prepareSendFrames("Text_" + System.currentTimeMillis() + ".txt", "text/plain", bytes, "text");
    }

    private void setupSendFile(Uri uri) {
        titleTextView.setText(R.string.qr_transfer_send_title);
        statusTextView.setText(R.string.qr_transfer_instruction);

        if (uri == null) {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, read);
            }
            byte[] fileBytes = baos.toByteArray();
            String name = "file_" + System.currentTimeMillis();
            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "application/octet-stream";

            prepareSendFrames(name, mime, fileBytes, "file");
        } catch (Exception e) {
            Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void prepareSendFrames(String name, String mime, byte[] data, String type) {
        int chunkSize = FountainFEC.DEFAULT_CHUNK_SIZE;
        sendChunks = FountainFEC.chunkData(data, chunkSize);

        baseFrame = new ErikrafTQRProtocol.Frame();
        baseFrame.id = UUID.randomUUID().toString().substring(0, 8);
        baseFrame.name = name;
        baseFrame.mime = mime;
        baseFrame.type = type;
        baseFrame.size = data.length;
        baseFrame.k = sendChunks.length;
        baseFrame.sha256 = ErikrafTQRProtocol.computeSHA256(data);

        progressBar.setMax(baseFrame.k);
        progressBar.setProgress(baseFrame.k);
        progressTextView.setText("Dados recuperados: 100%");
        symbolsTextView.setText("Símbolos total: " + baseFrame.k);

        startAnimation();
    }

    private void startAnimation() {
        animationRunnable = new Runnable() {
            @Override
            public void run() {
                if (isCancelled) return;
                if (!isPaused && sendChunks != null && sendChunks.length > 0) {
                    baseFrame.seq = currentChunkIndex;
                    byte[] chunk = sendChunks[currentChunkIndex];
                    baseFrame.crc = ErikrafTQRProtocol.computeCRC32(chunk);
                    baseFrame.data = ErikrafTQRProtocol.encodeBase64(chunk);

                    String encodedStr = ErikrafTQRProtocol.encodeFrame(baseFrame);
                    try {
                        Bitmap qrBitmap = QREncoder.generateQRCode(encodedStr, 400, 400);
                        qrImageView.setImageBitmap(qrBitmap);
                    } catch (Exception e) {
                        // ignore rendering errors and continue the animation
                    }

                    currentChunkIndex = (currentChunkIndex + 1) % sendChunks.length;
                }
                handler.postDelayed(this, 300);
            }
        };
        handler.post(animationRunnable);
    }

    private void setupReceiveMode() {
        isReceiving = true;
        titleTextView.setText(R.string.qr_transfer_receive_title);
        statusTextView.setText(R.string.qr_transfer_receive_searching);
        progressTextView.setText("Dados recuperados: 0%");
        symbolsTextView.setText("Símbolos recebidos: 0");

        qrImageView.setVisibility(View.GONE);
        cameraSurfaceView.setVisibility(View.VISIBLE);

        surfaceHolder = cameraSurfaceView.getHolder();
        surfaceHolder.addCallback(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CAMERA);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (isReceiving) {
            startCameraPreview();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        stopCamera();
    }

    private void startCameraPreview() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            startFrameScanning();
        } catch (Exception e) {
            statusTextView.setText("Erro ao iniciar câmera");
        }
    }

    private void startFrameScanning() {
        if (camera == null || isCancelled) return;
        camera.setOneShotPreviewCallback((data, cam) -> {
            if (!isCancelled && !isPaused && data != null) {
                try {
                    Camera.Parameters parameters = cam.getParameters();
                    Camera.Size size = parameters.getPreviewSize();
                    int width = size.width;
                    int height = size.height;

                    LuminanceSource source = new PlanarYUVLuminanceSource(
                            data, width, height, 0, 0, width, height, false);
                    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                    Result result = multiFormatReader.decode(bitmap);
                    if (result != null && result.getText() != null) {
                        QRDecoder.DecodeResult decodeResult = qrDecoder.processQRString(result.getText());
                        if (decodeResult.success) {
                            progressBar.setMax(decodeResult.totalChunks);
                            progressBar.setProgress(decodeResult.uniqueChunks);
                            progressTextView.setText(String.format("Dados recuperados: %.1f%%", decodeResult.progress));
                            symbolsTextView.setText("Símbolos recebidos: " + decodeResult.totalSymbols);

                            if (decodeResult.isComplete && decodeResult.finalData != null) {
                                statusTextView.setText("Transferência concluída!");
                                handleReceivedResult(decodeResult);
                                return;
                            } else {
                                statusTextView.setText("Recebendo QR Code...");
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (!isCancelled && isReceiving) {
                handler.postDelayed(this::startFrameScanning, 100);
            }
        });
    }

    private void handleReceivedResult(QRDecoder.DecodeResult result) {
        stopCamera();
        if ("text".equals(result.type)) {
            String text = new String(result.finalData, StandardCharsets.UTF_8);
            ClipboardUtils.copy(this, text);
            saveReceivedTextFile(result.name, result.finalData);
        } else {
            try {
                File file = new File(getExternalFilesDir(null), result.name != null ? result.name : "received_file");
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(result.finalData);
                }
                Toast.makeText(this, "Arquivo salvo em: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Falha ao salvar arquivo recebido", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveReceivedTextFile(String requestedName, byte[] data) {
        String name = requestedName;
        if (name == null || name.trim().isEmpty()) {
            name = "received_text.txt";
        }
        if (!name.toLowerCase().endsWith(".txt")) {
            name += ".txt";
        }
        name = name.replace('/', '_').replace('\\', '_');

        try {
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 1);

                uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IllegalStateException("MediaStore insert failed");

                try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
                    if (outputStream == null) throw new IllegalStateException("Unable to open download");
                    outputStream.write(data);
                }

                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CAMERA + 1);
                    Toast.makeText(this, "Permissão de armazenamento necessária para salvar o TXT", Toast.LENGTH_LONG).show();
                    return;
                }
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloads.exists() && !downloads.mkdirs()) {
                    throw new IllegalStateException("Unable to create Downloads directory");
                }
                File target = new File(downloads, name);
                try (OutputStream outputStream = new FileOutputStream(target)) {
                    outputStream.write(data);
                }
                uri = Uri.fromFile(target);
            }

            Toast.makeText(this, "TXT salvo em Downloads: " + name, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Falha ao salvar TXT em Downloads", Toast.LENGTH_LONG).show();
        }
    }

    private void stopCamera() {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.setPreviewCallback(null);
                camera.release();
            } catch (Exception ignored) {}
            camera = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (isReceiving && surfaceHolder != null) {
                startCameraPreview();
            }
        }
    }

    private void togglePause() {
        isPaused = !isPaused;
        pauseButton.setText(isPaused ? R.string.resume : R.string.pause);
    }

    private void cancelTransfer() {
        isCancelled = true;
        stopCamera();
        if (handler != null && animationRunnable != null) {
            handler.removeCallbacks(animationRunnable);
        }
        if (qrDecoder != null) {
            qrDecoder.reset();
        }
        sendChunks = null;
        baseFrame = null;
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelTransfer();
    }
}
