/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.tspapp.core

import kotlin.math.hypot
import kotlin.random.Random

/** 厳密解 ([Method.EXACT]) で解ける, 点の数の上限 (計算の量は, 点の数 n に対して, n² × 2ⁿ). */
const val EXACT_MAX_POINTS = 12

/** この数以下の点は, どの順でも, 巡回路の長さが同じ (厳密解で, そのまま返す). */
private const val TRIVIAL_POINTS = 3

/** 2-opt で, 短くなったとみなす, 差の下限 (浮動小数点の誤差で, 入れ替えを繰り返さないため). */
private const val EPSILON = 1e-9

/**
 * 平面の上の点.
 *
 * @property x 横の位置
 * @property y 縦の位置
 */
data class Point(
    val x: Double,
    val y: Double,
)

/**
 * 巡回路 (全ての点を 1 回ずつ訪れて, 最初の点に戻る道).
 *
 * @property order 訪れる順の, 点の番号 (最後の点から, 最初の点に戻る)
 * @property length 長さ (最初の点に戻る分を含む)
 */
data class Tour(
    val order: List<Int>,
    val length: Double,
)

/** 解き方. */
enum class Method {
    /** 最近傍法: 今の点から, 最も近い, まだ訪れていない点へ進む. 速いが, 最短とは限らない. */
    NEAREST_NEIGHBOR,

    /** 最近傍法の道を, 2-opt (交差する 2 本の辺を, つなぎ替える) で, 短くできなくなるまで改善する. */
    TWO_OPT,

    /** 動的計画法 (Held-Karp) で, 最短の巡回路を求める. [EXACT_MAX_POINTS] 点まで. */
    EXACT,
}

/**
 * 2 点の間の距離 (ユークリッド距離).
 *
 * @param a 点
 * @param b 点
 * @return 距離
 */
fun distance(
    a: Point,
    b: Point,
): Double = hypot(a.x - b.x, a.y - b.y)

/**
 * 巡回路の長さを返す (最後の点から, 最初の点に戻る分を含む).
 *
 * @param points 点
 * @param order 訪れる順の, 点の番号
 * @return 長さ (点が 1 つ以下なら, 0)
 */
fun tourLength(
    points: List<Point>,
    order: List<Int>,
): Double =
    if (order.size < 2) {
        0.0
    } else {
        order.indices.sumOf { distance(points[order[it]], points[order[(it + 1) % order.size]]) }
    }

/**
 * 巡回路を求める.
 *
 * @param points 点
 * @param method 解き方
 * @return 巡回路 (最初の点は, 0 番)
 * @throws IllegalArgumentException 厳密解で, 点が [EXACT_MAX_POINTS] より多いとき
 */
fun solve(
    points: List<Point>,
    method: Method,
): Tour {
    val order =
        when (method) {
            Method.NEAREST_NEIGHBOR -> nearestNeighbor(points)
            Method.TWO_OPT -> twoOpt(points, nearestNeighbor(points))
            Method.EXACT -> exact(points)
        }
    return Tour(order, tourLength(points, order))
}

/**
 * 最近傍法で, 巡回路を作る (0 番の点から, 最も近い, まだ訪れていない点へ, 順に進む).
 *
 * @param points 点
 * @return 訪れる順の, 点の番号 (点がなければ, 空)
 */
fun nearestNeighbor(points: List<Point>): List<Int> {
    if (points.isEmpty()) {
        return emptyList()
    }
    val visited = BooleanArray(points.size)
    val order = mutableListOf(0)
    visited[0] = true
    repeat(points.size - 1) {
        val last = points[order.last()]
        val next = points.indices.filter { !visited[it] }.minBy { distance(last, points[it]) }
        visited[next] = true
        order += next
    }
    return order
}

/**
 * 2-opt で, 巡回路を短くする (2 本の辺 a-b と c-d を, a-c と b-d につなぎ替えて, 短くなれば, そうする.
 * 短くできなくなるまで, 繰り返す). 最初の点は, 変えない.
 *
 * @param points 点
 * @param tour 元の巡回路 (訪れる順の, 点の番号)
 * @return 短くした巡回路
 */
