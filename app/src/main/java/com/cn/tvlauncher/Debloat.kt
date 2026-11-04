package com.cn.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * 去广告：思路来自 Free My TV（MIT）——应用通过本机 127.0.0.1:5555 的 ADB（需用户开启 USB/网络调试并在电视上
 * 允许“USB 调试”）执行：cmd package set-home-activity、pm disable-user --user 0（只停用、不卸载）、
 * settings put secure screensaver_components。所有变更写入本地日志，并备份到 settings global（卸载重装后仍可还原）。
 */
object Debloat {
    const val HOME = "com.cn.tvlauncher/com.cn.tvlauncher.MainActivity"
    const val DREAM = "com.cn.tvlauncher/com.cn.tvlauncher.Saver"
    private const val BACKUP_KEY = "cn_tvlauncher_changes"

    data class Item(val pkg: String, val brand: String, val what: String, val boot: Boolean = false)

    /**
     * 推广/开机广告包名清单（只停用本机实际存在的；OTA 后可能复活，重新执行即可）。来源：
     *  [A] github.com/wangrongding/tv-fresh README「去广告组件清单」（小米）
     *  [B] debloattv.com/xiaomi-tv-debloat（小米，"every Xiaomi TV list agrees on"）
     *  [C] 151365.cc/post/46「TCL电视免 Root 卸载预装应用」（com.tcl.bootadservice = TCL 开机广告）
     *  [D] blog.alliot.tech/post/fxxk-android-tv（创维/酷开）
     *  [E] znds.com/tv-1247333 / blog.csdn.net/xyaocc/article/details/136986798（海信精简清单）
     *  [F] Free My TV HomeMode.java DEBLOAT（小米、创维海外机型部分）
     * 均为社区实测清单，未在每款机型核实；不同固件包名可能不同。
     */
    val ADS = listOf(
        Item("com.miui.systemAdSolution", "小米", "系统广告引擎（开屏/横幅）[A]", boot = true),
        Item("com.mitv.tvad", "小米", "电视开机广告 [A]", boot = true),
        Item("com.xiaomi.mitv.advertise", "小米", "广告服务 [A]"),
        Item("com.duokan.videodaily", "小米", "今日头屏 [A]"),
        Item("com.xiaomi.mitv.tvpush", "小米", "消息推送弹窗 [A]"),
        Item("com.miui.tv.analytics", "小米", "行为统计 [A][B][F]"),
        Item("com.xiaomi.statistic", "小米", "统计上报 [B][F]"),
        Item("com.mitv.shoplugin", "小米", "商城插件 [A]"),
        Item("com.tcl.bootadservice", "TCL", "开机广告 AdService [C]", boot = true),
        Item("com.tcl.tshop", "TCL", "T商店/T惠购 [C]"),
        Item("com.tcl.playskill", "TCL", "玩机技巧 [C]"),
        Item("com.tcl.tliveplay", "TCL", "轮播频道（王牌频道）[C]"),
        Item("com.hisense.tv.advertisementpr", "海信", "广告组件 [E]", boot = true),
        Item("com.hisense.service.ad", "海信", "广告服务 [E]"),
        Item("com.hisense.magic.ad", "海信", "广告组件 [E]"),
        Item("com.hisense.hitv.advertise", "海信", "广告服务"),
        Item("com.hisense.tv.ad", "海信", "广告"),
        Item("com.hisense.ad", "海信", "广告"),
        Item("com.jamdeo.tv.vendor.ads", "海信/Jamdeo", "厂商广告"),
        Item("com.hisense.tv.messagecenter", "海信", "消息中心弹窗"),
        Item("com.hisense.push", "海信", "推送"),
        Item("com.hisense.store", "海信", "应用商店推广"),
        Item("com.tcl.waterfall.overseas", "TCL", "瀑布流推广"),
        Item("com.tcl.partnercustomizer", "TCL", "合作推广"),
        Item("com.xiaomi.mitv.payment", "小米", "支付/商城推广"),
        Item("com.mitv.gallery", "小米", "画廊推广"),
        Item("com.tianci.ad", "创维/酷开", "广告 [D]", boot = true),
        Item("com.tianci.push", "创维/酷开", "推送 [D]"),
        Item("com.coocaa.messageprovider", "创维/酷开", "消息推送 [D]"),
        Item("com.coocaa.playtvskill", "创维/酷开", "玩机技巧 [D]"),
        Item("com.smartdevice.recommendation", "创维", "推广频道 [F]"),
    )

