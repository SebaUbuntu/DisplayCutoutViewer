/*
 * SPDX-FileCopyrightText: 2023 Sebastiano Barezzi
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.sebaubuntu.displaycutoutviewer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

class PathView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint().apply {
        isAntiAlias = true
        strokeWidth = 2F
        style = Paint.Style.STROKE

        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnBackground, typedValue, true)
        val colorData = typedValue.data
        color = colorData
    }

    private var path: Path? = null

    fun setPath(path: Path) {
        this.path = path

        invalidate()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas?.apply {
            path?.let {
                drawPath(it, paint)
            }
        }
    }
}
