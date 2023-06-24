/*
 * SPDX-FileCopyrightText: 2023 Sebastiano Barezzi
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.sebaubuntu.displaycutoutviewer.ext

import android.graphics.Rect

fun Rect.scale(scale: Float) {
    if (scale != 1.0f) {
        left = (left * scale + 0.5f).toInt()
        top = (top * scale + 0.5f).toInt()
        right = (right * scale + 0.5f).toInt()
        bottom = (bottom * scale + 0.5f).toInt()
    }
}
