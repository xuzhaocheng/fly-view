# 飞牛TV Lite(Android 4.4 电视客户端)实现计划

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 Android 4.4(API 19)老电视盒子写一个"飞牛影视"客户端,实现登录 → 海报墙 → 选片 → 硬解播放,产出可安装的 APK。

**Architecture:** 原生 Android(Java,单 Activity 多 Fragment/多页面),通过飞牛影视官方 HTTP API(带 authx 签名 + token 鉴权)拉取媒体库/海报/剧集/播放流;海报用 Glide 加载;视频用原生 MediaPlayer + SurfaceView 硬解 H.264。

**Tech Stack:** Java, minSdk 19 / targetSdk 28 / compileSdk 28, Gradle 6.7.1 + AGP 4.2.2 + JDK 11, AndroidX(appcompat 1.1.0 / recyclerview 1.1.0 / cardview 1.0.0), OkHttp 3.12.13, Gson 2.8.9, Glide 4.11.0。全部依赖卡在 Android 4.4 兼容线。

---

## 已确认的飞牛影视 API(逆向自 fnos-tv-web 源码)

- **Base:** `http://<NAS_IP>:5666/v`
- **鉴权:** 登录拿 token 存 `Authorization` 头;每个请求额外带 `authx` 头(签名)。
- **响应统一格式:** `{code:0, data:..., msg:...}`,code=0 成功,code=-2 需重新登录。

### 签名算法(authx)

```
secret  = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
api_key = "16CCEB3D-AB42-077D-36A1-F355324E4237"

path = "/v" + url_path_without_query          // 如 "/v/api/v1/mediadb/list"
// GET: rt = stringify(sorted params + query),把 + 替换成 %20
// 非 GET: rt = JSON.stringify(body) 或 ""
st = md5(decodeURIComponent(rt.replace(/%(?![0-9A-Fa-f]{2})/g, "%25")))
nonce = 6位随机字符串(100000~999999)
timestamp = 当前毫秒时间戳(字符串)
lt = [secret, path, nonce, timestamp, st, api_key].join("_")
sign = md5(lt)
authx = "nonce=" + nonce + "&timestamp=" + timestamp + "&sign=" + sign
```

### 端点

| 用途 | 方法 | 路径 | 关键字段 |
|---|---|---|---|
| 登录 | POST | `/api/v1/login` | body `{username,password,app_name}` → `data.token`(或 authorization) |
| 用户信息 | GET | `/api/v1/user/info` | 校验登录态 |
| 媒体库列表 | GET | `/api/v1/mediadb/list` | `[{guid,title,category}]` |
| 媒体库数量 | GET | `/api/v1/mediadb/sum` | `{guid:count}` |
| 海报墙列表 | POST | `/api/v1/item/list` | body `{ancestor_guid,tags:{type:[Movie,TV,Directory,Video]},exclude_grouped_video:1,sort_type,sort_column,page_size}` |
| 条目详情 | GET | `/api/v1/item/{guid}` | `{name,tv_title,title,overview,posters,backdrops,type,guid}` |
| 播放信息 | POST | `/api/v1/play/info` | body `{item_guid}` → `{item:{guid,parent_guid,season_number,episode_number},type}` |
| 季列表 | GET | `/api/v1/season/list/{guid}` | `[{guid,title,poster}]` |
| 剧集列表 | GET | `/api/v1/episode/list/{guid}` | 剧集数组 |
| 海报图 | GET | `/api/v1/sys/img/92/17/{poster_guid}?w={width}` | 图片流 |

> 已核实的播放流获取流程(逆向自 VideoPlayer.vue):`POST /api/v1/play/info` → `GET /api/v1/stream/list/{guid}?before_play=1` → `POST /api/v1/play/quality {media_guid}` → `POST /api/v1/play/play {media_guid,video_guid,video_encoder,resolution,bitrate,startTimestamp,audio_encoder,audio_guid,subtitle_guid,channels}` → 返回 `play_link`(m3u8 或直链)。

---

## 任务分解

### Task 1: 环境搭建(JDK + Android SDK + Gradle)

**Files:**
- 创建 `settings.gradle`、根 `build.gradle`、`gradle.properties`、`gradle/wrapper/*`

**步骤:**
1. 安装 JDK 11(`apt-get install -y openjdk-11-jdk` 或下载解压),验证 `java -version`。
2. 下载 Android cmdline-tools + platform android-28 + build-tools 28.0.3 + platform-tools,配置 `ANDROID_HOME`。
3. 生成 Gradle wrapper(Gradle 6.7.1)。
4. 验证 `./gradlew -v` 可运行。

