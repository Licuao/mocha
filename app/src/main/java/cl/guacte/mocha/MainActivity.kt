package cl.guacte.mocha

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import cl.guacte.mocha.audio.Player
import cl.guacte.mocha.audio.Recorder
import cl.guacte.mocha.audio.cleaned
import cl.guacte.mocha.audio.isPlayable
import cl.guacte.mocha.audio.reversed
import cl.guacte.mocha.audio.withoutTapNoise

class MainActivity : Activity() {

    /** Qué está ocupando el micrófono o el parlante en este momento. */
    private enum class Busy { NONE, REC_ORIGINAL, PLAY_ORIGINAL, REC_MIMIC, PLAY_MIMIC, REVEAL }

    private val recorder = Recorder()
    private val player = Player()
    private val main = Handler(Looper.getMainLooper())

    private var original: ShortArray? = null
    private var mimic: ShortArray? = null
    private var busy = Busy.NONE
    private var pendingRecord = Busy.NONE
    private var recordStartedAt = 0L
    private var pulse: ObjectAnimator? = null
    private var scores = intArrayOf(0, 0)

    private lateinit var cardRecordOriginal: View
    private lateinit var cardPlayOriginal: View
    private lateinit var cardRecordMimic: View
    private lateinit var cardPlayMimic: View
    private lateinit var btnReveal: View
    private lateinit var iconRecordOriginal: ImageView
    private lateinit var iconPlayOriginal: ImageView
    private lateinit var iconRecordMimic: ImageView
    private lateinit var iconPlayMimic: ImageView
    private lateinit var iconReveal: ImageView
    private lateinit var hintRecordOriginal: TextView
    private lateinit var hintPlayOriginal: TextView
    private lateinit var hintRecordMimic: TextView
    private lateinit var hintPlayMimic: TextView
    private lateinit var scoreViews: List<View>
    private lateinit var scoreValues: List<TextView>

