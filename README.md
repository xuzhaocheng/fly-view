# 飞映 / FlyView

飞牛（fnOS）影视的 Android TV 客户端，为 **Android 4.4（API 19）** 老电视写的：
纯原生 Java + 系统 `MediaPlayer` 硬解，没有花哨的动画，遥控器优先。

顺带内置了一个 **DLNA 投屏接收端**，老电视不用装任何投屏 App 也能接手机的投屏。

## 功能

- 登录 NAS 后展示媒体库、继续观看、各库海报墙，布局对齐飞牛网页版
- 详情页选季选集、字幕/音轨/清晰度切换、片头片尾自动跳过
- 「继续观看」进度回写，跨设备续播
- 投屏接收：标准 UPnP AVTransport（DMR-1.50），支持手机端投屏、暂停、拖进度、调音量
- 投屏时与本地播放共用同一个播放页，标题显示真实片名

## 构建

需要 JDK 8+、Android SDK（platform 33 / build-tools 30+）：

```bash
export JAVA_HOME=/path/to/jdk
echo "sdk.dir=/path/to/android-sdk" > local.properties
gradle assembleDebug      # 调试包
gradle assembleRelease    # 体验更好的包（未开混淆，行为与调试包一致）
```

安装到电视：

```bash
adb connect <TV_IP>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Android 4.4 只认 v1 签名，`assembleRelease` 若自行配置签名，务必保留 v1 scheme。

## 使用

首次启动填 NAS 地址（如 `http://192.168.1.8:5666`）和账号，之后免登录。

投屏：手机和电视在同一局域网，在支持投屏的 App 里选择「飞映」即可。投屏服务
在 App 启动后常驻（前台服务 + 开机自启），控制端口 50000–50009 顺延，
SSDP 监听 1900。

## 已知限制

- 投屏播放不会把进度回写到飞牛的「继续观看」（推过来的地址里没有内容 ID）
- 只做了 DMR 接收端，不包含 DMS（浏览 NAS 文件）与 DMC（遥控别的设备）
- 部分发送端在 DIDL 里不带 `dc:title`，此时标题回退为文件名

## 免责声明

非官方客户端。接口与签名方式来自对飞牛网页端的分析，仅供个人学习与自用，
请勿用于商业用途；由此产生的风险由使用者自行承担。

## 目录

```
app/src/main/java/com/feiniu/tv/
├── api/      飞牛 HTTP 接口与 authx 签名
├── cast/     DLNA 接收端（SSDP / SOAP / GENA / 会话状态机）
├── ui/       登录、首页、详情、播放页
├── adapter/  首页分区、海报网格等 RecyclerView 适配器
└── util/     图片加载、偏好设置、错误描述
launcher/     同源的极简 TV 桌面（实验）
probe/        海思/夏普信号源 API 探针（实验）
```
