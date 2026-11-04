package com.cn.tvlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import java.io.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/** 自定义壁纸文件：files/wall/u_时间.jpg，最多保留 6 张 */
object Uploads {
    const val MAX = 6
    fun dir(c: Context) = File(c.filesDir, "wall").apply { mkdirs() }
    fun list(c: Context): List<File> = (dir(c).listFiles { f -> f.name.endsWith(".jpg") } ?: emptyArray()).sortedByDescending { it.lastModified() }
    fun file(c: Context, kind: String): File? = kind.removePrefix("file:").takeIf { kind.startsWith("file:") && !it.contains('/') }?.let { File(dir(c), it) }?.takeIf { it.exists() }
    fun delete(c: Context, f: File) { if (Prefs.wall == "file:${f.name}") Prefs.wall = "posters"; f.delete() }

    /** 壁纸区建议尺寸：全宽 × 屏高约 72%（1080p≈1920×670，4K≈3840×1340） */
    fun wallSize(c: Context): Pair<Int, Int> {
        val dm = c.resources.displayMetrics
        val w = maxOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(1280)
        val h = minOf(dm.widthPixels, dm.heightPixels).coerceAtLeast(720)
        return w to (h * 0.72f).toInt().coerceAtLeast(400)
    }
    fun sizeHint(c: Context): String { val (w, h) = wallSize(c); return "${w}×${h}（按本机屏幕自动适配；1080p 约 1920×780，4K 约 3840×1550）" }

    /** 解码后按壁纸区做 Cover（居中裁剪填满），最长边不超过目标宽；返回文件名 */
    fun save(c: Context, data: ByteArray): String {
        val (tw, th) = wallSize(c)
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeByteArray(data, 0, data.size, o)
        require(o.outWidth > 0) { "不是图片" }
        var s = 1; while (maxOf(o.outWidth / s, o.outHeight / s) > maxOf(tw, th) * 2) s *= 2
        var b = BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: error("解码失败")
        // Cover：等比放大后居中裁到 tw×th
        val scale = maxOf(tw.toFloat() / b.width, th.toFloat() / b.height)
        val sw = maxOf(1, Math.round(b.width * scale)); val sh = maxOf(1, Math.round(b.height * scale))
        val scaled = if (sw != b.width || sh != b.height) Bitmap.createScaledBitmap(b, sw, sh, true).also { if (it !== b) b.recycle() } else b
        val x = ((scaled.width - tw) / 2).coerceAtLeast(0); val y = ((scaled.height - th) / 2).coerceAtLeast(0)
        val cw = minOf(tw, scaled.width); val ch = minOf(th, scaled.height)
        val cropped = Bitmap.createBitmap(scaled, x, y, cw, ch).also { if (it !== scaled) scaled.recycle() }
        val name = "u_${System.currentTimeMillis()}.jpg"
        FileOutputStream(File(dir(c), name)).use { cropped.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        cropped.recycle()
        list(c).drop(MAX).forEach { delete(c, it) }
        return name
    }

    /** 上传状态（电视界面用 StateFlow 订阅） */
    val status = kotlinx.coroutines.flow.MutableStateFlow("等待上传")

    /** 取电视真实局域网 IP：先用系统当前网络（以太网/Wi-Fi），再枚举网卡，跳过回环、投屏 p2p、热点、VPN 等虚拟网卡 */
    fun lanIp(c: Context): String? = runCatching {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.activeNetwork?.let { cm.getLinkProperties(it) }?.linkAddresses?.map { it.address }
            ?.filterIsInstance<Inet4Address>()?.map { it.hostAddress ?: "" }?.firstOrNull { isLan(it) }
    }.getOrNull() ?: pickIp(runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { ni.name to (it.hostAddress ?: "") } }
    }.getOrDefault(emptyList()))

    fun isLan(ip: String) = !ip.startsWith("127.") && !ip.startsWith("169.254.") && !ip.startsWith("192.168.49.") &&
        (ip.startsWith("10.") || ip.startsWith("192.168.") || Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(ip))
    private val SKIP = listOf("lo", "p2p", "ap", "tun", "ppp", "rmnet", "dummy", "docker", "veth", "br-", "sit", "ip6", "rndis", "usb")
    /** 纯函数：从 (网卡名, IPv4) 中选出最可能的局域网地址；eth 优先，其次 wlan */
    fun pickIp(list: List<Pair<String, String>>): String? = list.filter { (n, ip) -> isLan(ip) && SKIP.none { n.startsWith(it) } }
        .sortedBy { (n, _) -> when { n.startsWith("eth") -> 0; n.startsWith("wlan") -> 1; else -> 2 } }.firstOrNull()?.second
}