fun twoOpt(
    points: List<Point>,
    tour: List<Int>,
): List<Int> {
    val order = tour.toMutableList()
    while (improveOnce(points, order)) {
        // 短くできなくなるまで, 繰り返す
    }
    return order
}

/**
 * 2-opt で, 巡回路を 1 周見て, 短くなるつなぎ替えを, 全てする.
 *
 * @param points 点
 * @param order 巡回路 (書き換える)
 * @return つなぎ替えたら, true
 */
private fun improveOnce(
    points: List<Point>,
    order: MutableList<Int>,
): Boolean {
    val n = order.size
    var improved = false
    for (i in 0 until n - 1) {
        // i = 0 と j = n - 1 の辺は, つながっている (最後から最初に戻る辺) ので, 除く
        val end = if (i == 0) n - 1 else n
        for (j in i + 2 until end) {
            val a = points[order[i]]
            val b = points[order[i + 1]]
            val c = points[order[j]]
            val d = points[order[(j + 1) % n]]
            if (distance(a, c) + distance(b, d) - distance(a, b) - distance(c, d) < -EPSILON) {
                order.subList(i + 1, j + 1).reverse()
                improved = true
            }
        }
    }
    return improved
}

/**
 * 動的計画法 (Held-Karp) で, 最短の巡回路を求める.
 *
 * 「0 番の点から出て, 集合 S の点を全て訪れて, 点 j で終わる道」の最短の長さを, S の小さい順に求めて,
 * 最後に 0 番の点に戻る. 計算の量は, 点の数 n に対して, n² × 2ⁿ.
 *
 * @param points 点 ([EXACT_MAX_POINTS] 点まで)
 * @return 訪れる順の, 点の番号 (最初は, 0 番)
 * @throws IllegalArgumentException 点が [EXACT_MAX_POINTS] より多いとき
 */
fun exact(points: List<Point>): List<Int> {
    val n = points.size
    require(n <= EXACT_MAX_POINTS) { "Too many points for the exact solution: $n" }
    if (n <= TRIVIAL_POINTS) {
        return points.indices.toList()
    }
    val full = 1 shl n
    val cost = Array(full) { DoubleArray(n) { Double.POSITIVE_INFINITY } }
    val parent = Array(full) { IntArray(n) }
    cost[1][0] = 0.0
    // 0 番の点を含む集合 (最下位のビットが 1) だけを, 小さい順に見る
    for (half in 0 until full / 2) {
        val mask = half * 2 + 1
        for (last in 0 until n) {
            if (cost[mask][last].isFinite()) {
                extend(points, cost, parent, mask, last)
            }
        }
    }
    val all = full - 1
    var last = (1 until n).minBy { cost[all][it] + distance(points[it], points[0]) }
    var mask = all
    val order = ArrayDeque<Int>()
    while (last != 0) {
        order.addFirst(last)
        val previous = parent[mask][last]
        mask = mask xor (1 shl last)
        last = previous
    }
    order.addFirst(0)
    return order
}

/**
 * 動的計画法で, 「集合 mask を訪れて, last で終わる道」に, まだ訪れていない点を 1 つ足した道を, 短ければ記録する.
 *
 * @param points 点
 * @param cost 道の長さの表 (書き換える)
 * @param parent 1 つ前の点の表 (書き換える)
 * @param mask 訪れた点の集合 (ビット)
 * @param last 最後の点
 */
private fun extend(
    points: List<Point>,
    cost: Array<DoubleArray>,
    parent: Array<IntArray>,
    mask: Int,
    last: Int,
) {
    for (next in 1 until points.size) {
        val bit = 1 shl next
        val extended = mask or bit
        val candidate = cost[mask][last] + distance(points[last], points[next])
        if (mask and bit == 0 && candidate < cost[extended][next]) {
            cost[extended][next] = candidate
            parent[extended][next] = last
        }
    }
}

/**
 * でたらめな位置の点を作る (試すため).
 *
 * @param count 点の数
 * @param width 横の範囲 (0 から width まで)
 * @param height 縦の範囲 (0 から height まで)
 * @param random 乱数 (テストでは, 種を決める)
 * @return 点
 */
fun randomPoints(
    count: Int,
    width: Double,
    height: Double,
    random: Random = Random.Default,
): List<Point> = List(count) { Point(random.nextDouble() * width, random.nextDouble() * height) }
