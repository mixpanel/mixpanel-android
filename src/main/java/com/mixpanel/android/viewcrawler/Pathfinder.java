package com.mixpanel.android.viewcrawler;

import android.view.View;
import android.view.ViewGroup;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Paths in the view hierarchy, and the machinery for finding views using them.
 *
 * An individual pathfinder is NOT THREAD SAFE, and should only be used by one thread at a time.
 */
/* package */ class Pathfinder {

    public static class PathElement {
        public PathElement(int usePrefix, String vClass, int ix, int vId, String cDesc, String vTag) {
            prefix = usePrefix;
            viewClassName = vClass;
            index = ix;
            viewId = vId;
            contentDescription = cDesc;
            tag = vTag;
        }

        public String toString() {
            try {
                final JSONObject ret = new JSONObject();
                if (prefix == SHORTEST_PREFIX) {
                    ret.put("prefix", "**");
                }
                if (null != viewClassName) {
                    ret.put("view_class", viewClassName);
                }
                if (index > -1) {
                    ret.put("index", index);
                }
                if (viewId > -1) {
                    ret.put("id", viewId);
                }
                if (null != contentDescription) {
                    ret.put("contentDescription", contentDescription);
                }
                if (null != tag) {
                    ret.put("tag", tag);
                }
                return ret.toString();
            } catch (JSONException e) {
                throw new RuntimeException("Can't serialize PathElement to String", e);
            }
        }

        public final int prefix;
        public final String viewClassName;
        public final int index;
        public final int viewId;
        public final String contentDescription;
        public final String tag;

        public static final int ZERO_LENGTH_PREFIX = 0;
        public static final int SHORTEST_PREFIX = 1;
    }

    public interface Accumulator {
        public void accumulate(View v);
    }

    public Pathfinder() {
        mIndexStack = new IntStack();
    }

    public void findTargetsInRoot(View givenRootView, List<PathElement> path, Accumulator accumulator) {
        if (path.isEmpty()) {
            return;
        }

        if (mIndexStack.full()) {
            return; // No memory to perform the find.
        }

        final PathElement rootPathElement = path.get(0);
        final List<PathElement> childPath = path.subList(1, path.size());

        int indexKey = mIndexStack.alloc();
        final View rootView = findPrefixedMatch(rootPathElement, givenRootView, indexKey);
        mIndexStack.free();

        if (null != rootView) {
            findTargetsInMatchedView(rootView, childPath, accumulator);
        }
    }


    private void findTargetsInMatchedView(View alreadyMatched, List<PathElement> remainingPath, Accumulator accumulator) {
        // When this is run, alreadyMatched has already been matched to a path prefix.
        // path is a possibly empty "remaining path" suffix left over after the match

        if (remainingPath.isEmpty()) {
            // Nothing left to match- we're found!
            accumulator.accumulate(alreadyMatched);
            return;
        }

        if (!(alreadyMatched instanceof ViewGroup)) {
            // Matching a non-empty path suffix is impossible, because we have no children
            return;
        }

        if (mIndexStack.full()) {
            // Can't match anyhow, stack is too deep
            return;
        }

        final ViewGroup parent = (ViewGroup) alreadyMatched;
        final PathElement matchElement = remainingPath.get(0);
        final List<PathElement> nextPath = remainingPath.subList(1, remainingPath.size());

        final int childCount = parent.getChildCount();
        int indexKey = mIndexStack.alloc();
        for (int i = 0; i < childCount; i++) {
            final View givenChild = parent.getChildAt(i);
            final View child = findPrefixedMatch(matchElement, givenChild, indexKey);
            if (null != child) {
                findTargetsInMatchedView(child, nextPath, accumulator);
            }
            if (matchElement.index >= 0 && mIndexStack.read(indexKey) > matchElement.index) {
                return;
            }
        }
        mIndexStack.free();
    }

    // Finds the first matching view of the path element in the given subject's view hierarchy.
    // If the path is indexed, it needs a start index, and will consume some indexes
    private View findPrefixedMatch(PathElement findElement, View subject, int indexKey) {
        int currentIndex = mIndexStack.read(indexKey);
        if (matches(findElement, subject)) {
            mIndexStack.increment(indexKey);
            if (findElement.index == -1 || findElement.index == currentIndex) {
                return subject;
            }
        }

        if (findElement.prefix == PathElement.SHORTEST_PREFIX && subject instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) subject;
            final int childCount = group.getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = group.getChildAt(i);
                final View result = findPrefixedMatch(findElement, child, indexKey);
                if (null != result) {
                    return result;
                }
            }
        }

        return null;
    }


    private boolean matches(PathElement matchElement, View subject) {
        if (null != matchElement.viewClassName) {
            if (!hasClassName(subject, matchElement.viewClassName)) {
                return false;
            }
        }

        final int matchId = matchElement.viewId;
        if (-1 != matchId) {
            final int subjectId = subject.getId();
            if (subjectId != matchId) {
                return false;
            }
        }

        final String matchContentDescription = matchElement.contentDescription;
        if (null != matchContentDescription) {
            final CharSequence description = subject.getContentDescription();
            if (null == description || ! matchContentDescription.equals(description)) {
                return false;
            }
        }

        final String matchTag = matchElement.tag;
        if (null != matchTag) {
            final Object subjectTag = subject.getTag();
            if (null == subjectTag || ! matchTag.equals(subjectTag.toString())) {
                return false;
            }
        }

        return true;
    }

    private static boolean hasClassName(Object o, String className) {
        Class klass = o.getClass();
        while (true) {
            if (klass.getCanonicalName().equals(className)) {
                return true;
            }

            if (klass == Object.class) {
                return false;
            }

            klass = klass.getSuperclass();
        }
    }

    /**
     * Bargain-bin pool of integers, for use in avoiding allocations during path crawl
     */
    private static class IntStack {
        public IntStack() {
            mStack = new int[MAX_INDEX_STACK_SIZE];
            mStackSize = 0;
        }

        public boolean full() {
            return mStack.length == mStackSize;
        }

        /**
         * Pushes a new value, and returns the index you can use to increment and read that value later.
         */
        public int alloc() {
            int index = mStackSize;
            mStackSize++;
            mStack[index] = 0;
            return index;
        }

        /**
         * Gets the value associated with index. index should be the result of a previous call to alloc()
         */
        public int read(int index) {
            return mStack[index];
        }

        public void increment(int index) {
            mStack[index]++;
        }

        /**
         * Should be matched to each call to alloc. Once free has been called, the key associated with the
         * matching alloc should be considered invalid.
         */
        public void free() {
            mStackSize--;
            if (mStackSize < 0) {
                throw new ArrayIndexOutOfBoundsException(mStackSize);
            }
        }

        private final int[] mStack;
        private int mStackSize;

        private static final int MAX_INDEX_STACK_SIZE = 256;
    }

    private final IntStack mIndexStack;
}
