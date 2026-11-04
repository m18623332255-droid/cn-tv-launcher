package com.cn.tvlauncher

/** 极简二维码编码器（字节模式、纠错 L、版本 1–5、单块），用于局域网上传地址；纯 Kotlin，可单元测试 */
object Qr {
    private val DATA = intArrayOf(0, 19, 34, 55, 80, 108)
    private val ECC = intArrayOf(0, 7, 10, 15, 20, 26)
    private val ALIGN = intArrayOf(0, 0, 18, 22, 26, 30)
    private val EXP = IntArray(512); private val LOG = IntArray(256)
    init { var x = 1; for (i in 0 until 255) { EXP[i] = x; LOG[x] = i; x = x shl 1; if (x and 0x100 != 0) x = x xor 0x11D }; for (i in 255 until 512) EXP[i] = EXP[i - 255] }
    private fun mul(a: Int, b: Int) = if (a == 0 || b == 0) 0 else EXP[LOG[a] + LOG[b]]

    fun rs(data: IntArray, n: Int): IntArray {
        var g = intArrayOf(1)
        for (i in 0 until n) { val ng = IntArray(g.size + 1); for (j in g.indices) { ng[j] = ng[j] xor g[j]; ng[j + 1] = ng[j + 1] xor mul(g[j], EXP[i]) }; g = ng }
        val r = IntArray(n)
        for (d in data) { val f = d xor r[0]; for (j in 0 until n - 1) r[j] = r[j + 1] xor mul(g[j + 1], f); r[n - 1] = mul(g[n], f) }
        return r
    }

    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val ver = (1..5).firstOrNull { DATA[it] - 2 >= bytes.size } ?: error("文本太长")
        val cap = DATA[ver]
        val bits = ArrayList<Boolean>()
        fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits += (v shr i) and 1 == 1 }
        put(0b0100, 4); put(bytes.size, 8); bytes.forEach { put(it.toInt() and 0xFF, 8) }
        put(0, minOf(4, cap * 8 - bits.size)); while (bits.size % 8 != 0) bits += false
        val cw = IntArray(cap); var k = 0
        for (i in 0 until bits.size / 8) { var b = 0; for (j in 0 until 8) b = (b shl 1) or (if (bits[i * 8 + j]) 1 else 0); cw[k++] = b }
        var pad = 0; while (k < cap) cw[k++] = if (pad++ % 2 == 0) 0xEC else 0x11
        val all = cw + rs(cw, ECC[ver])

        val n = 17 + 4 * ver
        val m = Array(n) { BooleanArray(n) }; val fn = Array(n) { BooleanArray(n) }
        fun set(x: Int, y: Int, v: Boolean) { m[y][x] = v; fn[y][x] = true }
        fun finder(cx: Int, cy: Int) { for (dy in -4..4) for (dx in -4..4) { val x = cx + dx; val y = cy + dy
            if (x in 0 until n && y in 0 until n) { val d = maxOf(Math.abs(dx), Math.abs(dy)); set(x, y, d != 2 && d != 4) } } }
        finder(3, 3); finder(n - 4, 3); finder(3, n - 4)
        for (i in 0 until n) { if (!fn[6][i]) set(i, 6, i % 2 == 0); if (!fn[i][6]) set(6, i, i % 2 == 0) }
        if (ver >= 2) { val a = ALIGN[ver]; for (dy in -2..2) for (dx in -2..2) set(a + dx, a + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1) }
        // 预留格式信息区
        for (i in 0..8) { if (!fn[8][i]) set(i, 8, false); if (!fn[i][8]) set(8, i, false) }
        for (i in 0 until 8) { set(n - 1 - i, 8, false); set(8, n - 1 - i, false) }
        set(8, n - 8, true) // 暗模块

        // 数据放置
        var bi = 0; val total = all.size * 8
        var right = n - 1; var up = true
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until n) for (j in 0..1) {
                val x = right - j; val y = if (up) n - 1 - vert else vert
                if (!fn[y][x]) { m[y][x] = bi < total && (all[bi / 8] shr (7 - bi % 8)) and 1 == 1; bi++ }
            }
            up = !up; right -= 2
        }
        // 掩码 0：(x+y)%2==0
        val mask = 0
        for (y in 0 until n) for (x in 0 until n) if (!fn[y][x] && (x + y) % 2 == 0) m[y][x] = !m[y][x]
        // 格式信息（L=01）
        val d = (0b01 shl 3) or mask
        var r = d shl 10; for (i in 14 downTo 10) if ((r shr i) and 1 == 1) r = r xor (0x537 shl (i - 10))
        val f = ((d shl 10) or r) xor 0x5412
        fun b(i: Int) = (f shr i) and 1 == 1
        for (i in 0..5) m[i][8] = b(i)
        m[7][8] = b(6); m[8][8] = b(7); m[8][7] = b(8)
        for (i in 9..14) m[8][14 - i] = b(i)
        for (i in 0..7) m[8][n - 1 - i] = b(i)
        for (i in 8..14) m[n - 15 + i][8] = b(i)
        m[n - 8][8] = true
        return m
    }
}
