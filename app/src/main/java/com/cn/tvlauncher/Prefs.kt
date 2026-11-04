package com.cn.tvlauncher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf

data class City(val name: String, val lat: Double, val lon: Double)

/** 设置项（SharedPreferences），ver 自增用于触发界面刷新 */
object Prefs {
    lateinit var sp: SharedPreferences
    val ver = mutableIntStateOf(0)
    fun init(c: Context) { if (!::sp.isInitialized) sp = c.applicationContext.getSharedPreferences("cfg", 0) }
    fun str(k: String, d: String = "") = sp.getString(k, d) ?: d
    fun put(k: String, v: String) { sp.edit().putString(k, v).apply(); ver.intValue++ }

    val CITIES = listOf(
        City("重庆", 29.563, 106.551), City("北京", 39.904, 116.407), City("上海", 31.230, 121.474), City("广州", 23.129, 113.264),
        City("深圳", 22.543, 114.058), City("成都", 30.573, 104.066), City("杭州", 30.274, 120.155), City("武汉", 30.593, 114.305),
        City("西安", 34.341, 108.940), City("南京", 32.060, 118.797), City("天津", 39.343, 117.362), City("长沙", 28.228, 112.939),
        City("郑州", 34.747, 113.625), City("沈阳", 41.806, 123.432), City("哈尔滨", 45.803, 126.535), City("昆明", 25.038, 102.718),
        City("贵阳", 26.647, 106.630), City("南宁", 22.817, 108.366), City("福州", 26.074, 119.296), City("厦门", 24.480, 118.089),
        City("济南", 36.651, 117.120), City("青岛", 36.067, 120.383), City("合肥", 31.821, 117.227), City("南昌", 28.683, 115.858),
        City("石家庄", 38.042, 114.515), City("太原", 37.871, 112.549), City("兰州", 36.061, 103.834), City("乌鲁木齐", 43.826, 87.617),
        City("拉萨", 29.652, 91.172), City("海口", 20.044, 110.199), City("呼和浩特", 40.842, 111.749), City("长春", 43.817, 125.324))
    val city: City get() = CITIES.firstOrNull { it.name == str("city", "重庆") } ?: CITIES[0]

    /** auto | light | dark */
    var theme: String get() = str("theme", "auto"); set(v) = put("theme", v)
    /** auto(夜间继续观看) | recs | continue | clock */
    var top: String get() = str("top", "auto"); set(v) = put("top", v)
    var shelf: List<String> get() = str("shelf").split(",").filter { it.isNotBlank() }; set(v) = put("shelf", v.joinToString(","))
    var hiddenPlatforms: Set<String> get() = str("hidePlat").split(",").filter { it.isNotBlank() }.toSet(); set(v) = put("hidePlat", v.joinToString(","))
    var recKinds: Set<String> get() = str("recKinds", "movie,tv,show").split(",").filter { it.isNotBlank() }.toSet(); set(v) = put("recKinds", v.joinToString(","))
    /** 壁纸：posters | dyn_* | st_* */
    var wall: String get() = str("wall", "posters"); set(v) = put("wall", v)
    const val DEFAULT_TITLE = "还原电视最初的样子"
    var title: String get() = str("title", DEFAULT_TITLE); set(v) = put("title", v)
    var saverOk: Boolean get() = str("saverOk", "1") == "1"; set(v) = put("saverOk", if (v) "1" else "0")
}
