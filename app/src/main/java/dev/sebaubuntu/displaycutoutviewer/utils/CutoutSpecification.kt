/*
 * SPDX-FileCopyrightText: 2020 The Android Open Source Project
 * SPDX-FileCopyrightText: 2023 Sebastiano Barezzi
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.sebaubuntu.displaycutoutviewer.utils

import android.annotation.SuppressLint
import android.graphics.Insets
import android.graphics.Matrix
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.view.Gravity
import androidx.core.graphics.PathParser
import dev.sebaubuntu.displaycutoutviewer.ext.*

/**
 * Imported from frameworks/base/core/java/android/view/CutoutSpecification.java
 */
@SuppressLint("RtlHardcoded")
class CutoutSpecification private constructor(parser: Parser) {
    val path = parser.path
    private val leftBound = parser.leftBound
    private val topBound = parser.topBound
    private val rightBound = parser.rightBound
    private val bottomBound = parser.bottomBound
    private var insets = parser.insets

    init {
        applyPhysicalPixelDisplaySizeRatio(parser.physicalPixelDisplaySizeRatio)
    }

    private fun applyPhysicalPixelDisplaySizeRatio(physicalPixelDisplaySizeRatio: Float) {
        if (physicalPixelDisplaySizeRatio == 1f) {
            return
        }

        path?.let {
            if (!it.isEmpty) {
                val matrix = Matrix()
                matrix.postScale(physicalPixelDisplaySizeRatio, physicalPixelDisplaySizeRatio)
                it.transform(matrix)
            }
        }

        scaleBounds(leftBound, physicalPixelDisplaySizeRatio)
        scaleBounds(topBound, physicalPixelDisplaySizeRatio)
        scaleBounds(rightBound, physicalPixelDisplaySizeRatio)
        scaleBounds(bottomBound, physicalPixelDisplaySizeRatio)

        insets = scaleInsets(insets, physicalPixelDisplaySizeRatio)
    }

    private fun scaleBounds(r: Rect?, ratio: Float) {
        r?.let {
            if (!it.isEmpty) {
                it.scale(ratio)
            }
        }
    }

    private fun scaleInsets(insets: Insets, ratio: Float) = Insets.of(
        (insets.left * ratio + 0.5f).toInt(),
        (insets.top * ratio + 0.5f).toInt(),
        (insets.right * ratio + 0.5f).toInt(),
        (insets.bottom * ratio + 0.5f).toInt()
    )

