/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.tspapp.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.Slider
import jp.tspapp.core.EXACT_MAX_POINTS
import jp.tspapp.core.Method
import jp.tspapp.core.Point
import jp.tspapp.core.randomPoints
import jp.tspapp.core.solve
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import kotlin.time.measureTimedValue

/** 要素と要素の間の余白 (dp). */
private const val GAP_DP = 12

/** でたらめな都市の数の, 下限. */
private const val RANDOM_MIN = 4

/** でたらめな都市の数の, 上限. */
private const val RANDOM_MAX = 60

/** でたらめな都市の数の, 最初の値 (厳密解で, 解ける数). */
private const val RANDOM_DEFAULT = EXACT_MAX_POINTS

/** でたらめな都市を, 盤の端から離す距離 (dp. 点が, 盤からはみ出さないように). */
private const val RANDOM_MARGIN_DP = 16.0

/** 保存する状態の, 都市の位置 (x, y の順の配列) の名前. */
private const val STATE_POINTS = "points"

/** ログのタグ. */
private const val TAG = "Solver"

/**
 * 巡回セールスマン問題の画面.
 *
 * 盤 ([TourView]) をタップして, 都市を置く. 「ランダム」で, でたらめな都市を置き直す. 解き方
 * (最近傍, 2-opt, 厳密解) を選んで「解く」を押すと, 裏のスレッドで解いて, 巡回路を描き, 長さと時間を表示する.
 */
