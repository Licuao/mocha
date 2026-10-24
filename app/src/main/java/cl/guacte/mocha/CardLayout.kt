package cl.guacte.mocha

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * Tarjeta vertical cuyo primer hijo es el ícono. En pantallas chicas o con letra grande,
 * si el texto no cabe completo se esconde el ícono antes de cortar la pista.
 */
class CardLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val icon = getChildAt(0)
        if (icon != null && MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            val available = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
            val show = contentHeight(widthMeasureSpec) <= available
            icon.visibility = if (show) VISIBLE else GONE
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /** Alto que necesitan todos los hijos sin recortes, ícono incluido. */
    private fun contentHeight(widthMeasureSpec: Int): Int {
        val unbounded = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var total = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            measureChildWithMargins(child, widthMeasureSpec, 0, unbounded, 0)
            val lp = child.layoutParams as MarginLayoutParams
            total += child.measuredHeight + lp.topMargin + lp.bottomMargin
        }
        return total
    }
}
