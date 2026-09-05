package com.erikraft.drop.qr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** EKQR receiver for base chunks and the web client's two-source XOR parity frames. */
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
        private final Map<Integer, ErikrafTQRProtocol.Frame> parityFrames = new HashMap<>();
        private final Set<Integer> receivedSymbols = new HashSet<>();
        private int totalSymbolsReceived = 0;

        public Receiver(int totalChunks, long expectedSize, String expectedHash) {
            this.totalChunks = totalChunks;
            this.expectedSize = expectedSize;
            this.expectedHash = expectedHash;
        }

        public synchronized boolean processFrame(ErikrafTQRProtocol.Frame frame) {
            if (frame == null) return false;
            if (receivedSymbols.add(frame.seq)) totalSymbolsReceived++;

            final byte[] rawChunk;
            try {
                rawChunk = ErikrafTQRProtocol.decodeBase64(frame.data);
            } catch (Exception e) {
                return false;
            }

            if (frame.fec != null && frame.fec.length == 2) {
                if (!parityFrames.containsKey(frame.seq)) parityFrames.put(frame.seq, frame);
            } else if (frame.seq >= 0 && frame.seq < totalChunks) {
                if (!receivedChunks.containsKey(frame.seq)) receivedChunks.put(frame.seq, rawChunk);
            } else {
                return false;
            }

            recoverAvailableParity();
            return true;
        }

        private void recoverAvailableParity() {
            boolean progress;
            do {
                progress = false;
                for (ErikrafTQRProtocol.Frame parityFrame : parityFrames.values()) {
                    int first = parityFrame.fec[0];
                    int second = parityFrame.fec[1];
                    byte[] firstData = receivedChunks.get(first);
                    byte[] secondData = receivedChunks.get(second);
                    if ((firstData == null) == (secondData == null)) continue;

                    int missing = firstData == null ? first : second;
                    byte[] known = firstData == null ? secondData : firstData;
                    byte[] parity = ErikrafTQRProtocol.decodeBase64(parityFrame.data);
                    byte[] recovered = new byte[parity.length];
                    for (int i = 0; i < recovered.length; i++) {
                        recovered[i] = (byte) ((i < known.length ? known[i] : 0) ^ parity[i]);
                    }
                    if (!receivedChunks.containsKey(missing)) {
                        receivedChunks.put(missing, recovered);
                        progress = true;
                    }
                }
            } while (progress);
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

        /** Returns the encoded payload bytes before optional deflate decompression. */
        public synchronized byte[] reassemblePayload() throws IOException {
            if (!isComplete()) throw new IOException("Transfer incomplete");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunk = receivedChunks.get(i);
                if (chunk == null) throw new IOException("Missing chunk " + i);
                bos.write(chunk);
            }
            return bos.toByteArray();
        }

        public long getExpectedSize() {
            return expectedSize;
        }

        public String getExpectedHash() {
            return expectedHash;
        }

        public synchronized byte[] reassemble() throws IOException {
            byte[] data = reassemblePayload();
            if (expectedSize >= 0 && data.length > expectedSize) {
                byte[] exact = new byte[(int) expectedSize];
                System.arraycopy(data, 0, exact, 0, exact.length);
                data = exact;
            }
            if (expectedHash != null && !expectedHash.isEmpty()
                    && !expectedHash.equalsIgnoreCase(ErikrafTQRProtocol.computeSHA256(data))) {
                throw new IOException("Integrity check failed: SHA-256 hash mismatch");
            }
            return data;
        }

        public synchronized void reset() {
            receivedChunks.clear();
            parityFrames.clear();
            receivedSymbols.clear();
            totalSymbolsReceived = 0;
        }
    }
}
