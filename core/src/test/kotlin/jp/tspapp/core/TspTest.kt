/*
 * Copyright (c) 2026 Tetsuya Higashi
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package jp.tspapp.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** 巡回セールスマン問題の, 解き方の単体テスト. */
class TspTest {
    /** 1 辺 1 の正方形の角 (最短の巡回路の長さは, 4). */
    private val square = listOf(Point(0.0, 0.0), Point(1.0, 1.0), Point(1.0, 0.0), Point(0.0, 1.0))

    /** 全ての順を試して, 最短の長さを返す (小さな問題の, 正解). */
    private fun bruteForce(points: List<Point>): Double {
        fun permutations(rest: List<Int>): List<List<Int>> =
            if (rest.isEmpty()) {
                listOf(emptyList())
            } else {
                rest.flatMap { first -> permutations(rest - first).map { listOf(first) + it } }
            }
        return permutations((1 until points.size).toList()).minOf {
            tourLength(
                points,
                listOf(0) + it,
            )
        }
    }

    /** 全ての点を, 1 回ずつ訪れているか. */
    private fun assertVisitsAll(
        points: List<Point>,
        order: List<Int>,
    ) {
        assertEquals(points.indices.toList(), order.sorted())
        assertEquals(0, order.firstOrNull() ?: 0)
    }

    /** 距離は, ユークリッド距離. */
    @Test
    fun distanceIsEuclidean() {
        assertEquals(5.0, distance(Point(0.0, 0.0), Point(3.0, 4.0)))
    }

    /** 巡回路の長さは, 最初の点に戻る分を含む. 点が 1 つ以下なら, 0. */
    @Test
    fun tourLengthIncludesTheWayBack() {
        assertEquals(4.0, tourLength(square, listOf(0, 2, 1, 3)))
        assertEquals(0.0, tourLength(square, listOf(0)))
        assertEquals(0.0, tourLength(emptyList(), emptyList()))
    }

    /** 最近傍法は, 0 番から, 最も近い点へ進む. 点がなければ, 空. */
    @Test
    fun nearestNeighborGoesToTheNearestPoint() {
        val points = listOf(Point(0.0, 0.0), Point(10.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0))

        assertEquals(listOf(0, 2, 3, 1), nearestNeighbor(points))
        assertEquals(emptyList(), nearestNeighbor(emptyList()))
        assertEquals(listOf(0), nearestNeighbor(listOf(Point(1.0, 1.0))))
    }

    /** 2-opt は, 交差する辺を, つなぎ替える. */
    @Test
    fun twoOptRemovesTheCrossing() {
        // 0 → 1 → 2 → 3 は, 対角線が交差する (長さ 2 + 2√2)
        val order = twoOpt(square, listOf(0, 1, 2, 3))

        assertVisitsAll(square, order)
        assertEquals(4.0, tourLength(square, order), 1e-9)
    }

    /** 2-opt は, 短くならなければ, 変えない. 点が 3 つ以下でも, 動く. */
    @Test
    fun twoOptKeepsTheShortTour() {
        assertEquals(listOf(0, 2, 1, 3), twoOpt(square, listOf(0, 2, 1, 3)))
        assertEquals(listOf(0, 1, 2), twoOpt(square.take(3), listOf(0, 1, 2)))
        assertEquals(emptyList(), twoOpt(emptyList(), emptyList()))
    }

    /** 厳密解は, 全ての順を試した最短と, 同じ長さ. */
    @Test
    fun exactMatchesTheBruteForce() {
        val random = Random(1)
        repeat(5) {
            val points = randomPoints(7, 100.0, 100.0, random)
            val order = exact(points)

            assertVisitsAll(points, order)
            assertEquals(bruteForce(points), tourLength(points, order), 1e-9)
        }
    }

    /** 厳密解は, 3 点以下なら, そのままの順. */
    @Test
    fun exactOfFewPoints() {
        assertEquals(emptyList(), exact(emptyList()))
        assertEquals(listOf(0, 1, 2), exact(square.take(3)))
    }

    /** 厳密解は, 上限より多い点では, 失敗する. */
    @Test
    fun exactFailsWithTooManyPoints() {
        val points = randomPoints(EXACT_MAX_POINTS + 1, 1.0, 1.0, Random(2))

        assertFailsWith<IllegalArgumentException> { exact(points) }
    }

    /** solve は, 解き方ごとに, 巡回路と, その長さを返す. 厳密解が最も短い. */
    @Test
    fun solveWithEachMethod() {
        val points = randomPoints(10, 100.0, 100.0, Random(3))
        val tours = Method.entries.associateWith { solve(points, it) }

        tours.values.forEach {
            assertVisitsAll(points, it.order)
            assertEquals(tourLength(points, it.order), it.length)
        }
        val best = tours.getValue(Method.EXACT).length
        assertTrue(tours.values.all { it.length >= best - 1e-9 })
        assertTrue(
            tours.getValue(Method.TWO_OPT).length <= tours.getValue(Method.NEAREST_NEIGHBOR).length,
        )
    }

    /** でたらめな点は, 範囲の中にある. */
    @Test
    fun randomPointsAreInTheRange() {
        val points = randomPoints(50, 30.0, 20.0, Random(4))

        assertEquals(50, points.size)
        assertTrue(points.all { it.x in 0.0..30.0 && it.y in 0.0..20.0 })
        assertEquals(3, randomPoints(3, 1.0, 1.0).size)
    }

    /** データクラスの, 文字列, 複製, 分解が, 使える. */
    @Test
    fun dataClassFeatures() {
        val point = Point(1.0, 2.0)
        val tour = Tour(listOf(0, 1), 2.0)

        assertEquals(point, point.copy())
        assertEquals(point.hashCode(), point.copy().hashCode())
        assertEquals("Point(x=1.0, y=2.0)", point.toString())
        assertEquals(listOf(1.0, 2.0), listOf(point.component1(), point.component2()))
        assertEquals(tour, tour.copy())
        assertEquals(tour.hashCode(), tour.copy().hashCode())
        assertEquals("Tour(order=[0, 1], length=2.0)", tour.toString())
        assertEquals(listOf<Any>(listOf(0, 1), 2.0), listOf(tour.component1(), tour.component2()))
    }
}
