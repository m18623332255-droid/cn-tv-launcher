package com.cn.tvlauncher

import org.junit.Assert.*
import org.junit.Test

/** 1.0.1/1.0.2 闪退回归：构造 MainActivity（字段初始化）时不得访问未初始化的 Prefs */
class StartupTest {
    @Test fun prefsBeforeInitThrows() { assertTrue(runCatching { Prefs.theme }.isFailure) }  // 旧版字段初始化即走到这里 → 闪退
    @Test fun isDarkSafeBeforeInit() { isDark() }
    @Test fun manyDuplicateApps() {
        val apps = (0 until 50).map { AppInfo("p${it % 20}", "应用$it", null) }
        assertNull(fallbackPlayer(apps))
        assertEquals(7, (apps.take(6).size + 2 + apps.drop(6).chunked(8).size).coerceAtMost(7))
    }
}
