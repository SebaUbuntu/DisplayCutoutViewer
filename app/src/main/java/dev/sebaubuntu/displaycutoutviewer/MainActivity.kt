/*
 * SPDX-FileCopyrightText: 2023 Sebastiano Barezzi
 * SPDX-License-Identifier: Apache-2.0
 */

package dev.sebaubuntu.displaycutoutviewer

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.sebaubuntu.displaycutoutviewer.ui.PathView
import dev.sebaubuntu.displaycutoutviewer.utils.CutoutSpecification

class MainActivity : AppCompatActivity() {
    // Views
    private val applyPathButton by lazy { findViewById<Button>(R.id.applyPathButton) }
    private val pathEditText by lazy { findViewById<EditText>(R.id.pathEditText) }
    private val pathView by lazy { findViewById<PathView>(R.id.pathView) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemBars()

        setContentView(R.layout.activity_main)

        // Set the default cutout value
        pathEditText.setText(getDefaultCutout())

        applyPathButton.setOnClickListener {
            val pathData = pathEditText.text.toString().takeUnless {
                it.isBlank()
            } ?: return@setOnClickListener

            val physicalDisplayWidth = pathView.width
            val physicalDisplayHeight = pathView.height
            val density = resources.displayMetrics.density

            val path = CutoutSpecification.Parser(
                density, physicalDisplayWidth, physicalDisplayHeight, 1f
            ).parse(pathData).path ?: run {
                Toast.makeText(this, R.string.invalid_path, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            pathView.setPath(path)
        }
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        // Configure the behavior of the hidden system bars
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Hide the status bar
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    @SuppressLint("DiscouragedApi")
    private fun getDefaultCutout(): String {
        val resources = Resources.getSystem()

        for (resourceName in CUTOUT_RESOURCE_NAMES) {
            resources.getIdentifier(
                resourceName, "string", "android"
            ).takeIf { it > 0 }?.let { id ->
                resources.getString(id).takeIf { path ->
                    path.isNotBlank()
                }?.let { path ->
                    return path
                }
            }
        }

        return getString(R.string.example_path)
    }

    companion object {
        private val CUTOUT_RESOURCE_NAMES = listOf(
            "config_mainBuiltInDisplayCutout",
            "config_mainBuiltInDisplayCutoutRectApproximation",
        )
    }
}
