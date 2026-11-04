package com.cn.tvlauncher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val GUIDES = listOf(
    "小米电视/盒子" to listOf("设置 → 我的设备（或“关于”）→ 连续按 OK 键 7 次点击「产品型号」，出现“已进入开发者模式”",
        "返回 设置 → 账号与安全 → 打开「ADB 调试」，「安装未知来源应用」选允许", "部分机型重启后 ADB 会被复位，需要再次打开"),
    "TCL / 雷鸟" to listOf("设置 → 系统 → 关于本机 → 连续按 OK 7 次「版本号」或「系统版本」", "返回 设置 → 系统 → 开发者选项 → 打开「USB 调试」/「网络调试」",
        "TCL 默认限制后台自启：去广告后若无效，用电脑执行 adb shell appops set com.cn.tvlauncher AUTO_START allow"),
    "海信 / VIDAA" to listOf("设置 → 关于 → 本机信息，连按 3 次菜单键进入安卓设置（部分机型）", "关于 → 连续按 OK 7 次「版本号」打开开发者选项",
        "开发者选项 → 打开「USB 调试」。海信原厂桌面写入系统，本应用只设为默认桌面，不停用原厂桌面"),
    "创维 / 酷开" to listOf("设置 → 通用 → 关于本机 → 连续按 OK 7 次「系统版本」", "设置 → 通用 → 开发者选项 → 打开「ADB 调试」",
        "创维/酷开停用原厂桌面可能卡开机，本应用只设为默认桌面，不停用原厂桌面"),
    "当贝盒子 / 其他安卓盒子" to listOf("设置 → 关于 → 连续按 OK 7 次「版本号」", "设置 → 开发者选项 → 打开「USB 调试」（有的叫「ADB 调试」「网络 ADB」）",
        "Android TV/Google TV：设置 → 系统 → 关于 → 连按 7 次「Android TV OS 版本」"),
    "授权弹窗" to listOf("回到本设置 → 去广告 → 开始去广告", "电视弹出「允许 USB 调试吗？」时，勾选「一律允许」并点「允许」",
        "Android 11+ 的「无线调试」需配对码，本应用不支持；请用电脑 adb tcpip 5555 打开 5555 端口"),
)

class SettingsActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); Prefs.init(this); setContent { InitScale(); Screen() } }

    @Composable fun Screen() {
        Prefs.ver.intValue
        val p = Pal(isDark())
        val menu = listOf("应用架", "推荐来源", "天气城市", "主题", "屏保", "去广告", "恢复原厂", "开发教程", "壁纸与标题")
        var sel by remember { mutableIntStateOf(intent.getIntExtra("sel", 0)) }
        val first = remember { FocusRequester() }
        Box(Modifier.fillMaxSize().background(p.bg)) {
            Column(Modifier.padding(start = 80.u, top = 60.u, bottom = 60.u).width(480.u).fillMaxHeight().clip(RoundedCornerShape(44.u)).background(p.glass)
                .border(1.u, p.glassBorder, RoundedCornerShape(44.u)).padding(horizontal = 24.u, vertical = 36.u)) {
                T("设置", 40, p.fg, FontWeight.Bold, Modifier.padding(start = 16.u, bottom = 20.u))
                Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                    menu.forEachIndexed { i, s ->
                        var fo by remember { mutableStateOf(false) }
                        val on = i == sel
                        Box(Modifier.padding(bottom = 6.u).fillMaxWidth().clip(RoundedCornerShape(24.u))
                            .background(if (fo) Color.White else if (on) Color(0xFF1F8BFF) else Color.Transparent)
                            .then(if (i == 0) Modifier.focusRequester(first) else Modifier).tvClick({ fo = it; if (it) sel = i }) { sel = i }.padding(horizontal = 24.u, vertical = 16.u)) {
                            T(s, 28, if (fo) Color(0xFF111111) else if (on) Color.White else if (i == 6) Color(0xFFFF6A3D) else p.fg, FontWeight.SemiBold, alpha = if (fo || on) 1f else .85f)
                        }
                    }
                    Spacer(Modifier.height(40.u))
                }
            }
            Column(Modifier.padding(start = 620.u, end = 80.u, top = 60.u, bottom = 48.u).fillMaxHeight()) {
                when (sel) {
                    0 -> ShelfPane(p); 1 -> SourcePane(p); 2 -> CityPane(p); 3 -> ThemePane(p); 4 -> SaverPane(p)
                    5 -> AdPane(p); 6 -> RestorePane(p); 8 -> WallPane(p); else -> GuidePane(p)
                }
            }
        }
        LaunchedEffect(Unit) { delay(200); runCatching { first.requestFocus() } }
    }

    @Composable fun Head(p: Pal, t: String, d: String) { T(t, 48, p.fg, FontWeight.Bold); T(d, 24, p.fg, modifier = Modifier.padding(top = 12.u, bottom = 36.u), alpha = .6f, lines = 3) }

    @Composable fun Item(p: Pal, title: String, sub: String = "", state: String = "", sw: Boolean? = null, onClick: () -> Unit = {}, trailing: @Composable RowScope.() -> Unit = {}) {
        var fo by remember { mutableStateOf(false) }
        val fg = if (fo) Color(0xFF111111) else p.fg
        Row(Modifier.padding(bottom = 18.u).fillMaxWidth().clip(RoundedCornerShape(28.u)).background(if (fo) Color.White else Color(0x33FFFFFF))
            .border(1.u, if (fo) Color.Transparent else p.glassBorder, RoundedCornerShape(28.u))
            .tvClick({ fo = it }, onClick = onClick).padding(horizontal = 40.u, vertical = 26.u), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T(title, 30, fg, FontWeight.SemiBold); if (sub.isNotBlank()) T(sub, 20, fg, modifier = Modifier.padding(top = 6.u), alpha = .7f, lines = 3) }
            if (state.isNotBlank()) T(state, 22, fg, modifier = Modifier.padding(end = 24.u), alpha = .6f)
            if (sw != null) Box(Modifier.size(96.u, 54.u).clip(RoundedCornerShape(30.u)).background(if (sw) Color(0xFF34C759) else Color(0x33888888)).padding(5.u),
                contentAlignment = if (sw) Alignment.CenterEnd else Alignment.CenterStart) { Box(Modifier.size(44.u).clip(RoundedCornerShape(22.u)).background(Color.White)) }
            trailing()
        }
    }

    @Composable fun ShelfPane(p: Pal) {
        val ctx = LocalContext.current
        var all by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
        LaunchedEffect(Unit) { all = withContext(Dispatchers.IO) { Apps.installed(ctx) } }
        val cur = Apps.shelf(ctx, all).map { it.pkg }
        Head(p, "应用架", "OK 加入/移出；按菜单键把该应用前移一位。当前顺序：" + cur.mapNotNull { pk -> all.firstOrNull { it.pkg == pk }?.label }.joinToString(" › "))
        LazyColumn(Modifier.fillMaxHeight()) {
            items(cur.mapNotNull { pk -> all.firstOrNull { it.pkg == pk } } + all.filter { it.pkg !in cur }, key = { it.pkg }) { a ->
                val on = a.pkg in cur
                ShelfRow(p, a, on, cur)
            }
        }
    }
    @Composable fun ShelfRow(p: Pal, a: AppInfo, on: Boolean, cur: List<String>) {
        var fo by remember { mutableStateOf(false) }
        val fg = if (fo) Color(0xFF111111) else p.fg
        Row(Modifier.padding(bottom = 14.u).fillMaxWidth().clip(RoundedCornerShape(24.u)).background(if (fo) Color.White else p.glass)
            .tvClick({ fo = it }, onMenu = {
                if (on) { val l = cur.toMutableList(); val i = l.indexOf(a.pkg); if (i > 0) { l.removeAt(i); l.add(i - 1, a.pkg); Prefs.shelf = l } }
            }) { Prefs.shelf = if (on) cur - a.pkg else cur + a.pkg }.padding(horizontal = 30.u, vertical = 16.u), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(a.icon, Modifier.size(64.u)); Spacer(Modifier.width(24.u))
            T(a.label, 28, fg, modifier = Modifier.weight(1f))
            T(if (on) "第 ${cur.indexOf(a.pkg) + 1} 位 ✓" else "未加入", 22, fg, alpha = .7f)
        }
    }

    @Composable fun SourcePane(p: Pal) {
        val ctx = LocalContext.current
        Head(p, "推荐来源", "数据来自豆瓣公开榜单，每小时刷新；只推荐本机已安装的平台，点击用该平台搜索（无法直达时打开应用）。")
        LazyColumn {
            items(Recs.KINDS.entries.toList()) { (k, n) -> val on = k in Prefs.recKinds
                Item(p, n, "今日热播显示$n", sw = on, onClick = { Prefs.recKinds = if (on) Prefs.recKinds - k else Prefs.recKinds + k; Prefs.sp.edit().putLong("recTime", 0).apply() }) }
            items(PLATFORMS.filter { it.key != "home" }) { pl -> val pkg = Apps.platformPkg(ctx, pl); val on = pl.key !in Prefs.hiddenPlatforms
                Item(p, pl.name, if (pkg != null) "已安装：$pkg" else "未安装（${pl.pkgs.joinToString()}）", sw = on && pkg != null,
                    onClick = { Prefs.hiddenPlatforms = if (on) Prefs.hiddenPlatforms + pl.key else Prefs.hiddenPlatforms - pl.key; Prefs.sp.edit().putLong("recTime", 0).apply() }) }
        }
    }

    @Composable fun CityPane(p: Pal) {
        Head(p, "天气城市", "天气来自 Open-Meteo（免费、无需 key），日出日落用于自动深浅主题。当前：${Prefs.city.name}")
        LazyColumn { items(Prefs.CITIES) { c -> Item(p, c.name, "%.2f, %.2f".format(c.lat, c.lon), state = if (c == Prefs.city) "✓ 当前" else "",
            onClick = { Prefs.put("city", c.name); Prefs.sp.edit().putLong("recTime", 0).apply() }) } }
    }

    @Composable fun ThemePane(p: Pal) {
        val c = Prefs.city; val sun = Sun.times(java.time.LocalDate.now(), c.lat, c.lon)
        val st = sun?.let { "%02d:%02d 日出 · %02d:%02d 日落".format(it.first / 60, it.first % 60, it.second / 60, it.second % 60) } ?: ""
        Head(p, "主题", "自动：按${c.name}日出日落切换（$st）")
        LazyColumn {
            items(listOf("auto" to "自动（日出日落）", "light" to "浅色", "dark" to "深色")) { (k, n) -> Item(p, n, state = if (Prefs.theme == k) "✓" else "", onClick = { Prefs.theme = k }) }
            item { T("首页顶部", 30, p.fg, FontWeight.Bold, Modifier.padding(vertical = 20.u)) }
            items(listOf("auto" to "自动（夜间显示继续观看）", "recs" to "今日热播", "continue" to "继续观看", "clock" to "大时钟")) { (k, n) ->
                Item(p, n, state = if (Prefs.top == k) "✓" else "", onClick = { Prefs.top = k }) }
        }
    }

    @Composable fun SaverPane(p: Pal) {
        val ctx = LocalContext.current
        Head(p, "屏保", "海报轮播 + 大时钟，按 OK 打开当前影片。可在系统设置里选择「清爽桌面屏保」，或在去广告时自动设置。")
        Item(p, "去广告时设为系统屏保", "需要 ADB 授权；恢复原厂时还原", sw = Prefs.saverOk, onClick = { Prefs.saverOk = !Prefs.saverOk })
        Item(p, "打开系统屏保设置", onClick = { runCatching { ctx.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS)) }.onFailure { toast("本机没有屏保设置入口") } })
    }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show()

    @Composable fun AdPane(p: Pal) {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var msg by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val log = Debloat.log()
        val homes = remember { Debloat.stockHomes(ctx).map { it.substringBefore("/") } }
        val adsHere = remember { Debloat.ADS.filter { Apps.isInstalled(ctx, it.pkg) } }
        val auditHere = remember { Debloat.auditInstalled(ctx) }
        Head(p, "去广告", if (log.empty) "通过本机 ADB 设为默认桌面，停用（不卸载）原厂桌面和推广/开机广告。清单 ${adsHere.size} 项，审计另发现 ${auditHere.size} 项疑似广告。需先打开 USB 调试。" else "已为你屏蔽原厂推广内容，桌面更干净、开机更快")
        LazyColumn {
            item {
                val homeOff = homes.any { it in log.disabled }
                Item(p, "原厂桌面", if (homes.isEmpty()) "未检测到其他桌面" else homes.joinToString(), if (homeOff) "已关闭" else if (!log.empty) "已替换默认" else "未处理", sw = homeOff)
                val boot = adsHere.filter { it.boot }; val bootOff = boot.isNotEmpty() && boot.all { it.pkg in log.disabled }
                Item(p, "开机广告", if (boot.isEmpty()) "本机未发现清单中的开机广告组件" else boot.joinToString { it.pkg }, if (bootOff) "已关闭" else "未处理", sw = bootOff)
                val promo = adsHere.filter { !it.boot }; val promoOff = promo.isNotEmpty() && promo.all { it.pkg in log.disabled }
                Item(p, "推广应用", if (promo.isEmpty()) "本机未发现清单中的推广应用" else promo.joinToString { it.pkg }, if (promoOff) "已关闭" else "未处理", sw = promoOff)
                Item(p, if (busy) "处理中…" else "▶ 开始去广告", "首次会弹出「允许 USB 调试吗？」，请勾选「一律允许」", onClick = {
                    if (busy) return@Item; busy = true; msg = "正在连接本机 ADB…"
                    scope.launch {
                        msg = withContext(Dispatchers.IO) { runCatching { Debloat.apply(ctx, Prefs.saverOk) { msg = "请在电视弹窗中勾选「一律允许」并点「允许」（60 秒内）" } }
                            .getOrElse { "未能完成：${it.message}\n\n无 ADB 授权时本应用仍可作为普通桌面使用：按主页键选择「清爽桌面」并设为默认。开启方法见「开发者选项教程」。" } }
                        busy = false; Prefs.ver.intValue++
                    }
                })
                if (msg.isNotBlank()) T(msg, 24, p.fg, modifier = Modifier.padding(vertical = 16.u), lines = 30)
                if (log.disabled.isNotEmpty()) T("变更记录（恢复时全部还原）：\n" + log.disabled.joinToString("\n") { "· pm disable-user $it" } +
                    (log.oldDream?.let { "\n· 屏保 原值：$it" } ?: ""), 22, p.fg, alpha = .7f, lines = 60, modifier = Modifier.padding(top = 10.u))
                Spacer(Modifier.height(200.u))
            }
        }
    }

    @Composable fun RestorePane(p: Pal) {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        var msg by remember { mutableStateOf("") }
        var fo by remember { mutableStateOf(false) }
        Head(p, "恢复原厂桌面", "启用所有被停用的应用、还原屏保、把默认桌面交回原厂。卸载本应用前请先执行。")
        Box(Modifier.padding(top = 20.u).scale(if (fo) 1.08f else 1f).shadow(20.u, RoundedCornerShape(40.u), spotColor = Color(0x73E8453C)).clip(RoundedCornerShape(40.u))
            .background(Brush.linearGradient(listOf(Color(0xFFFF6A3D), Color(0xFFE8453C)))).then(if (fo) Modifier.border(4.u, Color.White, RoundedCornerShape(40.u)) else Modifier)
            .tvClick({ fo = it }) {
                msg = "正在恢复…"
                scope.launch { msg = withContext(Dispatchers.IO) { runCatching { Debloat.restore(ctx) { msg = "请在电视弹窗中允许 USB 调试" } }
                    .getOrElse { "未能连接 ADB：${it.message}\n也可用电脑执行：adb shell pm enable <包名>（记录见「去广告」页）" } }; Prefs.ver.intValue++ }
            }.padding(horizontal = 60.u, vertical = 26.u)) { T("↺ 一键恢复原厂桌面", 32, Color.White, FontWeight.ExtraBold) }
        if (msg.isNotBlank()) T(msg, 24, p.fg, modifier = Modifier.padding(top = 30.u), lines = 30)
    }

    @Composable fun GuidePane(p: Pal) {
        Head(p, "如何打开开发者选项 / USB 调试", "各品牌菜单名称可能随系统版本略有不同。")
        LazyColumn {
            items(GUIDES) { (brand, steps) ->
                var fo by remember { mutableStateOf(false) }
                Column(Modifier.padding(bottom = 18.u).fillMaxWidth().clip(RoundedCornerShape(28.u)).background(if (fo) Color.White else p.glass).tvClick({ fo = it }) {}.padding(horizontal = 40.u, vertical = 26.u)) {
                    val fg = if (fo) Color(0xFF111111) else p.fg
                    T("📺 $brand", 30, fg, FontWeight.Bold)
                    steps.forEachIndexed { i, s -> Row(Modifier.padding(top = 12.u), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(36.u).clip(RoundedCornerShape(18.u)).background(Color(0xFF1F8BFF)), contentAlignment = Alignment.Center) { T("${i + 1}", 20, Color.White, FontWeight.Bold) }
                        T(s, 22, fg, modifier = Modifier.padding(start = 16.u), lines = 3, alpha = .85f)
                    } }
                }
            }
        }
    }

    @Composable fun WallPane(p: Pal) {
        val ctx = LocalContext.current
        val hint = remember { Uploads.sizeHint(ctx) }
        val recs = remember { loadRecs().values.flatten() }
        val uploads = remember { mutableStateOf(Uploads.list(ctx)) }
        Head(p, "壁纸与标题", "当前：${Wall.name(Prefs.wall)} · 标题：${Prefs.title.ifBlank { "（不显示）" }}。壁纸在上方固定区，应用架在下方可滚动。")
        LazyColumn(Modifier.fillMaxHeight(), contentPadding = PaddingValues(bottom = 120.u)) {
            item {
                T("自定义壁纸（扫码上传）", 32, p.fg, FontWeight.Bold, Modifier.padding(bottom = 10.u))
                T("建议尺寸：$hint", 22, Color(0xFFE6A800), FontWeight.SemiBold, Modifier.padding(bottom = 14.u), lines = 2)
                var fo by remember { mutableStateOf(false) }
                Box(Modifier.padding(bottom = 16.u).fillMaxWidth().clip(RoundedCornerShape(28.u))
                    .background(if (fo) Color.White else Color(0xFF0071E3))
                    .tvClick({ fo = it }) { runCatching { startActivity(Intent(this@SettingsActivity, UploadActivity::class.java)) } }
                    .padding(horizontal = 40.u, vertical = 28.u)) {
                    T(if (Prefs.wall.startsWith("file:")) "▶ 打开扫码上传（当前正在用自定义壁纸）" else "▶ 打开扫码上传",
                        30, if (fo) Color(0xFF111111) else Color.White, FontWeight.Bold)
                }
                if (uploads.value.isNotEmpty()) {
                    T("最近上传（OK 使用 · 菜单键删除）", 24, p.fg, modifier = Modifier.padding(bottom = 10.u))
                    LazyRow(Modifier.padding(bottom = 20.u), horizontalArrangement = Arrangement.spacedBy(16.u)) {
                        items(uploads.value, key = { it.name }) { f ->
                            var ffo by remember { mutableStateOf(false) }
                            val on = Prefs.wall == "file:${f.name}"
                            Box(Modifier.size(220.u, 120.u).clip(RoundedCornerShape(14.u))
                                .border(if (ffo || on) 4.u else 0.u, if (on) Color(0xFF34C759) else Color.White, RoundedCornerShape(14.u))
                                .tvClick({ ffo = it }, onMenu = { Uploads.delete(ctx, f); uploads.value = Uploads.list(ctx) }) { Prefs.wall = "file:${f.name}" }) {
                                Wallpaper("file:${f.name}", emptyList(), Modifier.fillMaxSize(), dim = false)
                            }
                        }
                    }
                }
            }
            item { T("电影海报 / 动态壁纸（二选一）", 30, p.fg, FontWeight.Bold, Modifier.padding(top = 8.u, bottom = 14.u)) }
            item { WallRow(p, listOf("posters" to "电影海报") + Wall.DYNAMIC, recs) }
            item { T("静态壁纸", 30, p.fg, FontWeight.Bold, Modifier.padding(top = 28.u, bottom = 14.u)) }
            item { WallRow(p, Wall.STATIC, recs) }
            item { T("右上角标题", 30, p.fg, FontWeight.Bold, Modifier.padding(top = 28.u, bottom = 14.u)) }
            item {
                Box(Modifier.padding(bottom = 16.u).fillMaxWidth().clip(RoundedCornerShape(20.u)).background(Color(0xFF1C1F2A)).padding(horizontal = 30.u, vertical = 20.u)) {
                    T("预览（靠右）", 20, Color(0xFFAAAAAA), modifier = Modifier.align(Alignment.CenterStart))
                    T(Prefs.title.ifBlank { "（不显示标题）" }, 30, Color.White, FontWeight.SemiBold, Modifier.align(Alignment.CenterEnd))
                }
            }
            items(listOf(Prefs.DEFAULT_TITLE, "清爽桌面", "我的家庭影院", "")) { t ->
                Item(p, t.ifBlank { "不显示标题" }, state = if (Prefs.title == t) "✓" else "", onClick = { Prefs.title = t }) }
            item {
                var txt by remember { mutableStateOf(Prefs.title) }
                T("自定义标题（最多 20 字）", 24, p.fg, modifier = Modifier.padding(bottom = 10.u))
                Row(Modifier.padding(bottom = 40.u), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.text.BasicTextField(txt, { txt = it.take(20) },
                        Modifier.weight(1f).heightIn(min = 72.u).clip(RoundedCornerShape(20.u)).background(Color.White)
                            .padding(horizontal = 24.u, vertical = 18.u),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 28.f, color = Color.Black), singleLine = true)
                    Spacer(Modifier.width(20.u))
                    var fo by remember { mutableStateOf(false) }
                    Box(Modifier.width(200.u).clip(RoundedCornerShape(20.u)).background(if (fo) Color.White else Color(0xFF34C759))
                        .tvClick({ fo = it }) { Prefs.title = txt.trim(); toast("已保存") }.padding(vertical = 18.u), contentAlignment = Alignment.Center) {
                        T("保存", 28, if (fo) Color.Black else Color.White, FontWeight.Bold)
                    }
                }
            }
        }
    }

    @Composable fun WallRow(p: Pal, list: List<Pair<String, String>>, recs: List<Rec>) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.u)) {
            list.forEach { (k, n) ->
                var fo by remember { mutableStateOf(false) }
                val on = Prefs.wall == k
                Column(Modifier.tvClick({ fo = it }) { Prefs.wall = k }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(256.u, 144.u).clip(RoundedCornerShape(18.u))
                        .border(if (fo) 5.u else if (on) 3.u else 0.u, if (fo) Color.White else Color(0xFF34C759), RoundedCornerShape(18.u))) {
                        Wallpaper(k, recs, Modifier.fillMaxSize(), dim = false)
                    }
                    T((if (on) "✓ " else "") + n, 22, p.fg, FontWeight.SemiBold, Modifier.padding(top = 8.u))
                }
            }
        }
    }
}
