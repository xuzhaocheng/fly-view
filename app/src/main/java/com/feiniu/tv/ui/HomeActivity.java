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
        focusFirstSection();
    }

    /** 每个库取最近 20 条填进自己的行;「更多」里才是完整网格。 */
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
        focusFirstSection();
    }

    /** 焦点回到媒体库的第一张卡片,而不是顶部按钮。 */
    private void focusFirstSection() {
        if (homeAdapter.getItemCount() == 0) {
            return;
        }
        rvHome.post(new Runnable() {
            @Override
            public void run() {
                RecyclerView.ViewHolder vh =
                        rvHome.findViewHolderForAdapterPosition(1);
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

    private void addSettingItem(String label, final Runnable action) {
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
    }

    private void hideSettings() {
        llSettings.setVisibility(View.GONE);
    }

    private void openDetail(MediaItem item) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("guid", item.guid);
        intent.putExtra("type", item.type != null ? item.type : "Movie");
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
