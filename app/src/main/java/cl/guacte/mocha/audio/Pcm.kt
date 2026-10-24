package cl.guacte.mocha.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Audio PCM 16-bit mono. Todo lo de este archivo es puro: se prueba sin Android.

const val SAMPLE_RATE = 44100

/** Tope de grabación: 10 s. */
const val MAX_SAMPLES = SAMPLE_RATE * 10

/** Menos de 0,15 s de voz no alcanza a ser una palabra. */
const val MIN_SAMPLES = 6615

/**
 * Piso del umbral de silencio. El umbral real es relativo al pico (un décimo),
 * para que una imitación dicha bajito no se descarte como silencio.
 */
const val TRIM_THRESHOLD_FLOOR = 300

/** Se descartan los primeros 50 ms: ahí cae la vibración del celular al empezar a grabar. */
const val TAP_HEAD = SAMPLE_RATE / 20

/** Se descartan los últimos 150 ms: ahí queda grabado el golpe del dedo al tocar para parar. */
const val TAP_TAIL = SAMPLE_RATE * 3 / 20

/** Margen que se deja antes y después de la voz (50 ms) para no cortar consonantes. */
const val TRIM_PAD = SAMPLE_RATE / 20

/** Pico al que se lleva la voz al normalizar (~88 % del máximo). */
const val TARGET_PEAK = 29000

/** Ganancia máxima, para no inflar el ruido de fondo de una grabación muy baja. */
const val MAX_GAIN = 8f

fun reversed(samples: ShortArray): ShortArray =
    ShortArray(samples.size) { samples[samples.size - 1 - it] }

fun isPlayable(samples: ShortArray?): Boolean =
    samples != null && samples.size >= MIN_SAMPLES

/** Quita el ruido de la vibración inicial y del toque para parar. Si se llegó al tope, no hubo toque. */
fun withoutTapNoise(raw: ShortArray): ShortArray {
    val tail = if (raw.size >= MAX_SAMPLES) 0 else TAP_TAIL
    if (raw.size < TAP_HEAD + tail) return ShortArray(0)
    return raw.copyOfRange(TAP_HEAD, raw.size - tail)
}

fun trimmedSilence(samples: ShortArray): ShortArray {
    val peak = samples.maxOfOrNull { abs(it.toInt()) } ?: return ShortArray(0)
    val threshold = max(TRIM_THRESHOLD_FLOOR, peak / 10)
    val first = samples.indexOfFirst { abs(it.toInt()) > threshold }
    if (first < 0) return ShortArray(0)
    val last = samples.indexOfLast { abs(it.toInt()) > threshold }
    val start = max(0, first - TRIM_PAD)
    val end = min(samples.size, last + 1 + TRIM_PAD)
    return samples.copyOfRange(start, end)
}

fun normalized(samples: ShortArray): ShortArray {
    val peak = samples.maxOfOrNull { abs(it.toInt()) } ?: return ShortArray(0)
    if (peak == 0) return samples.copyOf()
    val gain = min(TARGET_PEAK.toFloat() / peak, MAX_GAIN)
    if (gain <= 1f) return samples.copyOf()
    return ShortArray(samples.size) {
        (samples[it] * gain).roundToInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()
    }
}

/** Lo que se guarda de cada grabación: sin silencios en las puntas y a buen volumen. */
fun cleaned(raw: ShortArray): ShortArray = normalized(trimmedSilence(raw))
