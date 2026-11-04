package com.cn.tvlauncher

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import android.os.Build
import android.graphics.ImageDecoder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** 壁纸：posters=豆瓣海报轮播；dyn_*=轻量动态（Canvas，约 15 帧/秒，只重绘不重组）；st_*=内置静态图 */
object Wall {
    /** 偷看小猫 = res/raw/wp_peekcat.gif，来源 LottieFiles「Peekaboo cat」https://lottiefiles.com/free-animation/peekaboo-cat-o7AV4dTpEZ （Lottie Simple License） */
    val DYNAMIC = listOf("dyn_aurora" to "极光", "dyn_particles" to "星尘", "dyn_wave" to "流光", "dyn_peekcat" to "偷看小猫")
    val STATIC = listOf("st_1" to "暮色山峦", "st_2" to "月下海面", "st_3" to "彩色柔光", "st_4" to "星空")
    val ALL = listOf("posters" to "电影海报（轮播）") + DYNAMIC + STATIC
    fun name(k: String) = if (k.startsWith("file:")) "自定义壁纸" else ALL.firstOrNull { it.first == k }?.second ?: "电影海报（轮播）"
    fun res(k: String) = when (k) { "st_1" -> R.drawable.wp_static_1; "st_2" -> R.drawable.wp_static_2; "st_3" -> R.drawable.wp_static_3; "st_4" -> R.drawable.wp_static_4; else -> 0 }
}

/** 动画时间：低帧率自增，读取只发生在 draw 阶段 */
@Composable fun rememberSlowTime(fps: Int = 15): State<Float> {
    val t = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val step = 1000L / fps; while (true) { delay(step); t.floatValue += step / 1000f } }
    return t
}

@Composable fun Wallpaper(kind: String, posters: List<Rec>, modifier: Modifier = Modifier.fillMaxSize(), dim: Boolean = true) {
    Box(modifier.background(Color(0xFF0B0D16))) {
        when {
            kind == "posters" -> {
                val list = posters.filter { it.cover.isNotBlank() }
                var i by remember { mutableIntStateOf(0) }
                LaunchedEffect(list.size) { while (list.size > 1) { delay(20_000); i++ } }
                if (list.isNotEmpty()) Poster(list[i % list.size].cover, Modifier.fillMaxSize(), 960, 540)
                else Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A2F4A), Color(0xFF0B0D16)))))
                if (dim) Box(Modifier.fillMaxSize().background(Color(0xB30B0D16)))
            }
            kind.startsWith("file:") -> {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val f = remember(kind) { Uploads.file(ctx, kind) }
                if (f != null) {
                    val req = remember(f) { coil.request.ImageRequest.Builder(ctx).data(f).size(1920, 680).precision(coil.size.Precision.INEXACT)
                        .bitmapConfig(android.graphics.Bitmap.Config.RGB_565).allowRgb565(true).build() }
                    coil.compose.AsyncImage(model = req, contentDescription = null, imageLoader = Img.get(ctx), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (dim) Box(Modifier.fillMaxSize().background(Color(0x4D000000)))
                }
            }
            kind.startsWith("st_") -> {
                Image(painterResource(Wall.res(kind)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (dim) Box(Modifier.fillMaxSize().background(Color(0x4D000000)))
            }
            kind == "dyn_peekcat" -> GifWall(R.raw.wp_peekcat, Modifier.fillMaxSize())
            else -> DynamicWall(kind)
        }
    }
}

/** 真实 GIF 壁纸（偷看小猫）：ImageDecoder / Movie 循环播放，不依赖 coil-gif */
@Composable fun GifWall(resId: Int, modifier: Modifier = Modifier) {
    // 软蓝底衬托竖图猫 GIF
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFFFFF6F8), Color(0xFFFFE8EE), Color(0xFFF5F7FA))))) {
        AndroidView(factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                adjustViewBounds = false
                try {
                    if (Build.VERSION.SDK_INT >= 28) {
                        val src = ImageDecoder.createSource(ctx.resources, resId)
                        val d = ImageDecoder.decodeDrawable(src) { decoder, _, _ -> decoder.isMutableRequired = false }
                        setImageDrawable(d)
                        (d as? android.graphics.drawable.Animatable)?.start()
                    } else {
                        @Suppress("DEPRECATION")
                        val movie = android.graphics.Movie.decodeStream(ctx.resources.openRawResource(resId))
                        if (movie != null) {
                            val iv = this
                            val run = object : Runnable {
                                override fun run() {
                                    val t = (android.os.SystemClock.uptimeMillis() % movie.duration().coerceAtLeast(1)).toInt()
                                    movie.setTime(t)
                                    val bmp = android.graphics.Bitmap.createBitmap(movie.width().coerceAtLeast(1), movie.height().coerceAtLeast(1), android.graphics.Bitmap.Config.RGB_565)
                                    movie.draw(android.graphics.Canvas(bmp), 0f, 0f)
                                    iv.setImageBitmap(bmp)
                                    iv.postDelayed(this, 80L)
                                }
                            }
                            post(run)
                        } else setImageResource(resId)
                    }
                } catch (e: Exception) { setImageResource(android.R.color.darker_gray) }
            }
        }, modifier = Modifier.fillMaxSize(), update = { v -> (v.drawable as? android.graphics.drawable.Animatable)?.start() })
    }
}

@Composable fun DynamicWall(kind: String) {
    val time = rememberSlowTime(15)
    val seeds = remember { val r = java.util.Random(7); List(40) { floatArrayOf(r.nextFloat(), r.nextFloat(), 0.3f + r.nextFloat(), 1f + r.nextFloat() * 3f) } }
    Canvas(Modifier.fillMaxSize()) {
        val t = time.value; val w = size.width; val h = size.height
        when (kind) {
            "dyn_particles" -> {
                drawRect(Brush.verticalGradient(listOf(Color(0xFF0A0E24), Color(0xFF1B1440))))
                for (s in seeds) {
                    val y = ((s[1] - t * 0.02f * s[2]) % 1f + 1f) % 1f
                    val x = s[0] + 0.01f * sin(t * 0.5f + s[3])
                    drawCircle(Color.White.copy(alpha = 0.25f + 0.35f * s[2] / 1.3f), s[3] * h / 540f, Offset(x * w, y * h))
                }
            }
            "dyn_wave" -> {
                val a = t * 0.15f
                drawRect(Brush.linearGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)),
                    Offset(w * (0.5f + 0.5f * cos(a)), 0f), Offset(w * (0.5f - 0.5f * cos(a)), h)))
                drawCircle(Brush.radialGradient(listOf(Color(0x5534C3FF), Color.Transparent), Offset(w * (0.5f + 0.35f * sin(a * 1.3f)), h * 0.6f), h * 0.7f),
                    h * 0.7f, Offset(w * (0.5f + 0.35f * sin(a * 1.3f)), h * 0.6f))
            }
            else -> { // 极光
                drawRect(Color(0xFF070B1A))
                val blobs = listOf(Color(0x6633FFAA) to 0f, Color(0x553388FF) to 2f, Color(0x55AA44FF) to 4f)
                for ((c, ph) in blobs) {
                    val o = Offset(w * (0.5f + 0.35f * sin(t * 0.12f + ph)), h * (0.4f + 0.2f * cos(t * 0.09f + ph)))
                    drawCircle(Brush.radialGradient(listOf(c, Color.Transparent), o, h * 0.8f), h * 0.8f, o)
                }
            }
        }
    }
}