/**
 * 极简 HTTP 服务（纯 JVM，可单元测试）：监听 0.0.0.0，端口忙则顺延。
 * GET / → 上传页；POST /upload → multipart/form-data 或原始图片；支持 Expect: 100-continue 与 chunked。
 */
class UploadServer(private val firstPort: Int, private val save: (ByteArray) -> String, private val onStatus: (String) -> Unit, private val onSaved: (String) -> Unit) {
    @Volatile private var ss: ServerSocket? = null
    var port = firstPort; private set
    fun start(): Int {
        var last: Exception? = null
        for (p in firstPort until firstPort + 20) {
            try { ss = ServerSocket().apply { reuseAddress = true; bind(java.net.InetSocketAddress("0.0.0.0", p)) }; port = p; break } catch (e: Exception) { last = e }
        }
        val server = ss ?: throw IOException("端口 $firstPort 起都被占用：${last?.message}")
        thread(isDaemon = true, name = "upload-accept") {
            while (!server.isClosed) { val s = try { server.accept() } catch (e: Exception) { break }; thread(isDaemon = true, name = "upload-conn") { handle(s) } }
        }
        return port
    }
    fun stop() { runCatching { ss?.close() }; ss = null }

    private fun handle(s: Socket) { try { s.use { serve(it) } } catch (e: Exception) { onStatus("失败：${e.message ?: e.javaClass.simpleName}") } }

