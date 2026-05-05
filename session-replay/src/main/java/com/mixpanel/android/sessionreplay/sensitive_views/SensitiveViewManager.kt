package com.mixpanel.android.sessionreplay.sensitive_views

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.mixpanel.android.sessionreplay.extensions.SensitiveViewNode
import com.mixpanel.android.sessionreplay.extensions.mpReplaySensitivePropKey
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class SubviewSummary(
    val boundsSnapshot: Set<Rect> = emptySet(),
    val hasActiveTransition: Boolean = false
) {
    val needsMasking: Boolean get() = boundsSnapshot.isNotEmpty()
}

enum class AutoMaskedView {
    Text,
    Image,
    Web
    ;

    companion object {
        fun defaultSet(): Set<AutoMaskedView> = setOf(Text, Image, Web)
    }
}

/**
 * Type of mask applied to a region.
 * Ordered by priority (lowest to highest ordinal = lowest to highest priority).
 */
internal enum class MaskDecision {
    NONE, // No masking applied
    UNMASK, // Explicitly marked as safe via addSafeView
    AUTO, // Auto-masked based on view type (text, images, web views)
    MASK, // Explicitly marked sensitive by developer (addSensitiveView, mpSensitive modifier)
    TEXT_ENTRY; // Text entry fields (EditText) - security enforced, cannot be overridden
}

/**
 * Listener interface for receiving mask region updates.
 */
internal fun interface MaskRegionsListener {
    /**
     * Called when mask regions have been detected.
     * @param entries Map of bounds to mask decision type
     */
    fun onMaskRegionsDetected(entries: Map<Rect, MaskDecision>)
}

object SensitiveViewManager {
    private var _autoMaskedViews = mutableSetOf<AutoMaskedView>()
    private var maskRegionsListener: MaskRegionsListener? = null

    // Only track unmask regions when debug overlay is enabled (saves overhead in production)
    private val trackUnmask: Boolean get() = maskRegionsListener != null

    // Attempt to load ComposeView class if available
    private val composeViewClass: Class<*>? =
        try {
            Class.forName("androidx.compose.ui.platform.ComposeView")
        } catch (e: ClassNotFoundException) {
            null // Compose library is not available in the project
        }

    // Cache the paint object and make it thread-safe
    private val paint =
        Paint().apply {
            color = Color.LTGRAY
            style = Paint.Style.FILL
            isAntiAlias = true
        }

    // Use concurrent hash sets for better memory efficiency while maintaining thread safety
    private val sensitiveViews = Collections.synchronizedSet(HashSet<View>())
    private val safeViews = Collections.synchronizedSet(HashSet<View>())

