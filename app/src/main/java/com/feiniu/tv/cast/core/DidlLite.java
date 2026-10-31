package com.feiniu.tv.cast.core;

/**
 * Reads the title out of a DIDL-Lite document.
 *
 * <p>Same hand-written scanning approach as {@link SoapRequest}: metadata
 * arrives from arbitrary control points, is tiny, and must never throw.
 */
public final class DidlLite {

    private DidlLite() {
    }

    /**
     * Value of the {@code <dc:title>} element, XML-unescaped. "" when the
     * document is null, has no title, or the title is empty.
     */
    public static String title(String didl) {
        if (didl == null || didl.isEmpty()) {
            return "";
        }
        int i = 0;
        while (i < didl.length()) {
            int open = didl.indexOf('<', i);
            if (open < 0 || open + 1 >= didl.length()) {
                return "";
            }
            char next = didl.charAt(open + 1);
            if (next == '/' || next == '!' || next == '?') {
                i = open + 2;
                continue;
            }
            int close = tagEnd(didl, open);
            if (close < 0) {
                return "";
            }
            String tag = didl.substring(open, close + 1);
            String qualifiedName = tagName(tag);
            if ("title".equals(localName(qualifiedName))) {
                if (isSelfClosing(tag)) {
                    return "";
                }
                int start = close + 1;
                int end = findCloseTag(didl, qualifiedName, start);
                if (end < 0) {
                    return "";
                }
                String title = SoapRequest.unescape(didl.substring(start, end)).trim();
                // Senders that post GBK (fnOS does) arrive here already mangled
                // by the HTTP charset decode; showing replacement characters is
                // worse than showing nothing.
                if (title.indexOf('\uFFFD') >= 0) {
                    return "";
                }
                return title;
            }
            i = close + 1;
        }
        return "";
    }

    /** Index of the {@code >} closing the tag that starts at {@code open}, or -1. */
    private static int tagEnd(String s, int open) {
        char quote = 0;
        for (int i = open + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    /** Index of the {@code <} of {@code </qualifiedName>} (whitespace before '>' allowed), or -1. */
    private static int findCloseTag(String s, String qualifiedName, int from) {
        String needle = "</" + qualifiedName;
        int i = s.indexOf(needle, from);
        while (i >= 0) {
            int j = i + needle.length();
            while (j < s.length() && Character.isWhitespace(s.charAt(j))) {
                j++;
            }
            if (j < s.length() && s.charAt(j) == '>') {
                return i;
            }
            i = s.indexOf(needle, i + 1);
        }
        return -1;
    }

    /** Element name of a tag, prefix included. */
    private static String tagName(String tag) {
        int i = 1;
        if (i < tag.length() && tag.charAt(i) == '/') {
            i++;
        }
        int start = i;
        while (i < tag.length() && isNameChar(tag.charAt(i))) {
            i++;
        }
        return tag.substring(start, i);
    }

    private static String localName(String qualifiedName) {
        int colon = qualifiedName.indexOf(':');
        return colon < 0 ? qualifiedName : qualifiedName.substring(colon + 1);
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == ':' || c == '_' || c == '-' || c == '.';
    }

    private static boolean isSelfClosing(String tag) {
        int i = tag.length() - 2;
        while (i > 0 && Character.isWhitespace(tag.charAt(i))) {
            i--;
        }
        return i > 0 && tag.charAt(i) == '/';
    }
}