    private fun serve(s: Socket) {
        s.soTimeout = 60_000
        val inp = BufferedInputStream(s.getInputStream(), 64 * 1024)
        val out = BufferedOutputStream(s.getOutputStream())
        fun line(): String { val b = ByteArrayOutputStream(); while (true) { val x = inp.read(); if (x < 0 || x == '\n'.code) break; if (x != '\r'.code) b.write(x) }; return b.toString("UTF-8") }
        val req = line().split(" ")
        val h = HashMap<String, String>()
        while (true) { val l = line(); if (l.isEmpty()) break; val i = l.indexOf(':'); if (i > 0) h[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim() }
        fun send(code: String, type: String, body: String) { val b = body.toByteArray(); out.write("HTTP/1.1 $code\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${b.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray()); out.write(b); out.flush() }
        val xhr = h["x-requested-with"] != null
        fun reply(ok: Boolean, msg: String) = if (xhr) send(if (ok) "200 OK" else "400 Bad Request", "text/plain", msg)
            else send(if (ok) "200 OK" else "400 Bad Request", "text/html", PAGE.replace("<div id=\"m\"></div>", "<div id=\"m\">$msg</div>"))
        if (req.getOrNull(0) != "POST") { send("200 OK", "text/html", PAGE); return }
        onStatus("收到图片，正在接收…")
        if (h["expect"]?.lowercase() == "100-continue") { out.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray()); out.flush() }
        val body = if (h["transfer-encoding"]?.lowercase()?.contains("chunked") == true) readChunked(inp) else {
            val len = h["content-length"]?.toIntOrNull() ?: -1
            if (len < 0 || len > MAX_BYTES) { onStatus("失败：图片太大或缺少长度"); reply(false, "图片太大（最大 30MB）"); return }
            readN(inp, len)
        }
        val ct = h["content-type"] ?: ""
        val img = if (ct.startsWith("multipart/", true)) multipartFile(body, ct) else body
        if (img == null || img.isEmpty()) { onStatus("失败：没有收到图片数据"); reply(false, "没有收到图片"); return }
        val name = try { save(img) } catch (e: Exception) { onStatus("失败：${e.message ?: "无法识别图片"}"); reply(false, "上传失败：${e.message}"); return }
        onSaved(name); onStatus("已设为壁纸 ✓（${img.size / 1024} KB）")
        reply(true, "已上传，电视已换上新壁纸 ✓")
    }

    companion object {
        const val MAX_BYTES = 30 * 1024 * 1024
        fun readN(i: InputStream, n: Int): ByteArray { val d = ByteArray(n); var r = 0; while (r < n) { val k = i.read(d, r, n - r); if (k < 0) throw EOFException("连接中断（$r/$n）"); r += k }; return d }
        fun readChunked(i: InputStream): ByteArray {
            val o = ByteArrayOutputStream()
            fun line(): String { val b = StringBuilder(); while (true) { val x = i.read(); if (x < 0 || x == '\n'.code) break; if (x != '\r'.code) b.append(x.toChar()) }; return b.toString() }
            while (true) { val n = line().substringBefore(';').trim().toInt(16); if (n == 0) { while (line().isNotEmpty()) {}; break }; o.write(readN(i, n)); line(); if (o.size() > MAX_BYTES) throw IOException("图片太大") }
            return o.toByteArray()
        }
        fun indexOf(a: ByteArray, p: ByteArray, from: Int = 0): Int {
            outer@ for (i in from..a.size - p.size) { for (j in p.indices) if (a[i + j] != p[j]) continue@outer; return i }
            return -1
        }
        /** 从 multipart 中取第一个带 filename 的部分（无则取第一个部分） */
        fun multipartFile(body: ByteArray, ct: String): ByteArray? {
            val bd = Regex("boundary=\"?([^\";]+)\"?").find(ct)?.groupValues?.get(1) ?: return null
            val delim = "--$bd".toByteArray(); val sep = "\r\n\r\n".toByteArray(); val end = "\r\n--$bd".toByteArray()
            var pos = indexOf(body, delim); var first: ByteArray? = null
            while (pos >= 0) {
                val hs = pos + delim.size + 2; if (hs >= body.size) break
                val he = indexOf(body, sep, hs); if (he < 0) break
                val head = String(body, hs, he - hs, Charsets.UTF_8)
                val ds = he + 4; val de = indexOf(body, end, ds); if (de < 0) break
                val part = body.copyOfRange(ds, de)
                if (head.contains("filename=", true)) return part
                if (first == null) first = part
                pos = de + 2
            }
            return first
        }
        val PAGE = """<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>上传壁纸 · 清爽桌面</title><style>body{font-family:-apple-system,"PingFang SC",system-ui,sans-serif;background:#f5f5f7;margin:0;padding:20px;color:#1d1d1f}
.c{background:#fff;border-radius:18px;padding:22px;box-shadow:0 4px 20px rgba(0,0,0,.06)}h1{font-size:22px;margin:0 0 8px}
input[type=file]{display:block;width:100%;padding:14px 0;font-size:16px}img{max-width:100%;border-radius:12px;margin-top:10px;display:none}
button{width:100%;margin-top:14px;padding:14px;border:0;border-radius:12px;background:#0071e3;color:#fff;font-size:18px}
#m{margin-top:14px;text-align:center;font-size:16px}progress{width:100%;display:none}</style></head><body><div class="c"><h1>给电视换壁纸</h1>
<p>选一张照片，上传后电视会自动换上。</p><p style="color:#666;font-size:14px">建议横图，约 1920×780（1080p）或 3840×1550（4K）。电视会居中裁剪填满上方壁纸区。</p><form id="fm" method="post" action="/upload" enctype="multipart/form-data">
<input id="f" name="file" type="file" accept="image/*"><img id="p"><progress id="g" max="100" value="0"></progress><button type="submit">上传到电视</button></form><div id="m"></div></div>
<script>var f=document.getElementById('f'),p=document.getElementById('p'),m=document.getElementById('m'),g=document.getElementById('g');
f.onchange=function(){try{if(f.files[0]){p.src=URL.createObjectURL(f.files[0]);p.style.display='block'}}catch(e){}};
document.getElementById('fm').onsubmit=function(ev){if(!window.FormData||!window.XMLHttpRequest)return true;ev.preventDefault();
if(!f.files[0]){m.textContent='请先选择图片';return false}var fd=new FormData();fd.append('file',f.files[0]);var x=new XMLHttpRequest();
x.open('POST','/upload');x.setRequestHeader('X-Requested-With','XMLHttpRequest');g.style.display='block';m.textContent='上传中…';
x.upload.onprogress=function(e){if(e.lengthComputable)g.value=e.loaded*100/e.total};x.onload=function(){m.textContent=x.responseText};
x.onerror=function(){m.textContent='上传失败，请确认手机和电视连的是同一个网络'};x.send(fd);return false};</script></body></html>"""
    }
}

class UploadActivity : ComponentActivity() {
    private var server: UploadServer? = null
    private val files = mutableStateOf<List<File>>(emptyList())
    private val err = mutableStateOf("")
    private val port = 8899

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); Prefs.init(this)
        setContent { InitScale(); Screen() }
    }
    private val livePort = mutableIntStateOf(8899)
    override fun onStart() {
        super.onStart()
        files.value = Uploads.list(this)
        Uploads.status.value = "等待上传"
        val main = Handler(Looper.getMainLooper())
        val srv = UploadServer(port, { Uploads.save(applicationContext, it) }, { st -> Uploads.status.value = st },
            { name -> main.post { Prefs.wall = "file:$name"; files.value = Uploads.list(this) } })
        server = srv
        thread(name = "upload-start") {   // 绑定端口不放在主线程
            runCatching { srv.start() }.onSuccess { p -> main.post { livePort.intValue = p; err.value = "" } }
                .onFailure { e -> main.post { err.value = "无法启动上传服务：${e.message}"; Uploads.status.value = "失败：上传服务没有启动" } }
        }
    }
    override fun onStop() { server?.stop(); server = null; super.onStop() }   // 离开页面即关闭服务