    /** 广告特征：包名含这些词且不在保护名单 → 列入审计候选（不自动停用，由 apply 再过滤） */
    private val AD_HINTS = listOf("advertise", "advert", ".ad.", "adservice", "adservice", "tvad", "bootad", "push", "analytics", "statistic", "messageprovider", "playskill", "recommendation")
    private val AD_HINT_SKIP = listOf("load", "pad", "shadow", "radio", "reader", "adapter", "admin", "address", "download", "update", "launcher", "provider.media", "input")

    /** 扫描本机已装应用，找出清单外、包名像广告的候选（纯函数便于测试） */
    fun auditHints(pkgs: List<String>): List<String> = pkgs.filter { p ->
        val low = p.lowercase()
        !isProtected(p) && ADS.none { it.pkg == p } && AD_HINTS.any { low.contains(it) } && AD_HINT_SKIP.none { low.contains(it) }
    }.distinct().sorted()

    fun auditInstalled(c: Context): List<Item> = runCatching {
        val installed = c.packageManager.getInstalledPackages(0).map { it.packageName }
        auditHints(installed).map { Item(it, "审计", "包名疑似广告/推广（审计发现）") }
    }.getOrDefault(emptyList())

        /** 永不停用：系统、设置、输入源、遥控、安装器等 */
    private val PROTECTED_PREFIX = listOf("android", "com.android.", "com.google.android.gms", "com.google.android.tv.remote",
        "com.mediatek.", "mitv.service", "com.mitv.tvplayer", "com.mitv.livetv", "com.xiaomi.mitv.settings")
    /** 原厂桌面被写死在系统里的品牌：只设默认桌面，不停用原厂桌面（创维/酷开停用会卡开机 [D]；海信、当贝未经核实，保守处理） */
    private val KEEP_HOME_PREFIX = listOf("com.tianci.", "com.coocaa.", "com.skyworth.", "com.hisense.", "com.jamdeo.", "com.dangbei.", "com.android.tv.settings", "com.android.settings")

    /** 任何“设置”类包都不停用（保留电视原来的设置） */
    fun isProtected(p: String) = p.contains("setting", ignoreCase = true) || PROTECTED_PREFIX.any { p == it || (it.endsWith(".") && p.startsWith(it)) } || p == "com.cn.tvlauncher"
    fun mayDisableHome(p: String) = !isProtected(p) && KEEP_HOME_PREFIX.none { p.startsWith(it) }

