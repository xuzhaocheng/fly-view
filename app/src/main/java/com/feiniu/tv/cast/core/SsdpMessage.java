package com.feiniu.tv.cast.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SsdpMessage {

    private final Map<String, String> headers;

    private SsdpMessage(Map<String, String> headers) {
        this.headers = headers;
    }

    public static SsdpMessage parse(String raw) {
        if (raw == null) {
            return null;
        }
        String[] lines = raw.split("\r\n|\n|\r");
        if (lines.length == 0) {
            return null;
        }
        String start = lines[0].trim();
        if (!start.toUpperCase(Locale.US).startsWith("M-SEARCH")) {
            return null;
        }
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0) {
                headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.US),
                        lines[i].substring(colon + 1).trim());
            }
        }
        return new SsdpMessage(headers);
    }

    public boolean isSearch() {
        return true;
    }

    public String searchTarget() {
        String st = headers.get("st");
        return st == null ? "" : st;
    }

    public int mx() {
        try {
            return Integer.parseInt(headers.get("mx"));
        } catch (Exception e) {
            return 1;
        }
    }

    public Map<String, String> headers() {
        return headers;
    }
}
