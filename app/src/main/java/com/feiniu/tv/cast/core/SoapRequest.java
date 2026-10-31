package com.feiniu.tv.cast.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * One SOAP action request received from a control point.
 *
 * <p>Deliberately a hand-written scanner instead of a DOM parser: this runs on
 * a TV for every control request and the documents have a fixed, tiny shape
 * (SOAP body holding exactly one action element with flat text arguments).
 * Parsing never throws; anything unrecognised yields an empty request.
 */
public final class SoapRequest {

    private static final SoapRequest EMPTY =
            new SoapRequest("", "", Collections.<String, String>emptyMap());

    private final String action;
    private final String serviceType;
    private final Map<String, String> arguments;

    private SoapRequest(String action, String serviceType, Map<String, String> arguments) {
        this.action = action;
        this.serviceType = serviceType;
        this.arguments = arguments;
    }

    /**
     * Parses a SOAP envelope. The action element is the first child of
     * {@code Body}; its own prefix (whatever it is) names the {@code xmlns}
     * declaration that carries the service type.
     */
    public static SoapRequest parse(String xml) {
        if (xml == null) {
            return EMPTY;
        }
        int bodyOpen = findBodyOpenTag(xml);
        if (bodyOpen < 0) {
            return EMPTY;
        }
        int bodyClose = tagEnd(xml, bodyOpen);
        if (bodyClose < 0) {
            return EMPTY;
        }
        String bodyTag = xml.substring(bodyOpen, bodyClose + 1);
        if (isSelfClosing(bodyTag)) {
            return EMPTY;
        }

        int actionOpen = xml.indexOf('<', bodyClose + 1);
        if (actionOpen < 0 || actionOpen + 1 >= xml.length() || xml.charAt(actionOpen + 1) == '/') {
            return EMPTY;
        }
        int actionEnd = tagEnd(xml, actionOpen);
        if (actionEnd < 0) {
            return EMPTY;
        }
        String actionTag = xml.substring(actionOpen, actionEnd + 1);
        String qualifiedName = tagName(actionTag);
        if (qualifiedName.isEmpty()) {
            return EMPTY;
        }

        int colon = qualifiedName.indexOf(':');
        String prefix = colon < 0 ? "" : qualifiedName.substring(0, colon);
        String actionName = colon < 0 ? qualifiedName : qualifiedName.substring(colon + 1);
        String nsAttribute = prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix;
        String serviceType = attribute(actionTag, nsAttribute);
        if (serviceType.isEmpty() && !prefix.isEmpty()) {
            // Some control points declare the service namespace on Envelope instead.
            serviceType = attribute(xml, nsAttribute);
        }

        String body = "";
        if (!isSelfClosing(actionTag)) {
            int actionClose = findCloseTag(xml, qualifiedName, actionEnd + 1);
            body = xml.substring(actionEnd + 1, actionClose < 0 ? xml.length() : actionClose);
        }
        return new SoapRequest(actionName, serviceType, scanArguments(body));
    }

    /** Action element name with its prefix stripped, or "" when unparsed. */
    public String action() {
        return action;
    }

    /** Namespace of the action element, e.g. {@code urn:schemas-upnp-org:service:AVTransport:1}. */
    public String serviceType() {
        return serviceType;
    }

    /** Raw (already unescaped) argument text; "" when the argument is absent. */
    public String arg(String name) {
        String value = arguments.get(name);
        return value == null ? "" : value;
    }

    // ---------------------------------------------------------------- scanning

