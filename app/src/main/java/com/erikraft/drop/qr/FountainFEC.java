package com.erikraft.drop.qr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FountainFEC {

    public static final int DEFAULT_CHUNK_SIZE = 256;

    public static int calculateTotalChunks(long totalSize, int chunkSize) {
        if (totalSize <= 0 || chunkSize <= 0) return 1;
        return (int) Math.ceil((double) totalSize / chunkSize);
    }

    public static byte[][] chunkData(byte[] data, int chunkSize) {
        int totalChunks = calculateTotalChunks(data.length, chunkSize);
        byte[][] chunks = new byte[totalChunks][];
        for (int i = 0; i < totalChunks; i++) {
            int offset = i * chunkSize;
            int len = Math.min(chunkSize, data.length - offset);
            chunks[i] = new byte[len];
            System.arraycopy(data, offset, chunks[i], 0, len);
        }
        return chunks;
    }

    public static class Receiver {
        private final int totalChunks;
        private final long expectedSize;
        private final String expectedHash;
        private final Map<Integer, byte[]> receivedChunks = new HashMap<>();
        private int totalSymbolsReceived = 0;

        public Receiver(int totalChunks, long expectedSize, String expectedHash) {
            this.totalChunks = totalChunks;
            this.expectedSize = expectedSize;
            this.expectedHash = expectedHash;
        }

        public synchronized boolean processFrame(ErikrafTQRProtocol.Frame frame) {
            totalSymbolsReceived++;
            if (frame == null || frame.seq < 0 || frame.seq >= totalChunks) {
                return false;
            }
            if (!receivedChunks.containsKey(frame.seq)) {
                byte[] rawChunk = ErikrafTQRProtocol.decodeBase64(frame.data);
                receivedChunks.put(frame.seq, rawChunk);
                return true;
            }
            return false;
        }

        public synchronized int getReceivedSymbolsCount() {
            return totalSymbolsReceived;
        }

        public synchronized int getUniqueChunksCount() {
            return receivedChunks.size();
        }

        public synchronized int getTotalChunksCount() {
            return totalChunks;
        }

        public synchronized float getProgressPercentage() {
            if (totalChunks <= 0) return 0f;
            return Math.min(100f, ((float) receivedChunks.size() / totalChunks) * 100f);
        }

        public synchronized boolean isComplete() {
            return receivedChunks.size() >= totalChunks;
        }

        public synchronized byte[] reassemble() throws IOException {
            if (!isComplete()) {
                throw new IOException("Transfer incomplete");
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunk = receivedChunks.get(i);
                if (chunk != null) {
                    bos.write(chunk);
                }
            }
            byte[] reassembled = bos.toByteArray();
            if (expectedHash != null && !expectedHash.isEmpty()) {
                String actualHash = ErikrafTQRProtocol.computeSHA256(reassembled);
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    throw new IOException("Integrity check failed: SHA-256 hash mismatch");
                }
            }
            return reassembled;
        }

        public synchronized void reset() {
            receivedChunks.clear();
            totalSymbolsReceived = 0;
        }
    }
}
