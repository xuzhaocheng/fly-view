package com.feiniu.tv.api;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * 参考值由 /tmp/fn_sign_ref.js(Node 复刻 JS 算法,固定 nonce=123456, timestamp=1700000000000)生成。
 */
public class FnSignerTest {

    private static final String NONCE = "123456";
    private static final String TS = "1700000000000";

    @Test
    public void md5Empty() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", FnSigner.md5Hex(""));
    }

    @Test
    public void md5Hello() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", FnSigner.md5Hex("hello"));
    }

    @Test
    public void stringifyParamsSortsAndEncodes() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("b", "2");
        m.put("a", "1");
        assertEquals("a=1&b=2", FnSigner.stringifyParams(m));
    }

    @Test
    public void stringifyParamsSpace() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("q", "hello world");
        assertEquals("q=hello%20world", FnSigner.stringifyParams(m));
    }

    @Test
    public void signGetNoParams() {
        String authx = FnSigner.sign("GET", "/v/api/v1/mediadb/list", null, null, NONCE, TS);
        assertEquals("nonce=123456&timestamp=1700000000000&sign=e66026ea494b211557f15d2d1e2a3c46", authx);
    }

    @Test
    public void signPostPlayInfo() {
        String authx = FnSigner.sign("POST", "/v/api/v1/play/info", "{\"item_guid\":\"abc\"}", null, NONCE, TS);
        assertEquals("nonce=123456&timestamp=1700000000000&sign=53893c288764584aaeb29a3d3b126a33", authx);
    }

    @Test
    public void signGetImageWithParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("w", "200");
        String authx = FnSigner.sign("GET", "/v/api/v1/sys/img/92/17/abc123", null, params, NONCE, TS);
        assertEquals("nonce=123456&timestamp=1700000000000&sign=92ce0aa5950d31399b468ee4a20d2ecc", authx);
    }
}
