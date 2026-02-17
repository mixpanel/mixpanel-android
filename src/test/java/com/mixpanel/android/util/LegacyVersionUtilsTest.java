package com.mixpanel.android.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class LegacyVersionUtilsTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testRemoveLegacyImageFiles() throws IOException {
        // Create files with legacy prefix
        File legacyImage = tempFolder.newFile("MP_IMG_test.png");
        File normalFile = tempFolder.newFile("normal_file.txt");

        assertTrue(legacyImage.exists());
        assertTrue(normalFile.exists());

        LegacyVersionUtils.removeLegacyResidualImageFiles(tempFolder.getRoot());

        assertFalse(legacyImage.exists());
        assertTrue(normalFile.exists());
    }

    @Test
    public void testRemoveLegacyDirectory() throws IOException {
        // Create directory with legacy prefix
        File legacyDir = tempFolder.newFolder("MixpanelAPI.Images.test");
        File fileInDir = new File(legacyDir, "MP_IMG_image.png");
        fileInDir.createNewFile();

        assertTrue(legacyDir.exists());
        assertTrue(fileInDir.exists());

        LegacyVersionUtils.removeLegacyResidualImageFiles(tempFolder.getRoot());

        assertFalse(fileInDir.exists());
        assertFalse(legacyDir.exists());
    }

    @Test
    public void testRemoveNestedLegacyFiles() throws IOException {
        File subDir = tempFolder.newFolder("subdir");
        File legacyFile = new File(subDir, "MP_IMG_nested.png");
        legacyFile.createNewFile();

        assertTrue(legacyFile.exists());

        LegacyVersionUtils.removeLegacyResidualImageFiles(tempFolder.getRoot());

        assertFalse(legacyFile.exists());
    }

    @Test
    public void testNoFilesToRemove() throws IOException {
        File normalFile = tempFolder.newFile("normal.txt");
        File normalDir = tempFolder.newFolder("normal_dir");

        LegacyVersionUtils.removeLegacyResidualImageFiles(tempFolder.getRoot());

        assertTrue(normalFile.exists());
        assertTrue(normalDir.exists());
    }

    @Test
    public void testEmptyDirectory() throws IOException {
        File emptyDir = tempFolder.newFolder("empty");
        LegacyVersionUtils.removeLegacyResidualImageFiles(emptyDir);
        // Should not throw
    }

    @Test
    public void testNonExistentFile() {
        File nonExistent = new File("/tmp/nonexistent_mixpanel_test_dir");
        // Should not throw
        LegacyVersionUtils.removeLegacyResidualImageFiles(nonExistent);
    }
}
