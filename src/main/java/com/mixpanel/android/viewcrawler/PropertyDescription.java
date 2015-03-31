package com.mixpanel.android.viewcrawler;

/* package */ class PropertyDescription {
    public PropertyDescription(String name, Class<?> targetClass, PropertySetCaller accessor, String mutatorName) {
        this.name = name;
        this.targetClass = targetClass;
        this.accessor = accessor;

        mMutatorName = mutatorName;
    }

    public PropertySetCaller makeMutator(Object[] methodArgs)
        throws NoSuchMethodException {
        if (null == mMutatorName) {
            return null;
        }

        return new PropertySetCaller(this.targetClass, mMutatorName, methodArgs, Void.TYPE);
    }

    @Override
    public String toString() {
        return "[PropertyDescription " + name + "," + targetClass + ", " + accessor + "/" + mMutatorName + "]";
    }

    public final String name;
    public final Class<?> targetClass;
    public final PropertySetCaller accessor;

    private final String mMutatorName;
}
