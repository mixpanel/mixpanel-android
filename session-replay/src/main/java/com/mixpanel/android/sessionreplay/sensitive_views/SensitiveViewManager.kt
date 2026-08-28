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
import androidx.annotation.RestrictTo
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import android.widget.Button
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.mixpanel.android.sessionreplay.extensions.SensitiveViewNode
import com.mixpanel.android.sessionreplay.extensions.mpReplaySensitivePropKey
import com.mixpanel.android.sessionreplay.extensions.mpReplayWireframeTextPropKey
import com.mixpanel.android.sessionreplay.wireframe.WireframeElement
import com.mixpanel.android.sessionreplay.wireframe.WireframeType
import com.mixpanel.android.sessionreplay.wireframe.MaskDecision
import java.util.Collections
import java.util.WeakHashMap
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
internal enum class InternalMaskDecision {
    NONE, // No masking applied
    UNMASK, // Explicitly marked as safe via addSafeView
    AUTO, // Auto-masked based on AutoMaskedView (text, images, web views)
    MASK, // Explicitly marked sensitive by developer (addSensitiveView, mpReplaySensitive(true), addSensitiveClass)
    TEXT_ENTRY; // Text entry fields (EditText) - security enforced, cannot be overridden

    /** Maps this internal decision onto the wireframe-facing [MaskDecision]. */
    fun toWire(): MaskDecision = when (this) {
        TEXT_ENTRY -> MaskDecision.TEXT_ENTRY
        MASK -> MaskDecision.EXPLICIT
        AUTO -> MaskDecision.AUTO
        UNMASK, NONE -> MaskDecision.NONE
    }
}

/**
 * Listener interface for receiving mask region updates.
 */
