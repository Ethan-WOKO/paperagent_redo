package com.yanban.api.agent.sandbox;

import java.util.regex.Pattern;

/** Produces a small display-safe tail; full bounded output remains only in the immutable receipt. */
final class SandboxOutputSummary {
    private static final int MAX_CHARS = 4096;
    private static final int MAX_LINES = 40;
    private static final Pattern SECRET = Pattern.compile("(?i)(authorization|api[_-]?key|token|secret|password)\\s*[:=]\\s*\\S+");
    private static final Pattern BEARER = Pattern.compile("(?i)(authorization\\s*[:=]\\s*bearer)\\s+[^\\s\"']+");
    private static final Pattern UNIX_ABSOLUTE = Pattern.compile("(?<![A-Za-z0-9_.-])/(?:[^\\s/:]+/)+[^\\s:]*");
    private static final Pattern WINDOWS_ABSOLUTE = Pattern.compile("(?i)\\b[A-Z]:\\\\(?:[^\\s\\\\]+\\\\)*[^\\s]*");

    private SandboxOutputSummary() { }

    static String summarize(String value) {
        if (value == null || value.isEmpty()) return "";
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        normalized = BEARER.matcher(normalized).replaceAll("$1 <redacted>");
        normalized = SECRET.matcher(normalized).replaceAll("$1=<redacted>");
        normalized = WINDOWS_ABSOLUTE.matcher(normalized).replaceAll("<host-path>");
        normalized = UNIX_ABSOLUTE.matcher(normalized).replaceAll("<host-path>");
        String[] lines = normalized.split("\n", -1);
        int start = Math.max(0, lines.length - MAX_LINES);
        String tail = String.join("\n", java.util.Arrays.copyOfRange(lines, start, lines.length));
        if (tail.length() > MAX_CHARS) tail = tail.substring(tail.length() - MAX_CHARS);
        return tail;
    }
}
