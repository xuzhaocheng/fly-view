package com.feiniu.tv.cast.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Request headers for fetching media from a CDN.
 *
 * <p>Many CDNs reject unknown clients or serve a different encoding depending
 * on the client they think they are talking to, so the receiver presents
 * itself as a desktop browser.
 */
public final class MediaHttpHeaders {

    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static final String ACCEPT = "video/*,application/vnd.apple.mpegurl,*/*";

    private MediaHttpHeaders() {
    }

    /**
     * A fresh, mutable header map. Callers may add {@code Referer} or cookies
     * per stream without affecting other requests.
     */
    public static Map<String, String> defaults() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("User-Agent", USER_AGENT);
        headers.put("Accept", ACCEPT);
        return headers;
    }
}
