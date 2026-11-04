package com.cn.tvlauncher

import org.junit.Assert.*
import org.junit.Test

class QrTest {
    @Test fun rsKnown() {
        // ISO 18004 附录示例 1-M："01234567" 的纠错码（10 个）
        val d = intArrayOf(16, 32, 12, 86, 97, 128, 236, 17, 236, 17, 236, 17, 236, 17, 236, 17)
        assertArrayEquals(intArrayOf(165, 36, 212, 193, 237, 54, 199, 135, 44, 85), Qr.rs(d, 10))
    }
    @Test fun dumpForDecoder() {
        for ((i, s) in listOf("http://192.168.1.23:8899", "http://10.0.0.5:8899", "http://192.168.100.200:8899/").withIndex()) {
            val m = Qr.encode(s)
            System.getProperty("qrdump")?.let { dir -> java.io.File(dir, "q$i.txt").writeText(s + "\n" + m.joinToString("\n") { r -> r.joinToString("") { if (it) "1" else "0" } }) }
        }
    }
}
