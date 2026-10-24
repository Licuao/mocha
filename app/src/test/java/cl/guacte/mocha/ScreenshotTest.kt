package cl.guacte.mocha

import android.view.View
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.Mocha")

    private fun screen(): View = paparazzi.inflate<View>(R.layout.activity_main).also {
        it.findViewById<TextView>(R.id.scoreOneValue).text = "3"
        it.findViewById<TextView>(R.id.scoreTwoValue).text = "12"
        it.findViewById<View>(R.id.cardPlayMimic).alpha = 0.4f
    }

    @Test fun pixel5() = paparazzi.snapshot(screen())

    @Test fun nexus5_fuenteGrande() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.NEXUS_5.copy(fontScale = 1.3f))
        paparazzi.snapshot(screen())
    }
}