    /**
     * The CutoutSpecification Parser.
     */
    class Parser internal constructor(
        private val stableDensity: Float,
        private val physicalDisplayWidth: Int,
        private val physicalDisplayHeight: Int,
        val physicalPixelDisplaySizeRatio: Float
    ) {
        private val isShortEdgeOnTop = physicalDisplayWidth < physicalDisplayHeight
        private val matrix = Matrix()
        lateinit var insets: Insets
        private var safeInsetLeft = 0
        private var safeInsetTop = 0
        private var safeInsetRight = 0
        private var safeInsetBottom = 0
        private val tmpRect = Rect()
        private val tmpRectF = RectF()
        private var inDp = false
        var path: Path? = null
        var leftBound: Rect? = null
        var topBound: Rect? = null
        var rightBound: Rect? = null
        var bottomBound: Rect? = null
        private var positionFromLeft = false
        private var positionFromRight = false
        private var positionFromBottom = false
        private var positionFromCenterVertical = false
        private var bindLeftCutout = false
        private var bindRightCutout = false
        private var bindBottomCutout = false
        private var isTouchShortEdgeStart = false
        private var isTouchShortEdgeEnd = false
        private var isCloserToStartSide = false

        private fun computeBoundsRectAndAddToRegion(p: Path, inoutRegion: Region, inoutRect: Rect) {
            tmpRectF.setEmpty()
            p.computeBounds(tmpRectF, false /* unused */)
            tmpRectF.round(inoutRect)
            inoutRegion.op(inoutRect, Region.Op.UNION)
        }

        private fun resetStatus(sb: StringBuilder) {
            sb.setLength(0)
            positionFromBottom = false
            positionFromLeft = false
            positionFromRight = false
            positionFromCenterVertical = false
            bindLeftCutout = false
            bindRightCutout = false
            bindBottomCutout = false
        }

        private fun translateMatrix() {
            val offsetX = if (positionFromRight) {
                physicalDisplayWidth.toFloat()
            } else if (positionFromLeft) {
                0f
            } else {
                physicalDisplayWidth / 2f
            }
            val offsetY = if (positionFromBottom) {
                physicalDisplayHeight.toFloat()
            } else if (positionFromCenterVertical) {
                physicalDisplayHeight / 2f
            } else {
                0f
            }
            matrix.reset()
            if (inDp) {
                matrix.postScale(stableDensity, stableDensity)
            }
            matrix.postTranslate(offsetX, offsetY)
        }

        private fun computeSafeInsets(gravity: Int, rect: Rect): Int {
            if (
                gravity == Gravity.LEFT && (rect.right > 0) && (rect.right < physicalDisplayWidth)
            ) {
                return rect.right
            } else if (
                (gravity == Gravity.TOP) && (rect.bottom > 0) && (rect.bottom < physicalDisplayHeight)
            ) {
                return rect.bottom
            } else if (
                gravity == Gravity.RIGHT && (rect.left > 0) && (rect.left < physicalDisplayWidth)
            ) {
                return physicalDisplayWidth - rect.left
            } else if (
                gravity == Gravity.BOTTOM && (rect.top > 0) && (rect.top < physicalDisplayHeight)
            ) {
                return physicalDisplayHeight - rect.top
            }
            return 0
        }

        private fun setSafeInset(gravity: Int, inset: Int) {
            when (gravity) {
                Gravity.LEFT -> safeInsetLeft = inset
                Gravity.TOP -> safeInsetTop = inset
                Gravity.RIGHT -> safeInsetRight = inset
                Gravity.BOTTOM -> safeInsetBottom = inset
            }
        }

        private fun getSafeInset(gravity: Int) = when (gravity) {
            Gravity.LEFT -> safeInsetLeft
            Gravity.TOP -> safeInsetTop
            Gravity.RIGHT -> safeInsetRight
            Gravity.BOTTOM -> safeInsetBottom
            else -> 0
        }

        private fun onSetEdgeCutout(isStart: Boolean, isShortEdge: Boolean, rect: Rect): Rect {
            val gravity = if (isShortEdge) {
                decideWhichEdge(isShortEdgeOnTop, true, isStart)
            } else {
                if (isTouchShortEdgeStart && isTouchShortEdgeEnd) {
                    decideWhichEdge(isShortEdgeOnTop, false, isStart)
                } else if (isTouchShortEdgeStart || isTouchShortEdgeEnd) {
                    decideWhichEdge(
                        isShortEdgeOnTop, true,
                        isCloserToStartSide
                    )
                } else {
                    decideWhichEdge(isShortEdgeOnTop, isShortEdge, isStart)
                }
            }
            val oldSafeInset = getSafeInset(gravity)
            val newSafeInset = computeSafeInsets(gravity, rect)
            if (oldSafeInset < newSafeInset) {
                setSafeInset(gravity, newSafeInset)
            }
            return Rect(rect)
        }

        private fun setEdgeCutout(newPath: Path) {
            if (bindRightCutout && rightBound == null) {
                rightBound = onSetEdgeCutout(false, !isShortEdgeOnTop, tmpRect)
            } else if (bindLeftCutout && leftBound == null) {
                leftBound = onSetEdgeCutout(true, !isShortEdgeOnTop, tmpRect)
            } else if (bindBottomCutout && bottomBound == null) {
                bottomBound = onSetEdgeCutout(false, isShortEdgeOnTop, tmpRect)
            } else if (
                !(bindBottomCutout || bindLeftCutout || bindRightCutout)
                && topBound == null
            ) {
                topBound = onSetEdgeCutout(true, isShortEdgeOnTop, tmpRect)
            } else {
                return
            }

            path?.also {
                it.addPath(newPath)
            } ?: run {
                path = newPath
            }
        }

        private fun parseSvgPathSpec(region: Region, spec: String) {
            if (spec.length < MINIMAL_ACCEPTABLE_PATH_LENGTH) {
                return
            }

            translateMatrix()
            val newPath = PathParser.createPathFromPathData(spec)
            newPath.transform(matrix)
            computeBoundsRectAndAddToRegion(newPath, region, tmpRect)

            if (tmpRect.isEmpty) {
                return
            }

            if (isShortEdgeOnTop) {
                isTouchShortEdgeStart = tmpRect.top <= 0
                isTouchShortEdgeEnd = tmpRect.bottom >= physicalDisplayHeight
                isCloserToStartSide = tmpRect.centerY() < physicalDisplayHeight / 2
            } else {
                isTouchShortEdgeStart = tmpRect.left <= 0
                isTouchShortEdgeEnd = tmpRect.right >= physicalDisplayWidth
                isCloserToStartSide = tmpRect.centerX() < physicalDisplayWidth / 2
            }

            setEdgeCutout(newPath)
        }

        private fun parseSpecWithoutDp(specWithoutDp: String) {
            val region = Region()
            var sb: StringBuilder? = null
            var currentIndex: Int
            var lastIndex = 0

            while (
                specWithoutDp.indexOf(MARKER_START_CHAR, lastIndex).also {
                    currentIndex = it
                } != -1
            ) {
                if (sb == null) {
                    sb = StringBuilder(specWithoutDp.length)
                }

                sb.append(specWithoutDp, lastIndex, currentIndex)
                if (specWithoutDp.startsWith(LEFT_MARKER, currentIndex)) {
                    if (!positionFromRight) {
                        positionFromLeft = true
                    }
                    currentIndex += LEFT_MARKER.length
                } else if (specWithoutDp.startsWith(RIGHT_MARKER, currentIndex)) {
                    if (!positionFromLeft) {
                        positionFromRight = true
                    }
                    currentIndex += RIGHT_MARKER.length
                } else if (specWithoutDp.startsWith(BOTTOM_MARKER, currentIndex)) {
                    parseSvgPathSpec(region, sb.toString())
                    currentIndex += BOTTOM_MARKER.length

                    /* prepare to parse the rest path */
                    resetStatus(sb)
                    bindBottomCutout = true
                    positionFromBottom = true
                } else if (specWithoutDp.startsWith(CENTER_VERTICAL_MARKER, currentIndex)) {
                    parseSvgPathSpec(region, sb.toString())
                    currentIndex += CENTER_VERTICAL_MARKER.length

                    /* prepare to parse the rest path */
                    resetStatus(sb)
                    positionFromCenterVertical = true
                } else if (specWithoutDp.startsWith(CUTOUT_MARKER, currentIndex)) {
                    parseSvgPathSpec(region, sb.toString())
                    currentIndex += CUTOUT_MARKER.length

                    /* prepare to parse the rest path */
                    resetStatus(sb)
                } else if (specWithoutDp.startsWith(BIND_LEFT_CUTOUT_MARKER, currentIndex)) {
                    bindBottomCutout = false
                    bindRightCutout = false
                    bindLeftCutout = true
                    currentIndex += BIND_LEFT_CUTOUT_MARKER.length
                } else if (specWithoutDp.startsWith(BIND_RIGHT_CUTOUT_MARKER, currentIndex)) {
                    bindBottomCutout = false
                    bindLeftCutout = false
                    bindRightCutout = true
                    currentIndex += BIND_RIGHT_CUTOUT_MARKER.length
                } else {
                    currentIndex += 1
                }
                lastIndex = currentIndex
            }

            sb?.also {
                it.append(specWithoutDp, lastIndex, specWithoutDp.length)
                parseSvgPathSpec(region, it.toString())
            } ?: run {
                parseSvgPathSpec(region, specWithoutDp)
            }
        }

        /**
         * To parse specification string as the CutoutSpecification.
         *
         * @param originalSpec the specification string
         * @return the CutoutSpecification instance
         */
        fun parse(originalSpec: String): CutoutSpecification {
            val dpIndex = originalSpec.lastIndexOf(DP_MARKER)
            inDp = dpIndex != -1
            val spec: String = if (dpIndex != -1) {
                (originalSpec.substring(0, dpIndex)
                        + originalSpec.substring(dpIndex + DP_MARKER.length))
            } else {
                originalSpec
            }
            parseSpecWithoutDp(spec)
            insets = Insets.of(safeInsetLeft, safeInsetTop, safeInsetRight, safeInsetBottom)
            return CutoutSpecification(this)
        }
    }

