package com.mixpanel.android.abtesting;

import android.test.AndroidTestCase;
import android.view.View;

import java.util.HashSet;
import java.util.Set;

public class ViewTraversalTest extends AndroidTestCase {
    public void setUp() {
        mView = new TestView(getContext());
    }

    public void testTraverse() {
        ViewTraversal traversal = new ViewTraversal(mView);

        final Set foundViews = new HashSet();
        while (traversal.hasNext()) {
            foundViews.add(traversal.next());
        }

        assertEquals(mView.mAllViews, foundViews);
    }

    private TestView mView;
}
