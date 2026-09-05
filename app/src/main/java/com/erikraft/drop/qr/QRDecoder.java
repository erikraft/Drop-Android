package com.erikraft.drop.qr;

import android.graphics.Bitmap;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Inflater;

public class QRDecoder {

    private final MultiFormatReader reader = new MultiFormatReader();
    private FountainFEC.Receiver receiver = null;
    private String currentTransferId = null;
    private boolean currentCompressed = false;

    public static class DecodeResult {
        public boolean success;
        public boolean newFrameAdded;
        public boolean isComplete;
        public float progress;
        public int uniqueChunks;
        public int totalChunks;
        public int totalSymbols;
        public String transferId;
        public String name;
        public String type;
        public byte[] finalData;
        public String sha256;
        public String errorMessage;
    }

    public DecodeResult processQRString(String qrContent) {
        DecodeResult res = new DecodeResult();
        ErikrafTQRProtocol.Frame frame = ErikrafTQRProtocol.decodeFrame(qrContent);
        if (frame == null) {
            res.success = false;
            res.errorMessage = "Invalid EKQR frame or CRC32 checksum mismatch";
            return res;
        }

        res.success = true;
        res.transferId = frame.id;
        res.name = frame.name;
        res.type = frame.type;

        if (receiver == null || !frame.id.equals(currentTransferId)) {
            currentTransferId = frame.id;
            currentCompressed = frame.compressed == 1;
            receiver = new FountainFEC.Receiver(frame.k, frame.size, frame.sha256);
        }

        res.newFrameAdded = receiver.processFrame(frame);
        res.progress = receiver.getProgressPercentage();
        res.uniqueChunks = receiver.getUniqueChunksCount();
        res.totalChunks = receiver.getTotalChunksCount();
        res.totalSymbols = receiver.getReceivedSymbolsCount();
        res.isComplete = receiver.isComplete();

        if (res.isComplete) {
            try {
                byte[] payload = receiver.reassemblePayload();
                byte[] finalData = currentCompressed ? inflateRaw(payload, receiver.getExpectedSize()) : payload;
                if (receiver.getExpectedSize() >= 0 && finalData.length != receiver.getExpectedSize()) {
                    throw new IOException("Size mismatch after reconstruction");
                }
                String actualHash = ErikrafTQRProtocol.computeSHA256(finalData);
                if (receiver.getExpectedHash() != null && !receiver.getExpectedHash().isEmpty()
                        && !receiver.getExpectedHash().equalsIgnoreCase(actualHash)) {
                    throw new IOException("Integrity check failed: SHA-256 hash mismatch");
                }
                res.finalData = finalData;
                res.sha256 = actualHash;
            } catch (Exception e) {
                res.isComplete = false;
                res.errorMessage = e.getMessage();
            }
        }
        return res;
    }

    /** Decompresses the web client's deflate-raw payload without ZIP/zlib wrappers. */
    private static byte[] inflateRaw(byte[] compressed, long expectedSize) throws IOException {
        Inflater inflater = new Inflater(true);
        ByteArrayOutputStream output = new ByteArrayOutputStream(expectedSize > 0 && expectedSize < Integer.MAX_VALUE ? (int) expectedSize : 8192);
        byte[] buffer = new byte[8192];
        try {
            inflater.setInput(compressed);
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count > 0) {
                    output.write(buffer, 0, count);
                    if (expectedSize > 0 && output.size() > expectedSize) {
                        throw new IOException("Decompressed data exceeds expected size");
                    }
                } else if (inflater.needsDictionary() || inflater.needsInput()) {
                    break;
                }
            }
            if (!inflater.finished()) throw new IOException("Unable to decompress EKQR payload");
            return output.toByteArray();
        } catch (java.util.zip.DataFormatException e) {
            throw new IOException("Invalid deflate-raw payload", e);
        } finally {
            inflater.end();
        }
    }

    public DecodeResult processBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            DecodeResult res = new DecodeResult();
            res.success = false;
            res.errorMessage = "Bitmap is null";
            return res;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        LuminanceSource source = new RGBLuminanceSource(width, height, pixels);
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(source));

        try {
            Result result = reader.decode(binaryBitmap);
            return processQRString(result.getText());
        } catch (NotFoundException e) {
            DecodeResult res = new DecodeResult();
            res.success = false;
            res.errorMessage = "QR code not found";
            return res;
        }
    }

    public void reset() {
        if (receiver != null) receiver.reset();
        receiver = null;
        currentTransferId = null;
        currentCompressed = false;
    }
}
