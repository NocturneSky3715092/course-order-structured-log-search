package education.store.logs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private Json() {}

    static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> fields = new ArrayList<>();
            map.forEach((key, item) -> fields.add(quote(key.toString()) + ":" + stringify(item)));
            return "{" + String.join(",", fields) + "}";
        }
        if (value instanceof Iterable<?> items) {
            List<String> values = new ArrayList<>();
            items.forEach(item -> values.add(stringify(item)));
            return "[" + String.join(",", values) + "]";
        }
        throw new IllegalArgumentException("Cannot encode " + value.getClass().getName());
    }

    static Map<String, Object> parseObject(String source) {
        Object value = new Parser(source).parse();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected a JSON object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(key.toString(), item));
        return result;
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        return out.append('"').toString();
    }

    private static final class Parser {
        private final String source;
        private int index;

        private Parser(String source) { this.source = source; }

        private Object parse() {
            Object value = value();
            whitespace();
            if (index != source.length()) throw error("Unexpected trailing content");
            return value;
        }

        private Object value() {
            whitespace();
            if (index >= source.length()) throw error("Expected a value");
            return switch (source.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> values = new LinkedHashMap<>();
            index++;
            whitespace();
            if (take('}')) return values;
            do {
                whitespace();
                String key = string();
                whitespace();
                expect(':');
                values.put(key, value());
                whitespace();
            } while (take(','));
            expect('}');
            return values;
        }

        private List<Object> array() {
            List<Object> values = new ArrayList<>();
            index++;
            whitespace();
            if (take(']')) return values;
            do {
                values.add(value());
                whitespace();
            } while (take(','));
            expect(']');
            return values;
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (index < source.length()) {
                char ch = source.charAt(index++);
                if (ch == '"') return out.toString();
                if (ch != '\\') { out.append(ch); continue; }
                if (index >= source.length()) throw error("Incomplete escape");
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> out.append(escaped);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) throw error("Incomplete unicode escape");
                        out.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw error("Unknown escape");
                }
            }
            throw error("Unterminated string");
        }

        private Object number() {
            int start = index;
            while (index < source.length() && "-+0123456789.eE".indexOf(source.charAt(index)) >= 0) index++;
            String token = source.substring(start, index);
            try { return token.contains(".") || token.contains("e") || token.contains("E")
                    ? Double.parseDouble(token) : Long.parseLong(token); }
            catch (NumberFormatException exception) { throw error("Invalid number"); }
        }

        private Object literal(String token, Object value) {
            if (!source.startsWith(token, index)) throw error("Invalid literal");
            index += token.length();
            return value;
        }

        private void whitespace() {
            while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
        }

        private boolean take(char expected) {
            if (index < source.length() && source.charAt(index) == expected) { index++; return true; }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) throw error("Expected " + expected);
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + index);
        }
    }
}
