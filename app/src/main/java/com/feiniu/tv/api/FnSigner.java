package com.feiniu.tv.api;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 复刻飞牛影视官方客户端的 authx 签名算法(逆向自 fnos-tv-web)。
 * 纯 Java、无 Android 依赖,可单元测试。
 */
public final class FnSigner {

    private static final String SECRET = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh";
    private static final String API_KEY = "16CCEB3D-AB42-077D-36A1-F355324E4237";

    private FnSigner() {
    }

    /**
     * 生成 authx 头的值:nonce=...&timestamp=...&sign=...
     *
     * @param httpMethod GET/POST/PUT/DELETE
     * @param pathWithV  签名路径,形如 "/v/api/v1/mediadb/list"(含 /v 前缀、不含 query)
     * @param jsonBody   非 GET 请求的 JSON 请求体(GET 传 null)
     * @param params     GET 请求的 query 参数(GET 之外传 null 或空 map)
     */
    public static String sign(String httpMethod, String pathWithV, String jsonBody, Map<String, String> params) {
        String nonce = String.format(Locale.US, "%06d", 100000 + (int) (Math.random() * 900000));
        String timestamp = String.valueOf(System.currentTimeMillis());
        return sign(httpMethod, pathWithV, jsonBody, params, nonce, timestamp);
    }

    /** 测试用:固定 nonce/timestamp,便于与 JS 参考实现对照。 */
    static String sign(String httpMethod, String pathWithV, String jsonBody, Map<String, String> params,
                       String nonce, String timestamp) {
        String upper = httpMethod.toUpperCase(Locale.US);
        String rt;
        if ("GET".equals(upper)) {
            rt = stringifyParams(params);
        } else {
            rt = (jsonBody != null) ? jsonBody : "";
        }
        String st = hashSignatureData(rt);
        String lt = SECRET + "_" + pathWithV + "_" + nonce + "_" + timestamp + "_" + st + "_" + API_KEY;
        String sign = md5Hex(lt);
        return "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign;
    }

    /** GET 参数序列化:key 升序、URL 编码、空格转 %20(等价 JS 的 stringifyParams)。 */
    static String stringifyParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        TreeMap<String, String> sorted = new TreeMap<>(params);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            String v = e.getValue();
            if (v == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(urlEncode(e.getKey())).append('=').append(urlEncode(v));
        }
        return sb.toString().replace("+", "%20");
    }

    /** 等价 JS 的 hashSignatureData:先修复裸 %,再 decodeURIComponent,再 md5。 */
    static String hashSignatureData(String s) {
        String fixed = s.replaceAll("%(?![0-9A-Fa-f]{2})", "%25");
        String decoded = decodeURIComponent(fixed);
        return md5Hex(decoded);
    }

    /** 等价 JS 的 decodeURIComponent:只解 %XX(UTF-8),不解 + 为空格。 */
    static String decodeURIComponent(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length() && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))) {
                out.write(Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                byte[] utf8 = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                out.write(utf8, 0, utf8.length);
            }
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** application/x-www-form-urlencoded 编码(空格转 +,特殊字符 %XX)。 */
    private static String urlEncode(String s) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append((char) c);
            } else if (c == ' ') {
                sb.append('+');
            } else {
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return sb.toString();
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }

    /** 对 UTF-8 字节计算 MD5,返回小写十六进制字符串。 */
    public static String md5Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 unavailable", e);
        }
    }
}
