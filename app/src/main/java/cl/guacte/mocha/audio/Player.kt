package cl.guacte.mocha.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper

/**
 * Reproduce un ShortArray completo de una vez. El fin se avisa por tiempo (duración + margen),
 * que es más confiable entre fabricantes que el marcador de posición de AudioTrack.
 */
class Player {
    private val main = Handler(Looper.getMainLooper())
    private var track: AudioTrack? = null
    private var finish: Runnable? = null

    /** Devuelve false si no se pudo reproducir. `onEnd` no se llama si se usa [stop]. */
    fun play(samples: ShortArray, onEnd: () -> Unit): Boolean {
        stop()
        if (samples.isEmpty()) return false
        val t = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(samples.size * 2)
                .build()
        } catch (e: Exception) {
            return false
        }
        if (t.write(samples, 0, samples.size) < 0 || t.state != AudioTrack.STATE_INITIALIZED) {
            t.release()
            return false
        }
        try {
            t.play()
        } catch (e: Exception) {
            t.release()
            return false
        }
        track = t
        val durationMs = samples.size * 1000L / SAMPLE_RATE
        finish = Runnable {
            release()
            onEnd()
        }.also { main.postDelayed(it, durationMs + 200) }
        return true
    }

    fun stop() {
        finish?.let { main.removeCallbacks(it) }
        release()
    }

    private fun release() {
        finish = null
        track?.let {
            try { it.stop() } catch (_: Exception) { }
            it.release()
        }
        track = null
    }
}