internal fun interface MaskRegionsListener {
    /**
     * Called when mask regions have been detected.
     * @param entries Map of bounds to mask decision type
     */
    fun onMaskRegionsDetected(entries: Map<Rect, InternalMaskDecision>)
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

    // Developer-declared wireframe text set via View.mpWireframeText(...). Weak keys
    // so annotating a view never prevents it from being garbage collected (matching the
    // identity-map idiom used elsewhere here rather than View.setTag). Declared text is
    // authored, not scraped, so it is emitted even when the view is masked — see
    // MaskDecision.DECLARED.
    private val declaredWireframeText = Collections.synchronizedMap(WeakHashMap<View, String>())

    // Use concurrent hash set for faster lookups and better thread safety
    private val _sensitiveClasses =
        Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>()).apply {
            add(EditText::class.java)
        }

    // Classes the developer registered through addSensitiveClass, tracked separately from the
    // AutoMaskedView classes that also live in _sensitiveClasses. Masking treats both the same,
    // but the ERD reports a customer-registered class as EXPLICIT and an AutoMaskedView class as
    // AUTO, so the two have to stay distinguishable. Also lets updateSensitiveClasses avoid
    // dropping a class the developer asked for when the matching AutoMaskedView is turned off.
    private val _customerSensitiveClasses =
        Collections.newSetFromMap(ConcurrentHashMap<Class<*>, Boolean>())

    // Cache for view class sensitivity checks to avoid repeated isAssignableFrom calls
    private val viewClassSensitivityCache = ConcurrentHashMap<Class<*>, Boolean>()

    // Same, for the "was this a customer-registered class?" question
    private val customerClassSensitivityCache = ConcurrentHashMap<Class<*>, Boolean>()

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
     *
     * `@RestrictTo` rather than `internal` so the off-device coordinate goldens in
     * `:session-replay:wireframe-goldens` can reset this singleton between cases from their own
     * Gradle module. Not public API — see [com.mixpanel.android.sessionreplay.wireframe.WireframeEmitter].
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun deinitialize() {
        sensitiveNodes.clear()
        safeNodes.clear()
        sensitiveViews.clear()
        safeViews.clear()
        declaredWireframeText.clear()
        // Registered classes go too: a new initialization is a new initialization, so the
        // incoming config decides what is masked and nothing survives from the last one.
        //
        // These used to persist, on the reasoning that masking a class is a standing
        // instruction outliving a session — but `addSensitiveView` and `mpWireframeText` are
        // equally standing instructions and are cleared three lines above, and iOS already
        // clears its equivalent by replacing the whole manager in `deinitializeInstance`. The
        // visible cost of keeping them was that a narrowed `autoMaskedViews` could not take
        // effect on re-initialize: React Native implements auto-masking *through*
        // `addSensitiveClass`, so the first initialize's registrations kept masking for the
        // life of the process and `syncAutoMaskedClass` refuses to drop a customer-registered
        // class, leaving no way to undo it.
        //
        // `EditText` is deliberately kept. It is not a developer registration but the
        // always-masked text-entry guarantee, seeded at construction and refused by
        // `removeSensitiveClass`; dropping it here would silently unmask every input until the
        // next `autoMaskedViews` assignment re-seeded it.
        _sensitiveClasses.retainAll(setOf(EditText::class.java))
        _customerSensitiveClasses.clear()
        viewClassSensitivityCache.clear()
        customerClassSensitivityCache.clear()
        autoMaskedViews = AutoMaskedView.defaultSet()
        useAccessibilityLabelFallback = false
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
    private fun collectNodeBounds(accumulator: MutableMap<Rect, InternalMaskDecision>) {
        synchronized(sensitiveNodes) {
            for (node in sensitiveNodes) {
                node.getCurrentBounds()?.let { bounds ->
                    addOrUpdateEntry(accumulator, bounds, InternalMaskDecision.MASK)
                }
            }
        }
        // Only track unmask regions when debug overlay is enabled
        if (trackUnmask) {
            synchronized(safeNodes) {
                for (node in safeNodes) {
                    node.getCurrentBounds()?.let { bounds ->
                        addOrUpdateEntry(accumulator, bounds, InternalMaskDecision.UNMASK)
                    }
                }
            }
        }
    }

    /**
     * Adds or updates a mask entry, keeping the highest priority type for each bounds.
     */
    private fun addOrUpdateEntry(
        accumulator: MutableMap<Rect, InternalMaskDecision>,
        bounds: Rect,
        newType: InternalMaskDecision
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

    /**
     * Mirrors [com.mixpanel.android.sessionreplay.models.WireframesOptions.useAccessibilityLabelFallback],
     * set from `MPSessionReplayInstance` at init and reset by [deinitialize].
     *
     * Wireframe text only — masking never consults it, so turning it off changes what a
     * wireframe says, never which pixels are grayed.
     *
     * `@RestrictTo` rather than `internal` so the `*_fallbackOff_*` goldens in
     * `:session-replay:wireframe-goldens` can flip it. Not public API — customers set it through
     * [com.mixpanel.android.sessionreplay.models.WireframesOptions].
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    var useAccessibilityLabelFallback: Boolean = false

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
        syncAutoMaskedClass(AutoMaskedView.Text, TextView::class.java)
        syncAutoMaskedClass(AutoMaskedView.Image, ImageView::class.java)
        syncAutoMaskedClass(AutoMaskedView.Web, WebView::class.java)

        // Clear the cache when sensitive classes change
        viewClassSensitivityCache.clear()
    }

    /**
     * Adds [aClass] to [_sensitiveClasses] while [autoMaskedView] is enabled and removes it when
     * it isn't — unless the developer registered the same class through `addSensitiveClass`, in
     * which case their registration outlives the auto-mask toggle.
     */
    private fun syncAutoMaskedClass(autoMaskedView: AutoMaskedView, aClass: Class<*>) {
        if (autoMaskedView in autoMaskedViews) {
            _sensitiveClasses.add(aClass)
        } else if (aClass !in _customerSensitiveClasses) {
            _sensitiveClasses.remove(aClass)
        }
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

    /**
     * Whether [viewClass] matches a class the developer registered via `addSensitiveClass`, as
     * opposed to one of the [AutoMaskedView] classes. Both mask identically; they differ only in
     * the [InternalMaskDecision] reported to the wireframe and the debug overlay.
     */
    private fun isViewClassCustomerSensitive(viewClass: Class<*>): Boolean {
        customerClassSensitivityCache[viewClass]?.let { return it }

        val isSensitive =
            _customerSensitiveClasses.any { sensitiveClass ->
                sensitiveClass.isAssignableFrom(viewClass)
            }
        customerClassSensitivityCache[viewClass] = isSensitive
        return isSensitive
    }

    fun addSensitiveView(view: View) = sensitiveViews.add(view)

    fun removeSensitiveView(view: View) = sensitiveViews.remove(view)

    private fun containsSensitiveView(view: View): Boolean = sensitiveViews.contains(view)

    fun addSafeView(view: View) = safeViews.add(view)

    fun removeSafeView(view: View) = safeViews.remove(view)

    private fun containsSafeView(view: View): Boolean = safeViews.contains(view)

    /**
     * Records (or, when [text] is null/blank, clears) developer-declared wireframe text for
     * [view]. Called by `View.mpWireframeText(...)`. The text is authored by the
     * developer, not scraped from the view, so it survives masking in the wireframe.
     */
    internal fun setWireframeText(view: View, text: String?) {
        if (text.isNullOrBlank()) {
            declaredWireframeText.remove(view)
        } else {
            declaredWireframeText[view] = text
        }
    }

    private fun wireframeTextFor(view: View): String? =
        declaredWireframeText[view]?.takeIf { it.isNotBlank() }

    /**
     * Masks every view that is an instance of [aClass].
     *
     * **Registrations do not survive re-initialization.** `MPSessionReplay.initialize`
     * deinitializes any previous instance first, and that clears registered classes so the
     * incoming config decides what is masked. Register again after re-initializing — the same
     * rule iOS has always had, where the manager is replaced outright on deinitialize.
     */
    fun addSensitiveClass(aClass: Class<*>?) {
        aClass?.let {
            _sensitiveClasses.add(it)
            _customerSensitiveClasses.add(it)
            // Clear the caches when sensitive classes change
            viewClassSensitivityCache.clear()
            customerClassSensitivityCache.clear()
        }
    }

    fun removeSensitiveClass(aClass: Class<*>?) {
        aClass?.let {
            if (it != EditText::class.java) {
                _sensitiveClasses.remove(it)
                _customerSensitiveClasses.remove(it)
                // Clear the caches when sensitive classes change
                viewClassSensitivityCache.clear()
                customerClassSensitivityCache.clear()
            }
        }
    }

    /**
     * Collects maskable Compose nodes by traversing Compose semantics trees.
     */
    private fun collectMaskableNodes(
        rootView: View,
        boundsAccumulator: MutableMap<Rect, InternalMaskDecision>,
        processedSemanticsOwners: MutableSet<Int>,
        wireframeOut: MutableList<WireframeElement>? = null
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
            traverseSemanticsNode(
                node = root.semanticsOwner.rootSemanticsNode,
                boundsAccumulator = boundsAccumulator,
                wireframeOut = wireframeOut
            )
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

    // The node's window bounds, or null when it occupies no pixels.
    private fun SemanticsNode.visibleBounds(): Rect? {
        // Skip nodes not placed in layout (e.g. LazyColumn items outside the viewport buffer)
        if (!layoutInfo.isPlaced) return null

        // Skip nodes clipped by scroll containers (boundsInRoot has clipping applied)
        if (boundsInRoot.isEmpty) return null

        // Skip nodes with no visible area in the window
        val bounds = boundsInWindow
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

    private data class ViewContext(
        val view: View,
        val isInsideSafeContainer: Boolean
    )

    private fun traverseSemanticsNode(
        node: SemanticsNode,
        boundsAccumulator: MutableMap<Rect, InternalMaskDecision>,
        wireframeOut: MutableList<WireframeElement>? = null,
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
                isInputField -> InternalMaskDecision.TEXT_ENTRY

                config.isSensitiveView() == true -> InternalMaskDecision.MASK

                isSafe -> {
                    if (trackUnmask) InternalMaskDecision.UNMASK else InternalMaskDecision.NONE
                }

                (AutoMaskedView.Text in autoMaskedViews) && config.hasText() -> InternalMaskDecision.AUTO

                (AutoMaskedView.Image in autoMaskedViews) && config.hasImage() -> InternalMaskDecision.AUTO

                (AutoMaskedView.Web in autoMaskedViews) && config.hasWebView() -> InternalMaskDecision.AUTO

                else -> InternalMaskDecision.NONE
            }

        // boundsInWindow resolves the node's position by walking its layout-coordinate chain, so
        // it's the priciest call in this traversal and both consumers below want the same rect.
        // Resolve it once, and only when something actually consumes it.
        val bounds = if (maskDecision != InternalMaskDecision.NONE || wireframeOut != null) {
            node.visibleBounds()
        } else {
            null
        }

        if (bounds != null) {
            if (maskDecision != InternalMaskDecision.NONE) {
                addOrUpdateEntry(boundsAccumulator, bounds, maskDecision)
            }

            if (wireframeOut != null) {
                collectWireframeForNode(node, bounds, isInputField, maskDecision, wireframeOut)
            }
        }

        for (child in node.children) {
            traverseSemanticsNode(child, boundsAccumulator, wireframeOut, isSafe)
        }
    }

    /**
     * Appends a [WireframeElement] for the given semantics node if the node carries content
     * worth recording (text, content description, image role, input field).
     */
    private fun collectWireframeForNode(
        node: SemanticsNode,
        rect: Rect,
        isInputField: Boolean,
        maskDecision: InternalMaskDecision,
        wireframeOut: MutableList<WireframeElement>
    ) {
        val config = node.config

        // Detect text-bearing nodes from the raw semantics BEFORE applying the masking filter.
        // Otherwise a masked Text composable would lose its `visibleText`, fail the text-type
        // check below, and get dropped from the wireframe entirely — instead we want to keep
        // the element (with redacted text) so the layout is still visible to consumers.
        val rawText = config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        // Gated at the read so the label can't reach classification either: a node whose only
        // content is a label is a node we only know is content *because of* the label, so with
        // the fallback off it falls through to `else -> return` rather than becoming an empty
        // text shell. Nodes that carry a role (an Icon, an image button) still emit textless.
        val rawContentDesc = if (useAccessibilityLabelFallback) {
            config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        } else {
            null
        }
        val hasTextContent = !rawText.isNullOrBlank() || !rawContentDesc.isNullOrBlank()

        // Developer-declared text via Modifier.mpWireframeText(...). Authored, not
        // scraped from the node's semantics.
        val declaredText = config.getOrNull(mpReplayWireframeTextPropKey)?.takeIf { it.isNotBlank() }

        val role = config.getOrNull(SemanticsProperties.Role)
        val type: WireframeType = when {
            isInputField -> WireframeType.Input
            role == Role.Image -> WireframeType.Image
            role == Role.Button -> WireframeType.Button
            hasTextContent -> WireframeType.Text
            declaredText != null -> WireframeType.Text
            else -> return // skip nodes with no meaningful content
        }

        if (declaredText != null) {
            // Layer 3 substitution. Emitted even when the node is masked; DECLARED exempts it
            // from the Layer 2 geometric strip so it survives to describe the view for the AI
            // summary. Masking still grays the pixels via the mask region recorded by
            // traverseSemanticsNode.
            wireframeOut.add(
                WireframeElement.fromRect(type, declaredText, rect, MaskDecision.DECLARED)
            )
            return
        }

        // Suppress text on any form of masking. Leaking text that's been masked in the
        // screenshot would defeat the privacy guarantee. Only NONE (unmonitored) and
        // UNMASK (explicitly safe) decisions pass text through.
        val isMasked = maskDecision != InternalMaskDecision.NONE && maskDecision != InternalMaskDecision.UNMASK
        val visibleText: String? = if (isMasked) {
            null
        } else {
            rawText?.takeIf { it.isNotBlank() }
                ?: rawContentDesc?.takeIf { it.isNotBlank() }
        }

        wireframeOut.add(WireframeElement.fromRect(type, visibleText, rect, maskDecision.toWire()))
    }

    private fun classifyAndroidView(view: View): WireframeType? = when {
        view is EditText -> WireframeType.Input
        view is ImageView -> WireframeType.Image
        view is Button -> WireframeType.Button
        view is TextView -> WireframeType.Text
        // Last: a role the developer declared through accessibility. Intent, not inference, and
        // the only signal available for a control that is a plain view — which is what React
        // Native's `Pressable`/`TouchableOpacity` produce.
        else -> accessibilityRole(view)
    }

    /**
     * Role implied by a view's declared accessibility role, or `null` if it declares none we
     * honor.
     *
     * React Native stores `accessibilityRole` as an `AccessibilityRole` **enum** in a view tag,
     * so what is read here is an enum name and never developer-authored text — nothing can reach
     * the `role` field that would bypass masking. See [WireframeType].
     *
     * Android can distinguish the full set; iOS collapses most of these to
     * `UIAccessibilityTraitNone` and reports only button/link/header. That asymmetry is
     * deliberate: reporting what a platform can actually see beats reporting the intersection.
     */
    private fun accessibilityRole(view: View): WireframeType? {
        val tagId = reactRoleTagId(view)
        if (tagId == 0) return null
        val role = runCatching { view.getTag(tagId) }.getOrNull() ?: return null
        return when (role.toString().uppercase()) {
            "BUTTON", "IMAGEBUTTON", "TOGGLEBUTTON" -> WireframeType.Button
            "LINK" -> WireframeType.Link
            "HEADER", "HEADING" -> WireframeType.Header
            "CHECKBOX" -> WireframeType.Checkbox
            "SWITCH" -> WireframeType.Switch
            "RADIO" -> WireframeType.Radio
            "TAB" -> WireframeType.Tab
            // Every other role in React Native's enum is deliberately unmapped: an allowlist,
            // so a new upstream value can never appear in the payload unreviewed.
            else -> null
        }
    }

    /**
     * Id of React Native's `accessibility_role` view tag, resolved once and cached.
     *
     * Looked up by name rather than referenced directly because this module does not depend on
     * React Native — and `0`, meaning "this is not a React Native app", is the common case and
     * is cached just as firmly. `getIdentifier` walks the resource table by string, which is far
     * too expensive to repeat for every unclassified view on every captured frame.
     */
    @Volatile
    private var cachedReactRoleTagId: Int = -1

    private fun reactRoleTagId(view: View): Int {
        val cached = cachedReactRoleTagId
        if (cached != -1) return cached
        val resolved = runCatching {
            val packageName = view.context?.packageName ?: return@runCatching 0
            view.resources?.getIdentifier("accessibility_role", "id", packageName) ?: 0
        }.getOrDefault(0)
        cachedReactRoleTagId = resolved
        return resolved
    }

    // No text absorption here, deliberately — unlike iOS.
    //
    // A roled container on Android does *not* become a wireframe leaf: the walk keeps emitting
    // its children, so a `<Pressable accessibilityRole="button">` wrapping a `<Text>` already
    // ships both a `button` element and the `text` element carrying the label. Borrowing the
    // descendants' text into the parent as well produced the label *twice*, which for a summary
    // is worse than either shape alone.
    //
    // iOS *does* close the subtree once a role is emitted (`newInsideLeaf = role != nil`), so it
    // absorbs there or the control would ship textless. The two platforms therefore describe the
    // same markup differently — one element carrying the label on iOS, a roled shell plus a text
    // element on Android. A capability difference, not a defect.

    private fun extractAndroidViewText(view: View, wasMasked: Boolean): String? {
        // Never return text that the screenshot has redacted — would defeat masking.
        if (wasMasked) return null
        val text = when (view) {
            is TextView -> view.text?.toString()
            else -> null
        }
        if (!text.isNullOrBlank()) return text
        if (!useAccessibilityLabelFallback) return null
        return view.contentDescription?.toString()?.takeIf { it.isNotBlank() }
    }

    /**
     * Processes subviews to detect sensitive content that needs masking.
     * Returns a SubviewSummary containing masking info and a snapshot of bounds for comparison.
     *
     * @param view The root view to process
     */
    fun processSubviews(view: View?): SubviewSummary = processSubviews(view, null)

    /**
     * Same as [processSubviews] but additionally appends a [WireframeElement] for every
     * visible, content-bearing view (Android or Compose) into [wireframeOut].
     *
     * Pass `null` for [wireframeOut] to skip wireframe collection (the existing one-arg
     * overload does this).
     */
    fun processSubviews(view: View?, wireframeOut: MutableList<WireframeElement>?): SubviewSummary {
        if (view == null) return SubviewSummary()

        val boundsAccumulator = mutableMapOf<Rect, InternalMaskDecision>()
        val viewsToProcess = ArrayDeque<ViewContext>()

        // Track processed SemanticsOwners to avoid duplicate Compose tree traversal
        val processedSemanticsOwners = HashSet<Int>()

        // Initialize with the root view, checking if it's marked as safe
        viewsToProcess.add(ViewContext(view, containsSafeView(view)))

        while (viewsToProcess.isNotEmpty()) {
            val (currentView, isInsideSafeContainer) = viewsToProcess.removeAt(0)

            // Skip views that aren't visible (including children of GONE/INVISIBLE parents)
            if (!currentView.isShown) continue

            // A fully transparent view paints nothing, so neither its pixels nor its text may
            // reach the replay — `isShown` only covers GONE/INVISIBLE. Alpha is multiplicative
            // down the hierarchy, so `continue` correctly drops the whole subtree: children are
            // only enqueued at the bottom of this loop. It also means such a view contributes no
            // mask region, which is right — there is nothing painted to cover.
            // Matches Flutter's `Opacity(0)` filter (reference suite fixture 44).
            if (currentView.alpha <= 0f) continue

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
            val isInputField = EditText::class.java.isAssignableFrom(currentView::class.java)

            // Resolved once and shared by the mask decision, the UNMASK entry, and the safe flag
            // propagated to children; the guard keeps plain leaf views out of the synchronized
            // set lookup, as before.
            val isSensitiveCandidate = isExplicitlySensitive || isClassSensitive
            val isSelfSafe = (isSensitiveCandidate || trackUnmask || currentView is ViewGroup) &&
                containsSafeView(currentView)
            val isSafe = isInsideSafeContainer || isSelfSafe

            // Explicitly sensitive, input fields, or non-safe class-sensitive views get masked
            val shouldMask = isSensitiveCandidate &&
                (isExplicitlySensitive || isInputField || !isSafe)

            // A safe view inside a sensitive one, or a standalone safe view, is worth an UNMASK
            // entry only when we're tracking them. Note this leans on isSelfSafe, not isSafe:
            // safety inherited from an ancestor doesn't earn its own entry.
            val wantsUnmaskEntry = trackUnmask && !shouldMask && (isSensitiveCandidate || isSelfSafe)

            // Wireframe classification resolved before the bounds lookup so both consumers can
            // share one rect.
            val declaredText = if (wireframeOut != null) wireframeTextFor(currentView) else null
            val wireframeType = if (wireframeOut != null) classifyAndroidView(currentView) else null
            val wantsWireframeElement = declaredText != null || wireframeType != null

            // getGlobalVisibleRect walks the whole parent chain intersecting clips, making it the
            // priciest per-view call in this loop — and with the default autoMaskedViews nearly
            // every content view needs it for both a mask entry and a wireframe element. Resolve
            // it at most once per view, and only when something actually consumes it.
            val visibleRect = if (shouldMask || wantsUnmaskEntry || wantsWireframeElement) {
                Rect().takeIf { currentView.getGlobalVisibleRect(it) }
            } else {
                null
            }

            // Mirrors the masking decision for this view so the wireframe path can suppress
            // text on anything that ends up masked (otherwise we'd leak the same text the
            // screenshot redacts).
            val wasMasked = shouldMask
            var wireDecision = MaskDecision.NONE

            if (shouldMask) {
                if (visibleRect != null) {
                    val maskDecision = when {
                        isInputField -> InternalMaskDecision.TEXT_ENTRY
                        isExplicitlySensitive -> InternalMaskDecision.MASK
                        // A class the developer registered via addSensitiveClass is EXPLICIT
                        // per the ERD's Layer 1 table, not AUTO — only the AutoMaskedView
                        // classes are AUTO. Reporting only; both mask the same pixels, and
                        // addSafeView still overrides a class match (see shouldMask above).
                        isViewClassCustomerSensitive(currentView::class.java) ->
                            InternalMaskDecision.MASK
                        else -> InternalMaskDecision.AUTO
                    }
                    addOrUpdateEntry(boundsAccumulator, Rect(visibleRect), maskDecision)
                    wireDecision = maskDecision.toWire()
                }
            } else if (wantsUnmaskEntry && visibleRect != null) {
                addOrUpdateEntry(boundsAccumulator, Rect(visibleRect), InternalMaskDecision.UNMASK)
            }

            if (wireframeOut != null && visibleRect != null &&
                visibleRect.width() > 0 && visibleRect.height() > 0
            ) {
                if (declaredText != null) {
                    // Layer 3 substitution. Developer-declared text
                    // (View.mpWireframeText(...)) is authored, not scraped. Emit it even
                    // when the view is masked, and even for views that don't map to one of the
                    // four roles (fall back to Text). Masking still grays the pixels via the mask
                    // region added above; DECLARED exempts the text from the Layer 2 geometric
                    // strip so it survives to describe the view for the AI summary.
                    wireframeOut.add(
                        WireframeElement.fromRect(
                            wireframeType ?: WireframeType.Text,
                            declaredText,
                            visibleRect,
                            MaskDecision.DECLARED
                        )
                    )
                } else if (wireframeType != null) {
                    // Keep the element so layout is visible; drop content when masked.
                    val text = extractAndroidViewText(currentView, wasMasked)
                    wireframeOut.add(
                        WireframeElement.fromRect(wireframeType, text, visibleRect, wireDecision)
                    )
                }
            }

            // Check for Compose content - either ComposeView or direct RootForTest (e.g., AndroidComposeView in ModalBottomSheet)
            val isComposeView = composeViewClass != null && currentView is ComposeView
            val isRootForTest = currentView is RootForTest

            if (isComposeView || isRootForTest) {
                collectMaskableNodes(currentView, boundsAccumulator, processedSemanticsOwners, wireframeOut)
            }

            // Then handle the ViewGroup case which happens regardless of jetpackComposeEnabled
            if (currentView is ViewGroup) {
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
            boundsAccumulator.filterValues { it != InternalMaskDecision.UNMASK }.keys
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