    private val tick = object : Runnable {
        override fun run() {
            if (busy == Busy.REC_ORIGINAL || busy == Busy.REC_MIMIC) {
                render()
                main.postDelayed(this, 250)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cardRecordOriginal = findViewById(R.id.cardRecordOriginal)
        cardPlayOriginal = findViewById(R.id.cardPlayOriginal)
        cardRecordMimic = findViewById(R.id.cardRecordMimic)
        cardPlayMimic = findViewById(R.id.cardPlayMimic)
        btnReveal = findViewById(R.id.btnReveal)
        iconRecordOriginal = findViewById(R.id.iconRecordOriginal)
        iconPlayOriginal = findViewById(R.id.iconPlayOriginal)
        iconRecordMimic = findViewById(R.id.iconRecordMimic)
        iconPlayMimic = findViewById(R.id.iconPlayMimic)
        iconReveal = findViewById(R.id.iconReveal)
        hintRecordOriginal = findViewById(R.id.hintRecordOriginal)
        hintPlayOriginal = findViewById(R.id.hintPlayOriginal)
        hintRecordMimic = findViewById(R.id.hintRecordMimic)
        hintPlayMimic = findViewById(R.id.hintPlayMimic)
        scoreViews = listOf(findViewById(R.id.scoreOne), findViewById(R.id.scoreTwo))
        scoreValues = listOf(findViewById(R.id.scoreOneValue), findViewById(R.id.scoreTwoValue))

        cardRecordOriginal.setOnClickListener { onRecordTapped(Busy.REC_ORIGINAL) }
        cardRecordMimic.setOnClickListener { onRecordTapped(Busy.REC_MIMIC) }
        cardPlayOriginal.setOnClickListener { onPlayTapped(Busy.PLAY_ORIGINAL) }
        cardPlayMimic.setOnClickListener { onPlayTapped(Busy.PLAY_MIMIC) }
        btnReveal.setOnClickListener { onPlayTapped(Busy.REVEAL) }
        findViewById<View>(R.id.btnNewRound).setOnClickListener { newRound() }

        val prefs = getPreferences(MODE_PRIVATE)
        scores = intArrayOf(prefs.getInt(KEY_SCORE_ONE, 0), prefs.getInt(KEY_SCORE_TWO, 0))
        scoreViews.forEachIndexed { i, view ->
            view.setOnClickListener { addPoint(i) }
            view.setOnLongClickListener { resetScores(); true }
        }

        render()
    }

    override fun onStop() {
        super.onStop()
        stopAll()
        render()
    }

    // --- Acciones ---

    private fun onRecordTapped(slot: Busy) {
        if (busy == slot) {
            recorder.stop() // onRecorded llega por el callback
            return
        }
        stopAll()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingRecord = slot
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        startRecording(slot)
    }

    private fun startRecording(slot: Busy) {
        val started = recorder.start { raw -> onRecorded(slot, raw) }
        if (!started) {
            toast(R.string.mic_error)
            return
        }
        busy = slot
        recordStartedAt = SystemClock.elapsedRealtime()
        window.decorView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        main.post(tick)
        render()
    }

    private fun onRecorded(slot: Busy, raw: ShortArray) {
        if (busy == slot) busy = Busy.NONE
        val clip = cleaned(withoutTapNoise(raw))
        if (!isPlayable(clip)) {
            toast(R.string.too_short)
        } else if (slot == Busy.REC_ORIGINAL) {
            original = clip
            mimic = null // palabra nueva: la imitación anterior ya no corresponde
        } else {
            mimic = clip
        }
        render()
    }

    private fun onPlayTapped(slot: Busy) {
        if (busy == slot) {
            player.stop()
            busy = Busy.NONE
            render()
            return
        }
        val clip = when (slot) {
            Busy.PLAY_ORIGINAL -> original?.let(::reversed)
            Busy.PLAY_MIMIC -> mimic?.let(::reversed)
            Busy.REVEAL -> original
            else -> null
        } ?: return
        stopAll()
        val started = player.play(clip) {
            if (busy == slot) busy = Busy.NONE
            render()
        }
        if (!started) {
            toast(R.string.play_error)
            return
        }
        busy = slot
        render()
    }

    private fun stopAll() {
        recorder.cancel()
        player.stop()
        busy = Busy.NONE
    }

    private fun newRound() {
        stopAll()
        original = null
        mimic = null
        render()
        toast(R.string.round_cleared)
    }

    private fun addPoint(who: Int) {
        scores[who]++
        saveScores()
        scoreViews[who].performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        scoreValues[who].apply {
            scaleX = 1.35f
            scaleY = 1.35f
            animate().scaleX(1f).scaleY(1f).setDuration(220).start()
        }
        render()
    }

    private fun resetScores() {
        scores = intArrayOf(0, 0)
        saveScores()
        render()
        toast(R.string.score_reset)
    }

    private fun saveScores() {
        getPreferences(MODE_PRIVATE).edit()
            .putInt(KEY_SCORE_ONE, scores[0])
            .putInt(KEY_SCORE_TWO, scores[1])
            .apply()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        val slot = pendingRecord
        pendingRecord = Busy.NONE
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && slot != Busy.NONE) {
            startRecording(slot)
        } else {
            toast(R.string.mic_denied, long = true)
        }
    }

    // --- Pantalla ---

    private fun render() {
        val seconds = ((SystemClock.elapsedRealtime() - recordStartedAt) / 1000).toInt()
        val recordingText = getString(R.string.recording, seconds)
        val playingText = getString(R.string.playing)

        renderCard(
            cardRecordOriginal, iconRecordOriginal, hintRecordOriginal,
            enabled = true, active = busy == Busy.REC_ORIGINAL, idleIcon = R.drawable.ic_mic,
            text = if (busy == Busy.REC_ORIGINAL) recordingText else getString(R.string.hint_record_original)
        )
        renderCard(
            cardPlayOriginal, iconPlayOriginal, hintPlayOriginal,
            enabled = isPlayable(original), active = busy == Busy.PLAY_ORIGINAL, idleIcon = R.drawable.ic_play_back,
            text = when {
                busy == Busy.PLAY_ORIGINAL -> playingText
                isPlayable(original) -> getString(R.string.hint_play_original_rev_ready)
                else -> getString(R.string.hint_play_original_rev)
            }
        )
        renderCard(
            cardRecordMimic, iconRecordMimic, hintRecordMimic,
            enabled = true, active = busy == Busy.REC_MIMIC, idleIcon = R.drawable.ic_mic,
            text = if (busy == Busy.REC_MIMIC) recordingText else getString(R.string.hint_record_mimic)
        )
        renderCard(
            cardPlayMimic, iconPlayMimic, hintPlayMimic,
            enabled = isPlayable(mimic), active = busy == Busy.PLAY_MIMIC, idleIcon = R.drawable.ic_play_back,
            text = when {
                busy == Busy.PLAY_MIMIC -> playingText
                isPlayable(mimic) -> getString(R.string.hint_play_mimic_rev_ready)
                else -> getString(R.string.hint_play_mimic_rev)
            }
        )

        btnReveal.isEnabled = isPlayable(original)
        btnReveal.alpha = if (btnReveal.isEnabled) 1f else DISABLED_ALPHA
        iconReveal.setImageResource(if (busy == Busy.REVEAL) R.drawable.ic_stop else R.drawable.ic_eye)

        val names = listOf(getString(R.string.player_one), getString(R.string.player_two))
        scoreValues.forEachIndexed { i, value -> value.text = scores[i].toString() }
        scoreViews.forEachIndexed { i, view ->
            view.contentDescription = getString(R.string.score_desc, names[i], scores[i])
        }

        updatePulse()
    }

    private fun renderCard(
        card: View, icon: ImageView, hint: TextView,
        enabled: Boolean, active: Boolean, idleIcon: Int, text: String
    ) {
        card.isEnabled = enabled
        card.alpha = if (enabled) 1f else DISABLED_ALPHA
        icon.setImageResource(if (active) R.drawable.ic_stop else idleIcon)
        hint.text = text
    }

    /** El ícono del que esté grabando o sonando late suave; los demás quedan quietos. */
    private fun updatePulse() {
        val target = when (busy) {
            Busy.REC_ORIGINAL -> iconRecordOriginal
            Busy.REC_MIMIC -> iconRecordMimic
            Busy.PLAY_ORIGINAL -> iconPlayOriginal
            Busy.PLAY_MIMIC -> iconPlayMimic
            Busy.REVEAL -> iconReveal
            Busy.NONE -> null
        }
        if (pulse?.target === target) return
        pulse?.cancel()
        (pulse?.target as? View)?.alpha = 1f
        pulse = target?.let {
            ObjectAnimator.ofFloat(it, View.ALPHA, 1f, 0.35f).apply {
                duration = 520
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }

    private fun toast(text: Int, long: Boolean = false) {
        Toast.makeText(this, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val REQ_MIC = 7
        const val DISABLED_ALPHA = 0.4f
        const val KEY_SCORE_ONE = "score_one"
        const val KEY_SCORE_TWO = "score_two"
    }
}
