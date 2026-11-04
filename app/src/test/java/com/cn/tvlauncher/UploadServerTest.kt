package com.cn.tvlauncher

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.net.Socket

/** 上传服务端到端：真实端口 + curl multipart（5MB JPEG）、Expect 100-continue、chunked、端口被占顺延 */
class UploadServerTest {
    private fun jpeg5mb(): File {
        val f = File.createTempFile("big", ".jpg")
        val src = System.getenv("TEST_JPEG")?.let { File(it) }?.takeIf { it.exists() }
        if (src != null) src.copyTo(f, true) else f.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()) + ByteArray(5 * 1024 * 1024) { (it * 31).toByte() })
        return f
    }

    @Test fun curlMultipartAndFallbackPort() {
        val busy = ServerSocket(18899)   // 占住首选端口
        val saved = mutableListOf<ByteArray>(); val statuses = mutableListOf<String>(); var applied: String? = null
        val srv = UploadServer(18899, { saved += it; "u_test.jpg" }, { statuses += it }, { applied = "file:$it" })
        val port = srv.start()
        try {
            assertNotEquals(18899, port)
            val img = jpeg5mb()
            val curl = runCatching { ProcessBuilder("curl", "-s", "-F", "file=@${img.path};type=image/jpeg", "http://127.0.0.1:$port/upload")
                .redirectErrorStream(true).start() }.getOrNull()
            if (curl != null) {
                val out = curl.inputStream.bufferedReader().readText(); curl.waitFor()
                assertTrue(out, out.contains("已上传"))
                assertArrayEquals(img.readBytes(), saved.last())
                assertEquals("file:u_test.jpg", applied)
                assertTrue(statuses.toString(), statuses.last().startsWith("已设为壁纸"))
            }
            // GET 首页
            val page = java.net.URL("http://127.0.0.1:$port/").readText()
            assertTrue(page.contains("给电视换壁纸"))
            // chunked + Expect: 100-continue（原始字节）
            val data = ByteArray(300_000) { (it % 251).toByte() }
            Socket("127.0.0.1", port).use { s ->
                val o = s.getOutputStream()
                o.write("POST /upload HTTP/1.1\r\nHost: x\r\nX-Requested-With: XMLHttpRequest\r\nContent-Type: image/jpeg\r\nTransfer-Encoding: chunked\r\nExpect: 100-continue\r\n\r\n".toByteArray())
                o.flush()
                for (i in data.indices step 65536) { val n = minOf(65536, data.size - i); o.write("${Integer.toHexString(n)}\r\n".toByteArray()); o.write(data, i, n); o.write("\r\n".toByteArray()) }
                o.write("0\r\n\r\n".toByteArray()); o.flush()
                val resp = s.getInputStream().readBytes().toString(Charsets.UTF_8)
                assertTrue(resp, resp.startsWith("HTTP/1.1 100 Continue")); assertTrue(resp.contains("已上传"))
            }
            assertArrayEquals(data, saved.last())
        } finally { srv.stop(); busy.close() }
    }

    @Test fun pickIpSkipsVirtual() {
        assertEquals("192.168.1.8", Uploads.pickIp(listOf("p2p0" to "192.168.49.1", "lo" to "127.0.0.1", "wlan0" to "192.168.1.8")))
        assertEquals("10.0.0.3", Uploads.pickIp(listOf("wlan0" to "192.168.43.2", "eth0" to "10.0.0.3")))
        assertNull(Uploads.pickIp(listOf("tun0" to "10.8.0.2")))
    }
}
