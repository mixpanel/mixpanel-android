package com.mixpanel.android.sessionreplay

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.mixpanel.android.sessionreplay.utils.DeviceInfo
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DeviceInfoTest {
    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager

    private val testPackageName = "com.mixpanel.test.app"
    private val testVersionCode = 42

    @Before
    fun setUp() {
        mockContext = mockk()
        mockPackageManager = mockk()

        every { mockContext.packageName } returns testPackageName
        every { mockContext.packageManager } returns mockPackageManager
        every { mockPackageManager.getPackageInfo(testPackageName, 0) } returns PackageInfo().apply {
            @Suppress("DEPRECATION")
            versionCode = testVersionCode
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testBundleIdReturnsPackageName() {
        assertEquals(testPackageName, DeviceInfo.bundleId(mockContext))
    }

    @Test
    fun testBuildNumberReturnsVersionCode() {
        // JVM unit tests report SDK_INT as 0, so this exercises the pre-API-28 branch
        assertEquals(testVersionCode.toString(), DeviceInfo.buildNumber(mockContext))
    }

    @Test
    fun testBundleIdReturnsNullWhenContextThrows() {
        every { mockContext.packageName } throws IllegalStateException("no package name")

        assertNull(DeviceInfo.bundleId(mockContext))
    }

    @Test
    fun testBuildNumberReturnsNullWhenPackageNotFound() {
        every {
            mockPackageManager.getPackageInfo(testPackageName, 0)
        } throws PackageManager.NameNotFoundException()

        assertNull(DeviceInfo.buildNumber(mockContext))
    }

    @Test
    fun testBuildNumberReturnsNullWhenPackageManagerUnavailable() {
        every { mockContext.packageManager } throws IllegalStateException("no package manager")

        assertNull(DeviceInfo.buildNumber(mockContext))
    }
}
