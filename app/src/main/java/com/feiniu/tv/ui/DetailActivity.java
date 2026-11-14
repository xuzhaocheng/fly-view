package com.feiniu.tv.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.feiniu.tv.R;
import com.feiniu.tv.adapter.EpisodeAdapter;
import com.feiniu.tv.adapter.PosterAdapter;
import com.feiniu.tv.adapter.SeasonAdapter;
import com.feiniu.tv.api.FnApiClient;
import com.feiniu.tv.api.model.Episode;
import com.feiniu.tv.api.model.ItemDetail;
import com.feiniu.tv.api.model.MediaDb;
import com.feiniu.tv.api.model.MediaItem;
import com.feiniu.tv.api.model.Season;
import com.feiniu.tv.api.model.StreamList;
import com.feiniu.tv.util.Err;
import com.feiniu.tv.util.ImageLoader;
import com.feiniu.tv.util.Prefs;

import java.util.ArrayList;
import java.util.List;

/** 详情页:电影直接播放,电视剧显示季/集。 */
public class DetailActivity extends AppCompatActivity {

    private ImageView imgBackdrop;
    private ImageView imgPoster;
    private TextView tvTitle;
    private TextView tvOriginal;
    private LinearLayout llInfo;
    private View llContent;
    private TextView tvLoading;
    private boolean infoReady;
    private boolean episodesReady;
    private boolean moreReady;
    private boolean contentRevealed;
    private LinearLayout llMeta;
    private LinearLayout llMedia;
    private TextView tvOverview;
    private TextView tvSeasonSummary;
    private Button btnPlay;
    private Button btnRestart;
    private View llPlay;
    private LinearLayout llSeasons;
    private LinearLayout llEpisodes;
    private LinearLayout llMore;
    private RecyclerView rvSeasons;
    private RecyclerView rvEpisodes;
    private RecyclerView rvMore;
    private PosterAdapter moreAdapter;
    private View fadeEpLeft, fadeEpRight, fadeMoreLeft, fadeMoreRight;

    private FnApiClient client;
    private String guid;
    private String type;
    private String token;

    private SeasonAdapter seasonAdapter;
    private EpisodeAdapter episodeAdapter;
    private final List<Episode> episodeList = new ArrayList<>();
    private String currentEpisodeGuid = "";
    /** 刚点播的集:服务端进度没跟上时,不能用旧的 play/info 把高亮改回去。 */
    private String launchedEpisodeGuid = "";
    private String selectedSeasonGuid = "";
    private String itemTitle = "";
    /** onCreate 后紧跟的那次 onResume 不用刷新观看进度。 */
    private boolean firstResume = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        Prefs prefs = new Prefs(this);
        String host = prefs.getHost();
        token = prefs.getToken();
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(token)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        client = new FnApiClient(host);
        client.setToken(token);

        guid = getIntent().getStringExtra("guid");
        type = getIntent().getStringExtra("type");

        imgBackdrop = findViewById(R.id.img_backdrop);
        imgPoster = findViewById(R.id.img_poster);
        tvTitle = findViewById(R.id.tv_title);
        tvOriginal = findViewById(R.id.tv_original);
        llInfo = findViewById(R.id.ll_info);
        llContent = findViewById(R.id.ll_content);
        tvLoading = findViewById(R.id.tv_loading);
        // 接口再慢也不能一直停在"加载中"
        llContent.postDelayed(new Runnable() {
            @Override
            public void run() {
                infoReady = true;
                episodesReady = true;
                moreReady = true;
                revealInfo();
            }
        }, 1200);
        llMeta = findViewById(R.id.ll_meta);
        llMedia = findViewById(R.id.ll_media);
        tvOverview = findViewById(R.id.tv_overview);
        tvSeasonSummary = findViewById(R.id.tv_season_summary);
        btnPlay = findViewById(R.id.btn_play);
        btnRestart = findViewById(R.id.btn_restart);
        llPlay = findViewById(R.id.ll_play_btn);
        llSeasons = findViewById(R.id.ll_seasons);
        llEpisodes = findViewById(R.id.ll_episodes);
        rvSeasons = findViewById(R.id.rv_seasons);
        rvEpisodes = findViewById(R.id.rv_episodes);
        llMore = findViewById(R.id.ll_more);
        rvMore = findViewById(R.id.rv_more);
        fadeEpLeft = findViewById(R.id.fade_ep_left);
        fadeEpRight = findViewById(R.id.fade_ep_right);
        fadeMoreLeft = findViewById(R.id.fade_more_left);
        fadeMoreRight = findViewById(R.id.fade_more_right);

