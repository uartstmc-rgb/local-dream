package io.github.xororz.localdream.ui.screens

import kotlin.math.roundToInt

/** One finished generation, reduced to what the time estimate needs. */
data class TimeSample(
    val steps: Int,
    val width: Int,
    val height: Int,
    val cfg: Float,
    val seconds: Double,
)

data class TimeEstimate(
    val seconds: Double,
    /** True when no past run used a comparable CFG, so the number is rougher. */
    val roughCfg: Boolean,
)

private val MIN_SEC_REGEX = Regex("""^(\d+)m(\d+)s$""")
private val SEC_REGEX = Regex("""^(\d+(?:\.\d+)?)s$""")
private val MS_REGEX = Regex("""^(\d+)ms$""")

/** Parses the history time strings ("123ms", "45.3s", "4m43s") into seconds. */
fun parseGenerationSeconds(text: String?): Double? {
    val t = text?.trim() ?: return null
    MIN_SEC_REGEX.find(t)?.let {
        return it.groupValues[1].toDouble() * 60 + it.groupValues[2].toDouble()
    }
    SEC_REGEX.find(t)?.let { return it.groupValues[1].toDouble() }
    MS_REGEX.find(t)?.let { return it.groupValues[1].toDouble() / 1000.0 }
    return null
}

/**
 * Estimates one image's time from past runs of the same model and runtime.
 * Cost is assumed proportional to steps x pixels; the median seconds-per-unit
 * of recent runs is used so one slow outlier (e.g. a cold start) does not
 * skew the result. Runs with a similar CFG class (<= 1 or > 1) are preferred.
 */
fun estimateGenerationTime(
    samples: List<TimeSample>,
    steps: Int,
    width: Int,
    height: Int,
    cfg: Float,
): TimeEstimate? {
    if (steps <= 0 || width <= 0 || height <= 0) return null
    val sameCfgClass = samples.filter { (it.cfg <= 1f) == (cfg <= 1f) }
    val pool = if (sameCfgClass.isNotEmpty()) sameCfgClass else samples
    val rates = pool.mapNotNull { s ->
        val units = s.steps.toDouble() * s.width * s.height
        if (units <= 0.0 || s.seconds <= 0.0) null else s.seconds / units
    }.sorted()
    if (rates.isEmpty()) return null
    val median = if (rates.size % 2 == 1) {
        rates[rates.size / 2]
    } else {
        (rates[rates.size / 2 - 1] + rates[rates.size / 2]) / 2.0
    }
    return TimeEstimate(
        seconds = median * steps * width * height,
        roughCfg = sameCfgClass.isEmpty(),
    )
}

fun formatEstimateSeconds(seconds: Double): String {
    val total = seconds.roundToInt().coerceAtLeast(1)
    return if (total < 60) "${total}s" else "${total / 60}m ${total % 60}s"
}
