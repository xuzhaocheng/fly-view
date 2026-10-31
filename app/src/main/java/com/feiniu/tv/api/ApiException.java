package com.feiniu.tv.api;

/** 飞牛 API 业务异常(code != 0)。 */
public class ApiException extends Exception {
    public final int code;

    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }
}
