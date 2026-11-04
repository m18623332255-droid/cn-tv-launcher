package com.cn.tvlauncher

import android.app.Application
import android.content.Context
import java.io.File

/** 崩溃记录：未捕获异常写入 files/crash.txt，下次启动进入安全模式显示 */
object Crash {
    private fun f(c: Context) = File(c.filesDir, "crash.txt")
    fun save(c: Context, t: Throwable, fatal: Boolean = true) = runCatching {
        val txt = "${java.time.LocalDateTime.now()} v${BuildConfig.VERSION_NAME}\n" + android.util.Log.getStackTraceString(t).ifBlank { t.stackTraceToString() }
        if (fatal) f(c).writeText(txt) else File(c.filesDir, "errors.txt").appendText(txt.take(3000) + "\n")
    }
    fun read(c: Context): String? = runCatching { f(c).takeIf { it.exists() }?.readText() }.getOrNull()
    fun clear(c: Context) { runCatching { f(c).delete() } }
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, e -> Crash.save(this, e); prev?.uncaughtException(th, e) }
    }
}
