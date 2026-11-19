package com.example.myslider

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

class Slider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_VALUE = 0f
        private const val MAX_VALUE = 100f
        private const val DEFAULT_VALUE = 0f
        private const val DEFAULT_RATIO = 3f
        private const val MIN_BAR_LENGTH_DP = 300f
        private const val MIN_CURSOR_DIAMETER_DP = 56f
    }

    var minValue: Float = MIN_VALUE
    var maxValue: Float = MAX_VALUE
    var defaultValue: Float = DEFAULT_VALUE
        set(value) {
            field = value.coerceIn(minValue, maxValue)
            invalidate()
        }
    var value: Float = defaultValue
        set(value) {
            field = value.coerceIn(minValue, maxValue)
            invalidate()
        }
    var cursorDiameterOverBarWidthRatio: Float = DEFAULT_RATIO.toFloat()
        set(value) {
            field = value.coerceAtLeast(1f)
            invalidate()
        }

    private data class DrawDims(
        val barLength: Float,
        val barWidth: Float,
        val cursorDiameter: Float,
        val paddingTop: Float,
        val paddingLeft: Float,
        val paddingRight: Float,
        val paddingBottom: Float
    )

    var cursorColor: Int = 0
    var barColor: Int = 0
    var valueBarColor: Int = 0
    var disableColor: Int = 0

    lateinit var barPaint: Paint
    lateinit var valueBarPaint: Paint
    lateinit var cursorPaint: Paint

    private var minBarLengthPx: Float = 0f
    private var minCursorDiameterPx: Float = 0f

    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

    private fun adaptDims(): DrawDims {
        val cursorDiameter = MIN_CURSOR_DIAMETER_DP.dpToPx()
        val availableHeight = height - paddingTop - paddingBottom - cursorDiameter
        val barLength = availableHeight.coerceAtLeast(MIN_BAR_LENGTH_DP.dpToPx())
        val barWidth = cursorDiameter / cursorDiameterOverBarWidthRatio
        return DrawDims(
            barLength = barLength,
            barWidth = barWidth,
            cursorDiameter = cursorDiameter,
            paddingTop = paddingTop.toFloat(),
            paddingLeft = paddingLeft.toFloat(),
            paddingRight = paddingRight.toFloat(),
            paddingBottom = paddingBottom.toFloat()
        )
    }

    private fun toPos(value: Float, d: DrawDims): PointF {
        val ratio = (minValue..maxValue).toRatio(value)
        val x = d.paddingLeft + (width - d.paddingLeft - d.paddingRight) / 2f
        val y = d.paddingTop + d.cursorDiameter / 2f + d.barLength * (1f - ratio)
        return PointF(x, y)
    }

    private fun toValue(pos: PointF, d: DrawDims): Float {
        val h= pos.y-d.paddingTop-d.cursorDiameter/2f
        val ratio = 1-h/d.barLength
        return (minValue..maxValue).fromRatio(ratio)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN ||
            event.action == MotionEvent.ACTION_MOVE ||
            event.action == MotionEvent.ACTION_UP
        ) {
            val d = adaptDims()
            value = toValue(PointF(event.x, event.y), d)
            parent.requestDisallowInterceptTouchEvent(true)
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (paddingLeft + paddingRight + MIN_CURSOR_DIAMETER_DP.dpToPx()).toInt()
        val desiredHeight = (paddingTop + paddingBottom + MIN_BAR_LENGTH_DP.dpToPx() +
            MIN_CURSOR_DIAMETER_DP.dpToPx()).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val d = adaptDims()
        val pMax = toPos(maxValue, d)
        val pMin = toPos(minValue, d)
        val pValue = toPos(value, d)

        barPaint.strokeWidth = d.barWidth
        valueBarPaint.strokeWidth = d.barWidth

        canvas.drawLine(
            pMin.x, pMin.y,
            pMax.x, pMax.y,
            barPaint
        )
        canvas.drawLine(
            pMin.x, pMin.y,
            pValue.x, pValue.y,
            valueBarPaint
        )
        canvas.drawCircle(pValue.x, pValue.y, d.cursorDiameter / 2f, cursorPaint)
    }

    init {
        cursorColor = ContextCompat.getColor(context, R.color.turquoise)
        barColor = ContextCompat.getColor(context, R.color.navy_blue)
        valueBarColor = ContextCompat.getColor(context, R.color.orange)
        disableColor = ContextCompat.getColor(context, R.color.gray)

        barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = barColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        valueBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = valueBarColor
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cursorColor
            style = Paint.Style.FILL
        }

        minBarLengthPx = MIN_BAR_LENGTH_DP.dpToPx()
        minCursorDiameterPx = MIN_CURSOR_DIAMETER_DP.dpToPx()
    }

}