    companion object {
        private const val MINIMAL_ACCEPTABLE_PATH_LENGTH = "H1V1Z".length

        private const val MARKER_START_CHAR = '@'

        private const val DP_MARKER = "${MARKER_START_CHAR}dp"
        private const val BOTTOM_MARKER = "${MARKER_START_CHAR}bottom"
        private const val RIGHT_MARKER = "${MARKER_START_CHAR}right"
        private const val LEFT_MARKER = "${MARKER_START_CHAR}left"
        private const val CUTOUT_MARKER = "${MARKER_START_CHAR}cutout"
        private const val CENTER_VERTICAL_MARKER = "${MARKER_START_CHAR}center_vertical"

        // By default, it's top bound cutout. That's why TOP_BOUND_CUTOUT_MARKER is not defined
        private const val BIND_RIGHT_CUTOUT_MARKER = "${MARKER_START_CHAR}bind_right_cutout"
        private const val BIND_LEFT_CUTOUT_MARKER = "${MARKER_START_CHAR}bind_left_cutout"

        private fun decideWhichEdge(
            isTopEdgeShortEdge: Boolean,
            isShortEdge: Boolean, isStart: Boolean
        ) = if (isTopEdgeShortEdge) {
            if (isShortEdge) {
                if (isStart) {
                    Gravity.TOP
                } else {
                    Gravity.BOTTOM
                }
            } else if (isStart) {
                Gravity.LEFT
            } else {
                Gravity.RIGHT
            }
        } else if (isShortEdge) {
            if (isStart) {
                Gravity.LEFT
            } else {
                Gravity.RIGHT
            }
        } else if (isStart) {
            Gravity.TOP
        } else {
            Gravity.BOTTOM
        }
    }
}
