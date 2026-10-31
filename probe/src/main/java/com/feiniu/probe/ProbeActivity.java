package com.feiniu.probe;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 探针:只验证第三方 App 能不能调用夏普/海思的 TV API 切信号源。
 * 全部走反射,不依赖 com.sharp.android.tvapi.jar 的编译期符号。
 * 结果同时写入 logcat(tag=FNPROBE)和屏幕上的 TextView。
 */
public class ProbeActivity extends Activity {

    private static final String TAG = "FNPROBE";
    private static final String SHARP_SOURCE = "com.sharp.android.tvapi.source.SharpSource";

    private final StringBuilder report = new StringBuilder();
    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        tv = new TextView(this);
        tv.setTextSize(16f);
        tv.setPadding(40, 40, 40, 40);
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        setContentView(sv);

        // SharpSource 的构造函数里会 new Handler(),必须在有 Looper 的线程上跑,
        // 所以直接放主线程执行(都是轻量调用)。
        probe();
        tv.setText(report.toString());
    }

    private void line(String s) {
        Log.i(TAG, s);
        report.append(s).append('\n');
    }

    private void probe() {
        line("=== FNPROBE start ===");
        line("uid=" + android.os.Process.myUid()
                + " pkg=" + getPackageName()
                + " sdk=" + android.os.Build.VERSION.SDK_INT);

        // 1) 当前信号源(存于 Settings.System,普通 App 可读)
        try {
            int src = Settings.System.getInt(getContentResolver(), "sourceid_changed", -1);
            line("[settings] sourceid_changed=" + src);
        } catch (Throwable t) {
            line("[settings] FAIL " + t);
        }

        // 2) 拿到 SharpSource 单例
        Object sharpSource = null;
        try {
            Class<?> cls = Class.forName(SHARP_SOURCE);
            Object inst = cls.getMethod("getInstance").invoke(null);
            sharpSource = inst != null ? inst : cls.getMethod("getInstance").invoke(null);
            line("[class] " + SHARP_SOURCE + " loaded, instance=" + sharpSource);
        } catch (Throwable t) {
            line("[class] FAIL " + describe(t));
            return;
        }

        // 3) 只读接口
        Object curId = call(sharpSource, "getCurSourceId", new Class[]{}, new Object[]{});
        line("[read] getCurSourceId() -> " + curId);

        dumpList(sharpSource, "getSourceList");
        dumpList(sharpSource, "getAvailSourceList");

        Object status = call(sharpSource, "getSignalStatus", new Class[]{}, new Object[]{});
        line("[read] getSignalStatus() -> " + status);

        dumpEnum("com.sharp.android.tvapi.constant.EnumSourceIndex");

        // 支持 --ei src <id> 直接切源,用于真机往返测试
        int wantSrc = getIntent().getIntExtra("src", Integer.MIN_VALUE);
        if (wantSrc != Integer.MIN_VALUE) {
            Object r = call(sharpSource, "selectSource",
                    new Class[]{int.class, boolean.class}, new Object[]{wantSrc, true});
            line("[switch] selectSource(" + wantSrc + ", true) -> " + r);
            Object now = call(sharpSource, "getCurSourceId", new Class[]{}, new Object[]{});
            line("[switch] now getCurSourceId() -> " + now);
        }

        // 4) 写接口:切到"当前源",理论上画面不变,只为验证权限
        int idToTest = curId instanceof Integer ? (Integer) curId : -1;
        if (idToTest >= 0) {
            Object r = call(sharpSource, "selectSource",
                    new Class[]{int.class, boolean.class}, new Object[]{idToTest, false});
            line("[write] selectSource(" + idToTest + ", false) -> " + r);
        } else {
            line("[write] skipped: current source id unusable");
        }

        line("=== FNPROBE end ===");
    }

    /** 把常量类里所有 int 字段名和值一起打出来,用来建立 id -> 名字 的映射 */
    private void dumpEnum(String className) {
        try {
            Class<?> cls = Class.forName(className);
            StringBuilder sb = new StringBuilder();
            for (java.lang.reflect.Field f : cls.getFields()) {
                if (f.getType() == int.class) {
                    sb.append(f.getName()).append('=').append(f.getInt(null)).append("  ");
                }
            }
            line("[enum] " + className + " -> " + sb);
        } catch (Throwable t) {
            line("[enum] " + className + " FAIL " + describe(t));
        }
    }

    private void dumpList(Object target, String method) {
        try {
            Method m = target.getClass().getMethod(method);
            Object r = m.invoke(target);
            if (r instanceof List) {
                List<?> l = (List<?>) r;
                StringBuilder sb = new StringBuilder();
                for (Object o : l) {
                    sb.append(o).append('(').append(o == null ? "null" : o.getClass().getSimpleName()).append(") ");
                }
                line("[read] " + method + "() size=" + l.size() + " -> " + sb);
            } else {
                line("[read] " + method + "() -> " + r);
            }
        } catch (Throwable t) {
            line("[read] " + method + "() FAIL " + describe(t));
        }
    }

    private Object call(Object target, String name, Class<?>[] types, Object[] args) {
        try {
            Method m = target.getClass().getMethod(name, types);
            return m.invoke(target, args);
        } catch (Throwable t) {
            return "EX:" + describe(t);
        }
    }

    /** 把反射包起来的异常剥到根因,方便看是不是 SecurityException */
    private String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
