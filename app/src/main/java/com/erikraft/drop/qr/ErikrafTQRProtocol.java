package com.erikraft.drop.qr;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * EKQR v1 wire protocol shared with the ErikrafT Drop web client.
 * This class intentionally has no dependency on Android JSON APIs so the same
 * implementation can be executed by Gradle JVM unit tests and on Android.
 */
public class ErikrafTQRProtocol {

    public static final String MAGIC = "EKQR";
    public static final int VERSION = 1;

    public static class Frame {
        public String magic = MAGIC;
        public int version = VERSION;
        public String id;
        public String type; // "file" or "text"
        public String name;
        public String mime;
        public long size; // Original/uncompressed size
        public int k; // Number of base chunks
        public int seq; // Base chunk or parity-frame index
        public int compressed; // 1 when payload uses deflate-raw
        public long crc; // CRC32 of the Base64 payload string
        public String sha256; // SHA-256 of the original/uncompressed payload
        public String data; // Base64 chunk
        public int[] fec; // Optional two source indexes for XOR parity
    }

    public static byte[] decodeBase64(String str) {
        try {
            return java.util.Base64.getDecoder().decode(str);
        } catch (Throwable ignored) {
            return android.util.Base64.decode(str, android.util.Base64.DEFAULT);
        }
    }