    /** 原厂桌面（HOME 组件，不含本应用、系统兜底 FallbackHome） */
    fun stockHomes(c: Context): List<String> =
        c.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DISABLED_COMPONENTS)
            .map { it.activityInfo.packageName + "/" + it.activityInfo.name }
            .filter { !it.startsWith("com.cn.tvlauncher/") && !it.contains("FallbackHome") }

    // ---------------- 变更日志 ----------------
    data class Log(val disabled: MutableList<String> = ArrayList(), var oldDream: String? = null, var oldHome: String? = null, var granted: MutableList<String> = ArrayList()) {
        fun json() = JSONObject().put("d", JSONArray(disabled)).put("dream", oldDream ?: "").put("home", oldHome ?: "").put("g", JSONArray(granted)).toString()
        val empty get() = disabled.isEmpty() && oldDream == null && oldHome == null && granted.isEmpty()
        companion object {
            fun parse(s: String?): Log = runCatching {
                val o = JSONObject(s!!)
                fun arr(k: String) = o.optJSONArray(k)?.let { a -> MutableList(a.length()) { a.getString(it) } } ?: ArrayList()
                Log(arr("d"), o.optString("dream").ifEmpty { null }, o.optString("home").ifEmpty { null }, arr("g"))
            }.getOrDefault(Log())
        }
    }
    fun log(): Log = Log.parse(Prefs.str("debloatLog", "{}"))
    private fun save(l: Log) = Prefs.put("debloatLog", l.json())
    val active get() = !log().empty

    fun connect(c: Context, onWait: () -> Unit): Adb = Adb.connect(c, 60_000) { onWait() }

    /** 执行去广告，返回中文报告。saver=是否同时设为屏保 */
    fun apply(c: Context, saver: Boolean, onWait: () -> Unit): String {
        val out = StringBuilder()
        connect(c, onWait).use { adb ->
            val l = log()
            // 恢复日志：卸载重装后从 settings global 找回
            if (l.empty) adb.shell("settings get global $BACKUP_KEY").trim().takeIf { it.startsWith("{") }?.let { Log.parse(it) }?.let { old ->
                l.disabled.addAll(old.disabled); l.oldDream = old.oldDream; l.oldHome = old.oldHome; l.granted.addAll(old.granted) }
            val homes = stockHomes(c)
            if (l.oldHome == null) l.oldHome = homes.firstOrNull()
            val r = adb.shell("cmd package set-home-activity $HOME")
            out.append(if (r.contains("Success")) "✓ 已设为默认桌面\n" else "· 系统不支持 set-home-activity（${r.trim().take(40)}），将依赖停用原厂桌面\n")
            for (h in homes) {
                val p = h.substringBefore("/")
                if (p in l.disabled) continue
                if (!mayDisableHome(p)) { out.append("· 保留原厂桌面 $p（该品牌停用有风险）\n"); continue }
                if (adb.shell("pm disable-user --user 0 $p").contains("disabled")) { l.disabled += p; out.append("✓ 停用原厂桌面 $p\n") }
                save(l)
            }
            val targets = ADS + auditInstalled(c)
            for (it in targets) {
                if (it.pkg in l.disabled || isProtected(it.pkg) || !Apps.isInstalled(c, it.pkg)) continue
                if (adb.shell("pm disable-user --user 0 ${it.pkg}").contains("disabled")) { l.disabled += it.pkg; out.append("✓ 停用 ${it.brand} ${it.what}（${it.pkg}）\n") }
                save(l)
            }
            if (saver) {
                val cur = adb.shell("settings get secure screensaver_components").trim()
                if (cur != DREAM) {
                    if (l.oldDream == null) l.oldDream = cur
                    adb.shell("settings put secure screensaver_components $DREAM"); out.append("✓ 屏保已设为本桌面屏保\n")
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= 26 && !l.granted.contains("android.permission.READ_TV_LISTINGS")) {
                if (!adb.shell("pm grant com.cn.tvlauncher android.permission.READ_TV_LISTINGS").contains("Exception")) l.granted += "android.permission.READ_TV_LISTINGS"
            }
            save(l)
            adb.shell("settings put global $BACKUP_KEY '${l.json()}'")
        }
        return out.toString().ifEmpty { "没有需要处理的项目" }
    }

    /** 一键恢复原厂桌面：启用全部停用项、还原屏保、默认桌面交回原厂 */
    fun restore(c: Context, onWait: () -> Unit): String {
        val out = StringBuilder()
        connect(c, onWait).use { adb ->
            var l = log()
            if (l.empty) adb.shell("settings get global $BACKUP_KEY").trim().takeIf { it.startsWith("{") }?.let { l = Log.parse(it) }
            val failed = ArrayList<String>()
            for (p in l.disabled.toList()) {
                val r = adb.shell("pm enable $p")
                if (r.contains("enabled")) out.append("✓ 已启用 $p\n") else failed += p
            }
            l.oldDream?.let { d ->
                if (d.isBlank() || d == "null") adb.shell("settings delete secure screensaver_components") else adb.shell("settings put secure screensaver_components '$d'")
                out.append("✓ 已还原屏保\n")
            }
            for (g in l.granted) adb.shell("pm revoke com.cn.tvlauncher $g")
            val home = l.oldHome ?: stockHomes(c).firstOrNull()
            if (home != null) {
                val r = adb.shell("cmd package set-home-activity '$home'")
                out.append(if (r.contains("Success")) "✓ 默认桌面已交回 $home\n" else "· 请按主页键后在弹窗中选择原厂桌面\n")
            }
            val left = Log(failed.toMutableList())
            save(left)
            if (left.empty) adb.shell("settings delete global $BACKUP_KEY") else adb.shell("settings put global $BACKUP_KEY '${left.json()}'")
            if (failed.isNotEmpty()) out.append("✗ 未能启用：${failed.joinToString()}（可用电脑 adb shell pm enable 包名）\n")
        }
        return out.toString().ifEmpty { "没有需要还原的变更" }
    }
}
