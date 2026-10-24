package cl.guacte.mocha.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import kotlin.math.max
import kotlin.math.min

/**
 * Graba PCM crudo del micrófono en un hilo propio. Corta solo al llegar a [MAX_SAMPLES].
 * `onDone` llega en el hilo principal con lo grabado, salvo que se llame a [cancel].
 */
class Recorder {
    private val main = Handler(Looper.getMainLooper())
    private var thread: Thread? = null

    @Volatile private var running = false
    @Volatile private var discard = false

    val isRecording: Boolean get() = running

    /** Devuelve false si el micrófono no se pudo abrir (ocupado, sin permiso, etc.). */
    @SuppressLint("MissingPermission") // la Activity pide el permiso antes de llamar
    fun start(onDone: (ShortArray) -> Unit): Boolean {
        cancel()
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false
        val bufferBytes = max(minBuffer, SAMPLE_RATE / 5 * 2)
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferBytes
            )
        } catch (e: Exception) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        try {
            record.startRecording()
        } catch (e: Exception) {
            record.release()
            return false
        }

        running = true
        discard = false
        thread = Thread({
            val out = ShortArray(MAX_SAMPLES)
            val chunk = ShortArray(minBuffer / 2)
            var count = 0
            try {
                while (running && count < MAX_SAMPLES) {
                    val read = record.read(chunk, 0, min(chunk.size, MAX_SAMPLES - count))
                    if (read < 0) break
                    System.arraycopy(chunk, 0, out, count, read)
                    count += read
                }
            } finally {
                try { record.stop() } catch (_: Exception) { }
                record.release()
                running = false
            }
            if (!discard) {
                val result = out.copyOf(count)
                main.post { onDone(result) }
            }
        }, "mocha-recorder").also { it.start() }
        return true
    }

    /** Termina y entrega lo grabado. */
    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    /** Termina y descarta lo grabado. */
    fun cancel() {
        discard = true
        stop()
    }
}
