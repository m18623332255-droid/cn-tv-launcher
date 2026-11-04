package com.cn.tvlauncher

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.imageLoader
import coil.request.ImageRequest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 屏保：海报轮播 + 毛玻璃大时钟，OK 打开当前影片（对应原型 saver.html） */
class Saver : DreamService() {
    private val h = Handler(Looper.getMainLooper())
    private var list: List<Rec> = emptyList()
    private var idx = 0
    private lateinit var img: ImageView; private lateinit var clock: TextView; private lateinit var date: TextView
    private lateinit var title: TextView; private lateinit var meta: TextView; private lateinit var ok: TextView
    private val px get() = resources.displayMetrics.widthPixels / 1920f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Prefs.init(this)
        isInteractive = true; isFullscreen = true
        list = mixed(loadRecs()).filter { it.cover.isNotBlank() }
        val k = px
        fun tv(size: Int, bold: Int = Typeface.NORMAL) = TextView(this).apply { setTextColor(Color.WHITE); textSize = size * k / resources.displayMetrics.scaledDensity; setTypeface(Typeface.create("sans-serif", bold)) }
        fun glass(r: Int, a: Int = 0x24) = GradientDrawable().apply { cornerRadius = r * k; setColor(Color.argb(a, 255, 255, 255)); setStroke((2 * k).toInt(), Color.argb(0x4D, 255, 255, 255)) }
        val root = FrameLayout(this)
        root.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF2B5876.toInt(), 0xFF4E4376.toInt(), 0xFF1A1030.toInt()))
        img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        root.addView(img, FrameLayout.LayoutParams(-1, -1))
        root.addView(View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x33000000, 0x22000000, 0xAA000000.toInt())) }, FrameLayout.LayoutParams(-1, -1))
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = glass(60); setPadding((110 * k).toInt(), (50 * k).toInt(), (110 * k).toInt(), (50 * k).toInt()) }
        clock = tv(220).apply { setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL)); gravity = Gravity.CENTER }
        date = tv(34).apply { alpha = .85f; gravity = Gravity.CENTER }
        card.addView(clock); card.addView(date)
        root.addView(card, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER).apply { bottomMargin = (100 * k).toInt() })
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title = tv(56, Typeface.BOLD); meta = tv(26).apply { alpha = .85f }
        info.addView(title); info.addView(meta)
        root.addView(info, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { leftMargin = (100 * k).toInt(); bottomMargin = (80 * k).toInt() })
        ok = tv(28, Typeface.BOLD).apply { text = "按 OK 观看"; background = glass(40, 0x33); setPadding((36 * k).toInt(), (16 * k).toInt(), (36 * k).toInt(), (16 * k).toInt()) }
        root.addView(ok, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { rightMargin = (100 * k).toInt(); bottomMargin = (86 * k).toInt() })
        setContentView(root)
    }

    private val tick = object : Runnable { override fun run() {
        val t = LocalDateTime.now()
        clock.text = t.format(DateTimeFormatter.ofPattern("HH:mm"))
        date.text = dateLine(t.toLocalDate()) + (weatherCache()?.let { " · ${Prefs.city.name} ${it.text} ${it.temp}°" } ?: "")
        h.postDelayed(this, 10_000)
    } }
    private val slide = object : Runnable { override fun run() { show(); idx++; h.postDelayed(this, 12_000) } }

    private fun show() {
        if (list.isEmpty()) { title.text = ""; meta.text = ""; ok.visibility = View.GONE; return }
        val r = list[idx % list.size]
        title.text = r.title
        meta.text = listOf(Recs.KINDS[r.kind] ?: "", if (r.rate.isNotBlank()) "豆瓣 ${r.rate}" else "", r.platform).filter { it.isNotBlank() }.joinToString(" · ")
        imageLoader.enqueue(ImageRequest.Builder(this).data(r.cover).addHeader("Referer", "https://movie.douban.com/").addHeader("User-Agent", UA).crossfade(800).target(img).build())
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); h.post(tick); h.post(slide) }
    override fun onDreamingStopped() { h.removeCallbacksAndMessages(null); super.onDreamingStopped() }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.action == KeyEvent.ACTION_UP && (e.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == KeyEvent.KEYCODE_ENTER) && list.isNotEmpty()) {
            val r = list[Math.floorMod(idx - 1, list.size)]
            if (r.pkg.isNotBlank()) Apps.openSearch(this, r.pkg, r.title)
            finish(); return true
        }
        if (e.action == KeyEvent.ACTION_UP) finish()
        return true
    }
}