    /** Collects the flat {@code <Name>value</Name>} children of the action element. */
    private static Map<String, String> scanArguments(String body) {
        Map<String, String> found = new HashMap<String, String>();
        int i = 0;
        while (i < body.length()) {
            int open = body.indexOf('<', i);
            if (open < 0 || open + 1 >= body.length()) {
                break;
            }
            char next = body.charAt(open + 1);
            if (next == '/' || next == '?' || next == '!') {
                i = open + 2;
                continue;
            }
            int close = tagEnd(body, open);
            if (close < 0) {
                break;
            }
            String tag = body.substring(open, close + 1);
            String qualifiedName = tagName(tag);
            if (qualifiedName.isEmpty()) {
                i = close + 1;
                continue;
            }
            if (isSelfClosing(tag)) {
                put(found, localName(qualifiedName), "");
                i = close + 1;
                continue;
            }
            int end = findCloseTag(body, qualifiedName, close + 1);
            if (end < 0) {
                break;
            }
            // Values are text, never re-parsed: DIDL metadata arrives escaped and
            // becomes markup only after unescaping.
            put(found, localName(qualifiedName), unescape(body.substring(close + 1, end)).trim());
            int after = body.indexOf('>', end);
            i = after < 0 ? body.length() : after + 1;
        }
        return found;
    }

    private static void put(Map<String, String> map, String name, String value) {
        if (!map.containsKey(name)) {
            map.put(name, value);
        }
    }

    private static int findBodyOpenTag(String xml) {
        int i = 0;
        while (true) {
            int open = xml.indexOf('<', i);
            if (open < 0 || open + 1 >= xml.length()) {
                return -1;
            }
            char next = xml.charAt(open + 1);
            if (next == '?' || next == '!') {
                i = open + 2;
                continue;
            }
            int end = tagEnd(xml, open);
            if (end < 0) {
                return -1;
            }
            String tag = xml.substring(open, end + 1);
            if (!isSelfClosing(tag) && "Body".equals(localName(tagName(tag)))) {
                return open;
            }
            i = end + 1;
        }
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

    /** Value of {@code name="..."} in {@code s} (a tag or a whole document), or "". */
    private static String attribute(String s, String name) {
        int i = 0;
        while (true) {
            i = s.indexOf(name, i);
            if (i < 0) {
                return "";
            }
            int after = i + name.length();
            boolean standalone = (i == 0 || !isNameChar(s.charAt(i - 1)))
                    && after < s.length()
                    && !isNameChar(s.charAt(after));
            if (standalone) {
                int equals = s.indexOf('=', after);
                if (equals < 0) {
                    return "";
                }
                int quoteStart = equals + 1;
                while (quoteStart < s.length() && Character.isWhitespace(s.charAt(quoteStart))) {
                    quoteStart++;
                }
                if (quoteStart >= s.length()) {
                    return "";
                }
                char quote = s.charAt(quoteStart);
                if (quote != '"' && quote != '\'') {
                    return "";
                }
                int quoteEnd = s.indexOf(quote, quoteStart + 1);
                return quoteEnd < 0 ? "" : unescape(s.substring(quoteStart + 1, quoteEnd));
            }
            i = after;
        }
    }

    /**
     * Decodes XML character references in a single pass, so {@code &amp;lt;}
     * becomes {@code &lt;} rather than {@code <}.
     */
    static String unescape(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != '&') {
                out.append(c);
                i++;
                continue;
            }
            int semicolon = s.indexOf(';', i + 1);
            if (semicolon < 0) {
                out.append(s, i, s.length());
                break;
            }
            String decoded = entity(s.substring(i + 1, semicolon));
            if (decoded == null) {
                out.append('&');
                i++;
            } else {
                out.append(decoded);
                i = semicolon + 1;
            }
        }
        return out.toString();
    }

    private static String entity(String name) {
        if ("lt".equals(name)) {
            return "<";
        }
        if ("gt".equals(name)) {
            return ">";
        }
        if ("amp".equals(name)) {
            return "&";
        }
        if ("quot".equals(name)) {
            return "\"";
        }
        if ("apos".equals(name)) {
            return "'";
        }
        if (name.startsWith("#")) {
            try {
                int code = name.startsWith("#x") || name.startsWith("#X")
                        ? Integer.parseInt(name.substring(2), 16)
                        : Integer.parseInt(name.substring(1));
                return new String(Character.toChars(code));
            } catch (RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
