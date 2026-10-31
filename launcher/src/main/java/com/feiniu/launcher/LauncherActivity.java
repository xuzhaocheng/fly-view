package com.feiniu.launcher;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 极简电视桌面:已安装应用网格 + 时钟。 */
public class LauncherActivity extends AppCompatActivity {

    private static final String OUR_APP = "com.feiniu.tv";

    /** 本次开机后是否已经显示过桌面(BootReceiver 用它决定还要不要补刀)。 */
    static volatile boolean shownSinceBoot = false;

    private TextView tvClock;
    private TextView tvDate;
    private TextView tvHint;
    private RecyclerView rvApps;
    private AppAdapter adapter;

    private final Handler handler = new Handler();
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("M月d日 EEEE", Locale.CHINA);

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            tvClock.setText(timeFmt.format(now));
            tvDate.setText(dateFmt.format(now));
            handler.postDelayed(this, 20000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        tvClock = findViewById(R.id.tv_clock);
        tvDate = findViewById(R.id.tv_date);
        tvHint = findViewById(R.id.tv_hint);
        rvApps = findViewById(R.id.rv_apps);

        adapter = new AppAdapter(this);
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels / density);
        int span = Math.max(4, screenWidthDp / 170);
        rvApps.setLayoutManager(new GridLayoutManager(this, span));
        rvApps.setAdapter(adapter);

        clockTick.run();
        loadApps();
        updateHint();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            boolean on = !Prefs.autoStart(this);
            Prefs.setAutoStart(this, on);
            updateHint();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void updateHint() {
        tvHint.setText(getString(R.string.clock_hint, Prefs.autoStart(this) ? "开" : "关"));
    }

    private void loadApps() {
        final PackageManager pm = getPackageManager();
        final List<AppAdapter.AppEntry> apps = new ArrayList<>();
        try {
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolves = pm.queryIntentActivities(main, 0);
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : resolves) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) {
                    continue; // 不显示桌面自己
                }
                if (seen.contains(pkg)) {
                    continue;
                }
                Intent launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null) {
                    continue;
                }
                seen.add(pkg);
                CharSequence label = ri.loadLabel(pm);
                Drawable icon = ri.loadIcon(pm);
                apps.add(new AppAdapter.AppEntry(label == null ? pkg : label.toString(), icon, launch));
            }
        } catch (Exception ignored) {
        }

        // 牛影TV 置顶
        Collections.sort(apps, new Comparator<AppAdapter.AppEntry>() {
            @Override
            public int compare(AppAdapter.AppEntry a, AppAdapter.AppEntry b) {
                boolean aOurs = OUR_APP.equals(packageOf(a));
                boolean bOurs = OUR_APP.equals(packageOf(b));
                if (aOurs && !bOurs) return -1;
                if (!aOurs && bOurs) return 1;
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        // 末尾加"设置"
        Drawable setIcon = null;
        try {
            setIcon = pm.getApplicationIcon("com.android.settings");
        } catch (Exception e) {
            try {
                setIcon = pm.getApplicationIcon("com.fsc.thtv.setting");
            } catch (Exception ignored) {
            }
        }
        if (setIcon == null) {
            setIcon = getResources().getDrawable(android.R.drawable.ic_menu_manage);
        }
        apps.add(new AppAdapter.AppEntry(getString(R.string.settings), setIcon,
                new Intent(Settings.ACTION_SETTINGS)));

        adapter.submit(apps);
        rvApps.post(new Runnable() {
            @Override
            public void run() {
                if (adapter.getItemCount() > 0) {
                    rvApps.scrollToPosition(0);
                }
            }
        });
    }

    private static String packageOf(AppAdapter.AppEntry e) {
        return e.intent != null && e.intent.getComponent() != null
                ? e.intent.getComponent().getPackageName() : "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        shownSinceBoot = true;
        clockTick.run();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
    }
}
