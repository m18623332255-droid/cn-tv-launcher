package com.cn.tvlauncher

import java.time.LocalDate
import kotlin.math.*

/** NOAA 简化算法计算日出/日落（返回当天从 0 点起的分钟数，按 tzHours 时区）。极昼/极夜返回 null。 */
object Sun {
    fun times(date: LocalDate, lat: Double, lon: Double, tzHours: Double = 8.0): Pair<Int, Int>? {
        val n = date.dayOfYear
        val g = 2 * PI / 365.0 * (n - 1)
        val eqt = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) - 0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) + 0.000907 * sin(2 * g) -
            0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latR = Math.toRadians(lat)
        val c = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
        if (c < -1 || c > 1) return null
        val ha = Math.toDegrees(acos(c))
        val noon = 720 - 4 * lon - eqt + tzHours * 60
        return Pair((noon - 4 * ha).roundToInt(), (noon + 4 * ha).roundToInt())
    }
    fun isNight(date: LocalDate, minuteOfDay: Int, lat: Double, lon: Double): Boolean {
        val t = times(date, lat, lon) ?: return false
        return minuteOfDay < t.first || minuteOfDay >= t.second
    }
}
