package com.mixpanel.android.sessionreplay.debug

import android.graphics.Color
import kotlinx.serialization.Serializable

/**
 * Configuration for debug overlay colors.
 *
 * Use this class to customize which mask types are displayed and their colors
 * in the debug overlay. Set a color to null to hide that mask type.
 *
 * @property maskColor Color for masked regions (TEXT_ENTRY and explicitly sensitive views).
 *   When null, masked regions are not shown in the debug overlay. Default: Red.
 * @property autoMaskColor Color for auto-masked regions (text, images, web views).
 *   When null, auto-masked regions are not shown in the debug overlay. Default: Orange.
 * @property unmaskColor Color for unmask regions (addSafeView areas).
 *   Shows areas that are explicitly excluded from auto-masking. Default: Green.
 * @property alpha Opacity of the debug overlay from 0.0 (fully transparent) to 1.0 (fully opaque). Default: 0.5.
 */
@Serializable
data class DebugOverlayColors(
    val maskColor: Int? = Color.RED,
    val autoMaskColor: Int? = Color.rgb(255, 165, 0), // Orange
    val unmaskColor: Int? = Color.GREEN,
    val alpha: Float = .5f
)
