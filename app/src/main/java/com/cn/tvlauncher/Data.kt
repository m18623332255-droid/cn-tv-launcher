package com.cn.tvlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS).build()
const val UA = "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36"

fun get(url: String, referer: String? = null): String {
    val b = Request.Builder().url(url).header("User-Agent", UA)
    if (referer != null) b.header("Referer", referer)
    http.newCall(b.build()).execute().use { r -> if (!r.isSuccessful) error("HTTP ${r.code}"); return r.body!!.string() }
}

// ---------------- 应用 ----------------
/** icon 为已缩放好的位图（缓存，避免每次刷新重新加载 Drawable 和重组） */
data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap?)

/** 常见 TVBox 类壳子包名（社区常见，尽力列出） */
val TVBOX_PKGS = listOf("com.github.tvbox.osc", "com.github.tvbox.osc.tk", "com.github.tvbox.osc.bh", "com.github.tvbox.osc.wxwz",
    "com.fongmi.android.tv", "com.github.catvod", "com.tvbox.osc", "com.box.tvbox", "com.takagen.tvbox", "com.mygithub0.tvbox0.osc")
const val HOME_VIDEO_PKG = "com.home.tvvideo"

/** 没装主流平台时的兜底播放器：TVBox 包名 → 名字含 TVBox/影视 → 家庭影视（纯函数，单元测试） */
fun fallbackPlayer(all: List<AppInfo>): AppInfo? =
    TVBOX_PKGS.firstNotNullOfOrNull { p -> all.firstOrNull { it.pkg == p } }
        ?: all.firstOrNull { it.label.contains("tvbox", true) || (it.label.contains("影视") && it.pkg != HOME_VIDEO_PKG) }
        ?: all.firstOrNull { it.pkg == HOME_VIDEO_PKG }

/** 视频平台：包名尽力列出（同一平台多个版本按顺序取第一个已装的） */
data class Platform(val key: String, val name: String, val color: Long, val pkgs: List<String>)
val PLATFORMS = listOf(
    Platform("tencent", "腾讯视频", 0xFF1F8BFF, listOf("com.ktcp.video", "com.ktcp.tvvideo", "com.tencent.qqlivetv", "com.ktcp.csvideo")), // 云视听极光 / 腾讯视频TV
    Platform("iqiyi", "爱奇艺", 0xFF16C35A, listOf("com.gitvdemo.video", "com.gitv.tv.launcher", "com.qiyi.video.tv")), // 银河奇异果
    Platform("youku", "优酷", 0xFF1AA9F0, listOf("com.cibn.tv", "com.yunos.tv.yingshi.boutique", "com.youku.taitan.tv")), // CIBN 酷喵
    Platform("mgtv", "芒果TV", 0xFFFF7A1A, listOf("com.hunantv.license", "com.mgtv.tv")),
    Platform("bili", "B站", 0xFFFB7299, listOf("com.xiaodianshi.tv.yst")), // 云视听小电视
    Platform("home", "家庭影视", 0xFF5B5CE2, listOf("com.home.tvvideo")),
)

