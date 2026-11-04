package com.cn.tvlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private val recs = mutableStateOf<Map<String, List<Rec>>>(emptyMap())
    private val weather = mutableStateOf<Weather?>(null)
    private val cont = mutableStateOf<List<Cont>>(emptyList())
    private val apps = mutableStateOf<List<AppInfo>>(emptyList())
    private val now = mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0))   // 只有 Clock 读取
    private val dark = mutableStateOf(false)   // 1.0.1/1.0.2 闪退根因：这里曾在 Prefs.init 之前调用 isDark()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Prefs.init(this)
        val crash = Crash.read(this)
        if (crash != null) { setContent { InitScale(); SafeMode(crash) }; return }
        dark.value = isDark()
        recs.value = loadRecs(); weather.value = weatherCache()
        setContent { InitScale(); Home() }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) {
            val t = LocalDateTime.now(); now.value = t.withSecond(0).withNano(0)   // 同一分钟内值相等，不触发重组
            dark.value = isDark()
            delay((60 - t.second) * 1000L + 50)
        } } }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { runCatching {
                val a = withContext(Dispatchers.IO) { Apps.installed(this@MainActivity) }
                if (a != apps.value) apps.value = a
                val cn = withContext(Dispatchers.IO) { Continue.all(this@MainActivity) }
                if (cn.map { it.title + it.sub + it.progress } != cont.value.map { it.title + it.sub + it.progress }) cont.value = cn
                // 每小时刷新推荐与天气
                if (System.currentTimeMillis() - Prefs.sp.getLong("recTime", 0) > 3600_000 || recs.value.isEmpty()) {
                    val r = withContext(Dispatchers.IO) { runCatching { Recs.fetch(this@MainActivity) }.getOrDefault(emptyMap()) }
                    if (r.values.any { it.isNotEmpty() }) { recs.value = r; saveRecs(r) }
                    else if (System.currentTimeMillis() - Prefs.sp.getLong("recTime", 0) > 6 * 3600_000) recs.value = emptyMap()
                    withContext(Dispatchers.IO) { runCatching { WeatherApi.fetch(Prefs.city) }.getOrNull() }?.let { weather.value = it; saveWeather(it) }
                }
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Crash.save(this@MainActivity, it, fatal = false) }
                delay(60_000)
            }
        } }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent) } // 主页键：保持在桌面
    @Deprecated("Deprecated") override fun onBackPressed() { /* 桌面不退出 */ }

    override fun onResume() {
        super.onResume()
        if (Prefs.sp.getBoolean("cityChanged", false)) { Prefs.sp.edit().putBoolean("cityChanged", false).putLong("recTime", 0).apply() }
    }

    @Composable fun Home() {
        val ver = Prefs.ver.intValue
        val dk = remember(dark.value, ver) { isDark() }
        val p = remember(dk) { Pal(dk) }
        var tab by remember { mutableIntStateOf(0) }
        val tabs = remember(ver) { listOf("推荐") + Recs.KINDS.filterKeys { it in Prefs.recKinds }.values }
        if (tab >= tabs.size) tab = 0
        val rv = recs.value
        val list = remember(rv, tab, tabs) { when (tab) { 0 -> mixed(rv); else -> rv[Recs.KINDS.entries.first { it.value == tabs[tab] }.key] ?: emptyList() } }
        val top = Prefs.top
        val showCont = cont.value.isNotEmpty() && (top == "continue" || (top == "auto" && dk && list.isEmpty()))
        val shelfFocus = remember { FocusRequester() }

        val screenH = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp
        val tiles = shelfTiles()
        val rows = remember(tiles) { listOf(tiles.take(8)) + tiles.drop(8).chunked(8) }
        Column(Modifier.fillMaxSize().background(p.bg).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(screenH)) {
            // 顶部标签（推荐/电影/剧集/综艺）
            if (!showCont && list.isNotEmpty() && top != "clock") Row(Modifier.padding(start = 100.u, top = 60.u).clip(RoundedCornerShape(40.u)).background(p.tabs)
                .border(1.u, p.glassBorder, RoundedCornerShape(40.u)).padding(8.u), horizontalArrangement = Arrangement.spacedBy(6.u)) {
                tabs.forEachIndexed { i, s ->
                    var fo by remember { mutableStateOf(false) }
                    val on = i == tab
                    Box(Modifier.clip(RoundedCornerShape(30.u)).background(if (on) Color.White else if (fo) Color(0x55FFFFFF) else Color.Transparent)
                        .tvClick({ fo = it; if (it) tab = i }) { tab = i }.padding(horizontal = 30.u, vertical = 10.u)) {
                        T(s, 24, if (on) Color(0xFF111111) else p.fg, FontWeight.SemiBold, alpha = if (on) 1f else .7f)
                    }
                }
            }
            val bigClock = list.isEmpty() && !showCont
            if (!bigClock) Clock(p, false, Modifier.align(Alignment.TopEnd).padding(top = 50.u, end = 100.u))
            when {
                bigClock -> Clock(p, true, Modifier.align(Alignment.TopCenter).padding(top = 180.u))
                showCont -> { T("继续观看", 34, p.fg, FontWeight.Bold, Modifier.padding(start = 100.u, top = 170.u)); ContRow(cont.value) }
                else -> { T("今日热播 · 豆瓣", 34, p.fg, FontWeight.Bold, Modifier.padding(start = 100.u, top = 170.u)); RecRow(list, tab) }
            }
            ShelfRow(p, rows[0], Modifier.align(Alignment.BottomCenter), shelfFocus)
        }
        // 第 9 个起的应用：往下排，按“下”键时焦点进入并自动滚动露出
        rows.drop(1).forEach { r -> ShelfRow(p, r, Modifier.padding(top = 0.u), null) }
        Spacer(Modifier.height(40.u))
        }
        LaunchedEffect(Unit) { delay(300); runCatching { shelfFocus.requestFocus() } }
    }

    /** 时钟单独成组件：每分钟只重组这几行文字 */
    @Composable fun Clock(p: Pal, big: Boolean, m: Modifier) {
        val t = now.value
        val hm = remember(t) { t.format(DateTimeFormatter.ofPattern("HH:mm")) }
        val dl = remember(t.toLocalDate()) { dateLine(t.toLocalDate()) }
        val w = weather.value?.let { "${it.icon} ${Prefs.city.name} ${it.text} ${it.temp}°" }
        Column(m, horizontalAlignment = if (big) Alignment.CenterHorizontally else Alignment.End) {
            T(hm, if (big) 200 else 64, p.fg, FontWeight.Light)
            T(if (w != null && !big) "$dl  $w" else dl, if (big) 34 else 22, p.fg, modifier = Modifier.padding(top = 6.u), alpha = .75f)
            if (big && w != null) T(w, 34, p.fg, modifier = Modifier.padding(top = 12.u), alpha = .8f)
        }
    }

    @Composable fun RecRow(list: List<Rec>, tab: Int) {
        val ctx = LocalContext.current
        val st = rememberLazyListState()
        LaunchedEffect(tab) { st.scrollToItem(0) }
        LazyRow(Modifier.padding(top = 240.u).height(480.u).fillMaxWidth(), st, contentPadding = PaddingValues(horizontal = 100.u),
            horizontalArrangement = Arrangement.spacedBy(36.u), verticalAlignment = Alignment.Bottom) {
            itemsIndexed(list.take(20), key = { _, r -> r.kind + r.id }, contentType = { _, _ -> "rec" }) { i, r ->
                var fo by remember { mutableStateOf(false) }
                val sc by animateFloatAsState(if (fo) 1.08f else 1f, tween(120), label = "s")
                val shape = RoundedCornerShape(24.u)
                Box(Modifier.size(300.u, 440.u).graphicsLayer { scaleX = sc; scaleY = sc }.clip(shape).background(grad(i + 1))
                    .then(if (fo) Modifier.border(4.u, Color.White, shape) else Modifier)
                    .tvClick({ fo = it }) { Apps.openRec(ctx, r, apps.value) }) {
                    Poster(r.cover, Modifier.fillMaxSize())
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x99000000)).padding(start = 18.u, end = 12.u, top = 12.u, bottom = 14.u)) {
                        Column {
                            T(r.title, 28, Color.White, FontWeight.Bold)
                            if (fo) T(Recs.KINDS[r.kind].orEmpty() + (if (r.rate.isNotBlank()) " · 豆瓣 ${r.rate}" else "") +
                                (if (r.platform.isNotBlank()) " · ${r.platform}" else ""), 20, Color.White, modifier = Modifier.padding(top = 6.u), alpha = .9f)
                        }
                    }
                }
            }
        }
    }

    @Composable fun ContRow(list: List<Cont>) {
        val ctx = LocalContext.current
        LazyRow(Modifier.padding(top = 240.u).height(420.u).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 100.u),
            horizontalArrangement = Arrangement.spacedBy(36.u), verticalAlignment = Alignment.Bottom) {
            itemsIndexed(list, key = { i, c -> "$i-${c.title}" }) { i, c ->
                var fo by remember { mutableStateOf(false) }
                val sc by animateFloatAsState(if (fo) 1.08f else 1f, tween(120), label = "s")
                Box(Modifier.size(440.u, 330.u).graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(28.u)).background(grad(i * 3 + 4))
                    .then(if (fo) Modifier.border(4.u, Color.White, RoundedCornerShape(28.u)) else Modifier)
                    .tvClick({ fo = it }) { c.intent?.let { runCatching { ctx.startActivity(it) } } }) {
                    if (c.image.isNotBlank()) Poster(c.image, Modifier.fillMaxSize(), 440, 330)
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(110.u).background(Color(0x88000000)))
                    T(c.source, 18, Color.White, modifier = Modifier.padding(start = 24.u, top = 20.u).clip(RoundedCornerShape(10.u)).background(Color(0x59000000)).padding(horizontal = 12.u, vertical = 4.u))
                    T(c.title + if (c.sub.isNotBlank()) " · ${c.sub}" else "", 30, Color.White, FontWeight.Bold, Modifier.align(Alignment.BottomStart).padding(start = 24.u, bottom = 44.u, end = 24.u))
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 24.u, end = 24.u, bottom = 22.u).fillMaxWidth().height(8.u).clip(RoundedCornerShape(4.u)).background(Color(0x4DFFFFFF))) {
                        Box(Modifier.fillMaxWidth(c.progress).fillMaxHeight().clip(RoundedCornerShape(4.u)).background(Color.White))
                    }
                }
            }
        }
    }

    class Tile(val key: String, val label: String, val icon: androidx.compose.ui.graphics.ImageBitmap?, val glyph: String?, val onClick: () -> Unit)

    /** 第一排：前 6 个应用 + 电视设置 + 桌面设置（固定可达）；其余应用每排 8 个 */
    @Composable fun shelfTiles(): List<Tile> {
        val ctx = LocalContext.current
        val ver = Prefs.ver.intValue
        val all = apps.value
        return remember(all, ver) {
            val items = Apps.shelf(ctx, all).filter { it.pkg !in Apps.VENDOR_SETTINGS }
                .map { a -> Tile(a.pkg, a.label, a.icon, null) { Apps.launch(ctx, a.pkg) } }
            val pinned = listOf(Tile("#tv", "电视设置", null, "📺") { Apps.openSystemSettings(ctx) },
                Tile("#me", "桌面设置", null, "⚙") { runCatching { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) } })
            items.take(6) + pinned + items.drop(6)
        }
    }

    @Composable fun ShelfRow(p: Pal, tiles: List<Tile>, m: Modifier, fr: FocusRequester?) {
        Row(m.padding(start = 100.u, end = 100.u, bottom = 70.u).fillMaxWidth().height(250.u)
            .clip(RoundedCornerShape(44.u)).background(p.glass).border(1.u, p.glassBorder, RoundedCornerShape(44.u)).padding(horizontal = 30.u),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            tiles.forEachIndexed { i, t -> key(t.key) { ShelfItem(p, t.label, t.icon, t.glyph, if (i == 0 && fr != null) Modifier.focusRequester(fr) else Modifier, t.onClick) } }
            repeat(8 - tiles.size) { Spacer(Modifier.width(160.u)) }   // 不满 8 个时保持对齐
        }
    }

    @Composable fun ShelfItem(p: Pal, label: String, icon: androidx.compose.ui.graphics.ImageBitmap?, glyph: String?, m: Modifier, onClick: () -> Unit) {
        var fo by remember { mutableStateOf(false) }
        val sc by animateFloatAsState(if (fo) 1.12f else 1f, tween(120), label = "s")
        val bg = remember(label, icon) { if (icon == null) Color(0xFF8E8E93) else Color(PLATFORMS.firstOrNull { label.contains(it.name.take(2)) }?.color ?: 0xFF3A3F55) }
        val shape = RoundedCornerShape(36.u)
        Column(m.width(160.u).tvClick({ fo = it }, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(140.u).graphicsLayer { scaleX = sc; scaleY = sc }.clip(shape).background(bg)
                .then(if (fo) Modifier.border(4.u, Color.White, shape) else Modifier), contentAlignment = Alignment.Center) {
                if (icon == null) T(glyph ?: "?", 60, Color.White, FontWeight.ExtraBold) else AppIcon(icon, Modifier.size(104.u))
            }
            T(label, 22, p.fg, FontWeight.SemiBold, Modifier.padding(top = 18.u))
        }
    }

    /** 安全模式：显示上次的错误，并给一个最简单的应用架 */
    @Composable fun SafeMode(err: String) {
        val ctx = LocalContext.current
        val list = remember { runCatching { Apps.installed(ctx) }.getOrDefault(emptyList()) }
        val first = remember { FocusRequester() }
        Column(Modifier.fillMaxSize().background(Color(0xFF15171F)).verticalScroll(rememberScrollState()).padding(60.u)) {
            T("清爽桌面 安全模式：上次运行出错了（错误已保存）", 36, Color.White, FontWeight.Bold)
            Row(Modifier.padding(vertical = 24.u), horizontalArrangement = Arrangement.spacedBy(20.u)) {
                SafeBtn("重新进入桌面", Modifier.focusRequester(first)) { Crash.clear(ctx); recreate() }
                SafeBtn("电视设置") { Apps.openSystemSettings(ctx) }
                SafeBtn("桌面设置") { runCatching { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) } }
            }
            list.chunked(6).forEach { r -> Row(Modifier.padding(bottom = 16.u), horizontalArrangement = Arrangement.spacedBy(20.u)) { r.forEach { a -> SafeBtn(a.label) { Apps.launch(ctx, a.pkg) } } } }
            T(err.take(4000), 18, Color(0xFFFFB4A9), modifier = Modifier.padding(top = 20.u), lines = 60)
        }
        LaunchedEffect(Unit) { delay(300); runCatching { first.requestFocus() } }
    }
    @Composable fun SafeBtn(s: String, m: Modifier = Modifier, onClick: () -> Unit) {
        var fo by remember { mutableStateOf(false) }
        Box(m.clip(RoundedCornerShape(20.u)).background(if (fo) Color.White else Color(0xFF333848)).tvClick({ fo = it }) { runCatching(onClick) }.padding(horizontal = 30.u, vertical = 16.u)) {
            T(s, 24, if (fo) Color.Black else Color.White)
        }
    }
}
