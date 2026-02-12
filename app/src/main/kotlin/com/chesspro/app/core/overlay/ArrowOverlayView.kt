package com.chesspro.app.core.overlay

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 透明箭头覆盖层
 * 全屏透明View，用于在其他APP棋盘上画箭头显示最佳走法
 */
class ArrowOverlayView(context: Context) : View(context) {

    // 箭头起点终点（屏幕像素坐标）
    private var arrowFrom: PointF? = null
    private var arrowTo: PointF? = null

    // 箭头样式 - Pro象棋风格绿色
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 38, 198, 176) // 青绿色
        style = Paint.Style.STROKE
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val arrowFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 38, 198, 176)
        style = Paint.Style.FILL
    }

    // 起点/终点圆圈 - 绿色高亮
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 38, 198, 176)
        style = Paint.Style.FILL
    }

    private val circleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 38, 198, 176)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // 提示文字
    private var hintText: String? = null
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 36f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    private var circleRadius = 30f

    fun setArrow(from: PointF?, to: PointF?, radius: Float = 30f) {
        arrowFrom = from
        arrowTo = to
        circleRadius = radius
        invalidate()
    }

    fun setHint(text: String?) {
        hintText = text
        invalidate()
    }

    fun clear() {
        arrowFrom = null
        arrowTo = null
        hintText = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val from = arrowFrom
        val to = arrowTo

        if (from != null && to != null) {
            // 画起点圆圈（金色高亮）
            canvas.drawCircle(from.x, from.y, circleRadius, circlePaint)
            canvas.drawCircle(from.x, from.y, circleRadius, circleBorderPaint)

            // 画终点圆圈
            canvas.drawCircle(to.x, to.y, circleRadius, circlePaint)
            canvas.drawCircle(to.x, to.y, circleRadius, circleBorderPaint)

            // 画箭头线
            val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())

            // 箭头线从起点圆圈边缘到终点圆圈边缘
            val startX = from.x + circleRadius * cos(angle).toFloat()
            val startY = from.y + circleRadius * sin(angle).toFloat()
            val endX = to.x - circleRadius * cos(angle).toFloat()
            val endY = to.y - circleRadius * sin(angle).toFloat()

            canvas.drawLine(startX, startY, endX, endY, arrowPaint)

            // 画箭头头部
            val arrowHeadSize = 24f
            val arrowAngle = Math.toRadians(30.0)

            val path = Path()
            path.moveTo(endX, endY)
            path.lineTo(
                endX - arrowHeadSize * cos(angle - arrowAngle).toFloat(),
                endY - arrowHeadSize * sin(angle - arrowAngle).toFloat()
            )
            path.lineTo(
                endX - arrowHeadSize * cos(angle + arrowAngle).toFloat(),
                endY - arrowHeadSize * sin(angle + arrowAngle).toFloat()
            )
            path.close()
            canvas.drawPath(path, arrowFillPaint)
        }

        // 画提示文字
        hintText?.let { text ->
            val x = width / 2f
            val y = 80f
            // 文字背景
            val textBounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            val bgPaint = Paint().apply {
                color = Color.argb(180, 0, 0, 0)
                style = Paint.Style.FILL
            }
            val bgRect = RectF(
                x - textBounds.width() / 2f - 16f,
                y - textBounds.height() - 8f,
                x + textBounds.width() / 2f + 16f,
                y + 12f
            )
            canvas.drawRoundRect(bgRect, 12f, 12f, bgPaint)
            canvas.drawText(text, x, y, textPaint)
        }
    }
}