    @Composable fun Screen() {
        Prefs.ver.intValue
        val ip = remember { Uploads.lanIp(applicationContext) }
        val url = if (ip != null) "http://$ip:${livePort.intValue}" else ""
        val st by Uploads.status.collectAsState()
        val qr = remember(url) { if (url.isNotEmpty()) runCatching { Qr.encode(url) }.getOrNull() else null }
        val first = remember { FocusRequester() }
        Row(Modifier.fillMaxSize().background(Color(0xFF15171F)).padding(80.u), horizontalArrangement = Arrangement.spacedBy(70.u)) {
            Box(Modifier.size(520.u).clip(RoundedCornerShape(28.u)).background(Color.White).padding(30.u), contentAlignment = Alignment.Center) {
                if (qr != null) Canvas(Modifier.fillMaxSize()) {
                    val n = qr.size; val cell = size.minDimension / n
                    for (y in 0 until n) for (x in 0 until n) if (qr[y][x]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                } else T("未连接网络", 30, Color.Black)
            }
            Column(Modifier.weight(1f)) {
                T("自定义壁纸", 48, Color.White, FontWeight.Bold)
                T("用手机扫码，选一张照片上传，电视会自动换上。", 26, Color.White, modifier = Modifier.padding(top = 16.u), alpha = .8f)
                T("建议尺寸：${Uploads.sizeHint(applicationContext)}", 22, Color(0xFFFFD60A), FontWeight.SemiBold, Modifier.padding(top = 12.u), lines = 2)
                T("照片会按「居中裁剪」填满上方壁纸区（全宽 × 屏高约 72%），下方应用架滚动时壁纸不动。", 20, Color.White, modifier = Modifier.padding(top = 8.u), alpha = .7f, lines = 2)
                T(if (url.isNotEmpty()) "或在手机浏览器打开：$url" else "电视没有联网，无法上传", 30, Color(0xFF64D2FF), FontWeight.SemiBold, Modifier.padding(top = 24.u))
                T("请让手机和电视连同一个 Wi-Fi。离开本页面后上传服务会自动关闭。", 22, Color.White, modifier = Modifier.padding(top = 12.u), alpha = .6f, lines = 2)
                T("状态：$st", 28, if (st.startsWith("失败")) Color(0xFFFF6A3D) else if (st.contains("✓")) Color(0xFF34C759) else Color.White, FontWeight.SemiBold, Modifier.padding(top = 20.u))
                if (err.value.isNotEmpty()) T(err.value, 22, Color(0xFFFF6A3D), modifier = Modifier.padding(top = 12.u))
                T("最近上传（最多 ${Uploads.MAX} 张）：OK 设为壁纸，菜单键删除", 26, Color.White, FontWeight.Bold, Modifier.padding(top = 40.u, bottom = 16.u))
                if (files.value.isEmpty()) T("还没有上传过", 22, Color.White, alpha = .5f)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.u)) {
                    items(files.value, key = { it.name }) { f ->
                        var fo by remember { mutableStateOf(false) }
                        val on = Prefs.wall == "file:${f.name}"
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(256.u, 144.u).clip(RoundedCornerShape(16.u))
                                .border(if (fo) 5.u else if (on) 3.u else 0.u, if (fo) Color.White else Color(0xFF34C759), RoundedCornerShape(16.u))
                                .then(if (f == files.value.first()) Modifier.focusRequester(first) else Modifier)
                                .tvClick({ fo = it }, onMenu = { Uploads.delete(this@UploadActivity, f); files.value = Uploads.list(this@UploadActivity) }) { Prefs.wall = "file:${f.name}" }) {
                                Wallpaper("file:${f.name}", emptyList(), Modifier.fillMaxSize(), dim = false)
                            }
                            T(if (on) "✓ 当前壁纸" else if (fo) "OK 使用 · 菜单键删除" else "", 20, Color.White, modifier = Modifier.padding(top = 8.u))
                        }
                    }
                }
            }
        }
        LaunchedEffect(files.value.size) { delay(200); runCatching { first.requestFocus() } }
    }
}
