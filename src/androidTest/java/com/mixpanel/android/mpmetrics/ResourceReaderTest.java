package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ResourceReaderTest {

  @Before
  public void setUp() {
    mDrawables =
        new ResourceReader.Drawables(
            TEST_PACKAGE_NAME, InstrumentationRegistry.getInstrumentation().getContext());
    mIds =
        new ResourceReader.Ids(
            TEST_PACKAGE_NAME, InstrumentationRegistry.getInstrumentation().getContext());
  }

  @Test
  public void testLocalIdExists() {
    assertTrue(mDrawables.knownIdName("TEST_DRAW_ZERO"));
    assertEquals(mDrawables.idFromName("TEST_DRAW_ZERO"), TEST_DRAW_ZERO);
    assertEquals(mDrawables.nameForId(TEST_DRAW_ZERO), "TEST_DRAW_ZERO");

    assertTrue(mIds.knownIdName("TEST_ID_ZERO"));
    assertEquals(mIds.idFromName("TEST_ID_ZERO"), TEST_ID_ZERO);
    assertEquals(mIds.nameForId(TEST_ID_ZERO), "TEST_ID_ZERO");
  }

  @Test
  public void testSystemIdExists() {
    assertTrue(mDrawables.knownIdName("android:ic_lock_idle_alarm"));
    assertEquals(
        mDrawables.idFromName("android:ic_lock_idle_alarm"), android.R.drawable.ic_lock_idle_alarm);
    assertEquals(
        mDrawables.nameForId(android.R.drawable.ic_lock_idle_alarm), "android:ic_lock_idle_alarm");

    assertTrue(mIds.knownIdName("android:primary"));
    assertEquals(mIds.idFromName("android:primary"), android.R.id.primary);
    assertEquals(mIds.nameForId(android.R.id.primary), "android:primary");
  }

  @Test
  public void testIdDoesntExist() {
    assertFalse(mDrawables.knownIdName("NO_SUCH_ID"));
    assertNull(mDrawables.nameForId(0x7f098888));

    assertFalse(mIds.knownIdName("NO_SUCH_ID"));
    assertNull(mIds.nameForId(0x7f098888));
  }

  private ResourceReader.Drawables mDrawables;
  private ResourceReader.Ids mIds;

  private static final String TEST_PACKAGE_NAME = "com.mixpanel.android.mpmetrics.test_r_package";
  private static final Class<?> RESOURCES_CLASS =
      com.mixpanel.android.mpmetrics.test_r_package.R.class;
  private static final int TEST_ID_ZERO =
      com.mixpanel.android.mpmetrics.test_r_package.R.id.TEST_ID_ZERO;
  private static final int TEST_DRAW_ZERO =
      com.mixpanel.android.mpmetrics.test_r_package.R.drawable.TEST_DRAW_ZERO;
}
