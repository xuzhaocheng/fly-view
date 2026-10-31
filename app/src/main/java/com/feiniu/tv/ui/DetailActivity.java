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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.feiniu.tv.R;
import com.feiniu.tv.adapter.EpisodeAdapter;
import com.feiniu.tv.adapter.GridSpacingItemDecoration;
import com.feiniu.tv.adapter.SeasonAdapter;
import com.feiniu.tv.api.FnApiClient;
import com.feiniu.tv.api.model.Episode;
import com.feiniu.tv.api.model.ItemDetail;
import com.feiniu.tv.api.model.Season;
import com.feiniu.tv.api.model.StreamList;
import com.feiniu.tv.util.Err;
import com.feiniu.tv.util.ImageLoader;
import com.feiniu.tv.util.Prefs;

import java.util.ArrayList;
import java.util.List;

/** 详情页:电影直接播放,电视剧显示季/集。 */
public class DetailActivity extends AppCompatActivity {

    private ImageView imgPoster;
    private TextView tvTitle;
    private TextView tvMeta;
    private TextView tvMediaInfo;
    private TextView tvOverview;
    private Button btnPlay;
    private LinearLayout llSeasons;
    private LinearLayout llEpisodes;
    private RecyclerView rvSeasons;
    private RecyclerView rvEpisodes;

    private FnApiClient client;
    private String guid;
    private String type;
    private String token;

    private SeasonAdapter seasonAdapter;
    private EpisodeAdapter episodeAdapter;
    private final List<Episode> episodeList = new ArrayList<>();
    private String currentEpisodeGuid = "";
    private String itemTitle = "";

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

        imgPoster = findViewById(R.id.img_poster);
        tvTitle = findViewById(R.id.tv_title);
        tvMeta = findViewById(R.id.tv_meta);
        tvMediaInfo = findViewById(R.id.tv_media_info);
        tvOverview = findViewById(R.id.tv_overview);
        btnPlay = findViewById(R.id.btn_play);
        llSeasons = findViewById(R.id.ll_seasons);
        llEpisodes = findViewById(R.id.ll_episodes);
        rvSeasons = findViewById(R.id.rv_seasons);
        rvEpisodes = findViewById(R.id.rv_episodes);

        seasonAdapter = new SeasonAdapter();
        rvSeasons.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvSeasons.setAdapter(seasonAdapter);

        episodeAdapter = new EpisodeAdapter(new EpisodeAdapter.OnEpisodeClick() {
            @Override
            public void onClick(Episode episode) {
                playEpisode(episode);
            }
        });
        // 集列表按屏幕宽度动态列数,紧凑铺满
        float density = getResources().getDisplayMetrics().density;
        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels / density);
        int spanCount = Math.max(8, screenWidthDp / 72);
        rvEpisodes.setLayoutManager(new GridLayoutManager(this, spanCount));
        rvEpisodes.addItemDecoration(new GridSpacingItemDecoration((int) (6 * density), spanCount));
        rvEpisodes.setAdapter(episodeAdapter);

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play(guid);
            }
        });

        loadDetail();
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
                        // 媒体信息拉取失败不影响详情展示
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
                                bindSeasons(seasons, curSeasonGuid);
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                llSeasons.setVisibility(View.GONE);
                                llEpisodes.setVisibility(View.GONE);
                            }
                        });
                    }
                } catch (final Exception e) {
                    Log.e("FeiniuTV", "loadDetail failed", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvOverview.setText("加载失败:\n" + Err.describe(e));
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
        StringBuilder meta = new StringBuilder();
        if (d.release_date != null && !d.release_date.isEmpty()) {
            meta.append(d.release_date);
        }
        if (d.vote_average > 0) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append("★").append(String.format("%.1f", d.vote_average));
        }
        tvMeta.setText(meta.toString());
        tvOverview.setText(d.overview);
        ImageLoader.load(this, imgPoster, client.imageUrl(d.posters, 400), token);
    }

    /** 显示媒体信息:分辨率 / 视频编码 / 音频编码 / 文件大小 / 字幕数。 */
    private void bindMediaInfo(StreamList sl) {
        if (sl == null || sl.video_streams == null || sl.video_streams.isEmpty()) {
            tvMediaInfo.setText("");
            return;
        }
        StreamList.Stream v = sl.video_streams.get(0);
        StreamList.Stream a = (sl.audio_streams != null && !sl.audio_streams.isEmpty())
                ? sl.audio_streams.get(0) : null;

        StringBuilder sb = new StringBuilder();
        if (v.resolution_type != null && !v.resolution_type.isEmpty()) {
            sb.append(v.resolution_type.toUpperCase());
        } else if (v.width > 0 && v.height > 0) {
            sb.append(v.height).append("p");
        }
        if (v.codec_name != null) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append("H.265".equalsIgnoreCase(v.codec_name) ? "HEVC"
                    : v.codec_name.toUpperCase());
        }
        if (a != null && a.codec_name != null) {
            sb.append(" · ").append(a.codec_name.toUpperCase());
            if (a.channels > 0) {
                sb.append(" ").append(a.channels).append("声道");
            }
        }
        if (sl.files != null && !sl.files.isEmpty() && sl.files.get(0).size > 0) {
            sb.append(" · ").append(formatSize(sl.files.get(0).size));
        }
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
            sb.append(" · ");
            if (embedded > 0) {
                sb.append("内嵌字幕").append(embedded);
            }
            if (external > 0) {
                if (embedded > 0) {
                    sb.append(" · ");
                }
                sb.append("外挂字幕").append(external);
            }
        }
        tvMediaInfo.setText(sb.toString());
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
            loadEpisodes(target);
        }
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
                            // 滚动到当前观看的集
                            int idx = episodeAdapter.indexOf(currentEpisodeGuid);
                            if (idx >= 0) {
                                rvEpisodes.scrollToPosition(idx);
                            }
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

    private void play(String itemGuid) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("guid", itemGuid);
        intent.putExtra("title", itemTitle);
        startActivity(intent);
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
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("guid", episode.guid);
        intent.putExtra("next_guid", next);
        intent.putExtra("title", title);
        intent.putExtra("next_title", nextTitle);
        startActivity(intent);
    }
}