        seasonAdapter = new SeasonAdapter(new SeasonAdapter.OnSeasonClick() {
            @Override
            public void onClick(Season season) {
                selectSeason(season);
            }
        });
        rvSeasons.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvSeasons.setAdapter(seasonAdapter);

        episodeAdapter = new EpisodeAdapter(new EpisodeAdapter.OnEpisodeClick() {
            @Override
            public void onClick(Episode episode) {
                playEpisode(episode);
            }
        });
        // 集卡片横向滑动:一屏放几张,左右键浏览
        rvEpisodes.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvEpisodes.setAdapter(episodeAdapter);

        moreAdapter = new PosterAdapter(new PosterAdapter.OnPosterClick() {
            @Override
            public void onClick(MediaItem item) {
                openDetail(item);
            }
        }, new PosterAdapter.ImageUrlBuilder() {
            @Override
            public String build(MediaItem item, int width) {
                return client.imageUrl(item.posterGuid(), width);
            }
        }, token, 0, R.layout.item_poster_compact);
        rvMore.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvMore.setAdapter(moreAdapter);

        bindEdgeFade(rvEpisodes, fadeEpLeft, fadeEpRight);
        bindEdgeFade(rvMore, fadeMoreLeft, fadeMoreRight);

        loadDetail();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
            return;
        }
        refreshCurrentEpisode();
    }

    /** 从播放页回来时,把「上次看到哪一集」重新对齐服务端。 */
    private void refreshCurrentEpisode() {
        if (TextUtils.isEmpty(guid) || (!"TV".equals(type) && !"Series".equals(type))) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    com.feiniu.tv.api.model.PlayInfo pi = client.getPlayInfo(guid);
                    final String cur = pi == null || pi.guid == null ? "" : pi.guid;
                    if (cur.isEmpty()) {
                        return;
                    }
                    if (!launchedEpisodeGuid.isEmpty()) {
                        // 本页刚点播过,以用户点的为准;服务端的进度可能还停在别的集
                        return;
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (cur.equals(currentEpisodeGuid)) {
                                return;
                            }
                            currentEpisodeGuid = cur;
                            episodeAdapter.setCurrentGuid(cur);
                            episodeAdapter.notifyDataSetChanged();
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    private void loadDetail() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final ItemDetail detail = client.getItemDetail(guid);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            bindDetail(detail);
                        }
                    });
                    // 拉取媒体信息(分辨率/编码/音频/大小)
                    try {
                        final StreamList sl = client.getStreamListForItem(guid);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                bindMediaInfo(sl);
                            }
                        });
                    } catch (Exception ignored) {
                        // 媒体信息拉取失败不影响详情展示,但页面还是得露出来
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                infoReady = true;
                                revealInfo();
                            }
                        });
                    }
                    if ("TV".equals(type) || "Series".equals(type)) {
                        // 查当前看到哪一集(play/info 顶层 guid 即当前集,parent_guid 即所属季)
                        String curEp = "";
                        String curSeason = "";
                        try {
                            com.feiniu.tv.api.model.PlayInfo pi = client.getPlayInfo(guid);
                            if (pi != null) {
                                curEp = pi.guid == null ? "" : pi.guid;
                                curSeason = pi.parent_guid == null ? "" : pi.parent_guid;
                            }
                        } catch (Exception ignored) {
                        }
                        final String curEpGuid = curEp;
                        final String curSeasonGuid = curSeason;
                        final List<Season> seasons = client.getSeasonList(guid);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                currentEpisodeGuid = curEpGuid;
                                if (seasons == null || seasons.isEmpty()) {
                                    // 没拿到季信息时不装剧集页,退回单集播放按钮
                                    llSeasons.setVisibility(View.GONE);
                                    llEpisodes.setVisibility(View.GONE);
                                    llPlay.setVisibility(View.VISIBLE);
                                    return;
                                }
                                // 剧集页靠季/集选择起播,单独的播放按钮是多余的
                                llPlay.setVisibility(View.GONE);
                                bindSeasons(seasons, curSeasonGuid);
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                llSeasons.setVisibility(View.GONE);
                                llEpisodes.setVisibility(View.GONE);
                                loadMore();
                            }
                        });
                    }
                } catch (final Exception e) {
                    Log.e("FeiniuTV", "loadDetail failed", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvOverview.setText("加载失败:\n" + Err.describe(e));
                            infoReady = true;
                            moreReady = true;
                            episodesReady = true;
                            revealInfo();
                        }
                    });
                }
            }
        }).start();
    }

    private void bindDetail(ItemDetail d) {
        if (d == null) {
            return;
        }
        String title = d.name != null && !d.name.isEmpty() ? d.name : d.title;
        if (d.tv_title != null && !d.tv_title.isEmpty()) {
            title = d.tv_title + " " + d.title;
        }
        itemTitle = title;
        tvTitle.setText(title);

        String original = d.original_title == null ? "" : d.original_title.trim();
        if (!original.isEmpty() && !original.equals(title)) {
            tvOriginal.setText(original);
            tvOriginal.setVisibility(View.VISIBLE);
        } else {
            tvOriginal.setVisibility(View.GONE);
        }

        buildMetaPills(d);
        tvOverview.setText(d.overview);

        // 看过就提供「继续播放」,同时给一条「从头播放」的退路
        final boolean resume = d.watched_ts > 0;
        // 续播时把上次的位置写在按钮上,免得用户猜"继续"是从哪继续
        btnPlay.setText(resume
                ? getString(R.string.continue_play) + "  " + formatClock(d.watched_ts)
                : getString(R.string.play));
        btnRestart.setVisibility(resume ? View.VISIBLE : View.GONE);
        final int startMs = resume ? (int) (d.watched_ts * 1000) : 0;
        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(guid, startMs);
            }
        });
        btnRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(guid, 0);
            }
        });

        int radius = (int) (8 * getResources().getDisplayMetrics().density);
        ImageLoader.load(this, imgPoster, client.imageUrl(d.posters, ImageLoader.W_THUMB), token, radius);
        String backdrop = client.imageUrl(d.backdrops, ImageLoader.W_HERO);
        if (!TextUtils.isEmpty(backdrop)) {
            ImageLoader.load(this, imgBackdrop, backdrop, token);
        }
    }

    /** 显示媒体信息:分辨率 / 视频编码 / 音频编码 / 文件大小 / 字幕数。 */
    /** 文件信息排成两列小表,比一长串 accent 文字更像"资料",也更耐看。 */
    private void bindMediaInfo(StreamList sl) {
        llMedia.removeAllViews();
        infoReady = true;
        if (sl == null || sl.video_streams == null || sl.video_streams.isEmpty()) {
            revealInfo();
            return;
        }
        StreamList.Stream v = sl.video_streams.get(0);
        StreamList.Stream a = (sl.audio_streams != null && !sl.audio_streams.isEmpty())
                ? sl.audio_streams.get(0) : null;

        String resolution = "";
        if (v.resolution_type != null && !v.resolution_type.isEmpty()) {
            resolution = v.resolution_type.toUpperCase();
        } else if (v.width > 0 && v.height > 0) {
            resolution = v.height + "p";
        }
        String codec = v.codec_name == null ? ""
                : ("H.265".equalsIgnoreCase(v.codec_name) ? "HEVC" : v.codec_name.toUpperCase());
        String audio = "";
        if (a != null && a.codec_name != null) {
            audio = a.codec_name.toUpperCase();
            if (a.channels > 0) {
                audio += " " + a.channels + "声道";
            }
        }
        String size = (sl.files != null && !sl.files.isEmpty() && sl.files.get(0).size > 0)
                ? formatSize(sl.files.get(0).size) : "";

        addMediaRow("分辨率", resolution, "编码", codec);
        addMediaRow("音轨", audio, "大小", size);

        if (sl.subtitle_streams != null && !sl.subtitle_streams.isEmpty()) {
            int embedded = 0;
            int external = 0;
            for (StreamList.SubtitleStream s : sl.subtitle_streams) {
                if (s.is_external == 1) {
                    external++;
                } else {
                    embedded++;
                }
            }
            StringBuilder sub = new StringBuilder();
            if (embedded > 0) {
                sub.append("内嵌 ").append(embedded);
            }
            if (external > 0) {
                if (sub.length() > 0) {
                    sub.append(" · ");
                }
                sub.append("外挂 ").append(external);
            }
            addMediaRow("字幕", sub.toString(), null, null);
        }
        revealInfo();
    }

    /**
     * 数据齐了再一次性显示整页:先空后涨的版式跳动比加载慢更难受。
     * 剧集页要等剧集列表也回来,否则季/集区会二次撑开。
     */
    private void revealInfo() {
        if (contentRevealed) {
            return;
        }
        if (!infoReady) {
            return;
        }
        if (isSeries() ? !episodesReady : !moreReady) {
            return;
        }
        contentRevealed = true;
        if (llInfo != null) {
            llInfo.setVisibility(View.VISIBLE);
        }
        if (llContent != null) {
            llContent.setVisibility(View.VISIBLE);
            llContent.setAlpha(0f);
            llContent.animate().alpha(1f).setDuration(160).start();
        }
        if (tvLoading != null) {
            tvLoading.setVisibility(View.GONE);
        }
    }

    private boolean isSeries() {
        return "TV".equals(type) || "Series".equals(type);
    }

    private void addMediaRow(String label1, String value1, String label2, String value2) {
        if (TextUtils.isEmpty(value1) && TextUtils.isEmpty(value2)) {
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);
        row.addView(mediaCell(label1, value1),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        if (label2 != null) {
            row.addView(mediaCell(label2, value2),
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        llMedia.addView(row, rlp);
    }

    private View mediaCell(String label, String value) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.HORIZONTAL);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(getResources().getColor(R.color.text_tertiary));
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(14);
        v.setTextColor(getResources().getColor(R.color.text_primary));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        vlp.leftMargin = dp(10);
        cell.addView(l);
        cell.addView(v, vlp);
        return cell;
    }

    private static String formatSize(long bytes) {
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1) {
            return String.format("%.1fGB", gb);
        }
        return String.format("%.0fMB", bytes / (1024.0 * 1024.0));
    }

    private void bindSeasons(List<Season> seasons, String currentSeasonGuid) {
        llSeasons.setVisibility(View.VISIBLE);
        seasonAdapter.submit(seasons);
        Season target = null;
        if (seasons != null && !seasons.isEmpty()) {
            target = seasons.get(0);
            if (currentSeasonGuid != null && !currentSeasonGuid.isEmpty()) {
                for (Season s : seasons) {
                    if (currentSeasonGuid.equals(s.guid)) {
                        target = s;
                        break;
                    }
                }
            }
        }
        if (target != null) {
            selectSeason(target);
        }
    }

    /**
     * 剧集页没有播放按钮,系统不会自动把焦点给 RecyclerView 的子项,
     * 这里兜底把焦点放到第一季上,否则方向键完全没反应。
     */
    /**
     * 横向列表的左右边缘渐隐:该方向还有内容才显示,用来提示可以继续滑。
     * 不往列表里塞箭头,免得跟焦点导航打架。
     */
    private void bindEdgeFade(final RecyclerView rv, final View left, final View right) {
        if (rv == null) {
            return;
        }
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateEdgeFade(rv, left, right);
            }
        });
        // 数据变化/scrollToPosition 之后要等整帧布局结束才知道能不能往两边滚
        rv.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // 只同步高度:可见性由数据初始化和滚动事件决定,避免布局时序误判
                        syncHeight(left, rv.getHeight());
                        syncHeight(right, rv.getHeight());
                    }
                });
        rv.post(new Runnable() {
            @Override
            public void run() {
                updateEdgeFade(rv, left, right);
            }
        });
    }

    private static void updateEdgeFade(RecyclerView rv, View left, View right) {
        // 用可见区间判断,比滚动范围/偏移稳:那些值在布局刚结束时还没定下来
        boolean canLeft = false;
        boolean canRight = false;
        RecyclerView.LayoutManager lm = rv.getLayoutManager();
        if (lm instanceof LinearLayoutManager && rv.getAdapter() != null) {
            LinearLayoutManager linear = (LinearLayoutManager) lm;
            int first = linear.findFirstVisibleItemPosition();
            int last = linear.findLastVisibleItemPosition();
            int total = rv.getAdapter().getItemCount();
            canLeft = first > 0;
            canRight = last >= 0 && last < total - 1;
        }
        int h = rv.getHeight();
        if (left != null) {
            syncHeight(left, h);
            left.setVisibility(canLeft ? View.VISIBLE : View.GONE);
        }
        if (right != null) {
            syncHeight(right, h);
            right.setVisibility(canRight ? View.VISIBLE : View.GONE);
        }
    }

    /** 渐隐层高度跟随列表(match_parent 会把 wrap_content 的容器撑满屏幕)。 */
    private static void syncHeight(View v, int height) {
        if (height <= 0 || v.getLayoutParams() == null || v.getLayoutParams().height == height) {
            return;
        }
        v.getLayoutParams().height = height;
        v.requestLayout();
    }

    private void ensureFocus() {
        if (getCurrentFocus() != null) {
            return;
        }
        rvSeasons.post(new Runnable() {
            @Override
            public void run() {
                if (getCurrentFocus() != null) {
                    return;
                }
                if (rvSeasons.getChildCount() > 0) {
                    rvSeasons.getChildAt(0).requestFocus();
                } else if (rvEpisodes.getChildCount() > 0) {
                    rvEpisodes.getChildAt(0).requestFocus();
                }
            }
        });
    }

    /** 季行右侧的小结:本季多少集、看过几集。 */
    private void bindSeasonSummary(List<Episode> episodes) {
        if (tvSeasonSummary == null) {
            return;
        }
        int total = episodes == null ? 0 : episodes.size();
        int seen = 0;
        if (episodes != null) {
            for (Episode e : episodes) {
                if (e.watched == 1 || e.watchedRatio() >= 0.9f) {
                    seen++;
                }
            }
        }
        String text = "本季 " + total + " 集";
        if (seen > 0) {
            text += " · 已看 " + seen + " 集";
        }
        tvSeasonSummary.setText(text);
        episodesReady = true;
        revealInfo();
    }

    /** 换季:更新高亮并重新拉这一季的集。 */
    private void selectSeason(Season season) {
        if (season == null) {
            return;
        }
        selectedSeasonGuid = season.guid == null ? "" : season.guid;
        seasonAdapter.setSelectedGuid(selectedSeasonGuid);
        loadEpisodes(season);
    }

    private void loadEpisodes(final Season season) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<Episode> episodes = client.getEpisodeList(season.guid);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            llEpisodes.setVisibility(View.VISIBLE);
                            episodeList.clear();
                            episodeList.addAll(episodes);
                            episodeAdapter.setCurrentGuid(currentEpisodeGuid);
                            episodeAdapter.submit(episodes);
                            bindSeasonSummary(episodes);
                            // 滚动到当前观看的集,再按滚动后的位置算左右渐隐
                            int idx = episodeAdapter.indexOf(currentEpisodeGuid);
                            if (idx >= 0) {
                                rvEpisodes.scrollToPosition(idx);
                            }
                            // 初始渐隐按数据直接定:列表滚到当前集,右侧还有没有看集数
                            syncHeight(fadeEpLeft, rvEpisodes.getHeight());
                            syncHeight(fadeEpRight, rvEpisodes.getHeight());
                            fadeEpLeft.setVisibility(idx > 0 ? View.VISIBLE : View.GONE);
                            fadeEpRight.setVisibility(episodes.size() > 5 ? View.VISIBLE : View.GONE);
                            ensureFocus();
                        }
                    });
                } catch (final Exception e) {
                    Log.e("FeiniuTV", "loadEpisodes failed", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvOverview.setText("加载剧集失败:\n" + Err.describe(e));
                        }
                    });
                }
            }
        }).start();
    }

    /** 电影页底部铺一排同库影片,免得下半屏空着。 */
    private void loadMore() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    MediaDb target = null;
                    for (MediaDb db : client.getMediaDbList()) {
                        if ("Movie".equals(db.category)) {
                            target = db;
                            break;
                        }
                    }
                    if (target == null) {
                        return;
                    }
                    List<MediaItem> items = client.getItemList(target.guid, 12);
                    final List<MediaItem> rest = new ArrayList<MediaItem>();
                    for (MediaItem it : items) {
                        if (it.guid != null && !it.guid.equals(guid)) {
                            rest.add(it);
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!rest.isEmpty()) {
                                moreAdapter.submit(rest);
                                llMore.setVisibility(View.VISIBLE);
                                rvMore.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateEdgeFade(rvMore, fadeMoreLeft, fadeMoreRight);
                                    }
                                });
                            }
                            moreReady = true;
                            revealInfo();
                        }
                    });
                } catch (Exception ignored) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            moreReady = true;
                            revealInfo();
                        }
                    });
                }
            }
        }).start();
    }

    private void openDetail(MediaItem item) {
        if (item == null || TextUtils.isEmpty(item.guid)) {
            return;
        }
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("guid", item.guid);
        intent.putExtra("type", item.type != null ? item.type : "Movie");
        startActivity(intent);
    }

    private void play(String itemGuid, int startMs) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("guid", itemGuid);
        intent.putExtra("title", itemTitle);
        if (startMs > 0) {
            intent.putExtra("start_ms", startMs);
        }
        startActivity(intent);
    }

    // ------------------------------------------------------------ meta 药丸

    /** 评分做成一颗强调色药丸,其余信息排成一行小字,避免一排方块显得碎。 */
    private void buildMetaPills(ItemDetail d) {
        llMeta.removeAllViews();
        if (d.vote_average > 0) {
            addPill("★ " + String.format("%.1f", d.vote_average), true);
        }
        StringBuilder rest = new StringBuilder();
        if (d.release_date != null && d.release_date.length() >= 4) {
            rest.append(d.release_date, 0, 4);
        }
        if (d.runtime > 0) {
            appendSeparator(rest).append(formatRuntime(d.runtime));
        }
        if (!TextUtils.isEmpty(d.content_ratings) && !"NR".equalsIgnoreCase(d.content_ratings)) {
            appendSeparator(rest).append(d.content_ratings);
        }
        if (d.production_countries != null && !d.production_countries.isEmpty()) {
            appendSeparator(rest).append(countryLabel(d.production_countries.get(0)));
        }
        if (d.number_of_seasons > 0) {
            appendSeparator(rest).append(d.number_of_seasons).append(" 季 · ")
                    .append(d.number_of_episodes).append(" 集");
        }
        if (rest.length() == 0) {
            return;
        }
        TextView meta = new TextView(this);
        meta.setText(rest.toString());
        meta.setTextSize(16);
        meta.setTextColor(getResources().getColor(R.color.text_secondary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(14);
        llMeta.addView(meta, lp);
    }

    private static StringBuilder appendSeparator(StringBuilder sb) {
        if (sb.length() > 0) {
            sb.append("  ·  ");
        }
        return sb;
    }

    private void addPill(String text, boolean accent) {
        TextView pill = new TextView(this);
        pill.setText(text);
        pill.setTextSize(14);
        pill.setPadding(dp(14), dp(7), dp(14), dp(7));
        pill.setBackgroundResource(accent ? R.drawable.bg_pill_accent : R.drawable.bg_pill);
        pill.setTextColor(getResources().getColor(accent ? R.color.accent : R.color.text_secondary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(10);
        pill.setLayoutParams(lp);
        llMeta.addView(pill);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private static String formatClock(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        }
        return String.format("%d:%02d", m, s);
    }

    private static String formatRuntime(int minutes) {
        if (minutes < 60) {
            return minutes + " 分钟";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + " 小时" : h + " 小时 " + m + " 分";
    }

    private static String countryLabel(String code) {
        if (code == null) {
            return "";
        }
        switch (code) {
            case "CN": return "中国";
            case "US": return "美国";
            case "JP": return "日本";
            case "GB": return "英国";
            case "FR": return "法国";
            case "DE": return "德国";
            case "IN": return "印度";
            case "KR": return "韩国";
            case "TW": return "台湾";
            case "HK": return "香港";
            case "ES": return "西班牙";
            case "IT": return "意大利";
            case "CA": return "加拿大";
            case "AU": return "澳大利亚";
            case "RU": return "俄罗斯";
            case "TH": return "泰国";
            default: return code;
        }
    }

    /** 播放某一集,并带上下一集 guid 用于自动连播。 */
    private void playEpisode(Episode episode) {
        String next = "";
        String nextTitle = "";
        for (int i = 0; i < episodeList.size(); i++) {
            if (episodeList.get(i).guid != null && episodeList.get(i).guid.equals(episode.guid)) {
                if (i + 1 < episodeList.size()) {
                    Episode ne = episodeList.get(i + 1);
                    next = ne.guid;
                    nextTitle = itemTitle + " 第" + ne.episode_number + "集";
                }
                break;
            }
        }
        String title = itemTitle;
        if (episode.episode_number > 0) {
            title = itemTitle + " 第" + episode.episode_number + "集";
        }
        // 立即把高亮挪到刚点的这一集,不用等服务端
        currentEpisodeGuid = episode.guid == null ? "" : episode.guid;
        launchedEpisodeGuid = currentEpisodeGuid;
        episodeAdapter.setCurrentGuid(currentEpisodeGuid);
        episodeAdapter.notifyDataSetChanged();

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("guid", episode.guid);
        intent.putExtra("next_guid", next);
        intent.putExtra("title", title);
        intent.putExtra("next_title", nextTitle);
        startActivity(intent);
    }
}
