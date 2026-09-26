package com.zako.xmuschedule.util

/**
 * 节次字段解析，兼容多种写法：
 *  "1,2" / "1-2" / "1~2" / "第1,2节" / "0102"（金智系统常见的两位补零写法）/ "10-11"
 */
object SectionsParser {

    fun parse(text: String?): Pair<Int, Int>? {
        if (text.isNullOrBlank()) return null
        val raw = text.trim()
        val hasSeparator = raw.any { it == '-' || it == '~' || it == '—' || it == '，' || it == ',' }
        val digits = raw.filter { it.isDigit() }

        // 四位纯数字且无分隔符：按"起止各两位"理解，如 0102 → 1..2，1011 → 10..11
        if (digits.length == 4 && !hasSeparator) {
            val a = digits.substring(0, 2).toIntOrNull() ?: return null
            val b = digits.substring(2, 4).toIntOrNull() ?: return null
            if (a in 1..20 && b in a..20) return a to b
            return null
        }

        val nums = Regex("\\d+").findAll(raw).map { it.value.toInt() }.toList()
        return when {
            nums.isEmpty() -> null
            nums.size == 1 -> {
                val n = nums[0]
                if (n in 1..20) n to n else null
            }
            else -> {
                val lo = nums.min(); val hi = nums.max()
                if (lo in 1..20 && hi in lo..20) lo to hi else null
            }
        }
    }
}
