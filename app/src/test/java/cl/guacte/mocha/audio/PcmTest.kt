package cl.guacte.mocha.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmTest {

    @Test
    fun reversed_invierteElOrden() {
        assertArrayEquals(shortArrayOf(3, 2, 1), reversed(shortArrayOf(1, 2, 3)))
    }

    @Test
    fun reversed_vacioDevuelveVacio() {
        assertEquals(0, reversed(ShortArray(0)).size)
    }

    @Test
    fun reversed_noModificaLaEntrada() {
        val input = shortArrayOf(1, 2, 3)
        reversed(input)
        assertArrayEquals(shortArrayOf(1, 2, 3), input)
    }

    @Test
    fun reversed_dosVecesVuelveAlOriginal() {
        val input = ShortArray(1000) { (it * 37 - 500).toShort() }
        assertArrayEquals(input, reversed(reversed(input)))
    }

    @Test
    fun isPlayable_nuloOCortoNoSirve() {
        assertFalse(isPlayable(null))
        assertFalse(isPlayable(ShortArray(0)))
        assertFalse(isPlayable(ShortArray(100)))
        assertFalse(isPlayable(ShortArray(MIN_SAMPLES - 1)))
    }

    @Test
    fun isPlayable_desdeElMinimoSirve() {
        assertEquals(6615, MIN_SAMPLES)
        assertTrue(isPlayable(ShortArray(MIN_SAMPLES)))
    }

    @Test
    fun maxSamples_esDiezSegundos() {
        assertEquals(441000, MAX_SAMPLES)
    }

    @Test
    fun trimmedSilence_quitaSilencioDeLasPuntasDejandoMargen() {
        val voz = ShortArray(10_000) { 5000 }
        val input = ShortArray(20_000) + voz + ShortArray(20_000)
        val out = trimmedSilence(input)
        assertEquals(10_000 + 2 * TRIM_PAD, out.size)
        assertEquals(0.toShort(), out.first())
        assertEquals(5000.toShort(), out[TRIM_PAD])
    }

    @Test
    fun trimmedSilence_soloSilencioQuedaVacio() {
        assertEquals(0, trimmedSilence(ShortArray(20_000) { 200 }).size)
    }

    @Test
    fun trimmedSilence_vozPegadaAlBordeNoSeSaleDelArreglo() {
        val input = ShortArray(5000) { 5000 }
        assertArrayEquals(input, trimmedSilence(input))
    }

    @Test
    fun trimmedSilence_umbralRelativo_vozBajaNoSePierde() {
        val vozBaja = ShortArray(10_000) { 600 }
        val out = trimmedSilence(ShortArray(20_000) { 30 } + vozBaja + ShortArray(20_000) { 30 })
        assertEquals(10_000 + 2 * TRIM_PAD, out.size)
    }

    @Test
    fun withoutTapNoise_quitaElToqueDelFinalYElInicio() {
        val voz = ShortArray(20_000) { 5000 }
        val toque = ShortArray(TAP_TAIL) { if (it < 500) 20_000 else 0 }
        val raw = ShortArray(TAP_HEAD) { 20_000 } + ShortArray(10_000) + voz + ShortArray(SAMPLE_RATE) + toque
        val out = cleaned(withoutTapNoise(raw))
        // El golpe del dedo al parar no sobrevive: lo que queda es solo la voz con su margen
        assertEquals(20_000 + 2 * TRIM_PAD, out.size)
    }

    @Test
    fun withoutTapNoise_grabacionCortaQuedaVacia() {
        assertEquals(0, withoutTapNoise(ShortArray(TAP_HEAD + TAP_TAIL - 1)).size)
    }

    @Test
    fun withoutTapNoise_alTopeNoSeCortaLaCola() {
        val raw = ShortArray(MAX_SAMPLES) { 1 }
        assertEquals(MAX_SAMPLES - TAP_HEAD, withoutTapNoise(raw).size)
    }

    @Test
    fun normalized_subeElPicoAlObjetivo() {
        val out = normalized(shortArrayOf(0, 4000, -8000))
        assertEquals(TARGET_PEAK.toShort(), (-out[2]).toShort())
        assertEquals((TARGET_PEAK / 2).toShort(), out[1])
    }

    @Test
    fun normalized_ganancia_tieneTope() {
        val out = normalized(shortArrayOf(100, -100))
        assertEquals((100 * MAX_GAIN).toInt().toShort(), out[0])
    }

    @Test
    fun normalized_silencioYVacioNoCambian() {
        assertArrayEquals(ShortArray(3), normalized(ShortArray(3)))
        assertEquals(0, normalized(ShortArray(0)).size)
    }

    @Test
    fun normalized_nuncaBajaElVolumen() {
        val fuerte = shortArrayOf(32000, -32000)
        assertArrayEquals(fuerte, normalized(fuerte))
    }
}
