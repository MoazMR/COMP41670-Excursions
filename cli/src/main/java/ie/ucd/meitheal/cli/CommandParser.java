package ie.ucd.meitheal.cli;

import java.util.ArrayList;
import java.util.List;

public final class CommandParser {
    public ParsedCommand parse(String line) {
        if (line == null) {
            throw new IllegalArgumentException("Command line must not be null.");
        }

        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inQuotes = false;
        boolean tokenStarted = false;

        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (inQuotes) {
                if (current == '"') {
                    inQuotes = false;
                } else if (current == '\\') {
                    char escaped = decodeQuotedEscape(line, ++index);
                    token.append(escaped);
                } else {
                    token.append(current);
                }
            } else if (Character.isWhitespace(current)) {
                if (tokenStarted) {
                    tokens.add(token.toString());
                    token.setLength(0);
                    tokenStarted = false;
                }
            } else if (current == '"') {
                inQuotes = true;
                tokenStarted = true;
            } else {
                token.append(current);
                tokenStarted = true;
            }
        }

        return finishCommand(tokens, token, inQuotes, tokenStarted);
    }

    private ParsedCommand finishCommand(
            List<String> tokens, StringBuilder token, boolean inQuotes, boolean tokenStarted) {
        if (inQuotes) {
            throw syntaxError("unmatched double quote");
        }
        if (tokenStarted) {
            tokens.add(token.toString());
        }
        if (tokens.isEmpty()) {
            throw syntaxError("missing command name");
        }

        return new ParsedCommand(tokens.get(0), tokens.subList(1, tokens.size()));
    }

    private char decodeQuotedEscape(String line, int escapedCharacterIndex) {
        if (escapedCharacterIndex >= line.length()) {
            throw syntaxError("dangling escape in quoted argument");
        }
        char escaped = line.charAt(escapedCharacterIndex);
        if (escaped != '"' && escaped != '\\') {
            throw syntaxError("invalid escape '\\" + escaped + "' in quoted argument");
        }
        return escaped;
    }

    private IllegalArgumentException syntaxError(String detail) {
        return new IllegalArgumentException("Invalid command syntax: " + detail + ".");
    }
}
