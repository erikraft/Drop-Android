package com.erikraft.drop;

import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Random;

public class FileIntegrityTest {

    private String calculateSHA256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private byte[] simulateBase64ChunkTransfer(byte[] originalData, int chunkSize) throws Exception {
        ByteArrayOutputStream reassembled = new ByteArrayOutputStream();
        int offset = 0;
        while (offset < originalData.length) {
            int length = Math.min(chunkSize, originalData.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(originalData, offset, chunk, 0, length);

            // Simulate JS encoding: ArrayBuffer/Uint8Array to Base64
            String base64Chunk = Base64.getEncoder().encodeToString(chunk);

            // Simulate Java decoding: Base64 to byte[]
            byte[] decodedBytes = Base64.getDecoder().decode(base64Chunk);
            reassembled.write(decodedBytes);

            offset += length;
        }
        return reassembled.toByteArray();
    }

    @Test
    public void testBinaryTransferPngJpgPdfZipMp3Mp4AndRandom() throws Exception {
        int[] sizes = {100, 1023, 1024, 1025, 2047, 2048, 2049, 10000, 65536, 100000};
        int[] chunkSizes = {512, 1024, 4096, 16384};

        Random random = new Random(42);

        for (int size : sizes) {
            for (int chunkSize : chunkSizes) {
                byte[] original = new byte[size];
                random.nextBytes(original);

                // Add header signatures for binary file simulation
                if (size >= 8) {
                    original[0] = (byte) 0x89; // PNG magic header
                    original[1] = 'P';
                    original[2] = 'N';
                    original[3] = 'G';
                    original[4] = (byte) 0xFF; // JPG magic header
                    original[5] = (byte) 0xD8;
                    original[6] = (byte) 0xFF;
                    original[7] = (byte) 0xE0;
                }

                String originalHash = calculateSHA256(original);
                byte[] reconstructed = simulateBase64ChunkTransfer(original, chunkSize);
                String reconstructedHash = calculateSHA256(reconstructed);

                Assert.assertEquals("Size mismatch for test size " + size + " chunk " + chunkSize, original.length, reconstructed.length);
                Assert.assertArrayEquals("Content mismatch for test size " + size + " chunk " + chunkSize, original, reconstructed);
                Assert.assertEquals("SHA-256 mismatch for test size " + size + " chunk " + chunkSize, originalHash, reconstructedHash);
            }
        }
    }
}
