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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
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
        // 冷启动：先画缓存界面，首帧后再查已装应用；网络推荐/天气再延后，避免卡住开机
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            var first = true
            while (true) { runCatching {
                if (first) delay(80)  // 让首帧先画出来
                val a = withContext(Dispatchers.IO) { Apps.installed(this@MainActivity) }
                if (a != apps.value) apps.value = a
                val cn = withContext(Dispatchers.IO) { Continue.all(this@MainActivity) }
                if (cn.map { it.title + it.sub + it.progress } != cont.value.map { it.title + it.sub + it.progress }) cont.value = cn
                val needNet = System.currentTimeMillis() - Prefs.sp.getLong("recTime", 0) > 3600_000 || recs.value.isEmpty()
                if (needNet) {
                    if (first) delay(400)  // 应用架出来后再拉豆瓣/天气
                    val r = withContext(Dispatchers.IO) { runCatching { Recs.fetch(this@MainActivity) }.getOrDefault(emptyMap()) }
                    if (r.values.any { it.isNotEmpty() }) { recs.value = r; saveRecs(r) }
                    else if (System.currentTimeMillis() - Prefs.sp.getLong("recTime", 0) > 6 * 3600_000) recs.value = emptyMap()
                    withContext(Dispatchers.IO) { runCatching { WeatherApi.fetch(Prefs.city) }.getOrNull() }?.let { weather.value = it; saveWeather(it) }
                }
                first = false
                }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; Crash.save(this@MainActivity, it, fatal = false) }
                delay(60_000)
            }
        } }
    }

    private val scroll = androidx.compose.foundation.ScrollState(0)
    private val shelfFocus = FocusRequester()
    /** 主页键/返回键：回到顶部、焦点回第一个图标（singleTask 会关掉压在上面的设置页） */
    private val tabState = mutableIntStateOf(0)
    private val recState = androidx.compose.foundation.lazy.LazyListState()
    private val contState = androidx.compose.foundation.lazy.LazyListState()
    /** 回到冷启动时的样子：页面滚动 0、标签回“推荐”、横排回第 0 项、焦点回第一个图标；布局后再校正两次，防止焦点滚动把页面带下去 */
    private fun goTop(focus: Boolean = true) {
        lifecycleScope.launch {
            tabState.intValue = 0
            runCatching { scroll.scrollTo(0) }
            runCatching { recState.scrollToItem(0, 0) }; runCatching { contState.scrollToItem(0, 0) }
            if (focus) runCatching { shelfFocus.requestFocus() }
            for (d in listOf(120L, 350L)) { delay(d); runCatching { scroll.scrollTo(0) }; runCatching { recState.scrollToItem(0, 0) } }
        }
    }
    private fun toTop() { if (scroll.value != 0) lifecycleScope.launch { runCatching { scroll.animateScrollTo(0) }; if (scroll.value != 0) runCatching { scroll.scrollTo(0) } } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); goTop() }
    @Deprecated("Deprecated") override fun onBackPressed() { goTop() }

    override fun onResume() {
        super.onResume()
        if (Prefs.sp.getBoolean("cityChanged", false)) { Prefs.sp.edit().putBoolean("cityChanged", false).putLong("recTime", 0).apply() }
    }

    @Composable fun Home() {
        val ver = Prefs.ver.intValue
        val dk = remember(dark.value, ver) { isDark() }
        var tab by tabState
        val tabs = remember(ver) { listOf("推荐") + Recs.KINDS.filterKeys { it in Prefs.recKinds }.values }
        if (tab >= tabs.size) tab = 0
        val wall = Prefs.wall
        val posterMode = wall == "posters"   // 海报模式与壁纸模式互斥
        val rv = if (posterMode) recs.value else emptyMap()
        val list = remember(rv, tab, tabs) { when (tab) { 0 -> mixed(rv); else -> rv[Recs.KINDS.entries.first { it.value == tabs[tab] }.key] ?: emptyList() } }
        val top = Prefs.top
        val showCont = cont.value.isNotEmpty() && (top == "continue" || (top == "auto" && dk && list.isEmpty()))
        val tiles = shelfTiles()
        val rows = remember(tiles) { listOf(tiles.take(8)) + tiles.drop(8).chunked(8) }
        val p = remember(wall, dk) { Pal(true) }  // 壁纸区文字统一白色
        BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF0B0D16))) {
            // 应用架视口高度 = 恰好一排（图标+文字+内边距），绝不多出空隙让第二排露头；壁纸区吃掉其余高度
            val rowH = 168.u   // ShelfRow 实际占用高度（与下方 ShelfRow 保持一致）
            val shelfH = rowH
            val wallH = maxHeight - shelfH
            // —— 固定壁纸层（滚动时不动）——
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(wallH)) {
                Wallpaper(wall, rv.values.flatten(), Modifier.fillMaxSize())
                val bigClock = list.isEmpty() && !showCont
                // 海报/继续观看放在下层，顶部留出标签+时钟空间，避免盖住标题/时钟/天气/标签
                when {
                    bigClock -> Clock(p, true, Modifier.align(Alignment.Center))
                    showCont -> Box(Modifier.fillMaxSize().padding(top = SAFE_TOP + 110.u, bottom = 8.u)) {
                        T("继续观看", 28, p.fg, FontWeight.Bold, Modifier.padding(start = SAFE_SIDE))
                        ContRow(cont.value)
                    }
                    else -> Box(Modifier.fillMaxSize().padding(top = SAFE_TOP + 110.u, bottom = 8.u)) {
                        T("今日热播 · 豆瓣", 28, p.fg, FontWeight.Bold, Modifier.padding(start = SAFE_SIDE))
                        RecRow(list, tab)
                    }
                }
                // 覆盖层：标签、标题、时钟永远在海报之上
                if (!showCont && list.isNotEmpty() && top != "clock") Row(Modifier.align(Alignment.TopStart).padding(start = SAFE_SIDE, top = SAFE_TOP).clip(RoundedCornerShape(40.u)).background(p.tabs)
                    .border(1.u, p.glassBorder, RoundedCornerShape(40.u)).padding(8.u), horizontalArrangement = Arrangement.spacedBy(6.u)) {
                    tabs.forEachIndexed { i, s ->
                        var fo by remember { mutableStateOf(false) }
                        val on = i == tab
                        Box(Modifier.clip(RoundedCornerShape(30.u)).background(if (on) Color.White else if (fo) Color(0x55FFFFFF) else Color.Transparent)
                            .tvClick({ fo = it; if (it) { tab = i; toTop() } }) { tab = i }.padding(horizontal = 30.u, vertical = 10.u)) {
                            T(s, 24, if (on) Color(0xFF111111) else p.fg, FontWeight.SemiBold, alpha = if (on) 1f else .7f)
                        }
                    }
                }
                TitleText(Modifier.align(Alignment.TopEnd).padding(top = SAFE_TOP, end = SAFE_SIDE))
                if (!bigClock) Clock(p, false, Modifier.align(Alignment.TopEnd).padding(top = SAFE_TOP + 48.u, end = SAFE_SIDE))
            }
            // —— 下方应用架：视口=一排高度 + clipToBounds，第二排被裁掉，只能滚动看见 ——
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(shelfH).clipToBounds()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = rows.size > 1)
                    .background(Color(0xE60B0D16))) {
                    rows.forEachIndexed { i, r -> ShelfRow(p, r, Modifier, if (i == 0) shelfFocus else null) }
                }
            }
        }
        LaunchedEffect(Unit) { delay(300); runCatching { shelfFocus.requestFocus() } }
    }

    /** 右上角标题（苹果风格），始终靠右上，在时钟上方 */
    /** 浅色壁纸上加半透明黑底，保证标题/时钟可读 */
    @Composable fun HudScrim(m: Modifier, content: @Composable ColumnScope.() -> Unit) {
        Column(m.clip(RoundedCornerShape(20.u)).background(Color(0x99000000)).padding(horizontal = 22.u, vertical = 12.u),
            horizontalAlignment = Alignment.End, content = content)
    }

    @Composable fun TitleText(m: Modifier) {
        val title = Prefs.title
        if (title.isNotBlank()) HudScrim(m) {
            androidx.compose.foundation.text.BasicText(title, Modifier,
                androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 28.f, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 2.f, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 1f), 8f)), maxLines = 1)
        }
    }

    /** 时钟单独成组件：每分钟只重组这几行文字 */
    @Composable fun Clock(p: Pal, big: Boolean, m: Modifier) {
        val t = now.value
        val hm = remember(t) { t.format(DateTimeFormatter.ofPattern("HH:mm")) }
        val dl = remember(t.toLocalDate()) { dateLine(t.toLocalDate()) }
        val w = weather.value?.let { "${it.icon} ${Prefs.city.name} ${it.text} ${it.temp}°" }
        if (big) {
            Column(m.clip(RoundedCornerShape(28.u)).background(Color(0x66000000)).padding(horizontal = 40.u, vertical = 24.u), horizontalAlignment = Alignment.CenterHorizontally) {
                T(hm, 180, Color.White, FontWeight.Light)
                T(dl, 32, Color.White, modifier = Modifier.padding(top = 6.u), alpha = .9f)
                if (w != null) T(w, 30, Color.White, modifier = Modifier.padding(top = 10.u), alpha = .85f)
            }
        } else {
            HudScrim(m) {
                T(hm, 56, Color.White, FontWeight.Light)
                T(if (w != null) "$dl  $w" else dl, 20, Color.White, modifier = Modifier.padding(top = 4.u), alpha = .9f)
            }
        }
    }

    @Composable fun RecRow(list: List<Rec>, tab: Int) {
        val ctx = LocalContext.current
        val st = recState
        LaunchedEffect(tab) { st.scrollToItem(0) }
        LazyRow(Modifier.padding(top = 36.u).fillMaxWidth().fillMaxHeight(), st, contentPadding = PaddingValues(horizontal = SAFE_SIDE),
            horizontalArrangement = Arrangement.spacedBy(28.u), verticalAlignment = Alignment.Bottom) {
            itemsIndexed(list.take(20), key = { _, r -> r.kind + r.id }, contentType = { _, _ -> "rec" }) { i, r ->
                var fo by remember { mutableStateOf(false) }
                val sc by animateFloatAsState(if (fo) 1.06f else 1f, tween(120), label = "s")
                val shape = RoundedCornerShape(20.u)
                Box(Modifier.size(220.u, 320.u).graphicsLayer { scaleX = sc; scaleY = sc }.clip(shape).background(grad(i + 1))
                    .then(if (fo) Modifier.border(4.u, Color.White, shape) else Modifier)
                    .tvClick({ fo = it; if (it) toTop() }) { Apps.openRec(ctx, r, apps.value) }) {
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
        LazyRow(Modifier.padding(top = 36.u).fillMaxWidth().fillMaxHeight(), contState, contentPadding = PaddingValues(horizontal = SAFE_SIDE),
            horizontalArrangement = Arrangement.spacedBy(28.u), verticalAlignment = Alignment.Bottom) {
            itemsIndexed(list, key = { i, c -> "$i-${c.title}" }) { i, c ->
                var fo by remember { mutableStateOf(false) }
                val sc by animateFloatAsState(if (fo) 1.06f else 1f, tween(120), label = "s")
                Box(Modifier.size(360.u, 240.u).graphicsLayer { scaleX = sc; scaleY = sc }.clip(RoundedCornerShape(24.u)).background(grad(i * 3 + 4))
                    .then(if (fo) Modifier.border(4.u, Color.White, RoundedCornerShape(28.u)) else Modifier)
                    .tvClick({ fo = it; if (it) toTop() }) { c.intent?.let { runCatching { ctx.startActivity(it) } } }) {
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
        // 固定高度 168.u = 视口高度；不要再加外边距，否则第二排会露头
        Row(m.fillMaxWidth().height(168.u).padding(horizontal = SAFE_SIDE)
            .clip(RoundedCornerShape(32.u)).background(p.glass).border(1.u, p.glassBorder, RoundedCornerShape(32.u)).padding(horizontal = 20.u),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            tiles.forEachIndexed { i, t -> key(t.key) { ShelfItem(p, t.label, t.icon, t.glyph, if (i == 0 && fr != null) Modifier.focusRequester(fr) else Modifier, t.onClick,
                onFocus = if (fr != null) ({ toTop() }) else ({})) } }
            repeat(8 - tiles.size) { Spacer(Modifier.width(120.u)) }
        }
    }

    @Composable fun ShelfItem(p: Pal, label: String, icon: androidx.compose.ui.graphics.ImageBitmap?, glyph: String?, m: Modifier, onClick: () -> Unit, onFocus: () -> Unit = {}) {
        var fo by remember { mutableStateOf(false) }
        val sc by animateFloatAsState(if (fo) 1.1f else 1f, tween(120), label = "s")
        val bg = remember(label, icon) { if (icon == null) Color(0xFF8E8E93) else Color(PLATFORMS.firstOrNull { label.contains(it.name.take(2)) }?.color ?: 0xFF3A3F55) }
        val shape = RoundedCornerShape(28.u)
        Column(m.width(120.u).tvClick({ fo = it; if (it) onFocus() }, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.u).graphicsLayer { scaleX = sc; scaleY = sc }.shadow(6.u, shape, clip = true, ambientColor = Color(0x55000000), spotColor = Color(0x88000000)).background(bg)
                .then(if (fo) Modifier.border(3.u, Color.White, shape) else Modifier), contentAlignment = Alignment.Center) {
                if (icon == null) T(glyph ?: "?", 48, Color.White, FontWeight.ExtraBold) else AppIcon(icon, Modifier.size(72.u))
            }
            T(label, 18, p.fg, FontWeight.SemiBold, Modifier.padding(top = 6.u))
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
