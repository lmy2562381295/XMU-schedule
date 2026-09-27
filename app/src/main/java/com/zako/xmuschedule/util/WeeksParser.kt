package com.zako.xmuschedule.util

/**
 * 周次文本解析："1-16周" / "1-16周(单)" / "3-8周(双)" / "1,3,5-7周" → 周次集合。
 * 解析结果为空时返回空集合（调用方自行兜底为全学期并标记可疑）。
 */
object WeeksParser {

    fun parse(text: String?, defaultRange: IntRange = 1..20): Set<Int> {
        if (text.isNullOrBlank()) return defaultRange.toSet()
        val trimmed = text.trim()

        // 位图格式（厦大新版金智）："111111111111111100" 每位代表该周是否有课
        if (trimmed.length in 10..40 && trimmed.all { it == '0' || it == '1' }) {
            val weeks = sortedSetOf<Int>()
            trimmed.forEachIndexed { index, c ->
                if (c == '1') weeks.add(index + 1)
            }
            if (weeks.isNotEmpty()) return weeks
        }

        var s = trimmed.replace("（", "(").replace("）", ")").replace("周", "").trim()

        var parity: Int? = null // 1=单周 0=双周
        val paren = Regex("\\(([^)]*)\\)").find(s)
        if (paren != null) {
            val inner = paren.groupValues[1]
            parity = when {
                inner.contains("单") || inner.contains("奇") -> 1
                inner.contains("双") || inner.contains("偶") -> 0
                else -> null
            }
            s = s.replace(paren.value, "")
        }

        val result = sortedSetOf<Int>()
        for (token in s.split(',', '，', '、', ';', '；', ' ')) {
            val t = token.trim()
            if (t.isEmpty()) continue
            val nums = Regex("\\d+").findAll(t).map { it.value.toInt() }.toList()
            when {
                nums.size >= 2 && t.contains('-') || nums.size >= 2 && t.contains('~') -> {
                    val lo = nums[0]; val hi = nums[1]
                    if (lo in 1..30 && hi in lo..30) (lo..hi).forEach { result.add(it) }
                }
                nums.size == 1 && nums[0] in 1..30 -> result.add(nums[0])
                nums.size >= 2 -> { // "1.3.5" 这类被拆开的
                    nums.filter { it in 1..30 }.forEach { result.add(it) }
                }
            }
        }

        return when (parity) {
            1 -> result.filter { it % 2 == 1 }.toSet()
            0 -> result.filter { it % 2 == 0 }.toSet()
            else -> result.toSet()
        }
    }

    /** 序列化为逗号分隔存储格式 */
    fun serialize(weeks: Set<Int>): String = weeks.toIntArray().sorted().joinToString(",")
}
