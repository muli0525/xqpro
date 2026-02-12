package com.chesspro.app.core.engine

import android.util.Log

/**
 * 纯Kotlin中国象棋引擎
 * Alpha-Beta搜索 + 基础评估函数
 * 不依赖任何外部二进制文件
 */
class XiangqiEngine {

    companion object {
        private const val TAG = "XiangqiEngine"
        const val ROWS = 10
        const val COLS = 9

        // 棋子编码
        const val EMPTY = '.'
        const val R_KING = 'K'; const val B_KING = 'k'
        const val R_ADVISOR = 'A'; const val B_ADVISOR = 'a'
        const val R_BISHOP = 'B'; const val B_BISHOP = 'b'
        const val R_KNIGHT = 'N'; const val B_KNIGHT = 'n'
        const val R_ROOK = 'R'; const val B_ROOK = 'r'
        const val R_CANNON = 'C'; const val B_CANNON = 'c'
        const val R_PAWN = 'P'; const val B_PAWN = 'p'

        // 棋子价值
        private val PIECE_VALUE = mapOf(
            R_KING to 10000, B_KING to 10000,
            R_ROOK to 900, B_ROOK to 900,
            R_CANNON to 450, B_CANNON to 450,
            R_KNIGHT to 400, B_KNIGHT to 400,
            R_BISHOP to 200, B_BISHOP to 200,
            R_ADVISOR to 200, B_ADVISOR to 200,
            R_PAWN to 100, B_PAWN to 100
        )

        // 兵/卒过河后加值
        private const val PAWN_CROSSED_BONUS = 80

        private const val INF = 999999
    }

    data class Move(val fr: Int, val fc: Int, val tr: Int, val tc: Int, val captured: Char = EMPTY)

    private val board = Array(ROWS) { CharArray(COLS) { EMPTY } }
    private var redToMove = true
    private var nodesSearched = 0L

    fun parseFEN(fen: String) {
        val parts = fen.trim().split(" ")
        val rows = parts[0].split("/")
        for (r in rows.indices.take(ROWS)) {
            var c = 0
            for (ch in rows[r]) {
                if (ch.isDigit()) {
                    repeat(ch - '0') { if (c < COLS) board[r][c++] = EMPTY }
                } else {
                    if (c < COLS) board[r][c++] = ch
                }
            }
            while (c < COLS) board[r][c++] = EMPTY
        }
        redToMove = if (parts.size > 1) parts[1] == "w" else true
    }

    private fun isRed(piece: Char) = piece.isUpperCase()
    private fun isBlack(piece: Char) = piece.isLowerCase() && piece != EMPTY
    private fun isAlly(piece: Char, red: Boolean) = if (red) isRed(piece) else isBlack(piece)
    private fun isEnemy(piece: Char, red: Boolean) = if (red) isBlack(piece) else isRed(piece)
    private fun inBoard(r: Int, c: Int) = r in 0 until ROWS && c in 0 until COLS

    private fun inPalace(r: Int, c: Int, red: Boolean): Boolean {
        return c in 3..5 && if (red) r in 7..9 else r in 0..2
    }

    private fun inOwnHalf(r: Int, red: Boolean): Boolean {
        return if (red) r >= 5 else r <= 4
    }

