# 清爽桌面

国内安卓电视桌面，包名 `com.cn.tvlauncher`。界面按 1920×1080 设计，遥控器即可操作。当前版本 1.0.10。

[![构建](https://github.com/m18623332255-droid/cn-tv-launcher/actions/workflows/android.yml/badge.svg)](https://github.com/m18623332255-droid/cn-tv-launcher/actions/workflows/android.yml)

## 功能

- **今日热播**：从豆瓣公开榜单读取电影、剧集、综艺，无论本机是否安装了腾讯视频、爱奇艺等平台都会显示。已安装的平台会轮流分配；点击后用对应平台搜索。没装这些平台时，依次尝试 TVBox、名称里带「影视」的应用、家庭影视；都没有时只提示片名。
- **壁纸**：电影海报轮播，或换成动态壁纸（极光、星尘、流光、偷看小猫）、内置静态图，也可以用手机扫码上传自己的照片。壁纸固定在上方，应用架在下方，滚动时壁纸不动，视口里只露出一排图标。浅色壁纸上的标题和时钟带半透明底，保证能看清。
- **标题**：可在右上角显示一行标题（设置里选预设或自定义，最多 20 字）。
- **主页键 / 返回键**：回到顶部，焦点回到第一个图标。
- **应用架**：每行 8 个。第一行固定有「电视设置」（打开系统原来的设置）和「桌面设置」，其余位置放常用应用，可在设置里调整顺序。超过 8 个时排到下面的行，按遥控器向下键滚动。
- **时钟、农历与天气**：首页显示时间、公历、农历；天气来自 [Open-Meteo](https://open-meteo.com/)（无需密钥），城市可在设置中更换。
- **昼夜主题**：按所选城市的日出日落自动切换深浅色，也可以固定浅色或深色。
- **屏保**：海报轮播加大时钟，按 OK 打开当前影片。可在系统里选择「清爽桌面屏保」。
- **继续观看**：读取家庭影视的继续观看列表，以及 Android TV 的 Watch Next（需要 `READ_TV_LISTINGS`）。
- **去广告**：通过本机 ADB 停用推广和开机广告包，并尝试设为默认桌面。1.0.10 的清单更全，还会标出疑似广告。包名里带 `setting` 的设置类应用不会被停用。做法参考 [Free My TV](https://github.com/simbasjungles/free-my-tv)。
- **安全模式**：启动时如果上次运行留下了崩溃记录，会显示错误信息，并保留「电视设置」和已安装应用列表。
- **一键还原**：按变更记录重新启用被停用的包，交回默认桌面，并恢复原来的屏保。

## 安装

安装包在 [Releases](https://github.com/m18623332255-droid/cn-tv-launcher/releases) 下载，文件名为 `cn-tvlauncher-<版本>-debug.apk`。这是 debug 包，可直接安装。

### 用 U 盘

1. 下载 APK，复制到 U 盘。
2. 把 U 盘插到电视上，用系统文件管理器打开该 APK 并安装。
3. 如果提示不能安装未知来源应用，到系统设置里允许当前文件管理器安装应用。
4. 按遥控器主页键，在桌面选择界面里选「清爽桌面」。

### 用 adb

电视和电脑在同一局域网。先在电视上打开开发者选项和 USB 调试或网络调试（各品牌的按键路径在应用内「设置 → 开发者选项教程」）。电脑安装 [platform-tools](https://developer.android.com/tools/releases/platform-tools)。

```bash
adb connect <电视IP>:5555
adb install -r cn-tvlauncher-v1.0.10-debug.apk
```

安装后按主页键，选择「清爽桌面」作为默认桌面。

Android 11 及以上的「无线调试」需要配对码，本应用的本机 ADB 不支持。请在电脑上执行 `adb tcpip 5555`，让电视监听 5555 端口。

## 去广告说明与免责声明

去广告需要电视打开 USB 调试。应用连接本机 `127.0.0.1:5555`，第一次会在电视上弹出「允许 USB 调试吗？」，请勾选「一律允许」。

它会：

- 执行 `cmd package set-home-activity`，把本应用设为默认桌面；
- 对原厂桌面和一份社区整理的推广/开机广告包执行 `pm disable-user --user 0`（停用，不卸载）；包名包含 `setting` 的设置应用会跳过；
- 可选地把系统屏保设为「清爽桌面屏保」；
- 把变更记在本机，并备份到 `settings global`，便于卸载重装后还原。

**停用系统包有风险，由使用者自行承担。** 可能出现无法开机、丢失输入源或设置、回不到原厂桌面等情况。这份清单来自公开讨论，**没有在真实设备上测试过**，不同固件的包名也可能不一样。创维/酷开、海信、当贝等机型默认只切换默认桌面，不停用原厂桌面。

请在还能用电脑 adb 连上电视时再试。还原方式：

- 应用内「设置 → 恢复原厂桌面」；
- 或 `adb shell pm enable <包名>`（被停用的包名在「去广告」页的变更记录里）。

卸载本应用之前，请先执行一次还原。

## 从源码构建

需要 JDK 17 和 Android SDK（compileSdk 34）。仓库已带 Gradle Wrapper。

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

调试包路径：`app/build/outputs/apk/debug/app-debug.apk`。

推送到 GitHub 时，Actions 会构建 debug APK。推送 `v*` 标签时，同一工作流会创建 Release 并附上 APK。

## 许可证与致谢

本仓库以 [MIT 许可证](LICENSE) 发布。

`app/src/main/java/com/cn/tvlauncher/Adb.java` 改编自 [Free My TV](https://github.com/simbasjungles/free-my-tv) 的 `app/src/io/freemytv/Adb.java`（MIT，Copyright (c) 2026 Free My TV contributors）。文件头中的版权与许可声明保持原样。去广告的整体思路（本机 localhost ADB、`set-home-activity`、`pm disable-user`、变更记录与一键还原）也参考该项目。许可证全文见 [THIRD_PARTY_FREE_MY_TV_LICENSE.txt](THIRD_PARTY_FREE_MY_TV_LICENSE.txt)。

「偷看小猫」动态壁纸是 [LottieFiles 上的 Peekaboo cat](https://lottiefiles.com/free-animation/peekaboo-cat-o7AV4dTpEZ)（作者 Charly Hill），采用 Lottie Simple License，见 [THIRD_PARTY_PEEKCAT.txt](THIRD_PARTY_PEEKCAT.txt)。

今日热播数据来自豆瓣公开接口。天气来自 [Open-Meteo](https://open-meteo.com/)。
