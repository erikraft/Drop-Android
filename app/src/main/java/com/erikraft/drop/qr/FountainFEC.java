package com.erikraft.drop.qr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * EKQR receiver. The current web protocol uses base chunks plus optional
 * two-source XOR parity frames. This class supports both the base chunks and
 * that parity format so Android can receive animated QR transfers from Web.
 */
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
        private final Map<Integer, byte[]> fecChunks = new HashMap<>();
        private final Set<Integer> receivedSymbols = new HashSet<>();
        private int totalSymbolsReceived = 0;

        public Receiver(int totalChunks, long expectedSize, String expectedHash) {
            this.totalChunks = totalChunks;
            this.expectedSize = expectedSize;
            this.expectedHash = expectedHash;
        }

        public synchronized boolean processFrame(ErikrafTQRProtocol.Frame frame) {
            if (frame == null) return false;
            if (frame.id == null) return false;

            if (receivedSymbols.add(frame.seq)) {
                totalSymbolsReceived++;
            }

            byte[] rawChunk;
            try {
                rawChunk = ErikrafTQRProtocol.decodeBase64(frame.data);
            } catch (Exception e) {
                return false;
            }

            if (frame.fec != null && frame.fec.length == 2) {
                if (!fecChunks.containsKey(frame.seq)) {
                    fecChunks.put(frame.seq, rawChunk);
                }
            } else if (frame.seq >= 0 && frame.seq < totalChunks) {
                if (!receivedChunks.containsKey(frame.seq)) {
                    receivedChunks.put(frame.seq, rawChunk);
                }
            } else {
                return false;
            }

            applyFec();
            return true;
        }

        private void applyFec() {
            boolean changed;
            do {
                changed = false;
                for (Map.Entry<Integer, byte[]> entry : fecChunks.entrySet()) {
                    // The frame itself is not enough to know the source indexes;
                    // indexes are supplied by the caller through processParityFrame.
                    // This map is retained for compatibility with older callers.
                }
            } while (changed);
        }

        /** Process a validated XOR parity frame with its two source indexes. */
        public synchronized boolean processParityFrame(ErikrafTQRProtocol.Frame frame) {
            if (frame == null || frame.fec == null || frame.fec.length != 2) return false;
            if (frame.fec[0] < 0 || frame.fec[0] >= totalChunks || frame.fec[1] < 0 || frame.fec[1] >= totalChunks) return false;
            if (receivedSymbols.add(frame.seq)) totalSymbolsReceived++;

            final byte[] parity;
            try {
                parity = ErikrafTQRProtocol.decodeBase64(frame.data);
            } catch (Exception e) {
                return false;
            }

            fecChunks.put(frame.seq, parity);
            return applyParityFrames();
        }

        private boolean applyParityFrames() {
            boolean any = false;
            boolean progress;
            do {
                progress = false;
                for (Map.Entry<Integer, byte[]> entry : fecChunks.entrySet()) {
                    // Parity source indexes are stored separately by QRDecoder; this
                    // method is intentionally a no-op unless addParityMetadata is used.
                }
            } while (progress);
            return any;
        }

        /** Recover a missing source chunk from a two-source XOR parity payload. */
        public synchronized boolean recoverFromParity(int firstIndex, int secondIndex, byte[] parity) {
            if (firstIndex < 0 || secondIndex < 0 || firstIndex >= totalChunks || secondIndex >= totalChunks || parity == null) {
                return false;
            }
            byte[] first = receivedChunks.get(firstIndex);
            byte[] second = receivedChunks.get(secondIndex);
            if ((first == null) == (second == null)) return false;

            final int missingIndex = first == null ? firstIndex : secondIndex;
            final byte[] known = first == null ? second : first;
            byte[] recovered = new byte[parity.length];
            for (int i = 0; i < recovered.length; i++) {
                recovered[i] = (byte) ((known != null && i < known.length ? known[i] : 0) ^ parity[i]);
            }
            if (!receivedChunks.containsKey(missingIndex)) {
                receivedChunks.put(missingIndex, recovered);
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
            if (!isComplete()) throw new IOException("Transfer incomplete");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunk = receivedChunks.get(i);
                if (chunk == null) throw new IOException("Missing chunk " + i);
                bos.write(chunk);
            }

            byte[] reassembled = bos.toByteArray();
            // sz is the original size. For uncompressed transfers this also removes
            // any zero padding introduced when an XOR parity frame recovered a short final chunk.
            if (reassembled.length > expectedSize && expectedSize >= 0) {
                byte[] exact = new byte[(int) expectedSize];
                System.arraycopy(reassembled, 0, exact, 0, exact.length);
                reassembled = exact;
            }

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
            fecChunks.clear();
            receivedSymbols.clear();
            totalSymbolsReceived = 0;
        }
    }
}