    /**
     * 生成所有合法走法
     */
    fun generateMoves(red: Boolean): List<Move> {
        val moves = mutableListOf<Move>()
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val p = board[r][c]
                if (!isAlly(p, red)) continue
                when (p.uppercaseChar()) {
                    'K' -> genKingMoves(r, c, red, moves)
                    'A' -> genAdvisorMoves(r, c, red, moves)
                    'B' -> genBishopMoves(r, c, red, moves)
                    'N' -> genKnightMoves(r, c, red, moves)
                    'R' -> genRookMoves(r, c, red, moves)
                    'C' -> genCannonMoves(r, c, red, moves)
                    'P' -> genPawnMoves(r, c, red, moves)
                }
            }
        }
        return moves
    }

    private fun addMove(r: Int, c: Int, tr: Int, tc: Int, red: Boolean, moves: MutableList<Move>) {
        if (!inBoard(tr, tc)) return
        val target = board[tr][tc]
        if (isAlly(target, red)) return
        moves.add(Move(r, c, tr, tc, target))
    }

    private fun genKingMoves(r: Int, c: Int, red: Boolean, moves: MutableList<Move>) {
        val dirs = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dr, dc) in dirs) {
            val nr = r + dr; val nc = c + dc
            if (inBoard(nr, nc) && inPalace(nr, nc, red)) addMove(r, c, nr, nc, red, moves)
        }
        // 飞将（对脸）
        val enemyKing = if (red) B_KING else R_KING
        val dir = if (red) -1 else 1
        var cr = r + dir
        while (inBoard(cr, c)) {
            if (board[cr][c] == enemyKing) { moves.add(Move(r, c, cr, c, enemyKing)); break }
            if (board[cr][c] != EMPTY) break
            cr += dir
        }
    }

    private fun genAdvisorMoves(r: Int, c: Int, red: Boolean, moves: MutableList<Move>) {
        val dirs = arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for ((dr, dc) in dirs) {
            val nr = r + dr; val nc = c + dc
            if (inBoard(nr, nc) && inPalace(nr, nc, red)) addMove(r, c, nr, nc, red, moves)
        }
    }

    private fun genBishopMoves(r: Int, c: Int, red: Boolean, moves: MutableList<Move>) {
        val dirs = arrayOf(-2 to -2, -2 to 2, 2 to -2, 2 to 2)
        val blocks = arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for (i in dirs.indices) {
            val nr = r + dirs[i].first; val nc = c + dirs[i].second
            val br = r + blocks[i].first; val bc = c + blocks[i].second
            if (!inBoard(nr, nc) || !inOwnHalf(nr, red)) continue
            if (board[br][bc] != EMPTY) continue // 塞象眼
            addMove(r, c, nr, nc, red, moves)
        }
    }

    private fun genKnightMoves(r: Int, c: Int, red: Boolean, moves: MutableList<Move>) {
        val jumps = arrayOf(
            -2 to -1, -2 to 1, 2 to -1, 2 to 1,
            -1 to -2, -1 to 2, 1 to -2, 1 to 2
        )
        val legs = arrayOf(
            -1 to 0, -1 to 0, 1 to 0, 1 to 0,
            0 to -1, 0 to 1, 0 to -1, 0 to 1
        )
        for (i in jumps.indices) {
            val nr = r + jumps[i].first; val nc = c + jumps[i].second
            val lr = r + legs[i].first; val lc = c + legs[i].second
            if (!inBoard(nr, nc)) continue
            if (board[lr][lc] != EMPTY) continue // 蹩马腿
            addMove(r, c, nr, nc, red, moves)
        }
    }

    private fun genRookMoves(r: Int, c: Int, red: Boolean, moves: MutableList<Move>) {
        val dirs = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dr, dc) in dirs) {
            var nr = r + dr; var nc = c + dc
            while (inBoard(nr, nc)) {
                if (board[nr][nc] == EMPTY) {
                    moves.add(Move(r, c, nr, nc))
                } else {
                    if (isEnemy(board[nr][nc], red)) moves.add(Move(r, c, nr, nc, board[nr][nc]))
                    break
                }
                nr += dr; nc += dc
            }
        }
    }

    private fun genCannonMoves(r: Int, c: Int, red: Boolean, moves: MutableList<Move>) {
        val dirs = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dr, dc) in dirs) {
            var nr = r + dr; var nc = c + dc
            var jumped = false
            while (inBoard(nr, nc)) {
                if (!jumped) {
                    if (board[nr][nc] == EMPTY) {
                        moves.add(Move(r, c, nr, nc))
                    } else {
                        jumped = true
                    }
                } else {
                    if (board[nr][nc] != EMPTY) {
                        if (isEnemy(board[nr][nc], red)) moves.add(Move(r, c, nr, nc, board[nr][nc]))
                        break
                    }
                }
                nr += dr; nc += dc
            }
        }
    }

    private fun genPawnMoves(r: Int, c: Int, red: Boolean, moves: MutableList<Move>) {
        // 前进方向
        val forward = if (red) -1 else 1
        addMove(r, c, r + forward, c, red, moves)
        // 过河后可以横走
        val crossed = if (red) r <= 4 else r >= 5
        if (crossed) {
            addMove(r, c, r, c - 1, red, moves)
            addMove(r, c, r, c + 1, red, moves)
        }
    }

    /**
     * 评估函数
     */
    private fun evaluate(): Int {
        var score = 0
        for (r in 0 until ROWS) {
            for (c in 0 until COLS) {
                val p = board[r][c]
                if (p == EMPTY) continue
                val base = PIECE_VALUE[p] ?: 0
                var value = base

                // 兵/卒过河加分
                if (p == R_PAWN && r <= 4) value += PAWN_CROSSED_BONUS
                if (p == B_PAWN && r >= 5) value += PAWN_CROSSED_BONUS

                // 车的活动性加分
                if (p.uppercaseChar() == 'R') {
                    var mobility = 0
                    for ((dr, dc) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                        var nr = r + dr; var nc = c + dc
                        while (inBoard(nr, nc) && board[nr][nc] == EMPTY) { mobility++; nr += dr; nc += dc }
                    }
                    value += mobility * 5
                }

                // 位置加分 - 占据中路
                val centerBonus = (4 - kotlin.math.abs(c - 4)) * 3
                value += centerBonus

                score += if (isRed(p)) value else -value
            }
        }
        return if (redToMove) score else -score
    }

    private fun makeMove(m: Move) {
        board[m.tr][m.tc] = board[m.fr][m.fc]
        board[m.fr][m.fc] = EMPTY
    }

    private fun undoMove(m: Move) {
        board[m.fr][m.fc] = board[m.tr][m.tc]
        board[m.tr][m.tc] = m.captured
    }

    /**
     * Alpha-Beta搜索
     */
    private fun alphaBeta(depth: Int, alpha: Int, beta: Int, isMax: Boolean): Int {
        nodesSearched++
        if (depth == 0) return evaluate()

        val red = if (isMax) redToMove else !redToMove
        val moves = generateMoves(red)

        if (moves.isEmpty()) return if (isMax) -INF + 1 else INF - 1

        var a = alpha; var b = beta

        if (isMax) {
            var best = -INF
            for (m in moves) {
                makeMove(m)
                val v = alphaBeta(depth - 1, a, b, false)
                undoMove(m)
                if (v > best) best = v
                if (best > a) a = best
                if (a >= b) break
            }
            return best
        } else {
            var best = INF
            for (m in moves) {
                makeMove(m)
                val v = alphaBeta(depth - 1, a, b, true)
                undoMove(m)
                if (v < best) best = v
                if (best < b) b = best
                if (a >= b) break
            }
            return best
        }
    }

    /**
     * 查找最佳走法 — 迭代加深
     */
    fun findBestMove(maxDepth: Int = 4, timeLimitMs: Long = 3000): SearchResult {
        nodesSearched = 0
        val startTime = System.currentTimeMillis()

        var bestMove: Move? = null
        var bestScore = 0
        var reachedDepth = 0

        for (d in 1..maxDepth) {
            val elapsed = System.currentTimeMillis() - startTime
            if (d > 2 && elapsed > timeLimitMs / 2) break

            val moves = generateMoves(redToMove)
            if (moves.isEmpty()) break

            var currentBest: Move? = null
            var currentScore = -INF

            for (m in moves) {
                makeMove(m)
                val v = -alphaBeta(d - 1, -INF, -currentScore, false)
                undoMove(m)

                if (v > currentScore) {
                    currentScore = v
                    currentBest = m
                }

                if (System.currentTimeMillis() - startTime > timeLimitMs) break
            }

            if (currentBest != null) {
                bestMove = currentBest
                bestScore = currentScore
                reachedDepth = d
            }

            if (System.currentTimeMillis() - startTime > timeLimitMs) break
        }

        val totalTime = System.currentTimeMillis() - startTime

        Log.d(TAG, "Search: depth=$reachedDepth nodes=$nodesSearched time=${totalTime}ms " +
                "best=${bestMove?.toUCI()} score=$bestScore")

        return SearchResult(
            bestMove = bestMove?.toUCI(),
            score = bestScore,
            depth = reachedDepth,
            nodes = nodesSearched,
            timeMs = totalTime
        )
    }

    private fun Move.toUCI(): String {
        val fc = ('a' + this.fc)
        val fr = this.fr
        val tc = ('a' + this.tc)
        val tr = this.tr
        return "$fc$fr$tc$tr"
    }

    data class SearchResult(
        val bestMove: String?,
        val score: Int,
        val depth: Int,
        val nodes: Long,
        val timeMs: Long
    )
}