class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val points = mutableListOf<Point>()
    private val methods = mutableMapOf<Int, Method>()
    private var job: Job? = null
    private lateinit var board: TourView
    private lateinit var count: android.widget.TextView
    private lateinit var result: android.widget.TextView
    private lateinit var busy: View
    private lateinit var group: MaterialButtonToggleGroup
    private lateinit var slider: Slider

    /**
     * 画面を作る (回転などで作り直したときは, 都市を戻す).
     *
     * @param savedInstanceState 保存した状態
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getDoubleArray(STATE_POINTS)?.let { saved ->
            saved.toList().chunked(2) { points += Point(it[0], it[1]) }
        }
        val root = content()
        root.applySystemBarInsets()
        setContentView(root)
        showPoints()
        AppLog.i(TAG, "Started with ${points.size} cities")
    }

    /**
     * 都市の位置を, 保存する (回転などで, 画面を作り直すとき).
     *
     * @param outState 保存する状態
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putDoubleArray(STATE_POINTS, points.flatMap { listOf(it.x, it.y) }.toDoubleArray())
    }

    /** 画面を閉じるときに, 解いている処理を止める. */
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /**
     * 画面の中身を作る.
     *
     * @return 画面の最も外側の View
     */
    private fun content(): LinearLayout {
        board =
            TourView(this).apply {
                onTap = {
                    // apply の中の points は, 盤の points なので, 画面の points を指す
                    this@MainActivity.points += it
                    showPoints()
                }
            }
        count =
            styledText(
                com.google.android.material.R.style.TextAppearance_Material3_LabelLarge,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
            )
        result =
            cardText().apply {
                // 行の数で, 盤の高さが変わらないように, 2 行に決める
                minLines = 2
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        busy = busyIndicator()
        group = methodGroup()
        val randomLabel =
            styledText(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        slider =
            Slider(this).apply {
                valueFrom = RANDOM_MIN.toFloat()
                valueTo = RANDOM_MAX.toFloat()
                stepSize = 1f
                value = RANDOM_DEFAULT.toFloat()
                addOnChangeListener { _, value, _ ->
                    randomLabel.text = getString(R.string.count, value.toInt())
                }
            }
        randomLabel.text = getString(R.string.count, RANDOM_DEFAULT)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(
                board,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                    topMargin = dp(GAP_DP)
                },
            )
            addSpaced(busy, GAP_DP)
            addSpaced(result, GAP_DP)
            addSpaced(group, GAP_DP)
            addSpaced(randomLabel, GAP_DP)
            addView(slider)
            addSpaced(buttons(), GAP_DP)
        }
    }

    /**
     * 見出し (題名と, 都市の数) を作る.
     *
     * @return 見出し
     */
    private fun header(): LinearLayout =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(
                styledText(
                    com.google.android.material.R.style.TextAppearance_Material3_TitleLarge,
                    androidx.appcompat.R.attr.colorPrimary,
                ).apply { setText(R.string.title) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(count)
        }

    /**
     * 解き方を選ぶボタンの組 (1 つだけ選ぶ. 最初は, 2-opt) を作る.
     *
     * @return ボタンの組
     */
    private fun methodGroup(): MaterialButtonToggleGroup =
        MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            listOf(
                R.string.method_nearest to Method.NEAREST_NEIGHBOR,
                R.string.method_two_opt to Method.TWO_OPT,
                R.string.method_exact to Method.EXACT,
            ).forEach { (text, method) ->
                val button =
                    MaterialButton(
                        context,
                        null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle,
                    ).apply {
                        id = View.generateViewId()
                        setText(text)
                    }
                methods[button.id] = method
                addView(
                    button,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                if (method == Method.TWO_OPT) {
                    check(button.id)
                }
            }
        }

    /**
     * 操作のボタン (ランダム, 消去, 解く) を, 横に並べる.
     *
     * @return ボタンの列
     */
    private fun buttons(): LinearLayout =
        LinearLayout(this).apply {
            val weight = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(
                materialButton(
                    R.string.random,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle,
                ) {
                    placeRandom()
                },
                LinearLayout.LayoutParams(weight),
            )
            addView(
                materialButton(R.string.clear, androidx.appcompat.R.attr.borderlessButtonStyle) {
                    points.clear()
                    showPoints()
                },
                LinearLayout.LayoutParams(weight),
            )
            addView(
                materialButton(R.string.solve) { solveTour() },
                LinearLayout.LayoutParams(weight),
            )
        }

    /** でたらめな都市を, 盤の中に置き直す (数は, スライダーの値). */
    private fun placeRandom() {
        val (width, height) = board.sizeInDp
        val margin = RANDOM_MARGIN_DP
        points.clear()
        randomPoints(slider.value.toInt(), width - 2 * margin, height - 2 * margin)
            .mapTo(points) { Point(it.x + margin, it.y + margin) }
        AppLog.d(TAG) { "Placed ${points.size} random cities in $width x $height dp" }
        showPoints()
    }

    /** 都市を描き直して, 巡回路と結果を消す (解いている処理も, 止める). */
    private fun showPoints() {
        job?.cancel()
        busy.isVisible = false
        board.points = points.toList()
        board.order = emptyList()
        count.text = resources.getQuantityString(R.plurals.points, points.size, points.size)
        // 結果の欄は, 隠さずに, 案内を出す (隠すと, 盤の高さが変わって, 置いた都市が, 盤からはみ出す)
        result.showOrHide(getString(R.string.hint))
    }

    /** 選んだ解き方で, 裏のスレッドで解いて, 巡回路と, 長さと, かかった時間を表示する. */
    private fun solveTour() {
        val method = methods.getValue(group.checkedButtonId)
        when {
            points.size < 2 -> {
                result.showOrHide(getString(R.string.too_few))
                return
            }
            method == Method.EXACT && points.size > EXACT_MAX_POINTS -> {
                result.showOrHide(
                    resources.getQuantityString(
                        R.plurals.too_many_exact,
                        EXACT_MAX_POINTS,
                        EXACT_MAX_POINTS,
                    ),
                )
                return
            }
        }
        job?.cancel()
        val snapshot = points.toList()
        busy.isVisible = true
        job =
            scope.launch {
                val (tour, time) =
                    withContext(Dispatchers.Default) {
                        measureTimedValue { solve(snapshot, method) }
                    }
                val millis = time.inWholeMilliseconds
                AppLog.i(
                    TAG,
                    "Solved ${snapshot.size} cities with $method: length ${tour.length}, $millis ms",
                )
                busy.isVisible = false
                board.order = tour.order
                val length = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
                result.showOrHide(
                    getString(
                        R.string.result,
                        length.format(tour.length),
                        resources.getQuantityString(R.plurals.points, snapshot.size, snapshot.size),
                        resources.getQuantityString(
                            R.plurals.milliseconds,
                            millis.toInt(),
                            millis.toInt(),
                        ),
                    ),
                )
            }
    }
}
