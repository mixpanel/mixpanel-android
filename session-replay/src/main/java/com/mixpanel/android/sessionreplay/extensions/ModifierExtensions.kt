package com.mixpanel.android.sessionreplay.extensions

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.mixpanel.android.sessionreplay.sensitive_views.SensitiveViewManager
import com.mixpanel.android.sessionreplay.utils.Compose.MP_REPLAY_SENSITIVE

val mpReplaySensitivePropKey = SemanticsPropertyKey<Boolean>(MP_REPLAY_SENSITIVE)
internal var SemanticsPropertyReceiver.mpReplaySensitive by mpReplaySensitivePropKey

/**
 * Modifier.Node implementation for tracking sensitive views independently of semantics.
 * This provides a backup tracking mechanism that works alongside semantics and is not
 * affected by the semantic merging that occurs with clickable parents.
 */
internal class SensitiveViewNode(var isSensitive: Boolean) : Modifier.Node(), GlobalPositionAwareModifierNode {

    @Volatile
    internal var coordinates: LayoutCoordinates? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        if (coordinates.isAttached) {
            SensitiveViewManager.registerNode(this, isSensitive)
        } else {
            unregister()
        }
    }

    override fun onDetach() {
        unregister()
        super.onDetach()
    }

    private fun unregister() {
        coordinates = null
        SensitiveViewManager.unregisterNode(this)
    }

    /**
     * Gets the current bounds of this node in window coordinates.
     * Called at screenshot capture time to get fresh bounds.
     */
    fun getCurrentBounds(): Rect? {
        val coords = coordinates ?: return null

        // Skip nodes not attached to layout hierarchy
        if (!coords.isAttached || !isAttached) return null

        // Skip nodes clipped by scroll containers (boundsInRoot has clipping applied)
        if (coords.boundsInRoot().isEmpty) return null

        // Skip nodes with no visible area in the window
        val bounds = coords.boundsInWindow()
        if (bounds.isEmpty) return null

        val rect = Rect(
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt(),
            bounds.bottom.toInt()
        )

        // Skip nodes with zero or negative dimensions after int conversion
        if (rect.width() <= 0 || rect.height() <= 0) return null

        return rect
    }
}

/**
 * ModifierNodeElement that creates and updates SensitiveViewNode instances.
 */
@SuppressLint("ModifierFactoryReturnType")
private data class SensitiveViewElement(val isSensitive: Boolean) :
    ModifierNodeElement<SensitiveViewNode>() {

    override fun create() = SensitiveViewNode(isSensitive)

    override fun update(node: SensitiveViewNode) {
        node.isSensitive = isSensitive
        // Re-register with updated sensitivity if coordinates are attached
        if (node.coordinates?.isAttached == true) {
            SensitiveViewManager.registerNode(node, isSensitive)
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "mpReplaySensitive"
        properties["isSensitive"] = isSensitive
    }
}

/**
 * Marks a Compose view as sensitive or safe for masking during session replay screenshots.
 *
 * This function adds metadata to the [Modifier] that informs the session replay library
 * whether the associated Composable should be masked (e.g., to hide sensitive user information).
 *
 * @param isSensitive
 * - `true` to mark the view as sensitive and ensure it is masked in screenshots.
 * - `false` to explicitly mark the view as safe and exclude it from masking.
 *
 * If this modifier is not applied, the library will fall back to its default sensitivity
 * detection logic, which automatically masks views based on the configuration provided
 * during initialization.
 *
 * @return A [Modifier] with the sensitivity flag applied.
 *
 * Example usage:
 * ```
 * Text(
 *     text = "Card Number: 1234 5678 9012 3456",
 *     modifier = Modifier.mpReplaySensitive(true)
 * )
 * ```
 */
fun Modifier.mpReplaySensitive(isSensitive: Boolean): Modifier =
    this then SensitiveViewElement(isSensitive).semantics { mpReplaySensitive = isSensitive }
