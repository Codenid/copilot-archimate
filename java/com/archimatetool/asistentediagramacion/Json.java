package com.archimatetool.asistentediagramacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private final String input;
    private int index;

    private Json(String input) {
        this.input = input;
    }

    static Object parse(String input) {
        Json parser = new Json(input);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.index != input.length()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            out.append('"');
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                switch (ch) {
                    case '"': out.append("\\\""); break;
                    case '\\': out.append("\\\\"); break;
                    case '\b': out.append("\\b"); break;
                    case '\f': out.append("\\f"); break;
                    case '\n': out.append("\\n"); break;
                    case '\r': out.append("\\r"); break;
                    case '\t': out.append("\\t"); break;
                    default:
                        if (ch < 0x20) {
                            out.append(String.format("\\u%04x", (int) ch));
                        } else {
                            out.append(ch);
                        }
                }
            }
            out.append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                writeValue(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
                first = false;
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) out.append(',');
                writeValue(out, list.get(i));
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
        }
    }

    private Object readValue() {
        skipWhitespace();
        if (index >= input.length()) throw error("Expected a value");
        return switch (input.charAt(index)) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        index++;
        skipWhitespace();
        Map<String, Object> result = new LinkedHashMap<>();
        if (take('}')) return result;
        while (true) {
            skipWhitespace();
            if (index >= input.length() || input.charAt(index) != '"') {
                throw error("Expected an object key");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            if (result.containsKey(key)) throw error("Duplicate object key");
            result.put(key, readValue());
            skipWhitespace();
            if (take('}')) return result;
            expect(',');
        }
    }

    private List<Object> readArray() {
        index++;
        skipWhitespace();
        List<Object> result = new ArrayList<>();
        if (take(']')) return result;
        while (true) {
            result.add(readValue());
            skipWhitespace();
            if (take(']')) return result;
            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (index < input.length()) {
            char ch = input.charAt(index++);
            if (ch == '"') return result.toString();
            if (ch < 0x20) throw error("Control character in string");
            if (ch != '\\') {
                result.append(ch);
                continue;
            }
            if (index >= input.length()) throw error("Incomplete escape");
            char escaped = input.charAt(index++);
            switch (escaped) {
                case '"': result.append('"'); break;
                case '\\': result.append('\\'); break;
                case '/': result.append('/'); break;
                case 'b': result.append('\b'); break;
                case 'f': result.append('\f'); break;
                case 'n': result.append('\n'); break;
                case 'r': result.append('\r'); break;
                case 't': result.append('\t'); break;
                case 'u':
                    if (index + 4 > input.length()) throw error("Incomplete Unicode escape");
                    try {
                        result.append((char) Integer.parseInt(input.substring(index, index + 4), 16));
                    } catch (NumberFormatException exception) {
                        throw error("Invalid Unicode escape");
                    }
                    index += 4;
                    break;
                default: throw error("Invalid escape");
            }
        }
        throw error("Unterminated string");
    }

    private Object readNumber() {
        int start = index;
        take('-');
        if (take('0')) {
            if (index < input.length() && Character.isDigit(input.charAt(index))) {
                throw error("Leading zero in number");
            }
        } else {
            readDigits();
        }
        if (take('.')) {
            readDigits();
        }
        if (index < input.length() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
            index++;
            if (index < input.length() && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
            readDigits();
        }
        if (start == index) throw error("Invalid value");
        try {
            return Double.valueOf(input.substring(start, index));
        } catch (NumberFormatException exception) {
            throw error("Invalid number");
        }
    }

    private void readDigits() {
        int start = index;
        while (index < input.length() && input.charAt(index) >= '0' && input.charAt(index) <= '9') index++;
        if (start == index) throw error("Expected a digit");
    }

    private Object readLiteral(String literal, Object value) {
        if (!input.startsWith(literal, index)) throw error("Invalid literal");
        index += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (index < input.length()) {
            char ch = input.charAt(index);
            if (ch != ' ' && ch != '\t' && ch != '\n' && ch != '\r') break;
            index++;
        }
    }

    private boolean take(char expected) {
        if (index < input.length() && input.charAt(index) == expected) {
            index++;
            return true;
        }
        return false;
    }

    private void expect(char expected) {
        if (!take(expected)) throw error("Expected '" + expected + "'");
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + index);
    }
}