**验收:** `./gradlew -v` 输出 Gradle 6.7.1;`$ANDROID_HOME` 指向 SDK。

### Task 2: 签名器 + API 客户端(纯 Java 逻辑,可单测)

**Files:**
- 创建 `app/src/main/java/com/feiniu/tv/api/FnSigner.java`(签名算法,纯 Java,无 Android 依赖)
- 创建 `app/src/main/java/com/feiniu/tv/api/FnApiClient.java`(OkHttp 请求封装:登录/媒体库/列表/详情/播放信息/图片 URL)
- 创建 `app/src/main/java/com/feiniu/tv/api/model/*.java`(Gson 模型)
- 测试 `app/src/test/java/com/feiniu/tv/api/FnSignerTest.java`

**步骤:**
1. 写 FnSignerTest:用已知输入断言 md5 签名结果(用 Gson 对 body 序列化需与 JS 的 JSON.stringify 一致)。
2. 实现 FnSigner(实现上面算法,md5 用 `java.security.MessageDigest`;GET 参数排序用 TreeMap)。
3. 跑 `./gradlew test` 通过。
4. 实现 FnApiClient + 模型;先只做 login + mediadb/list + item/list + sys/img URL 构造。
5. 读 fnos-tv-web 的 Play 视图 + fnos-tv 后端 `Fuction.py`,确认 play/info 返回的流 URL 字段名与登录 body 精确写法,补全注释。

**验收:** `FnSignerTest` 通过;客户端方法能拼出正确 URL 和 header。

### Task 3: 登录页 + token 持久化

**Files:**
- 创建 `LoginActivity.java`、`res/layout/activity_login.xml`、`util/Prefs.java`

**步骤:**
1. Prefs 用 SharedPreferences 存 NAS IP + token。
2. LoginActivity:输入框(IP、账号、密码)+ 登录按钮,调 login 拿 token 存 Prefs,跳 Home。
3. 已有 token 则启动直接进 Home。
4. `./gradlew assembleDebug` 编译通过。

**验收:** 编译通过;逻辑上登录成功跳转、失败提示。

### Task 4: 首页海报墙

**Files:**
- 创建 `HomeActivity.java`、`adapter/CategoryAdapter.java`、`adapter/PosterAdapter.java`、`res/layout/*`

**步骤:**
1. 顶部横向分类栏(媒体库分类),下方海报网格(RecyclerView GridLayoutManager)。
2. D-pad 焦点:遥控器上下切换分类/海报,左右移动,OK 选中。
3. Glide 加载海报图 URL。
4. `./gradlew assembleDebug` 编译通过。

**验收:** 编译通过;页面结构完整,焦点逻辑就位。

### Task 5: 详情页(剧集列表)

**Files:**
- 创建 `DetailActivity.java`、`adapter/EpisodeAdapter.java`、`res/layout/*`

**步骤:**
1. 点海报进入详情:电影直接播放,电视剧显示季/集列表。
2. 调 item/{guid}、season/list、episode/list。
3. 选中某集 → 进入播放。

**验收:** 编译通过。

### Task 6: 播放器

**Files:**
- 创建 `PlayerActivity.java`、`res/layout/activity_player.xml`

**步骤:**
1. 调 play/info 拿到流 URL(优先 h264/原画直链)。
2. MediaPlayer + SurfaceView 播放;遥控器上下/左右调音量进度,OK 暂停,返回退出。
3. 处理 MediaPlayer 生命周期(onPause/onStop 释放)。

**验收:** 编译通过,产出 APK。

### Task 7: 打包产出 APK

**步骤:**
1. `./gradlew assembleDebug` 生成 `app/build/outputs/apk/debug/app-debug.apk`。
2. 确认 APK minSdk=19、含 armeabi-v7a(及 universal)。
3. 输出 APK 路径给用户,指导 adb/优盘安装到盒子。

**验收:** 产出 APK,用户可安装。

---

## 风险与说明

- **真机验证只能用户做**:本机无 4.4 盒子/模拟器,播放与焦点需用户在真机确认,遇到问题迭代。
- **2 个 API 细节待核实**(登录 body、流 URL 字段),已列入 Task 2,不影响主体结构。
- **H.265/4K 不支持**:老盒子硬解不了,仅保证 H.264 1080P。
- **环境搭建可能受限**(下载 SDK 需网络/磁盘),若受限则改为交付源码+用户本地构建。

## 测试策略

- 可单测的纯逻辑(FnSigner、URL 构造、Gson 解析)用 JVM JUnit 覆盖。
- UI/播放/焦点依赖真机,由用户手动验证,记录问题迭代。