object Apps {
    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, Pair<String, AppInfo>>()
    fun installed(c: Context): List<AppInfo> {
        val pm = c.packageManager
        val seen = LinkedHashMap<String, AppInfo>()
        fun ver(p: String) = runCatching { pm.getPackageInfo(p, 0).lastUpdateTime }.getOrDefault(0L)
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val list = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0) }.getOrDefault(emptyList())
            for (ri in list) {
                val p = ri.activityInfo.packageName
                if (p == c.packageName || p in seen) continue
                val key = "$p@${ver(p)}"
                val cached = iconCache[p]
                seen[p] = if (cached != null && cached.first == key) cached.second else
                    AppInfo(p, ri.loadLabel(pm).toString(), runCatching { ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap() }.getOrNull())
                        .also { iconCache[p] = key to it }
            }
        }
        iconCache.keys.retainAll(seen.keys)
        return seen.values.sortedBy { it.label }
    }
    fun isInstalled(c: Context, p: String) = runCatching { c.packageManager.getPackageInfo(p, 0); true }.getOrDefault(false)
    fun platformPkg(c: Context, pl: Platform) = pl.pkgs.firstOrNull { isInstalled(c, it) }
    fun launchIntent(c: Context, p: String): Intent? =
        (c.packageManager.getLeanbackLaunchIntentForPackage(p) ?: c.packageManager.getLaunchIntentForPackage(p))?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun launch(c: Context, p: String): Boolean = launchIntent(c, p)?.let { runCatching { c.startActivity(it); true }.getOrDefault(false) } ?: false

    /** 尝试用平台搜索标题；平台没有公开 SEARCH 入口时直接打开应用 */
    fun openSearch(c: Context, p: String, q: String) {
        val pm = c.packageManager
        for (a in listOf(Intent.ACTION_SEARCH, "android.search.action.GLOBAL_SEARCH")) {
            val i = Intent(a).setPackage(p).putExtra("query", q).putExtra("keyword", q).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pm.queryIntentActivities(i, 0).isNotEmpty() && runCatching { c.startActivity(i); true }.getOrDefault(false)) return
        }
        launchIntent(c, p)?.putExtra("query", q)?.putExtra("keyword", q)?.putExtra("title", q)?.let { if (runCatching { c.startActivity(it); true }.getOrDefault(false)) return }
        launch(c, p)
    }

    /** 点击推荐：已装平台 → TVBox/影视类 → 家庭影视 → 提示片名 */
    fun openRec(c: Context, r: Rec, all: List<AppInfo>) {
        if (r.pkg.isNotBlank() && isInstalled(c, r.pkg)) { openSearch(c, r.pkg, r.title); return }
        val fb = fallbackPlayer(all)
        if (fb != null) { openSearch(c, fb.pkg, r.title); return }
        Toast.makeText(c, "《${r.title}》：本机没有可播放的影视应用", Toast.LENGTH_SHORT).show()
    }

    /** 厂商设置包名（海信等，尽力列出） */
    val VENDOR_SETTINGS = listOf("com.android.tv.settings", "com.hisense.tv.settings", "com.hisense.settings", "com.jamdeo.settings",
        "com.hisense.hitv.settings", "com.xiaomi.mitv.settings", "com.tcl.settings", "com.tianci.setting", "com.android.settings")

    /** 打开电视原来的系统设置 */
    fun openSystemSettings(c: Context) {
        fun go(i: Intent?) = i != null && runCatching { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
        if (go(Intent(Settings.ACTION_SETTINGS))) return
        for (p in VENDOR_SETTINGS) if (go(launchIntent(c, p))) return
        if (go(Intent("android.settings.SETTINGS"))) return
        if (go(Intent().setClassName("com.android.tv.settings", "com.android.tv.settings.MainSettings"))) return
        Toast.makeText(c, "没找到系统设置", Toast.LENGTH_SHORT).show()
    }
    /** 默认应用架：已装平台 + 其余 */
    fun shelf(c: Context, all: List<AppInfo>): List<AppInfo> {
        val chosen = Prefs.shelf
        if (chosen.isNotEmpty()) return chosen.mapNotNull { p -> all.firstOrNull { it.pkg == p } }
        val plat = PLATFORMS.mapNotNull { pl -> all.firstOrNull { it.pkg in pl.pkgs } }
        return plat + all.filter { it !in plat }
    }
}

// ---------------- 今日热播（豆瓣公开接口） ----------------
data class Rec(val id: String, val title: String, val rate: String, val cover: String, val kind: String, var platform: String = "", var pkg: String = "")

object Recs {
    val KINDS = linkedMapOf("movie" to "电影", "tv" to "剧集", "show" to "综艺")
    fun url(kind: String): String {
        val (type, tag) = when (kind) { "movie" -> "movie" to "热门"; "tv" -> "tv" to "热门"; else -> "tv" to "综艺" }
        return "https://movie.douban.com/j/search_subjects?type=$type&tag=${Uri.encode(tag)}&sort=recommend&page_limit=20&page_start=0"
    }
    /** 解析 search_subjects 返回（纯函数，单元测试） */
    fun parse(json: String, kind: String): List<Rec> {
        val a = JSONObject(json).optJSONArray("subjects") ?: return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val t = o.optString("title").trim(); val cov = o.optString("cover")
            if (t.isEmpty() || cov.isEmpty()) null else Rec(o.optString("id"), t, o.optString("rate"), cov, kind)
        }
    }
    /** 把推荐轮流分配给本机已装的平台（豆瓣不提供版权平台，故只能“在该平台搜索”） */
    fun assign(list: List<Rec>, plats: List<Pair<String, String>>): List<Rec> {
        if (plats.isEmpty()) return list   // 没装平台也照样显示，点击时再找 TVBox/家庭影视
        return list.mapIndexed { i, r -> r.copy(platform = plats[i % plats.size].first, pkg = plats[i % plats.size].second) }
    }
    fun fetch(c: Context): Map<String, List<Rec>> {
        val plats = PLATFORMS.filter { it.key != "home" && it.key !in Prefs.hiddenPlatforms }
            .mapNotNull { pl -> Apps.platformPkg(c, pl)?.let { pl.name to it } }
        val out = LinkedHashMap<String, List<Rec>>()
        for (k in Prefs.recKinds) runCatching { out[k] = assign(parse(get(url(k), "https://movie.douban.com/"), k), plats) }
        return out
    }
}

