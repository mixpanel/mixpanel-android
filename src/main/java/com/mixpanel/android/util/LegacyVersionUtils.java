package com.mixpanel.android.util;

import java.io.File;

public class LegacyVersionUtils {
    /**
     * Remove the residual image files produced from legacy SDK versions 5.x and older (from Messages and Experiments features)
     *
     * @param fileOrDirectory
     */
    public static void removeLegacyResidualImageFiles(File fileOrDirectory) {
        try {
            if (fileOrDirectory.isDirectory()) {
                File[] files = fileOrDirectory.listFiles();
                if (files != null) {
                    for (File child : files) {
                        removeLegacyResidualImageFiles(child);
                    }
                }
            }
            if (fileOrDirectory.getName().contains(DEFAULT_DIRECTORY_PREFIX) || fileOrDirectory.getName().contains(FILE_PREFIX)) {
                fileOrDirectory.delete();
            }
        }
        catch(Exception e) {}
    }

    private static final String FILE_PREFIX = "MP_IMG_";
    private static final String DEFAULT_DIRECTORY_PREFIX = "MixpanelAPI.Images.";
}
