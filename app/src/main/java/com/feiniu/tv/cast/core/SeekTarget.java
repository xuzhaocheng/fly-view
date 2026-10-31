package com.feiniu.tv.cast.core;

/**
 * Converts AVTransport {@code Seek} arguments into a millisecond position.
 *
 * <p>UPnP allows {@code TRACK_NR} and other units that a renderer without a
 * playlist cannot honour; those yield -1 so the caller can answer with an
 * error instead of guessing.
 */
public final class SeekTarget {

    private SeekTarget() {
    }

    /**
     * Parses {@code HH:MM:SS} or {@code HH:MM:SS.mmm} for the REL_TIME and
     * ABS_TIME units. Returns -1 for any other unit or a malformed target.
     */
    public static long parseMs(String unit, String target) {
        if (unit == null || target == null) {
            return -1;
        }
        String u = unit.trim();
        if (!"REL_TIME".equalsIgnoreCase(u) && !"ABS_TIME".equalsIgnoreCase(u)) {
            return -1;
        }
        String s = target.trim();
        if (s.isEmpty()) {
            return -1;
        }
        String[] parts = s.split(":", -1);
        if (parts.length != 3) {
            return -1;
        }
        long hours = parseField(parts[0]);
        long minutes = parseField(parts[1]);
        long seconds = parseSeconds(parts[2]);
        // A position beyond ~11 years cannot be real and would overflow the
        // millisecond arithmetic.
        if (hours < 0 || hours > 100000 || minutes < 0 || minutes > 59 || seconds < 0) {
            return -1;
        }
        return hours * 3600000L + minutes * 60000L + seconds;
    }

    /** Whole-number field, or -1 when empty/non-numeric/overflowing. */
    private static long parseField(String field) {
        if (field.isEmpty() || !digits(field)) {
            return -1;
        }
        try {
            return Long.parseLong(field);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Seconds field with optional fraction, in milliseconds; -1 when invalid. */
    private static long parseSeconds(String field) {
        int dot = field.indexOf('.');
        if (dot < 0) {
            long whole = parseField(field);
            return whole < 0 || whole > 59 ? -1 : whole * 1000L;
        }
        String wholePart = field.substring(0, dot);
        String fraction = field.substring(dot + 1);
        if (fraction.isEmpty() || !digits(fraction)) {
            return -1;
        }
        long whole = parseField(wholePart);
        if (whole < 0 || whole > 59) {
            return -1;
        }
        int millis = 0;
        for (int i = 0; i < 3; i++) {
            millis *= 10;
            if (i < fraction.length()) {
                millis += fraction.charAt(i) - '0';
            }
        }
        return whole * 1000L + millis;
    }

    private static boolean digits(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
