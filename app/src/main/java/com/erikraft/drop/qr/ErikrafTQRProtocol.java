package com.erikraft.drop.qr;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

public class ErikrafTQRProtocol {

    public static final String MAGIC = "EKQR1";
    public static final int VERSION = 1;

    public static class Frame {
        public String magic = MAGIC;
        public int version = VERSION;
        public String id;
        public String type; // "file" or "text"
        public String name;
        public String mime;
        public long size;
        public int k; // total chunks
        public int seq; // sequence / symbol index
        public long crc; // CRC32 of payload chunk
        public String sha256; // Final payload SHA-256 hex
        public String data; // Base64 chunk
    }

    public static byte[] decodeBase64(String str) {
        try {
            return java.util.Base64.getDecoder().decode(str);
        } catch (Throwable e) {
            return android.util.Base64.decode(str, android.util.Base64.NO_WRAP);
        }
    }

    public static String encodeBase64(byte[] data) {
        try {
            return java.util.Base64.getEncoder().encodeToString(data);
        } catch (Throwable e) {
            return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
        }
    }

    public static long computeCRC32(byte[] data) {
        CRC32 crc32 = new CRC32();
        crc32.update(data);
        return crc32.getValue();
    }

    public static String computeSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    public static String encodeFrame(Frame frame) {
        return "{\"m\":\"" + (frame.magic != null ? frame.magic : MAGIC) + "\","
                + "\"v\":" + frame.version + ","
                + "\"id\":\"" + (frame.id != null ? frame.id : "") + "\","
                + "\"t\":\"" + (frame.type != null ? frame.type : "file") + "\","
                + "\"n\":\"" + (frame.name != null ? frame.name : "") + "\","
                + "\"mime\":\"" + (frame.mime != null ? frame.mime : "") + "\","
                + "\"s\":" + frame.size + ","
                + "\"k\":" + frame.k + ","
                + "\"seq\":" + frame.seq + ","
                + "\"crc\":" + frame.crc + ","
                + "\"hash\":\"" + (frame.sha256 != null ? frame.sha256 : "") + "\","
                + "\"d\":\"" + (frame.data != null ? frame.data : "") + "\"}";
    }

    private static String extractStringField(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return "";
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end);
    }

    private static long extractNumberField(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return 0;
        start += pattern.length();
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static Frame decodeFrame(String frameStr) {
        if (frameStr == null || frameStr.trim().isEmpty()) {
            return null;
        }
        try {
            String magic = extractStringField(frameStr, "m");
            if (!MAGIC.equals(magic)) {
                return null;
            }
            int version = (int) extractNumberField(frameStr, "v");
            if (version != VERSION) {
                return null;
            }

            Frame frame = new Frame();
            frame.magic = magic;
            frame.version = version;
            frame.id = extractStringField(frameStr, "id");
            frame.type = extractStringField(frameStr, "t");
            frame.name = extractStringField(frameStr, "n");
            frame.mime = extractStringField(frameStr, "mime");
            frame.size = extractNumberField(frameStr, "s");
            frame.k = (int) extractNumberField(frameStr, "k");
            frame.seq = (int) extractNumberField(frameStr, "seq");
            frame.crc = extractNumberField(frameStr, "crc");
            frame.sha256 = extractStringField(frameStr, "hash");
            frame.data = extractStringField(frameStr, "d");

            // Verify frame CRC32
            byte[] rawChunk = decodeBase64(frame.data);
            long calculatedCrc = computeCRC32(rawChunk);
            if (calculatedCrc != frame.crc) {
                return null; // Corrupted frame
            }

            return frame;
        } catch (Exception e) {
            return null;
        }
    }
}
