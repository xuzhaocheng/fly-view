package com.feiniu.tv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.feiniu.tv.R;
import com.feiniu.tv.adapter.GridSpacingItemDecoration;
import com.feiniu.tv.adapter.HomeSectionAdapter;
import com.feiniu.tv.adapter.PosterAdapter;
import com.feiniu.tv.api.FnApiClient;
import com.feiniu.tv.api.model.MediaDb;
import com.feiniu.tv.api.model.MediaItem;
import com.feiniu.tv.util.ImageLoader;
import com.feiniu.tv.util.Prefs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首页结构对齐网页端:媒体库(电影/电视剧) + 继续观看 + 每个媒体库一行;
 * 全部承载在一个纵向 RecyclerView 里,焦点与滚动交给它统一处理。
 */
public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "FeiniuTV";
    private static final int CONTINUE_LIMIT = 12;

    private RecyclerView rvHome;
    private RecyclerView rvPosters;
    private TextView tvLibraryTitle;
    private View llHome;
    private View llLibrary;
    private View llSettings;
    private android.widget.LinearLayout llSettingsPanel;

    private HomeSectionAdapter homeAdapter;
    private PosterAdapter posterAdapter;

    private FnApiClient client;
    private Prefs prefs;
    private String token;

    private final List<HomeSectionAdapter.Section> sections = new ArrayList<>();
    private final List<MediaDb> libraries = new ArrayList<>();
    private final Map<String, List<MediaItem>> cache = new HashMap<>();
    private MediaDb currentLibrary;
    /** 从播放页/详情页返回时不刷新,免得把刚看过的列表又重建一遍。 */
    private boolean childActivityOpen = false;
    /** 在库网格里被刷新的情况下,回首页时补一次重载。 */
    private boolean homeStale = false;
    private long lastLoadAt = 0;
    /** 重建列表后要恢复到的行(适配器位置),-1 表示回到第一行。 */
    private int pendingFocusPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        startCastService();

        prefs = new Prefs(this);
        String host = prefs.getHost();
        token = prefs.getToken();
        if (host == null || host.isEmpty() || token == null || token.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        client = new FnApiClient(host);
        client.setToken(token);

        bindViews();
        initLists();
        loadHome();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (client == null) {
            return; // 没登录(或正在跳登录页),没有可刷的东西
        }
        if (childActivityOpen) {
            childActivityOpen = false;
            return;
        }
        if (lastLoadAt > 0 && System.currentTimeMillis() - lastLoadAt < 5000) {
            return; // onCreate 里刚加载过,这次 resume 是它带来的
        }
        refresh();
    }

    /**
     * 回到前台时重新拉数据:飞牛那边增删了资源(重新扫描媒体库),这里只有重拉才看得到。
     * 首页在前台就重建,否则只清缓存并标记,等回首页再载。
     */
    private void refresh() {
        cache.clear();
        if (llHome != null && llHome.getVisibility() == View.VISIBLE) {
            pendingFocusPosition = currentSectionPosition();
            loadHome();
            return;
        }
        homeStale = true;
        if (currentLibrary != null) {
            loadLibraryItems(currentLibrary);
        }
    }

    /** 当前焦点落在首页哪一行(适配器位置);焦点不在 rv_home 里时返回 -1。 */
    private int currentSectionPosition() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return -1;
        }
        View node = focused;
        android.view.ViewParent parent = node.getParent();
        while (parent != null && parent != rvHome) {
            if (!(parent instanceof View)) {
                return -1;
            }
            node = (View) parent;
            parent = node.getParent();
        }
        return parent == rvHome ? rvHome.getChildAdapterPosition(node) : -1;
    }

    private void bindViews() {
        rvHome = findViewById(R.id.rv_home);
        rvPosters = findViewById(R.id.rv_posters);
        tvLibraryTitle = findViewById(R.id.tv_library_title);
        llHome = findViewById(R.id.ll_home);
        llLibrary = findViewById(R.id.ll_library);
        llSettings = findViewById(R.id.ll_settings);
        llSettingsPanel = findViewById(R.id.ll_settings_panel);

        findViewById(R.id.tv_back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                backToHome();
            }
        });
    }

    private void initLists() {
        homeAdapter = new HomeSectionAdapter(new HomeSectionAdapter.Callbacks() {
            @Override
            public void onLibrary(MediaDb db) {
                openLibrary(db);
            }

            @Override
            public void onContinue(MediaItem item) {
                resumePlay(item);
            }

            @Override
            public void onPoster(MediaItem item) {
                openDetail(item);
            }

            @Override
            public void onMore(MediaDb db) {
                openLibrary(db);
            }

            @Override
            public void onSettings() {
                showSettings();
            }
        }, token);
        homeAdapter.setUrlBuilder(new HomeSectionAdapter.UrlBuilder() {
            @Override
            public String build(MediaItem item, int width) {
                return client.imageUrl(item.posterGuid(), width);
            }
        });
        rvHome.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        rvHome.setClipChildren(false);
        rvHome.setClipToPadding(false);
        rvHome.setAdapter(homeAdapter);

        posterAdapter = new PosterAdapter(new PosterAdapter.OnPosterClick() {
            @Override
            public void onClick(MediaItem item) {
                openDetail(item);
            }
        }, new PosterAdapter.ImageUrlBuilder() {
            @Override
            public String build(MediaItem item, int width) {
                return client.imageUrl(item.posterGuid(), width);
            }
        }, token);
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels / density);
        int spanCount = Math.max(4, screenWidthDp / 200);
        rvPosters.setLayoutManager(new GridLayoutManager(this, spanCount));
        rvPosters.addItemDecoration(new GridSpacingItemDecoration((int) (10 * density), spanCount));
        rvPosters.setAdapter(posterAdapter);
    }

    // ------------------------------------------------------------------ 数据

    private void loadHome() {
        // 进来就记时间:onCreate 之后紧跟的 onResume 不应该再触发一次刷新
        lastLoadAt = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<MediaDb> libs = new ArrayList<>();
                try {
                    for (MediaDb db : client.getMediaDbList()) {
                        if (!"Others".equals(db.category)) {
                            libs.add(db);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "library load failed", e);
                }
                List<MediaItem> continues = new ArrayList<>();
                try {
                    continues = client.getPlayList();
                } catch (Exception e) {
                    Log.e(TAG, "continue load failed", e);
                }
                final Map<String, List<MediaItem>> previews = new HashMap<>();
                for (MediaDb db : libs) {
                    try {
                        previews.put(db.guid, client.getItemList(db.guid, 4));
                    } catch (Exception e) {
                        Log.e(TAG, "preview load failed: " + db.guid, e);
                    }
                }
                final List<MediaItem> shown = firstN(continues, CONTINUE_LIMIT);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        buildSections(libs, shown, previews);
                    }
                });
            }
        }).start();
    }

    private void buildSections(List<MediaDb> libs, List<MediaItem> continues,
                               Map<String, List<MediaItem>> previews) {
        libraries.clear();
        libraries.addAll(libs);

        sections.clear();
        sections.add(new HomeSectionAdapter.Section(
                HomeSectionAdapter.TYPE_HEADER, "", null, false));

        HomeSectionAdapter.Section librarySection =
                new HomeSectionAdapter.Section(HomeSectionAdapter.TYPE_LIBRARIES, "媒体库", null, false);
        librarySection.dbList.addAll(libs);
        librarySection.previews.putAll(previews);
        librarySection.loading = false;
        sections.add(librarySection);

        HomeSectionAdapter.Section continueSection =
                new HomeSectionAdapter.Section(HomeSectionAdapter.TYPE_CONTINUE, "继续观看", null, false);
        continueSection.items.addAll(continues);
        continueSection.loading = false;
        sections.add(continueSection);

        for (MediaDb db : libs) {
            final HomeSectionAdapter.Section section =
                    new HomeSectionAdapter.Section(HomeSectionAdapter.TYPE_POSTERS,
                            db.title == null ? "" : db.title, db, true);
            sections.add(section);
            loadSectionItems(section);
        }
        homeAdapter.submit(sections);
        prefetchFirstScreen(continues, previews);
        lastLoadAt = System.currentTimeMillis();
        int target = pendingFocusPosition;
        pendingFocusPosition = -1;
        focusSection(target > 0 ? target : 1);
    }

    /** 首屏图片先落盘,滚到位时直接出图(只预取继续观看和媒体库卡片)。 */
    private void prefetchFirstScreen(List<MediaItem> continues,
                                     Map<String, List<MediaItem>> previews) {
        for (MediaItem item : continues) {
            ImageLoader.prefetch(this, client.imageUrl(item.posterGuid(), ImageLoader.W_WIDE),
                    token, ImageLoader.W_WIDE);
        }
        for (List<MediaItem> list : previews.values()) {
            for (MediaItem item : list) {
                ImageLoader.prefetch(this, client.imageUrl(item.posterGuid(), ImageLoader.W_THUMB),
                        token, ImageLoader.W_THUMB);
            }
        }
    }

    /** 每个库取最近 20 条填进自己的行;「更多」才是完整网格。 */
    private void loadSectionItems(final HomeSectionAdapter.Section section) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<MediaItem> items = client.getItemList(
                            section.db.guid, 20, "DESC", "create_time");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            section.items.clear();
                            section.items.addAll(items);
                            section.loading = false;
                            int index = sections.indexOf(section);
                            if (index >= 0) {
                                homeAdapter.updateSection(index);
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "section load failed: " + section.db.guid, e);
                }
            }
        }).start();
    }

    private void loadLibraryItems(final MediaDb db) {
        List<MediaItem> cached = cache.get(db.guid);
        if (cached != null) {
            posterAdapter.submit(cached);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<MediaItem> items = client.getItemList(db.guid, 200,
                            "DESC", "create_time");
                    cache.put(db.guid, items);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (currentLibrary == db) {
                                posterAdapter.submit(items);
                            }
                        }
                    });
                } catch (final Exception e) {
                    Log.e(TAG, "library items failed", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(HomeActivity.this,
                                    "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    // ------------------------------------------------------------------ 导航

    private void openLibrary(MediaDb db) {
        currentLibrary = db;
        tvLibraryTitle.setText(db.title == null ? "" : db.title);
        llHome.setVisibility(View.GONE);
        llLibrary.setVisibility(View.VISIBLE);
        posterAdapter.submit(new ArrayList<MediaItem>());
        loadLibraryItems(db);
        findViewById(R.id.tv_back).requestFocus();
    }

    private void backToHome() {
        currentLibrary = null;
        llLibrary.setVisibility(View.GONE);
        llHome.setVisibility(View.VISIBLE);
        if (homeStale) {
            // 在库里的时候被刷过,回首页补一次重载
            homeStale = false;
            loadHome();
            return;
        }
        focusFirstSection();
    }

    /** 焦点回到媒体库的第一张卡片,而不是顶部按钮。 */
    private void focusFirstSection() {
        focusSection(1);
    }

    /** 把焦点放到第 {@code position} 行的第一张卡片上;行不可用时退回 rv_home。 */
    private void focusSection(final int position) {
        if (homeAdapter.getItemCount() == 0) {
            return;
        }
        rvHome.post(new Runnable() {
            @Override
            public void run() {
                int pos = position;
                if (pos <= 0 || pos >= homeAdapter.getItemCount()) {
                    pos = 1;
                }
                RecyclerView.ViewHolder vh =
                        rvHome.findViewHolderForAdapterPosition(pos);
                if (vh != null) {
                    View row = vh.itemView.findViewById(R.id.rv_row);
                    if (row instanceof RecyclerView && ((RecyclerView) row).getChildCount() > 0) {
                        ((RecyclerView) row).getChildAt(0).requestFocus();
                        return;
                    }
                }
                rvHome.requestFocus();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (llSettings != null && llSettings.getVisibility() == View.VISIBLE) {
            hideSettings();
            return;
        }
        if (llLibrary != null && llLibrary.getVisibility() == View.VISIBLE) {
            backToHome();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------- 设置/详情

    private void showSettings() {
        llSettingsPanel.removeAllViews();
        addSettingItem("刷新媒体库", new Runnable() {
            @Override
            public void run() {
                hideSettings();
                refresh();
                Toast.makeText(HomeActivity.this, "正在刷新…", Toast.LENGTH_SHORT).show();
            }
        });
        final TextView cacheItem = addSettingItem("清理图片缓存", new Runnable() {
            @Override
            public void run() {
                ImageLoader.clearCache(HomeActivity.this);
                hideSettings();
                Toast.makeText(HomeActivity.this, "图片缓存已清理", Toast.LENGTH_SHORT).show();
            }
        });
        // 缓存大小要遍历目录,别在主线程算
        new Thread(new Runnable() {
            @Override
            public void run() {
                final long size = ImageLoader.diskCacheSize(HomeActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String text = size >= 1024 * 1024
                                ? String.format("%.1f MB", size / (1024.0 * 1024.0))
                                : (size / 1024) + " KB";
                        cacheItem.setText("清理图片缓存(" + text + ")");
                    }
                });
            }
        }, "img-cache-size").start();

        addSettingItem("退出登录", new Runnable() {
            @Override
            public void run() {
                prefs.clearToken();
                startActivity(new Intent(HomeActivity.this, LoginActivity.class));
                finish();
            }
        });
        llSettings.setVisibility(View.VISIBLE);
        if (llSettingsPanel.getChildCount() > 0) {
            llSettingsPanel.getChildAt(0).requestFocus();
        }
    }

    private TextView addSettingItem(String label, final Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(getResources().getColor(R.color.text_primary));
        tv.setTextSize(22);
        tv.setPadding(20, 18, 20, 18);
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(true);
        tv.setBackgroundResource(R.drawable.bg_poster_card);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 10;
        tv.setLayoutParams(lp);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        llSettingsPanel.addView(tv);
        return tv;
    }

    private void hideSettings() {
        llSettings.setVisibility(View.GONE);
    }

    private void openDetail(MediaItem item) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("guid", item.guid);
        intent.putExtra("type", item.type != null ? item.type : "Movie");
        childActivityOpen = true;
        startActivity(intent);
    }

    /** 「继续观看」直接起播,跳到上次的位置,不走详情页。 */
    private void resumePlay(MediaItem item) {
        if (TextUtils.isEmpty(item.guid)) {
            return;
        }
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("guid", item.guid);
        intent.putExtra("title", continueTitle(item));
        if (item.ts > 0) {
            // ts 是秒(long),播放页按 int 毫秒读,这里必须显式转型
            intent.putExtra("start_ms", (int) (item.ts * 1000));
        }
        childActivityOpen = true;
        startActivity(intent);
    }

    private static String continueTitle(MediaItem item) {
        String name = !TextUtils.isEmpty(item.parent_title) ? item.parent_title
                : (!TextUtils.isEmpty(item.tv_title) ? item.tv_title : item.title);
        if (name == null) {
            return "";
        }
        if (item.episode_number > 0) {
            return name + " 第" + item.episode_number + "集";
        }
        return name;
    }

    private void startCastService() {
        try {
            startService(new Intent(this, com.feiniu.tv.cast.service.CastService.class));
        } catch (RuntimeException e) {
            Log.w(TAG, "cannot start cast service", e);
        }
    }

    private static List<MediaItem> firstN(List<MediaItem> list, int n) {
        if (list == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(list.subList(0, Math.min(n, list.size())));
    }
}
