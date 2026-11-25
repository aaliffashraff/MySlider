package com.example.myslider

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat

class Slider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    class SavedState : View.BaseSavedState {
        val savedValue: Float

        constructor(superState: Parcelable?, savedValue: Float) : super(superState) {
            this.savedValue = savedValue
        }

        private constructor(source: Parcel) : super(source) {
            savedValue = source.readFloat()
        }

        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeFloat(savedValue)
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(source: Parcel) = SavedState(source)

                override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
            }
        }
    }

    companion object {
        private const val MIN_VALUE = 0f
        private const val MAX_VALUE = 100f
        private const val DEFAULT_VALUE = 50f
        private const val DEFAULT_RATIO = 3f
        private const val MIN_BAR_LENGTH_DP = 300f
        private const val MIN_CURSOR_DIAMETER_DP = 48f
    }

    var minValue: Float = MIN_VALUE
    var maxValue: Float = MAX_VALUE
    var defaultValue: Float = DEFAULT_VALUE
        set(value) {
            field = value.coerceIn(minValue, maxValue)
            invalidate()
        }

    var onValueChange: ((Float) -> Unit)? = null

    var value: Float = defaultValue
        set(value) {
            val newValue = value.coerceIn(minValue, maxValue)
            if (field != newValue) {
                field = newValue
                invalidate()
                onValueChange?.invoke(field)
            }
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
    private var lastCursorTapTime: Long? = null
    private var doubleTapInProgress = false
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var dragging = false

    private fun Float.dpToPx(): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

    private fun adaptDims(): DrawDims {
        var lp =paddingLeft.toFloat()
        var tp =paddingTop.toFloat()
        var rp = paddingRight.toFloat()
        var bp = paddingBottom.toFloat()

        if (minCursorDiameterPx>width){
            lp=0f;rp=0f;
        }
        else if(minCursorDiameterPx+lp+rp>width)
        {
            val ratio: Float = (width-minCursorDiameterPx)/(lp+rp)
            lp*=ratio;rp*=ratio;
        }
        if (minBarLengthPx+minCursorDiameterPx>height){
            tp=0f;bp=0f;
        }
        else if (minBarLengthPx+minCursorDiameterPx+tp+bp>height)
        {
            val ratio: Float = (height-minBarLengthPx-minCursorDiameterPx)/(tp+bp)
            tp*=ratio;bp*=ratio;
        }

        val availMain=height-tp-bp;
        val availCross=width-rp-lp;

        val cursorDiameter = minOf(availCross, availMain / 4f, MIN_CURSOR_DIAMETER_DP.dpToPx())
        val barLength = availMain - cursorDiameter
        val barWidth = cursorDiameter / cursorDiameterOverBarWidthRatio
        return DrawDims(
            barLength = barLength,
            barWidth = barWidth,
            cursorDiameter = cursorDiameter,
            paddingTop = tp,
            paddingLeft = lp,
            paddingRight = rp,
            paddingBottom = bp
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
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val dims = adaptDims()
                val cursorPosition = toPos(value, dims)
                val dx = event.x - cursorPosition.x
                val dy = event.y - cursorPosition.y
                val radius = dims.cursorDiameter / 2f
                val isOnCursor = dx * dx + dy * dy <= radius * radius
                val previousTapTime = lastCursorTapTime
                val isDoubleTap = isOnCursor &&
                    previousTapTime != null &&
                    event.eventTime >= previousTapTime &&
                    event.eventTime - previousTapTime < ViewConfiguration.getDoubleTapTimeout()

                touchDownX = event.x
                touchDownY = event.y
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)

                if (isDoubleTap) {
                    lastCursorTapTime = null
                    doubleTapInProgress = true
                    if (value == defaultValue) {
                        onValueChange?.invoke(value)
                    } else {
                        value = defaultValue
                    }
                } else {
                    doubleTapInProgress = false
                    lastCursorTapTime = if (isOnCursor) event.eventTime else null
                    if (!isOnCursor) {
                        value = toValue(PointF(event.x, event.y), dims)
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!doubleTapInProgress) {
                    val dx = event.x - touchDownX
                    val dy = event.y - touchDownY
                    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                    if (dragging || dx * dx + dy * dy > touchSlop * touchSlop) {
                        dragging = true
                        lastCursorTapTime = null
                        value = toValue(PointF(event.x, event.y), adaptDims())
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging && !doubleTapInProgress) {
                    value = toValue(PointF(event.x, event.y), adaptDims())
                }
                doubleTapInProgress = false
                dragging = false
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                lastCursorTapTime = null
                doubleTapInProgress = false
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        applyEnabledState()
    }

    override fun onSaveInstanceState(): Parcelable? =
        SavedState(super.onSaveInstanceState(), value)

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            value = state.savedValue
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    private fun applyEnabledState() {
        lastCursorTapTime = null
        doubleTapInProgress = false
        dragging = false

        if (::barPaint.isInitialized) {
            barPaint.color = if (isEnabled) barColor else disableColor
            valueBarPaint.color = if (isEnabled) valueBarColor else disableColor
            cursorPaint.color = if (isEnabled) cursorColor else disableColor
            invalidate()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
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


        applyEnabledState()
    }

}