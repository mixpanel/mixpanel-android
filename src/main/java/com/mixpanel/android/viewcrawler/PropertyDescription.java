package com.mixpanel.android.viewcrawler;

/**
* Created by joe on 8/29/14.
*/
/* package */ class PropertyDescription {
    public PropertyDescription(String name, Class targetClass, Caller accessor, String mutatorName) {
        this.name = name;
        this.targetClass = targetClass;
        this.accessor = accessor;

        mMutatorName = mutatorName;
    }

    public Caller makeMutator(Object[] methodArgs) {
        if (null == mMutatorName) {
            return null;
        }

        return new Caller(mMutatorName, methodArgs, Void.TYPE);
    }

    public final String name;
    public final Class targetClass;
    public final Caller accessor;

    private final String mMutatorName;
}
