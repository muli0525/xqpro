package com.chesspro.app.core.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.chesspro.app.core.capture.RecognizedPiece
import com.chesspro.app.core.chess.PieceColor
import com.chesspro.app.core.chess.PieceType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pro象棋风格悬浮窗 - 迷你棋盘视图
 * 显示当前棋局 + 最佳走法箭头
 */
class MiniBoardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pieces: List<RecognizedPiece> = emptyList()
    private var bestMove: String? = null

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(210, 168, 80)
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(80, 50, 20)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val redTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(200, 0, 0)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val blackTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 30, 30)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val pieceBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 248, 220)
        style = Paint.Style.FILL
    }
    private val pieceBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 38, 198, 176)
        style = Paint.Style.FILL
    }
    private val arrowLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 38, 198, 176)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
    }
    private val riverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 80, 50, 20)
        textAlign = Paint.Align.CENTER
    }

    fun updateBoard(newPieces: List<RecognizedPiece>, move: String?) {
        pieces = newPieces
        bestMove = move
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val margin = w * 0.06f
        val boardW = w - margin * 2
        val boardH = h - margin * 2
        val cellW = boardW / 8f
        val cellH = boardH / 9f
        val pieceR = min(cellW, cellH) * 0.4f

        // 背景
        canvas.drawRect(0f, 0f, w, h, boardPaint)

        // 网格线
        linePaint.strokeWidth = 1f
        for (i in 0..9) {
            val y = margin + i * cellH
            canvas.drawLine(margin, y, margin + 8 * cellW, y, linePaint)
        }
        for (i in 0..8) {
            val x = margin + i * cellW
            if (i == 0 || i == 8) {
                canvas.drawLine(x, margin, x, margin + 9 * cellH, linePaint)
            } else {
                canvas.drawLine(x, margin, x, margin + 4 * cellH, linePaint)
                canvas.drawLine(x, margin + 5 * cellH, x, margin + 9 * cellH, linePaint)
            }
        }

        // 九宫斜线
        canvas.drawLine(margin + 3 * cellW, margin, margin + 5 * cellW, margin + 2 * cellH, linePaint)
        canvas.drawLine(margin + 5 * cellW, margin, margin + 3 * cellW, margin + 2 * cellH, linePaint)
        canvas.drawLine(margin + 3 * cellW, margin + 7 * cellH, margin + 5 * cellW, margin + 9 * cellH, linePaint)
        canvas.drawLine(margin + 5 * cellW, margin + 7 * cellH, margin + 3 * cellW, margin + 9 * cellH, linePaint)

        // 楚河汉界
        riverPaint.textSize = cellH * 0.4f
        val riverY = margin + 4.5f * cellH + riverPaint.textSize * 0.35f
        canvas.drawText("楚河", margin + 2 * cellW, riverY, riverPaint)
        canvas.drawText("漢界", margin + 6 * cellW, riverY, riverPaint)

        // 棋子
        redTextPaint.textSize = pieceR * 1.0f
        blackTextPaint.textSize = pieceR * 1.0f
        pieceBorderPaint.strokeWidth = pieceR * 0.08f

        pieces.forEach { piece ->
            val cx = margin + piece.position.x * cellW
            val cy = margin + piece.position.y * cellH
            val isRed = piece.color == PieceColor.RED

            canvas.drawCircle(cx + 1f, cy + 1f, pieceR, Paint().apply {
                color = Color.argb(60, 0, 0, 0)
            })
            canvas.drawCircle(cx, cy, pieceR, pieceBgPaint)
            pieceBorderPaint.color = if (isRed) Color.rgb(200, 0, 0) else Color.rgb(30, 30, 30)
            canvas.drawCircle(cx, cy, pieceR, pieceBorderPaint)
            canvas.drawCircle(cx, cy, pieceR * 0.82f, pieceBorderPaint.apply { strokeWidth = pieceR * 0.03f })

            val text = getPieceChar(piece.type, piece.color)
            val paint = if (isRed) redTextPaint else blackTextPaint
            canvas.drawText(text, cx, cy + paint.textSize * 0.35f, paint)
        }

        // 箭头
        bestMove?.let { move ->
            if (move.length >= 4) {
                val fc = move[0] - 'a'
                val fr = move[1] - '0'
                val tc = move[2] - 'a'
                val tr = move[3] - '0'
                if (fc in 0..8 && fr in 0..9 && tc in 0..8 && tr in 0..9) {
                    val fx = margin + fc * cellW
                    val fy = margin + fr * cellH
                    val tx = margin + tc * cellW
                    val ty = margin + tr * cellH

                    // 起点圆
                    canvas.drawCircle(fx, fy, pieceR * 0.6f, arrowPaint)

                    // 线
                    val angle = atan2((ty - fy).toDouble(), (tx - fx).toDouble())
                    val startX = fx + pieceR * 0.6f * cos(angle).toFloat()
                    val startY = fy + pieceR * 0.6f * sin(angle).toFloat()
                    val endX = tx - pieceR * 0.6f * cos(angle).toFloat()
                    val endY = ty - pieceR * 0.6f * sin(angle).toFloat()
                    canvas.drawLine(startX, startY, endX, endY, arrowLinePaint)

                    // 箭头头
                    val headSize = pieceR * 0.5f
                    val arrowAngle = Math.toRadians(25.0)
                    val path = Path().apply {
                        moveTo(tx, ty)
                        lineTo(
                            tx - headSize * cos(angle - arrowAngle).toFloat(),
                            ty - headSize * sin(angle - arrowAngle).toFloat()
                        )
                        lineTo(
                            tx - headSize * cos(angle + arrowAngle).toFloat(),
                            ty - headSize * sin(angle + arrowAngle).toFloat()
                        )
                        close()
                    }
                    canvas.drawPath(path, arrowPaint)
                }
            }
        }
    }

    private fun getPieceChar(type: PieceType, color: PieceColor): String {
        return if (color == PieceColor.RED) {
            when (type) {
                PieceType.JU -> "車"
                PieceType.MA -> "馬"
                PieceType.XIANG -> "相"
                PieceType.SHI -> "仕"
                PieceType.JIANG -> "帥"
                PieceType.PAO -> "炮"
                PieceType.BING -> "兵"
            }
        } else {
            when (type) {
                PieceType.JU -> "車"
                PieceType.MA -> "馬"
                PieceType.XIANG -> "象"
                PieceType.SHI -> "士"
                PieceType.JIANG -> "將"
                PieceType.PAO -> "砲"
                PieceType.BING -> "卒"
            }
        }
    }
}
