package com.mixpanel.android.abtesting;

import android.view.View;
import android.view.ViewGroup;

import java.util.Iterator;

public class ViewTraversal implements Iterator<View> {
    public ViewTraversal(View v) {
        mHead = v;
        if (v instanceof ViewGroup) {
            mGroup = (ViewGroup) v;
        } else {
            mGroup = null;
        }
        mChildTraversalAt = -1;
        mChildTraversal = null;
    }

    @Override
    public boolean hasNext() {
        if (mChildTraversalAt == -1) {
            return true;
        } else if (null != mChildTraversal && mChildTraversal.hasNext()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public View next() {
        View ret = null;
        if (mChildTraversalAt == -1) {
            ret = mHead;
            mChildTraversalAt = 0;
            if (null != mGroup && mChildTraversalAt < mGroup.getChildCount()) {
                mChildTraversal = new ViewTraversal(mGroup.getChildAt(mChildTraversalAt));
            }
        } else if (null != mChildTraversal) {
            ret = mChildTraversal.next();
            if (! mChildTraversal.hasNext()) {
                mChildTraversalAt++;
                if (mChildTraversalAt < mGroup.getChildCount()) {
                    mChildTraversal = new ViewTraversal(mGroup.getChildAt(mChildTraversalAt));
                } else {
                    mChildTraversal = null;
                }
            }
        }

        return ret;
    }

    @Override
    public void remove() {
        throw new RuntimeException("Remove not supported.");
    }

    private final View mHead;
    private final ViewGroup mGroup;
    private int mChildTraversalAt;
    private Iterator<View> mChildTraversal;
}