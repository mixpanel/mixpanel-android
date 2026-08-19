package com.mixpanel.android.autocapture;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The semantics of a tapped Jetpack Compose element, handed to a
 * {@link ComposeElementIdExtractor} so it can decide what {@code $el_id} to report.
 *
 * <p>A Compose element is a semantics node rather than an {@link android.view.View}, so there is no
 * view to inspect. This class carries the node's semantics as plain values instead, which also keeps
 * the public API free of Compose types — apps that don't use Compose never load a Compose class
 * because of it.
 *
 * <p>Instances are created by the SDK only. Fields may be added in future releases, so treat this as
 * read-only input.
 */
public final class ComposeElementInfo {

    @Nullable private final String mTestTag;
    @Nullable private final String mContentDescription;
    @Nullable private final String mRole;
    @NonNull private final String mTagName;
    @NonNull private final String mAnonymousId;

    ComposeElementInfo(
            @Nullable String testTag,
            @Nullable String contentDescription,
            @Nullable String role,
            @NonNull String tagName,
            @NonNull String anonymousId) {
        mTestTag = testTag;
        mContentDescription = contentDescription;
        mRole = role;
        mTagName = tagName;
        mAnonymousId = anonymousId;
    }

    /**
     * The value of {@code Modifier.testTag("...")}, or {@code null} when the element has none.
     *
     * <p>The Compose analogue of an Android resource id: developer-assigned and never shown to the
     * user, which makes it the safest identifier to report.
     */
    @Nullable
    public String getTestTag() {
        return mTestTag;
    }

    /**
     * The value of {@code semantics { contentDescription = "..." }}, or {@code null} when unset.
     *
     * <p>This is user-facing accessibility text. It can carry personal data when it is derived from
     * screen content, so prefer {@link #getTestTag()} when both are present.
     */
    @Nullable
    public String getContentDescription() {
        return mContentDescription;
    }

    /**
     * The element's semantic role — e.g. {@code "Button"}, {@code "Checkbox"}, {@code "Tab"} — or
     * {@code null} when Compose reports none.
     */
    @Nullable
    public String getRole() {
        return mRole;
    }

    /**
     * The element's tag name as reported in {@code $el_tag_name}, e.g. {@code "Button"} or
     * {@code "Text"}. Never null.
     */
    @NonNull
    public String getTagName() {
        return mTagName;
    }

    /**
     * The anonymous, PII-free identifier the SDK falls back to: {@code <TagName>_<hash>}.
     *
     * <p>Returning this from
     * {@link ComposeElementIdExtractor#extractElementId(ComposeElementInfo)} is equivalent to
     * returning {@code null} — both report an identifier that carries no user data.
     */
    @NonNull
    public String getAnonymousId() {
        return mAnonymousId;
    }

    @Override
    @NonNull
    public String toString() {
        return "ComposeElementInfo{testTag=" + mTestTag
                + ", contentDescription=" + mContentDescription
                + ", role=" + mRole
                + ", tagName=" + mTagName + "}";
    }
}