// ---------------- 天气（open-meteo，无需 key） ----------------
data class Weather(val temp: Int, val text: String, val icon: String, val sunrise: String, val sunset: String)
object WeatherApi {
    fun desc(code: Int): Pair<String, String> = when (code) {
        0 -> "晴" to "☀"; 1 -> "晴间多云" to "🌤"; 2 -> "多云" to "⛅"; 3 -> "阴" to "☁"; 45, 48 -> "雾" to "🌫"
        in 51..57 -> "毛毛雨" to "🌦"; 61, 80 -> "小雨" to "🌧"; 63, 81 -> "中雨" to "🌧"; 65, 82 -> "大雨" to "🌧"; 66, 67 -> "冻雨" to "🌧"
        71, 85 -> "小雪" to "🌨"; 73 -> "中雪" to "🌨"; 75, 86 -> "大雪" to "❄"; 77 -> "雪粒" to "🌨"; in 95..99 -> "雷阵雨" to "⛈"
        else -> "—" to "☁"
    }
    fun parse(json: String): Weather {
        val o = JSONObject(json); val cw = o.getJSONObject("current_weather"); val d = o.optJSONObject("daily")
        val (t, i) = desc(cw.optInt("weathercode"))
        return Weather(Math.round(cw.optDouble("temperature")).toInt(), t, i,
            d?.optJSONArray("sunrise")?.optString(0)?.substringAfter("T") ?: "", d?.optJSONArray("sunset")?.optString(0)?.substringAfter("T") ?: "")
    }
    fun fetch(c: City) = parse(get("https://api.open-meteo.com/v1/forecast?latitude=${c.lat}&longitude=${c.lon}&current_weather=true&daily=sunrise,sunset&timezone=Asia%2FShanghai&forecast_days=1"))
}

// ---------------- 继续观看 ----------------
data class Cont(val title: String, val sub: String, val source: String, val image: String, val progress: Float, val intent: Intent?)
object Continue {
    /** 家庭影视 ContentProvider：content://com.home.tvvideo.continue/items */
    fun homeVideo(c: Context): List<Cont> = runCatching {
        val out = ArrayList<Cont>()
        c.contentResolver.query(Uri.parse("content://com.home.tvvideo.continue/items"), null, null, null, null)?.use { cur ->
            while (cur.moveToNext() && out.size < 10) {
                fun s(n: String) = cur.getColumnIndex(n).let { if (it < 0) "" else cur.getString(it) ?: "" }
                val pos = s("pos").toLongOrNull() ?: 0; val dur = s("dur").toLongOrNull() ?: 0
                val i = Intent().setComponent(ComponentName("com.home.tvvideo", "com.home.tvvideo.MainActivity"))
                    .putExtra("resume_key", s("key")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                out += Cont(s("title"), s("epName"), "家庭影视", s("pic"), if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f, i)
            }
        }
        out
    }.getOrDefault(emptyList())

    /** Android TV Watch Next（API 26+，需 READ_TV_LISTINGS；去广告时会尝试 pm grant） */
    fun watchNext(c: Context): List<Cont> {
        if (Build.VERSION.SDK_INT < 26) return emptyList()
        return runCatching {
            val out = ArrayList<Cont>()
            c.contentResolver.query(Uri.parse("content://android.media.tv/watch_next_program"), null, null, null, null)?.use { cur ->
                fun s(n: String) = cur.getColumnIndex(n).let { if (it < 0) "" else cur.getString(it) ?: "" }
                while (cur.moveToNext() && out.size < 10) {
                    val pos = s("last_playback_position_millis").toLongOrNull() ?: 0; val dur = s("duration_millis").toLongOrNull() ?: 0
                    val intent = runCatching { Intent.parseUri(s("intent_uri"), Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.getOrNull()
                    val pkg = s("package_name")
                    val lbl = runCatching { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                    out += Cont(s("title"), s("episode_title"), lbl, s("poster_art_uri").ifBlank { s("thumbnail_uri") },
                        if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f, intent ?: Apps.launchIntent(c, pkg))
                }
            }
            out
        }.getOrDefault(emptyList())
    }
    fun all(c: Context) = homeVideo(c) + watchNext(c)
}
