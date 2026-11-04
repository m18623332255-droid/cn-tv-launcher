package com.cn.tvlauncher

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import coil.ImageLoader
import coil.memory.MemoryCache
import coil.size.Precision
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** 原型以 1920×1080 设计：把原型像素换算为 dp */
object S { var k = 0.5f }
val Int.u: Dp get() = (this * S.k).dp
val Int.f: TextUnit get() = (this * S.k).sp

@Composable fun InitScale() { val c = LocalConfiguration.current; S.k = c.screenWidthDp / 1920f }

class Pal(val dark: Boolean) {
    val fg = if (dark) Color.White else Color(0xFF1A1D24)
    val bg: Brush = if (dark) Brush.radialGradient(listOf(Color(0xFF2A2F4A), Color(0xFF0B0D16)), Offset(380f, 100f), 1600f)
        else Brush.linearGradient(listOf(Color(0xFFEEF3FB), Color(0xFFDFE7F3), Color(0xFFF6EFE6)), Offset(0f, 0f), Offset(1920f, 1500f))
    val glass = if (dark) Color(0x14FFFFFF) else Color(0x73FFFFFF)
    val glassBorder = if (dark) Color(0x24FFFFFF) else Color(0xCCFFFFFF)
    val tabs = if (dark) Color(0x733C3C50) else Color(0x80FFFFFF)
}

fun isDark(): Boolean = runCatching { isDark0() }.getOrDefault(true)
private fun isDark0(): Boolean = when (Prefs.theme) {
    "dark" -> true; "light" -> false
    else -> { val c = Prefs.city; val t = LocalTime.now(); Sun.isNight(LocalDate.now(), t.hour * 60 + t.minute, c.lat, c.lon) }
}

val GRADS = listOf(0xFFFF6A3D to 0xFF7B1F5C, 0xFF2BC0E4 to 0xFF1A3A8A, 0xFFA8E063 to 0xFF1E6B4A, 0xFFF7B733 to 0xFFA8431B,
    0xFF8E54E9 to 0xFF2B1166, 0xFFFF5F8F to 0xFF3A1450, 0xFF43CEA2 to 0xFF185A9D, 0xFF606C88 to 0xFF1F2533)
fun grad(i: Int) = GRADS[Math.floorMod(i, GRADS.size)].let { Brush.linearGradient(listOf(Color(it.first), Color(it.second))) }

@Composable fun T(s: String, size: Int, color: Color, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, lines: Int = 1, alpha: Float = 1f) =
    BasicText(s, modifier, TextStyle(color = color.copy(alpha = color.alpha * alpha), fontSize = size.f, fontWeight = weight), overflow = TextOverflow.Ellipsis, maxLines = lines)

/** 可聚焦元素：onFocus 报告焦点，OK/点击触发 onClick，菜单键触发 onMenu */
@Composable fun Modifier.tvClick(onFocus: (Boolean) -> Unit = {}, onMenu: (() -> Unit)? = null, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    return this.onPreviewKeyEvent { e -> if (onMenu != null && e.key == Key.Menu && e.type == KeyEventType.KeyUp) { onMenu(); true } else false }
        .onFocusChanged { onFocus(it.isFocused) }.clickable(src, null) { onClick() }
}

/** 低内存电视用的图片加载器：小内存缓存、RGB_565、不做交叉淡入 */
object Img {
    @Volatile private var l: ImageLoader? = null
    fun get(c: Context): ImageLoader = l ?: synchronized(this) { l ?: ImageLoader.Builder(c.applicationContext)
        .memoryCache { MemoryCache.Builder(c.applicationContext).maxSizePercent(0.12).build() }
        .bitmapConfig(Bitmap.Config.RGB_565).allowRgb565(true).crossfade(false).build().also { l = it } }
}

@Composable fun Poster(url: String, modifier: Modifier, w: Int = 300, h: Int = 440) {
    val ctx = LocalContext.current
    val req = remember(url, w, h) { ImageRequest.Builder(ctx).data(url).addHeader("Referer", "https://movie.douban.com/").addHeader("User-Agent", UA)
        .size(w, h).precision(Precision.INEXACT).bitmapConfig(Bitmap.Config.RGB_565).allowRgb565(true).build() }
    AsyncImage(model = req, imageLoader = Img.get(ctx), contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = modifier)
}

@Composable fun AppIcon(bmp: ImageBitmap?, modifier: Modifier) {
    if (bmp != null) Image(remember(bmp) { BitmapPainter(bmp) }, null, modifier) else Box(modifier)
}

// 推荐缓存（屏保共用）
fun saveRecs(m: Map<String, List<Rec>>) {
    val o = JSONObject()
    m.forEach { (k, l) -> o.put(k, JSONArray().apply { l.forEach { put(JSONObject().put("id", it.id).put("t", it.title).put("r", it.rate).put("c", it.cover).put("p", it.platform).put("k", it.pkg)) } }) }
    Prefs.sp.edit().putString("recCache", o.toString()).putLong("recTime", System.currentTimeMillis()).apply()
}
fun loadRecs(): Map<String, List<Rec>> = runCatching {
    val o = JSONObject(Prefs.str("recCache", "{}")); val m = LinkedHashMap<String, List<Rec>>()
    o.keys().forEach { k -> val a = o.getJSONArray(k); m[k] = (0 until a.length()).map { a.getJSONObject(it).let { j -> Rec(j.optString("id"), j.optString("t"), j.optString("r"), j.optString("c"), k, j.optString("p"), j.optString("k")) } } }
    m
}.getOrDefault(emptyMap())
/** “推荐”= 各类交错混排 */
fun mixed(m: Map<String, List<Rec>>): List<Rec> { val ls = m.values.toList(); val n = ls.maxOfOrNull { it.size } ?: 0
    return (0 until n).flatMap { i -> ls.mapNotNull { it.getOrNull(i) } } }

val WEEK = arrayOf("", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
fun dateLine(d: LocalDate = LocalDate.now()) = "${d.monthValue}月${d.dayOfMonth}日 ${WEEK[d.dayOfWeek.value]} · ${Lunar.text(d)}"
fun weatherCache(): Weather? = runCatching { JSONObject(Prefs.str("wx", "")).let { Weather(it.getInt("t"), it.getString("x"), it.getString("i"), "", "") } }.getOrNull()
fun saveWeather(w: Weather) = Prefs.sp.edit().putString("wx", JSONObject().put("t", w.temp).put("x", w.text).put("i", w.icon).toString()).apply()

/** 电视过扫描安全边距：顶部至少 28dp，两侧至少 48dp */
val SAFE_TOP: Dp get() = maxOf(60.u, 28.dp)
val SAFE_SIDE: Dp get() = maxOf(100.u, 48.dp)
