package com.erikraft.drop.qr;

import android.graphics.Bitmap;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

public class QRDecoder {

    private final MultiFormatReader reader = new MultiFormatReader();
    private FountainFEC.Receiver receiver = null;
    private String currentTransferId = null;

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
        public String errorMessage;
    }

    public DecodeResult processQRString(String qrContent) {
        DecodeResult res = new DecodeResult();
        ErikrafTQRProtocol.Frame frame = ErikrafTQRProtocol.decodeFrame(qrContent);
        if (frame == null) {
            res.success = false;
            res.errorMessage = "Invalid frame or CRC32 checksum mismatch";
            return res;
        }

        res.success = true;
        res.transferId = frame.id;
        res.name = frame.name;
        res.type = frame.type;

        if (receiver == null || !frame.id.equals(currentTransferId)) {
            currentTransferId = frame.id;
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
                res.finalData = receiver.reassemble();
            } catch (Exception e) {
                res.isComplete = false;
                res.errorMessage = e.getMessage();
            }
        }
        return res;
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
        if (receiver != null) {
            receiver.reset();
        }
        receiver = null;
        currentTransferId = null;
    }
}
