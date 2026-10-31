package com.feiniu.tv.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/** 异常格式化工具:输出完整类名 + 消息 + 堆栈(便于真机报错定位)。 */
public final class Err {

    private Err() {
    }

    public static String describe(Throwable t) {
        if (t == null) {
            return "未知错误";
        }
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String full = sw.toString();
        // 截断到 800 字符,避免刷屏
        if (full.length() > 800) {
            full = full.substring(0, 800);
        }
        return full;
    }
}