    // Use concurrent hash set for faster lookups and better thread safety
    private val _sensitiveClasses =
        Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>()).apply {
            add(EditText::class.java)
        }

    // Cache for view class sensitivity checks to avoid repeated isAssignableFrom calls
    private val viewClassSensitivityCache = ConcurrentHashMap<Class<*>, Boolean>()

    // Track Modifier.Node based sensitive/safe views (bypasses semantics merging)
    private val sensitiveNodes = Collections.synchronizedSet(HashSet<SensitiveViewNode>())
    private val safeNodes = Collections.synchronizedSet(HashSet<SensitiveViewNode>())

    /**
     * Registers a SensitiveViewNode for tracking.
     * Called from Modifier.Node when positioned or updated.
     */
    internal fun registerNode(node: SensitiveViewNode, isSensitive: Boolean) {
        if (isSensitive) {
            safeNodes.remove(node)
            sensitiveNodes.add(node)
        } else {
            sensitiveNodes.remove(node)
            safeNodes.add(node)
        }
    }

    /**
     * Unregisters a SensitiveViewNode from tracking.
     * Called from Modifier.Node on detach.
     */
    internal fun unregisterNode(node: SensitiveViewNode) {
        sensitiveNodes.remove(node)
        safeNodes.remove(node)
    }

    /**
     * Clears all registered nodes and cached state.
     * Called during SDK deinitialization to prevent memory leaks.
     */
    internal fun deinitialize() {
        sensitiveNodes.clear()
        safeNodes.clear()
        sensitiveViews.clear()
        safeViews.clear()
        viewClassSensitivityCache.clear()
        autoMaskedViews = AutoMaskedView.defaultSet()
        maskRegionsListener = null
    }

    /**
     * Sets the listener to be notified when mask regions are detected.
     * Cleared automatically in [deinitialize].
     * @param listener The listener to receive mask regions updates
     */
    internal fun setMaskRegionsListener(listener: MaskRegionsListener?) {
        maskRegionsListener = listener
    }

    /**
     * Collects current bounds from all registered Modifier.Node based tracking.
     * Called at screenshot capture time to get fresh coordinates.
     */
    private fun collectNodeBounds(accumulator: MutableMap<Rect, MaskDecision>) {
        synchronized(sensitiveNodes) {
            for (node in sensitiveNodes) {
                node.getCurrentBounds()?.let { bounds ->
                    addOrUpdateEntry(accumulator, bounds, MaskDecision.MASK)
                }
            }
        }
        // Only track unmask regions when debug overlay is enabled
        if (trackUnmask) {
            synchronized(safeNodes) {
                for (node in safeNodes) {
                    node.getCurrentBounds()?.let { bounds ->
                        addOrUpdateEntry(accumulator, bounds, MaskDecision.UNMASK)
                    }
                }
            }
        }
    }

    /**
     * Adds or updates a mask entry, keeping the highest priority type for each bounds.
     */
    private fun addOrUpdateEntry(
        accumulator: MutableMap<Rect, MaskDecision>,
        bounds: Rect,
        newType: MaskDecision
    ) {
        val existingType = accumulator[bounds]
        if (existingType == null || newType.ordinal > existingType.ordinal) {
            accumulator[bounds] = newType
        }
    }

    var autoMaskedViews: Set<AutoMaskedView>
        get() = _autoMaskedViews
        set(value) {
            _autoMaskedViews = value.toMutableSet()
            updateSensitiveClasses()
        }

    private val sensitiveClasses: Set<Class<*>>
        get() = _sensitiveClasses

    private val IMAGE_INDICATORS =
        listOf(
            "image",
            "picture",
            "photo",
            "icon",
            "logo",
            "avatar",
            "thumbnail",
            "banner",
            "illustration",
            "graphic"
        )

    private fun updateSensitiveClasses() {
        if (AutoMaskedView.Text in autoMaskedViews) {
            _sensitiveClasses.add(TextView::class.java)
        } else {
            _sensitiveClasses.remove(TextView::class.java)
        }

        if (AutoMaskedView.Image in autoMaskedViews) {
            _sensitiveClasses.add(ImageView::class.java)
        } else {
            _sensitiveClasses.remove(ImageView::class.java)
        }

        if (AutoMaskedView.Web in autoMaskedViews) {
            _sensitiveClasses.add(WebView::class.java)
        } else {
            _sensitiveClasses.remove(WebView::class.java)
        }

        // Clear the cache when sensitive classes change
        viewClassSensitivityCache.clear()
    }

    /**
     * Checks if a view class is sensitive by checking against all sensitive classes.
     * Uses a cache to avoid repeated isAssignableFrom reflection calls.
     */
    private fun isViewClassSensitive(viewClass: Class<*>): Boolean {
        // Check if already cached
        val cached = viewClassSensitivityCache[viewClass]
        if (cached != null) {
            return cached
        }

        // Compute and cache the result
        val isSensitive =
            sensitiveClasses.any { sensitiveClass ->
                sensitiveClass.isAssignableFrom(viewClass)
            }
        viewClassSensitivityCache[viewClass] = isSensitive
        return isSensitive
    }

    fun addSensitiveView(view: View) = sensitiveViews.add(view)

    fun removeSensitiveView(view: View) = sensitiveViews.remove(view)

    private fun containsSensitiveView(view: View): Boolean = sensitiveViews.contains(view)

    fun addSafeView(view: View) = safeViews.add(view)

    fun removeSafeView(view: View) = safeViews.remove(view)

    private fun containsSafeView(view: View): Boolean = safeViews.contains(view)

    fun addSensitiveClass(aClass: Class<*>?) {
        aClass?.let {
            _sensitiveClasses.add(it)
            // Clear the cache when sensitive classes change
            viewClassSensitivityCache.clear()
        }
    }

    fun removeSensitiveClass(aClass: Class<*>?) {
        aClass?.let {
            if (it != EditText::class.java) {
                _sensitiveClasses.remove(it)
                // Clear the cache when sensitive classes change
                viewClassSensitivityCache.clear()
            }
        }
    }

    /**
     * Collects maskable Compose nodes by traversing Compose semantics trees.
     */
    private fun collectMaskableNodes(
        rootView: View,
        boundsAccumulator: MutableMap<Rect, MaskDecision>,
        processedSemanticsOwners: MutableSet<Int>
    ) {
        val composeRoots = ArrayList<RootForTest>(5) // Preallocate with expected size

        findComposeRoots(rootView, composeRoots)

        for (root in composeRoots) {
            val ownerHash = System.identityHashCode(root.semanticsOwner)
            /**
             *   The same SemanticsOwner can be reached through multiple paths:
             *
             *   1. Nested ComposeViews - A ComposeView inside another ComposeView might share the same semantics owner
             *   2. ModalBottomSheet/Dialogs - These create separate AndroidComposeView instances that might appear multiple times during view traversal
             *   3. View hierarchy traversal visits the same Compose root twice - If a ComposeView is both a RootForTest AND a child of a ViewGroup, you might encounter it in both findComposeRoots() and the parent iteration
             *
             *   Without this guard, you'd traverse the same semantics tree multiple times, resulting in wasted cpu time on main thread
             *
             *   Example scenario:
             *   DecorView
             *     └── FrameLayout
             *          └── ComposeView (RootForTest) ← found by findComposeRoots()
             *               └── AndroidComposeView (also RootForTest) ← found again!
             *
             *   Both would have the same semanticsOwner, so the second traversal is skipped.
             *
             */
            if (!processedSemanticsOwners.add(ownerHash)) {
                continue
            }
            traverseSemanticsNode(root.semanticsOwner.rootSemanticsNode, boundsAccumulator)
        }
    }

    private fun findComposeRoots(
        view: View?,
        composeRoots: MutableList<RootForTest>
    ) {
        when (view) {
            is RootForTest -> {
                composeRoots.add(view)
            }

            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    findComposeRoots(view.getChildAt(i), composeRoots)
                }
            }
        }
    }

    fun SemanticsConfiguration.hasText(): Boolean = !this.getOrNull(SemanticsProperties.Text).isNullOrEmpty()

    fun SemanticsConfiguration.hasImage(): Boolean {
        // Primary detection: Check for Image role (cheapest check first)
        if (this.getOrNull(SemanticsProperties.Role) == Role.Image) {
            return true
        }

        // Secondary detection: Check for content description typically used with images
        val contentDescription = this.getOrNull(SemanticsProperties.ContentDescription)
        if (contentDescription?.any { desc ->
                IMAGE_INDICATORS.any { indicator -> desc.contains(indicator, ignoreCase = true) }
            } == true
        ) {
            return true
        }

        // Tertiary detection: Node without text but with bounds (potential image)
        // Only do heuristics if primary/secondary detection failed
        return isPotentialImageByHeuristics()
    }

    private fun SemanticsConfiguration.isPotentialImageByHeuristics(): Boolean {
        // Early exit if has text - images typically don't have text
        if (!this.getOrNull(SemanticsProperties.Text).isNullOrEmpty()) {
            return false
        }

        // Early exit if has editable text - definitely not an image
        if (this.getOrNull(SemanticsProperties.EditableText) != null) {
            return false
        }

        // Only then check interactive properties
        val isClickable = this.getOrNull(SemanticsActions.OnClick) != null
        val contentDescription = this.getOrNull(SemanticsProperties.ContentDescription)
        // If no text content but is interactive or has description, could be an image
        return isClickable || !contentDescription.isNullOrEmpty()
    }

    fun SemanticsConfiguration.hasWebView(): Boolean {
        // todo: Rahul: figure out how to detect compose views with webview
        return false
    }

    fun SemanticsConfiguration.isSensitiveView(): Boolean? = this.getOrNull(mpReplaySensitivePropKey)

    private fun markNodeForMasking(
        node: SemanticsNode,
        accumulator: MutableMap<Rect, MaskDecision>,
        maskDecision: MaskDecision
    ) {
        // Skip nodes not placed in layout (e.g., LazyColumn items outside viewport buffer)
        if (!node.layoutInfo.isPlaced) return

        // Skip nodes clipped by scroll containers (boundsInRoot has clipping applied)
        if (node.boundsInRoot.isEmpty) return

        // Skip nodes with no visible area in the window
        val bounds = node.boundsInWindow
        if (bounds.isEmpty) return

        val rect = Rect(
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt(),
            bounds.bottom.toInt()
        )

        // Skip nodes with zero or negative dimensions after int conversion
        if (rect.width() <= 0 || rect.height() <= 0) return

        addOrUpdateEntry(accumulator, rect, maskDecision)
    }

    private data class ViewContext(
        val view: View,
        val isInsideSafeContainer: Boolean
    )

    private fun traverseSemanticsNode(
        node: SemanticsNode,
        boundsAccumulator: MutableMap<Rect, MaskDecision>,
        parentIsSafe: Boolean = false
    ) {
        val config = node.config

        // Check if this is an input field (always mask regardless of parent safe status)
        val isInputField = config.getOrNull(SemanticsProperties.EditableText) != null

        // Calculate if current node is safe (either parent is safe or explicitly marked)
        val isSafe = parentIsSafe || (config.isSensitiveView() == false)

        // Ordered by priority (highest to lowest): TEXT_ENTRY > MASK > AUTO > UNMASK
        // Cheap boolean checks first, then more expensive content checks
        val maskDecision =
            when {
                isInputField -> MaskDecision.TEXT_ENTRY

                config.isSensitiveView() == true -> MaskDecision.MASK

                isSafe -> {
                    if (trackUnmask) MaskDecision.UNMASK else MaskDecision.NONE
                }

                (AutoMaskedView.Text in autoMaskedViews) && config.hasText() -> MaskDecision.AUTO

                (AutoMaskedView.Image in autoMaskedViews) && config.hasImage() -> MaskDecision.AUTO

                (AutoMaskedView.Web in autoMaskedViews) && config.hasWebView() -> MaskDecision.AUTO

                else -> MaskDecision.NONE
            }

        if (maskDecision != MaskDecision.NONE) {
            markNodeForMasking(node, boundsAccumulator, maskDecision)
        }

        for (child in node.children) {
            traverseSemanticsNode(child, boundsAccumulator, isSafe)
        }
    }

    /**
     * Processes subviews to detect sensitive content that needs masking.
     * Returns a SubviewSummary containing masking info and a snapshot of bounds for comparison.
     *
     * @param view The root view to process
     */
    fun processSubviews(view: View?): SubviewSummary {
        if (view == null) return SubviewSummary()

        val boundsAccumulator = mutableMapOf<Rect, MaskDecision>()
        val viewsToProcess = ArrayDeque<ViewContext>()

        // Track processed SemanticsOwners to avoid duplicate Compose tree traversal
        val processedSemanticsOwners = HashSet<Int>()

        // Initialize with the root view, checking if it's marked as safe
        viewsToProcess.add(ViewContext(view, containsSafeView(view)))

        while (viewsToProcess.isNotEmpty()) {
            val (currentView, isInsideSafeContainer) = viewsToProcess.removeAt(0)

            // Skip views that aren't visible (including children of GONE/INVISIBLE parents)
            if (!currentView.isShown) continue

            // Check for active screen transitions (transitionAlpha != 1.0f indicates animation in progress)
            val hasTransitionAlpha = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && currentView.transitionAlpha != 1.0f

            // Fragment Transition animations may use legacy Animation API. This API is also used for other
            // animations (spinners, buttons, etc.) so we only skip captures if the animation is on a
            // fragment boundary (where lifecycle owner changes from parent to child).
            val hasAnimation = currentView.animation?.hasStarted() == true && currentView.animation?.hasEnded() == false

            // Only check lifecycle boundary if there's an animation (avoids walking up tree for every view)
            val hasFragmentAnimation = if (hasAnimation) {
                val viewOwner = currentView.findViewTreeLifecycleOwner()
                val parentOwner = (currentView.parent as? View)?.findViewTreeLifecycleOwner()
                // Fragment boundary = both have owners and they differ
                viewOwner != null && parentOwner != null && viewOwner != parentOwner
            } else {
                false
            }

            if (hasTransitionAlpha || hasFragmentAnimation) {
                // Early exit - no point processing rest of hierarchy if we'll skip capture
                return SubviewSummary(hasActiveTransition = true)
            }

            val isExplicitlySensitive = containsSensitiveView(currentView)
            val isClassSensitive = isViewClassSensitive(currentView::class.java)

            if (isExplicitlySensitive || isClassSensitive) {
                val isSafe = isInsideSafeContainer || containsSafeView(currentView)
                val isInputField = EditText::class.java.isAssignableFrom(currentView::class.java)

                // Explicitly sensitive, input fields, or non-safe class-sensitive views get masked
                val shouldMask = isExplicitlySensitive || isInputField || !isSafe

                if (shouldMask) {
                    val rect = Rect()
                    if (currentView.getGlobalVisibleRect(rect)) {
                        val maskDecision = when {
                            isInputField -> MaskDecision.TEXT_ENTRY
                            isExplicitlySensitive -> MaskDecision.MASK
                            else -> MaskDecision.AUTO
                        }
                        addOrUpdateEntry(boundsAccumulator, Rect(rect), maskDecision)
                    }
                } else if (trackUnmask) {
                    val rect = Rect()
                    if (currentView.getGlobalVisibleRect(rect)) {
                        addOrUpdateEntry(boundsAccumulator, Rect(rect), MaskDecision.UNMASK)
                    }
                }
            } else if (trackUnmask && containsSafeView(currentView)) {
                val rect = Rect()
                if (currentView.getGlobalVisibleRect(rect)) {
                    addOrUpdateEntry(boundsAccumulator, Rect(rect), MaskDecision.UNMASK)
                }
            }

            // Check for Compose content - either ComposeView or direct RootForTest (e.g., AndroidComposeView in ModalBottomSheet)
            val isComposeView = composeViewClass != null && currentView is ComposeView
            val isRootForTest = currentView is RootForTest

            if (isComposeView || isRootForTest) {
                collectMaskableNodes(currentView, boundsAccumulator, processedSemanticsOwners)
            }

            // Then handle the ViewGroup case which happens regardless of jetpackComposeEnabled
            if (currentView is ViewGroup) {
                // Calculate safe status to propagate to children
                val isSafe = isInsideSafeContainer || containsSafeView(currentView)
                for (i in 0 until currentView.childCount) {
                    currentView.getChildAt(i)?.let { child ->
                        viewsToProcess.add(ViewContext(child, isSafe))
                    }
                }
            }
        }

        // Collect bounds from Modifier.Node based tracking (bypasses semantics merging)
        collectNodeBounds(boundsAccumulator)

        // Notify debug overlay listener if enabled
        maskRegionsListener?.onMaskRegionsDetected(boundsAccumulator)

        // Extract mask bounds for production use
        // When trackUnmask is false, no UNMASK entries exist so no filtering needed
        val maskBounds = if (trackUnmask) {
            boundsAccumulator.filterValues { it != MaskDecision.UNMASK }.keys
        } else {
            boundsAccumulator.keys
        }

        return SubviewSummary(boundsSnapshot = maskBounds)
    }

    fun maskSensitiveViews(
        of: View?,
        inCanvas: Canvas?,
        bounds: Set<Rect>
    ) {
        if (of == null || inCanvas == null) return

        bounds.forEach { rect ->
            inCanvas.drawRect(rect, paint)
        }
    }
}