    public static String encodeBase64(byte[] data) {
        try {
            return java.util.Base64.getEncoder().encodeToString(data);
        } catch (Throwable ignored) {
            return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP);
        }
    }

    /** Matches the web implementation: CRC32 over the Base64 string characters. */
    public static long computeCRC32(String base64Payload) {
        CRC32 crc32 = new CRC32();
        byte[] bytes = base64Payload.getBytes(StandardCharsets.US_ASCII);
        crc32.update(bytes, 0, bytes.length);
        return crc32.getValue();
    }

    public static long computeCRC32(byte[] data) {
        return computeCRC32(encodeBase64(data));
    }

    public static String computeSHA256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    /** Encodes only the canonical v1 Web-compatible schema. */
    public static String encodeFrame(Frame frame) {
        StringBuilder json = new StringBuilder(256);
        json.append('{');
        appendString(json, "h", MAGIC);
        appendNumber(json, "v", frame.version);
        appendString(json, "id", frame.id != null ? frame.id : "");
        appendString(json, "t", frame.type != null ? frame.type : "file");
        appendString(json, "name", frame.name != null ? frame.name : "file.bin");
        appendString(json, "mime", frame.mime != null ? frame.mime : "application/octet-stream");
        appendNumber(json, "sz", frame.size);
        appendNumber(json, "i", frame.seq);
        appendNumber(json, "n", frame.k);
        appendNumber(json, "c", frame.compressed);
        appendNumber(json, "crc", frame.crc);
        appendString(json, "sha", frame.sha256 != null ? frame.sha256 : "");
        appendString(json, "d", frame.data != null ? frame.data : "");
        if (frame.fec != null && frame.fec.length == 2) {
            appendComma(json);
            appendJsonString(json, "fec");
            json.append(':').append('[').append(frame.fec[0]).append(',').append(frame.fec[1]).append(']');
        }
        json.append('}');
        return json.toString();
    }

    private static void appendString(StringBuilder json, String key, String value) {
        appendComma(json);
        appendJsonString(json, key);
        json.append(':');
        appendJsonString(json, value);
    }

    private static void appendNumber(StringBuilder json, String key, long value) {
        appendComma(json);
        appendJsonString(json, key);
        json.append(':').append(value);
    }

    private static void appendComma(StringBuilder json) {
        if (json.length() > 1 && json.charAt(json.length() - 1) != '{' && json.charAt(json.length() - 1) != ',') {
            json.append(',');
        }
    }

    private static void appendJsonString(StringBuilder json, String value) {
        json.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\b': json.append("\\b"); break;
                case '\f': json.append("\\f"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
            }
        }
        json.append('"');
    }

    /**
     * Decodes canonical Web v1 frames and the pre-canonical Android schema used by
     * older app builds. Legacy input is normalized into the current Frame model.
     */
    public static Frame decodeFrame(String frameStr) {
        if (frameStr == null || frameStr.trim().isEmpty()) return null;
        try {
            Object root = new JsonParser(frameStr).parse();
            if (!(root instanceof Map)) return null;
            Map<?, ?> json = (Map<?, ?>) root;

            boolean canonical = MAGIC.equals(stringValue(json.get("h")));
            boolean legacy = !canonical && MAGIC.equals(stringValue(json.get("m")));
            if (!canonical && !legacy) return null;

            int version = intValue(json.get("v"), -1);
            if (version != VERSION) return null;

            Frame frame = new Frame();
            frame.magic = MAGIC;
            frame.version = VERSION;

            if (canonical) {
                frame.id = stringValue(json.get("id"), "");
                frame.type = stringValue(json.get("t"), "file");
                frame.name = stringValue(json.get("name"), "file.bin");
                frame.mime = stringValue(json.get("mime"), "application/octet-stream");
                frame.size = longValue(json.get("sz"), -1);
                frame.seq = intValue(json.get("i"), -1);
                frame.k = intValue(json.get("n"), -1);
                frame.compressed = intValue(json.get("c"), 0);
                frame.crc = longValue(json.get("crc"), -1);
                frame.sha256 = stringValue(json.get("sha"), "");
                frame.data = stringValue(json.get("d"), "");

                Object fecValue = json.get("fec");
                if (fecValue instanceof List) {
                    List<?> fec = (List<?>) fecValue;
                    if (fec.size() == 2) {
                        frame.fec = new int[]{intValue(fec.get(0), -1), intValue(fec.get(1), -1)};
                    }
                }
            } else {
                // Legacy Android v1 field mapping: m/id/t/n/mime/s/k/seq/hash/d/crc.
                frame.id = stringValue(json.get("id"), "");
                frame.type = stringValue(json.get("t"), "file");
                frame.name = stringValue(json.get("n"), "file.bin");
                frame.mime = stringValue(json.get("mime"), "application/octet-stream");
                frame.size = longValue(json.get("s"), -1);
                frame.k = intValue(json.get("k"), -1);
                frame.seq = intValue(json.get("seq"), -1);
                frame.compressed = intValue(json.get("c"), 0);
                frame.crc = longValue(json.get("crc"), -1);
                frame.sha256 = stringValue(json.get("hash"), "");
                frame.data = stringValue(json.get("d"), "");
            }

            if (frame.id.isEmpty() || frame.size < 0 || frame.k <= 0 || frame.k > 100000
                    || frame.seq < 0 || frame.data.isEmpty()) return null;
            if (frame.fec == null && frame.seq >= frame.k) return null;
            if (frame.fec != null && (frame.fec[0] < 0 || frame.fec[0] >= frame.k
                    || frame.fec[1] < 0 || frame.fec[1] >= frame.k)) return null;
            if (frame.compressed != 0 && frame.compressed != 1) return null;
            if (computeCRC32(frame.data) != frame.crc) return null;

            // Decode once here so malformed Base64 never reaches the FEC/reassembly layer.
            decodeBase64(frame.data);
            return frame;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String ? (String) value : fallback;
    }

    private static int intValue(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        return fallback;
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number) return ((Number) value).longValue();
        return fallback;
    }

    /** Minimal JSON parser for the small, fixed EKQR object schema. */
    private static final class JsonParser {
        private final String input;
        private int index;

        JsonParser(String input) {
            this.input = input;
        }

        Object parse() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (index != input.length()) throw new IllegalArgumentException("Trailing JSON data");
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= input.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            char c = input.charAt(index);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            if (input.startsWith("true", index)) { index += 4; return Boolean.TRUE; }
            if (input.startsWith("false", index)) { index += 5; return Boolean.FALSE; }
            if (input.startsWith("null", index)) { index += 4; return null; }
            throw new IllegalArgumentException("Invalid JSON value");
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new HashMap<>();
            skipWhitespace();
            if (peek('}')) { index++; return object; }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) { index++; return object; }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> array = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) { index++; return array; }
            while (true) {
                array.add(parseValue());
                skipWhitespace();
                if (peek(']')) { index++; return array; }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < input.length()) {
                char c = input.charAt(index++);
                if (c == '"') return value.toString();
                if (c != '\\') {
                    if (c < 0x20) throw new IllegalArgumentException("Invalid control character");
                    value.append(c);
                    continue;
                }
                if (index >= input.length()) throw new IllegalArgumentException("Invalid escape");
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"': value.append('"'); break;
                    case '\\': value.append('\\'); break;
                    case '/': value.append('/'); break;
                    case 'b': value.append('\b'); break;
                    case 'f': value.append('\f'); break;
                    case 'n': value.append('\n'); break;
                    case 'r': value.append('\r'); break;
                    case 't': value.append('\t'); break;
                    case 'u':
                        if (index + 4 > input.length()) throw new IllegalArgumentException("Invalid unicode escape");
                        int code = Integer.parseInt(input.substring(index, index + 4), 16);
                        value.append((char) code);
                        index += 4;
                        break;
                    default: throw new IllegalArgumentException("Invalid escape");
                }
            }
            throw new IllegalArgumentException("Unterminated JSON string");
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) index++;
            if (index >= input.length()) throw new IllegalArgumentException("Invalid number");
            if (peek('0')) {
                index++;
            } else {
                if (!isDigit(input.charAt(index))) throw new IllegalArgumentException("Invalid number");
                while (index < input.length() && isDigit(input.charAt(index))) index++;
            }
            boolean decimal = false;
            if (peek('.')) {
                decimal = true;
                index++;
                if (index >= input.length() || !isDigit(input.charAt(index))) throw new IllegalArgumentException("Invalid number");
                while (index < input.length() && isDigit(input.charAt(index))) index++;
            }
            if (peek('e') || peek('E')) {
                decimal = true;
                index++;
                if (peek('+') || peek('-')) index++;
                if (index >= input.length() || !isDigit(input.charAt(index))) throw new IllegalArgumentException("Invalid number");
                while (index < input.length() && isDigit(input.charAt(index))) index++;
            }
            String number = input.substring(start, index);
            return decimal ? Double.valueOf(number) : Long.valueOf(number);
        }

        private boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private boolean peek(char expected) {
            return index < input.length() && input.charAt(index) == expected;
        }

        private void expect(char expected) {
            if (!peek(expected)) throw new IllegalArgumentException("Expected '" + expected + "'");
            index++;
        }

        private void skipWhitespace() {
            while (index < input.length()) {
                char c = input.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') index++;
                else break;
            }
        }
    }
}
