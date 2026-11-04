package com.cn.tvlauncher

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    @Test fun lunarKnownDates() {
        // 对照 lunar-python / 万年历
        assertEquals(Lunar.LDate(2026, 9, 26, false), Lunar.of(LocalDate.of(2026, 11, 4)))
        assertEquals(Lunar.LDate(2026, 1, 1, false), Lunar.of(LocalDate.of(2026, 2, 17)))   // 2026 春节
        assertEquals(Lunar.LDate(2023, 2, 1, true), Lunar.of(LocalDate.of(2023, 3, 22)))    // 闰二月初一
        assertEquals(Lunar.LDate(2020, 4, 1, true), Lunar.of(LocalDate.of(2020, 5, 23)))    // 闰四月
        assertEquals(Lunar.LDate(2033, 11, 1, true), Lunar.of(LocalDate.of(2033, 12, 22)))  // 闰冬月
        assertEquals(Lunar.LDate(2025, 1, 1, false), Lunar.of(LocalDate.of(2025, 1, 29)))
        assertEquals(Lunar.LDate(2024, 12, 29, false), Lunar.of(LocalDate.of(2025, 1, 28))) // 除夕（腊月小）
        assertEquals("农历九月廿六", Lunar.text(LocalDate.of(2026, 11, 4)))
        assertEquals("农历闰二月初一", Lunar.text(LocalDate.of(2023, 3, 22)))
        assertEquals("初十", Lunar.dayName(10)); assertEquals("二十", Lunar.dayName(20)); assertEquals("廿一", Lunar.dayName(21)); assertEquals("三十", Lunar.dayName(30))
    }

    @Test fun sunChongqing() {
        // 重庆 2026-11-04 日出 07:09、日落 18:05（Open-Meteo 同日数据，允许 ±6 分钟）
        val (rise, set) = Sun.times(LocalDate.of(2026, 11, 4), 29.563, 106.551)!!
        assertEquals(7 * 60 + 9.0, rise.toDouble(), 6.0)
        assertEquals(18 * 60 + 5.0, set.toDouble(), 6.0)
        assertTrue(Sun.isNight(LocalDate.of(2026, 11, 4), 22 * 60, 29.563, 106.551))
        assertFalse(Sun.isNight(LocalDate.of(2026, 11, 4), 12 * 60, 29.563, 106.551))
        // 北京夏至日出约 04:46
        assertEquals(4 * 60 + 46.0, Sun.times(LocalDate.of(2026, 6, 21), 39.904, 116.407)!!.first.toDouble(), 6.0)
    }

    @Test fun parseDouban() {
        val json = """{"subjects":[{"episodes_info":"29集全","rate":"8.0","title":"小城良方","url":"https://movie.douban.com/subject/36675942/","cover":"https://img2.doubanio.com/a.jpg","id":"36675942"},
            {"rate":"","title":"后会友期","cover":"https://img3.doubanio.com/b.jpg","id":"37100984"},{"title":"","cover":"x","id":"1"},{"title":"无图","cover":"","id":"2"}]}"""
        val l = Recs.parse(json, "tv")
        assertEquals(2, l.size)
        assertEquals("小城良方", l[0].title); assertEquals("8.0", l[0].rate); assertEquals("36675942", l[0].id); assertEquals("tv", l[0].kind)
        val a = Recs.assign(l, listOf("腾讯视频" to "com.ktcp.video", "芒果TV" to "com.hunantv.license"))
        assertEquals("腾讯视频", a[0].platform); assertEquals("com.hunantv.license", a[1].pkg)
        assertEquals(2, Recs.assign(l, emptyList()).size)  // 没装平台也显示
        assertTrue(Recs.parse("{}", "movie").isEmpty())
    }

    @Test fun weatherParse() {
        val w = WeatherApi.parse("""{"current_weather":{"temperature":17.6,"weathercode":3},"daily":{"sunrise":["2026-11-04T07:23"],"sunset":["2026-11-04T18:21"]}}""")
        assertEquals(18, w.temp); assertEquals("阴", w.text); assertEquals("07:23", w.sunrise)
    }

    @Test fun debloatSafety() {
        assertTrue(Debloat.isProtected("com.android.tv.settings"))
        assertTrue(Debloat.isProtected("com.hisense.tv.settings")); assertTrue(Debloat.isProtected("com.tianci.setting"))
        assertFalse(Debloat.isProtected("com.hisense.service.ad"))
        assertFalse(Debloat.mayDisableHome("com.tianci.homepage"))
        assertTrue(Debloat.mayDisableHome("com.mitv.tvhome"))
        val l = Debloat.Log(mutableListOf("a.b", "c.d"), "x/y", "h/i")
        assertEquals(l, Debloat.Log.parse(l.json()))
        assertTrue(Debloat.Log.parse("garbage").empty)
    }

    @Test fun fallbackPlayerPick() {
        fun a(p: String, l: String) = AppInfo(p, l, null)
        assertEquals("com.github.tvbox.osc", fallbackPlayer(listOf(a("x", "Hi音乐"), a("com.home.tvvideo", "家庭影视"), a("com.github.tvbox.osc", "TVBox")))?.pkg)
        assertEquals("y.z", fallbackPlayer(listOf(a("com.home.tvvideo", "家庭影视"), a("y.z", "My TVBox")))?.pkg)
        assertEquals("com.home.tvvideo", fallbackPlayer(listOf(a("com.home.tvvideo", "家庭影视")))?.pkg)
        assertNull(fallbackPlayer(listOf(a("x", "Hi投屏"))))
    }

    @Test fun auditHintsSkipProtected() {
        // 已在 ADS 清单里的包不会再进审计；设置/launcher 类要跳过
        val hits = Debloat.auditHints(listOf(
            "com.brand.newadvertise", "com.android.tv.settings", "com.demo.analytics.sdk",
            "com.good.launcher", "com.vendor.bootadservice", "com.hisense.tv.settings",
            "com.hisense.hitv.advertise"  // 已在 ADS，不应出现在审计结果
        ))
        assertTrue(hits.contains("com.brand.newadvertise"))
        assertTrue(hits.contains("com.demo.analytics.sdk"))
        assertTrue(hits.contains("com.vendor.bootadservice"))
        assertFalse(hits.contains("com.hisense.hitv.advertise"))
        assertFalse(hits.contains("com.android.tv.settings"))
        assertFalse(hits.contains("com.hisense.tv.settings"))
        assertFalse(hits.contains("com.good.launcher"))
    }
}
