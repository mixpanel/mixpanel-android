package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.util.Log;
import android.util.SparseArray;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ResourceReader implements ResourceIds {
    private ResourceReader(Context context) {
        mContext = context;
        mIdNameToId = new HashMap<String, Integer>();
        mIdToIdName = new SparseArray<String>();
        buildIdMap();
    }

    public static ResourceReader getInstance(Context context) {
        final Context appContext = context.getApplicationContext();
        synchronized (sInstanceMap) {
            if (! sInstanceMap.containsKey(appContext)) {
                sInstanceMap.put(appContext, new ResourceReader(appContext));
            }

            return sInstanceMap.get(appContext);
        }
    }

    @Override
    public boolean knownIdName(String name) {
        return mIdNameToId.containsKey(name);
    }

    @Override
    public int idFromName(String name) {
        return mIdNameToId.get(name);
    }

    @Override
    public String nameForId(int id) {
        return mIdToIdName.get(id);
    }

    private void buildIdMap() {
        MPConfig config = MPConfig.getInstance(mContext.getApplicationContext());

        mIdNameToId.clear();
        mIdToIdName.clear();

        try {
            final Class platformIdClass = android.R.id.class;
            final Field[] fields = platformIdClass.getFields();
            for (int i = 0; i < fields.length; i++) {
                final Field field = fields[i];
                final int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    final Class fieldType = field.getType();
                    if (fieldType == int.class) {
                        final String name = field.getName();
                        final int value = field.getInt(null);
                        final String namespacedName = "android:" + name;
                        mIdNameToId.put(namespacedName, value);
                        mIdToIdName.put(value, namespacedName);
                    }
                }
            }
        } catch (IllegalAccessException e) {
            Log.e(LOGTAG, "Can't read built-in id names from platform library", e);
        }

        // mContext.getPackageName() actually returns the "application id", which
        // usually (but not always) the same as package of the generated R class.
        //
        //  See: http://tools.android.com/tech-docs/new-build-system/applicationid-vs-packagename
        //
        // As far as I can tell, the original package name is lost in the build
        // process in these cases, and must be specified by the developer using
        // MPConfig meta-data.
        String resourcePackage = config.getResourcePackageName();
        if (null == resourcePackage) {
            resourcePackage = mContext.getPackageName();
        }

        final String rIdClassName = resourcePackage + ".R$id";

        try {
            final Class rIdClass = Class.forName(rIdClassName);
            final Field[] fields = rIdClass.getFields();
            for (int i = 0; i < fields.length; i++) {
                final Field field = fields[i];
                final int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    final Class fieldType = field.getType();
                    if (fieldType == int.class) {
                        final String name = field.getName();
                        final int value = field.getInt(null);
                        mIdNameToId.put(name, value);
                        mIdToIdName.put(value, name);
                    }
                }
            }// for fields
        } catch (ClassNotFoundException e) {
            Log.w(LOGTAG, "Can't load names for Android view ids from class " + rIdClassName + ", ids by name will not be available in the events editor.");
            Log.i(LOGTAG,
                    "You may be missing a Resources class for your package due to your proguard configuration, " +
                            "or you may be using an applicationId in your build that isn't the same as the package declared in your AndroidManifest.xml file.\n" +
                            "If you're using proguard, you can fix this issue by adding the following to your proguard configuration:\n\n" +
                            "-keep class **.R$* {\n" +
                            "    <fields>;\n" +
                            "}\n\n" +
                            "If you're not using proguard, or if your proguard configuration already contains the directive above, " +
                            "you can add the following to your AndroidManifest.xml file to explicitly point the Mixpanel library to " +
                            "the appropriate library for your resources class:\n\n" +
                            "<meta-data android:name=\"com.mixpanel.android.MPConfig.ResourcePackageName\" android:value=\"YOUR_PACKAGE_NAME\" />\n\n" +
                            "where YOUR_PACKAGE_NAME is the same string you use for the \"package\" attribute in your <manifest> tag."
            );
        } catch (IllegalAccessException e) {
            Log.e(LOGTAG, "Can't read id names for local resources");
        }
    }

    private final Context mContext;
    private final Map<String, Integer> mIdNameToId;
    private final SparseArray<String> mIdToIdName;

    private static final Map<Context, ResourceReader> sInstanceMap = new HashMap<Context, ResourceReader>();

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.ResourceReader";


}
