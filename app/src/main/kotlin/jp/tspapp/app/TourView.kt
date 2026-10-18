/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.tspapp.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import jp.tspapp.core.Point

/** 都市の点の半径 (dp). */
private const val POINT_RADIUS_DP = 6

/** 出発点の半径 (dp). */
private const val START_RADIUS_DP = 9

/** 道の線の太さ (dp). */
private const val LINE_WIDTH_DP = 3

/** 盤の角の丸み (dp). */
private const val CORNER_DP = 16

/** ログのタグ. */
private const val TAG = "Board"

/**
 * 都市を置いて, 巡回路を描く盤.
 *
 * タップした位置に, 都市を置く ([onTap]). 都市の位置は, dp で持つ (画面の密度によらず, 同じ距離になる).
 * 巡回路 ([order]) があれば, 都市を順に結んで, 最初の都市に戻る線を描く. 出発点 (0 番) は, 大きく, 別の色で描く.
 *
 * @param context コンテキスト
 */
class TourView(
    context: Context,
) : View(context) {
    private val density = resources.displayMetrics.density
    private val boardPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                    0,
                )
        }
    private val pointPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0)
        }
    private val startPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color =
                MaterialColors.getColor(
                    context,
                    com.google.android.material.R.attr.colorTertiary,
                    0,
                )
        }
    private val linePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0)
            style = Paint.Style.STROKE
            strokeWidth = LINE_WIDTH_DP * density
            strokeJoin = Paint.Join.ROUND
            alpha = LINE_ALPHA
        }
    private val path = Path()

    /** 都市 (位置は dp). */
    var points: List<Point> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    /** 巡回路 (訪れる順の, 都市の番号. なければ, 空). */
    var order: List<Int> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    /** 盤がタップされたとき (位置は dp). */
    var onTap: (Point) -> Unit = {}

    /** 盤の広さ (dp. でたらめな都市を置く範囲). */
    val sizeInDp: Pair<Double, Double>
        get() = (width / density).toDouble() to (height / density).toDouble()

    /**
     * 盤, 巡回路, 都市を描く.
     *
     * @param canvas キャンバス
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val corner = CORNER_DP * density
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), corner, corner, boardPaint)
        if (order.size >= 2) {
            path.reset()
            order.forEachIndexed { index, number ->
                val x = points[number].x.toFloat() * density
                val y = points[number].y.toFloat() * density
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, linePaint)
        }
        points.forEachIndexed { index, point ->
            val start = index == 0
            canvas.drawCircle(
                point.x.toFloat() * density,
                point.y.toFloat() * density,
                (if (start) START_RADIUS_DP else POINT_RADIUS_DP) * density,
                if (start) startPaint else pointPaint,
            )
        }
    }

    /**
     * タップした位置を, [onTap] に渡す.
     *
     * @param event タッチの操作
     * @return 処理したら, true
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val point = Point((event.x / density).toDouble(), (event.y / density).toDouble())
            AppLog.d(TAG) { "Tapped at $point" }
            onTap(point)
        }
        return true
    }

    private companion object {
        /** 道の線の, 不透明度 (0 から 255. 都市の点が, 見えるように). */
        const val LINE_ALPHA = 200
    }
}
