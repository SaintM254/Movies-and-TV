package com.vidsource.app.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

/**
 * Virtual pointer so web pages can be navigated with a D-pad remote.
 * Arrow keys move the cursor, OK clicks the element under it.
 * The pointer is drawn on top of the WebView and injects touch events.
 */
class DpadPointerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = 0xFFFFFFFF.toInt()
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCCFFFFFF.toInt()
    }

    private var webView: WebView? = null
    private var cursorX = 0f
    private var cursorY = 0f
    private var stepPx = 48

    /** Whether the pointer currently captures D-pad input. */
    var pointerActive = true
        set(value) {
            field = value
            visibility = if (value) VISIBLE else GONE
            if (value) center()
        }

    fun attach(webView: WebView, stepPx: Int) {
        this.webView = webView
        this.stepPx = stepPx
    }

    fun center() {
        post {
            cursorX = width / 2f
            cursorY = height / 2f
            invalidate()
        }
    }

    /** True when the cursor sits at the top edge (used to escape to the chrome). */
    fun isAtTop(): Boolean = cursorY <= stepPx / 2f

    /** Returns true when the key was consumed by the pointer. */
    fun handleKey(keyCode: Int): Boolean {
        if (!pointerActive) return false
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> move(-stepPx, 0)
            KeyEvent.KEYCODE_DPAD_RIGHT -> move(stepPx, 0)
            KeyEvent.KEYCODE_DPAD_UP -> move(0, -stepPx)
            KeyEvent.KEYCODE_DPAD_DOWN -> move(0, stepPx)
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> click()
            else -> return false
        }
        return true
    }

    private fun move(dx: Int, dy: Int) {
        cursorX = (cursorX + dx).coerceIn(0f, width.toFloat())
        cursorY = (cursorY + dy).coerceIn(0f, height.toFloat())
        invalidate()
        val now = System.currentTimeMillis()
        dispatchTouch(MotionEvent.ACTION_MOVE, now)
    }

    private fun click() {
        val now = System.currentTimeMillis()
        dispatchTouch(MotionEvent.ACTION_DOWN, now)
        dispatchTouch(MotionEvent.ACTION_UP, now + 80)
    }

    @SuppressLint("Recycle")
    private fun dispatchTouch(action: Int, time: Long) {
        val target = webView ?: return
        val ev = MotionEvent.obtain(time, time, action, cursorX, cursorY, 0)
        target.dispatchTouchEvent(ev)
        ev.recycle()
    }

    override fun onDraw(canvas: Canvas) {
        if (!pointerActive) return
        canvas.drawCircle(cursorX, cursorY, 34f, ringPaint)
        canvas.drawCircle(cursorX, cursorY, 11f, dotPaint)
    }
}
