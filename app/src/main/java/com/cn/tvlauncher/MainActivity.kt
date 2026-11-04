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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
    private val now = mutableStateOf(LocalDateTime.now())

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Prefs.init(this)
        recs.value = loadRecs(); weather.value = weatherCache()
        setContent { InitScale(); Home() }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { now.value = LocalDateTime.now(); delay(5_000) } } }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                apps.value = withContext(Dispatchers.IO) { Apps.installed(this@MainActivity) }
                cont.value = withContext(Dispatchers.IO) { Continue.all(this@MainActivity) }
                // 每小时刷新推荐与天气
                if (System.currentTimeMillis() - Prefs.sp.getLong("recTime", 0) > 3600_000 || recs.value.isEmpty()) {
                    val r = withContext(Dispatchers.IO) { runCatching { Recs.fetch(this@MainActivity) }.getOrDefault(emptyMap()) }
                    if (r.values.any { it.isNotEmpty() }) { recs.value = r; saveRecs(r) }
                    else if (System.currentTimeMillis() - Prefs.sp.getLong("recTime", 0) > 6 * 3600_000) recs.value = emptyMap()
                    withContext(Dispatchers.IO) { runCatching { WeatherApi.fetch(Prefs.city) }.getOrNull() }?.let { weather.value = it; saveWeather(it) }
                }
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
        Prefs.ver.intValue
        val t = now.value
        val dark = remember(t.hour, t.minute / 10, Prefs.ver.intValue) { isDark() }
        val p = Pal(dark)
        val ctx = LocalContext.current
        var tab by remember { mutableIntStateOf(0) }
        val tabs = listOf("推荐") + Recs.KINDS.filterKeys { it in Prefs.recKinds }.values
        val list = when (tab) { 0 -> mixed(recs.value); else -> recs.value[Recs.KINDS.entries.first { it.value == tabs[tab] }.key] ?: emptyList() }
        val top = Prefs.top
        val showCont = cont.value.isNotEmpty() && (top == "continue" || (top == "auto" && dark))
        val shelfFocus = remember { FocusRequester() }

        Box(Modifier.fillMaxSize().background(p.bg)) {
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
            val bigClock = (!showCont && list.isEmpty()) || top == "clock"
            if (!bigClock) Column(Modifier.align(Alignment.TopEnd).padding(top = 44.u, end = 100.u), horizontalAlignment = Alignment.End) {
                T(t.format(DateTimeFormatter.ofPattern("HH:mm")), 96, p.fg, FontWeight.Light)
                T(dateLine(t.toLocalDate()), 24, p.fg, modifier = Modifier.padding(top = 8.u), alpha = .75f)
                weather.value?.let { T("${it.icon} ${Prefs.city.name} ${it.text} ${it.temp}°", 24, p.fg, modifier = Modifier.padding(top = 8.u), alpha = .75f) }
            }
            when {
                bigClock -> Column(Modifier.align(Alignment.TopCenter).padding(top = 180.u), horizontalAlignment = Alignment.CenterHorizontally) {
                    T(t.format(DateTimeFormatter.ofPattern("HH:mm")), 220, p.fg, FontWeight.ExtraLight)
                    T(dateLine(t.toLocalDate()), 34, p.fg, alpha = .8f)
                    weather.value?.let { T("${it.icon} ${Prefs.city.name} ${it.text} ${it.temp}°", 34, p.fg, modifier = Modifier.padding(top = 12.u), alpha = .8f) }
                }
                showCont -> { T("继续观看", 34, p.fg, FontWeight.Bold, Modifier.padding(start = 100.u, top = 170.u)); ContRow(cont.value) }
                else -> { T("今日热播", 34, p.fg, FontWeight.Bold, Modifier.padding(start = 100.u, top = 170.u)); RecRow(list, tab) }
            }
            Shelf(p, Modifier.align(Alignment.BottomCenter), shelfFocus)
        }
        LaunchedEffect(Unit) { delay(300); runCatching { shelfFocus.requestFocus() } }
    }

    @Composable fun RecRow(list: List<Rec>, tab: Int) {
        val ctx = LocalContext.current
        val st = rememberLazyListState()
        LaunchedEffect(tab) { st.scrollToItem(0) }
        LazyRow(Modifier.padding(top = 240.u).height(480.u).fillMaxWidth(), st, contentPadding = PaddingValues(horizontal = 100.u),
            horizontalArrangement = Arrangement.spacedBy(36.u), verticalAlignment = Alignment.Bottom) {
            itemsIndexed(list.take(20), key = { i, r -> "$tab-$i-${r.id}" }) { i, r ->
                var fo by remember { mutableStateOf(false) }
                val w by animateDpAsState(if (fo) 820.u else 280.u, label = "w"); val h by animateDpAsState(if (fo) 460.u else 420.u, label = "h")
                Box(Modifier.size(w, h).shadow(if (fo) 30.u else 10.u, RoundedCornerShape(28.u)).clip(RoundedCornerShape(28.u)).background(grad(i + 1))
                    .then(if (fo) Modifier.border(4.u, Color(0xE6FFFFFF), RoundedCornerShape(28.u)) else Modifier)
                    .tvClick({ fo = it }) { Apps.openSearch(ctx, r.pkg, r.title) }) {
                    Poster(r.cover, Modifier.fillMaxSize())
                    if (fo) {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)), startY = 150f)))
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 40.u, bottom = 36.u)) {
                            T(r.title, 52, Color.White, FontWeight.ExtraBold)
                            T(Recs.KINDS[r.kind].orEmpty() + (if (r.rate.isNotBlank()) " · 豆瓣 ${r.rate}" else " · 豆瓣暂无评分"), 24, Color.White, modifier = Modifier.padding(top = 10.u), alpha = .92f)
                            Box(Modifier.padding(top = 18.u).clip(RoundedCornerShape(30.u)).background(Color.White).padding(horizontal = 30.u, vertical = 12.u)) {
                                T("▶ 在 ${r.platform} 搜索播放", 24, Color(0xFF111111), FontWeight.Bold)
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x80000000)), startY = 300f)))
                        T(r.title, 28, Color.White, FontWeight.Bold, Modifier.align(Alignment.BottomStart).padding(start = 22.u, bottom = 20.u, end = 12.u))
                    }
                }
            }
        }
    }

    @Composable fun ContRow(list: List<Cont>) {
        val ctx = LocalContext.current
        LazyRow(Modifier.padding(top = 240.u).height(420.u).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 100.u),
            horizontalArrangement = Arrangement.spacedBy(36.u), verticalAlignment = Alignment.Bottom) {
            itemsIndexed(list) { i, c ->
                var fo by remember { mutableStateOf(false) }
                val w by animateDpAsState(if (fo) 520.u else 440.u, label = "w"); val h by animateDpAsState(if (fo) 390.u else 330.u, label = "h")
                Box(Modifier.size(w, h).shadow(if (fo) 30.u else 10.u, RoundedCornerShape(28.u)).clip(RoundedCornerShape(28.u)).background(grad(i * 3 + 4))
                    .then(if (fo) Modifier.border(4.u, Color.White, RoundedCornerShape(28.u)) else Modifier)
                    .tvClick({ fo = it }) { c.intent?.let { runCatching { ctx.startActivity(it) } } }) {
                    if (c.image.isNotBlank()) Poster(c.image, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x33000000), Color.Transparent, Color(0x99000000)))))
                    T(c.source, 18, Color.White, modifier = Modifier.padding(start = 24.u, top = 20.u).clip(RoundedCornerShape(10.u)).background(Color(0x59000000)).padding(horizontal = 12.u, vertical = 4.u))
                    T(c.title + if (c.sub.isNotBlank()) " · ${c.sub}" else "", 30, Color.White, FontWeight.Bold, Modifier.align(Alignment.BottomStart).padding(start = 24.u, bottom = 44.u, end = 24.u))
                    Box(Modifier.align(Alignment.BottomStart).padding(start = 24.u, end = 24.u, bottom = 22.u).fillMaxWidth().height(8.u).clip(RoundedCornerShape(4.u)).background(Color(0x4DFFFFFF))) {
                        Box(Modifier.fillMaxWidth(c.progress).fillMaxHeight().clip(RoundedCornerShape(4.u)).background(Color.White))
                    }
                }
            }
        }
    }

    @Composable fun Shelf(p: Pal, m: Modifier, fr: FocusRequester) {
        val ctx = LocalContext.current
        Prefs.ver.intValue
        val items = Apps.shelf(ctx, apps.value)
        Row(m.padding(start = 100.u, end = 100.u, bottom = 70.u).fillMaxWidth().height(250.u).shadow(20.u, RoundedCornerShape(44.u), ambientColor = Color(0x14000000))
            .clip(RoundedCornerShape(44.u)).background(p.glass).border(1.u, p.glassBorder, RoundedCornerShape(44.u)).padding(horizontal = 40.u),
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { i, a -> ShelfItem(p, a.label, a.icon, if (i == 0) Modifier.focusRequester(fr) else Modifier) { Apps.launch(ctx, a.pkg) } }
            ShelfItem(p, "设置", null, if (items.isEmpty()) Modifier.focusRequester(fr) else Modifier) { ctx.startActivity(Intent(ctx, SettingsActivity::class.java)) }
        }
    }

    @Composable fun ShelfItem(p: Pal, label: String, icon: android.graphics.drawable.Drawable?, m: Modifier, onClick: () -> Unit) {
        var fo by remember { mutableStateOf(false) }
        val sc by animateFloatAsState(if (fo) 1.15f else 1f, label = "s")
        val plat = PLATFORMS.firstOrNull { label.contains(it.name.take(2)) }
        Column(m.width(170.u).tvClick({ fo = it }, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.scale(sc).size(150.u).shadow(if (fo) 26.u else 8.u, RoundedCornerShape(36.u)).clip(RoundedCornerShape(36.u))
                .background(if (icon == null) Color(0xFF8E8E93) else Color(plat?.color ?: 0xFF3A3F55))
                .then(if (fo) Modifier.border(4.u, Color.White, RoundedCornerShape(36.u)) else Modifier), contentAlignment = Alignment.Center) {
                if (icon == null) T("⚙", 64, Color.White, FontWeight.ExtraBold) else AppIcon(icon, Modifier.size(110.u))
                if (fo) Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(Color(0x66FFFFFF), Color.Transparent))))
            }
            T(label, 22, p.fg, FontWeight.SemiBold, Modifier.padding(top = if (fo) 26.u else 12.u))
        }
    }
}
