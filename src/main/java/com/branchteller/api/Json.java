package com.branchteller.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal hand-rolled JSON reader/writer -- no external dependency, same philosophy as
 * the NY Coffee Co. POS project's api.Json. Supports the subset needed here: objects,
 * arrays, strings, numbers, booleans, null.
 */
public class Json {

    // ---------- Writing ----------

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Map) {
            writeObject((Map<String, Object>) value, sb);
        } else if (value instanceof List) {
            writeArray((List<Object>) value, sb);
        } else {
            // fallback: enums, LocalDate, BigDecimal, etc. -- stringify
            writeString(value.toString(), sb);
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(entry.getKey(), sb);
            sb.append(':');
            writeValue(entry.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeArray(List<Object> list, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) sb.append(',');
            first = false;
            writeValue(item, sb);
        }
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    public static Map<String, Object> object() {
        return new LinkedHashMap<>();
    }

    // ---------- Reading ----------

    /** Parses a JSON object into a Map<String, Object> (values are String/Double/Boolean/Map/List/null). */
    public static Map<String, Object> parseObject(String json) {
        Parser p = new Parser(json);
        p.skipWhitespace();
        Object result = p.parseValue();
        if (!(result instanceof Map)) throw new IllegalArgumentException("Expected a JSON object");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        return map;
    }

    private static class Parser {
        private final String s;
        private int pos;

        Parser(String s) { this.s = s; this.pos = 0; }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        Object parseValue() {
            skipWhitespace();
            char c = s.charAt(pos);
            if (c == '{') return parseObjectInternal();
            if (c == '[') return parseArrayInternal();
            if (c == '"') return parseStringInternal();
            if (c == 't' || c == 'f') return parseBooleanInternal();
            if (c == 'n') { pos += 4; return null; }
            return parseNumberInternal();
        }

        Map<String, Object> parseObjectInternal() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWhitespace();
            if (s.charAt(pos) == '}') { pos++; return map; }
            while (true) {
                skipWhitespace();
                String key = parseStringInternal();
                skipWhitespace();
                pos++; // :
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = s.charAt(pos);
                pos++;
                if (c == '}') break;
            }
            return map;
        }

        List<Object> parseArrayInternal() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWhitespace();
            if (s.charAt(pos) == ']') { pos++; return list; }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                char c = s.charAt(pos);
                pos++;
                if (c == ']') break;
            }
            return list;
        }

        String parseStringInternal() {
            skipWhitespace();
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (s.charAt(pos) != '"') {
                char c = s.charAt(pos);
                if (c == '\\') {
                    pos++;
                    char esc = s.charAt(pos);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            String hex = s.substring(pos + 1, pos + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default: sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
                pos++;
            }
            pos++; // closing quote
            return sb.toString();
        }

        Boolean parseBooleanInternal() {
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            pos += 5;
            return Boolean.FALSE;
        }

        Double parseNumberInternal() {
            int start = pos;
            while (pos < s.length() && "-+.eE0123456789".indexOf(s.charAt(pos)) >= 0) pos++;
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
