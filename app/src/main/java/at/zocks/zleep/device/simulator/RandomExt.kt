package at.zocks.zleep.device.simulator

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Normalverteilte Zufallszahl (Box-Muller). */
internal fun Random.nextGaussian(mean: Double = 0.0, sd: Double = 1.0): Double {
    val u1 = nextDouble().coerceAtLeast(1e-12)
    val u2 = nextDouble()
    return mean + sd * sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
}

internal fun Random.chance(probability: Double): Boolean = nextDouble() < probability
