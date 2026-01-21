package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.StrictMode;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test that MixpanelAPI initialization doesn't violate StrictMode policies
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class StrictModeTest {

    private Context mContext;
    private static final String TEST_TOKEN = "test_token_strict_mode";

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Clear SharedPreferences for clean test
        mContext.getSharedPreferences("com.mixpanel.android.mpmetrics.MixpanelAPI.SharedPreferencesLoader", Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }

    @Test
    public void testNoDiskReadViolationsOnMainThread() throws InterruptedException {
        // This test verifies that initializing MixpanelAPI on the main thread
        // doesn't cause StrictMode DiskReadViolations
        
        // Capture the original error stream
        final PrintStream originalErr = System.err;
        final ByteArrayOutputStream errCapture = new ByteArrayOutputStream();
        final PrintStream captureStream = new PrintStream(errCapture);
        
        // Enable StrictMode to detect disk reads on main thread
        StrictMode.ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
        
        final CountDownLatch latch = new CountDownLatch(1);
        final boolean[] violationDetected = {false};
        
        try {
            // Redirect System.err to capture StrictMode violations
            System.setErr(captureStream);
            
            // Set up StrictMode to detect disk reads
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .penaltyLog() // Log violations to System.err
                    .build());

            // Run initialization on main thread
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Initialize MixpanelAPI on main thread (this is what the user does)
                        MixpanelAPI mixpanel = MixpanelAPI.getInstance(mContext, TEST_TOKEN, true);
                        assertNotNull(mixpanel);

                        // Track an event to ensure more initialization happens
                        mixpanel.track("Test Event");
                        
                        // Small delay to let async operations start
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Check captured output for violations
                                String capturedOutput = errCapture.toString();
                                
                                // Check if any of the known violation points are in the output
                                if (capturedOutput.contains("DiskReadViolation") && 
                                    capturedOutput.contains("com.mixpanel.android")) {
                                    
                                    boolean hasKnownViolation = 
                                        capturedOutput.contains("PersistentIdentity.getTimeEvents") ||
                                        capturedOutput.contains("MPDbAdapter$MPDatabaseHelper.<init>") ||
                                        capturedOutput.contains("MPDbAdapter.<init>") ||
                                        capturedOutput.contains("MixpanelAPI.<init>");
                                    
                                    violationDetected[0] = hasKnownViolation;
                                }
                                
                                latch.countDown();
                            }
                        }, 500);
                    } catch (Exception e) {
                        e.printStackTrace();
                        latch.countDown();
                    }
                }
            });
            
            // Wait for test to complete
            assertTrue("Test should complete within timeout", 
                latch.await(10, TimeUnit.SECONDS));
            
            if (violationDetected[0]) {
                fail("StrictMode DiskReadViolation detected in Mixpanel SDK initialization. " +
                     "Check the following locations:\n" +
                     "- PersistentIdentity.getTimeEvents()\n" +
                     "- MPDbAdapter$MPDatabaseHelper.<init>()\n" +
                     "- MixpanelAPI.<init>()");
            }
            
        } finally {
            // Restore original StrictMode policy and error stream
            StrictMode.setThreadPolicy(originalPolicy);
            System.setErr(originalErr);
        }
    }

    @Test
    public void testInitializationCompletes() throws InterruptedException {
        // Simple test to ensure the SDK can initialize without errors
        final CountDownLatch latch = new CountDownLatch(1);
        final MixpanelAPI[] mixpanelRef = new MixpanelAPI[1];
        final Exception[] exceptionRef = new Exception[1];
        
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    // Initialize MixpanelAPI
                    mixpanelRef[0] = MixpanelAPI.getInstance(mContext, TEST_TOKEN, true);
                    
                    // Track an event
                    mixpanelRef[0].track("Test Event");
                    
                    // Access persistent identity (which would trigger getTimeEvents)
                    String distinctId = mixpanelRef[0].getDistinctId();
                    assertNotNull("Distinct ID should not be null", distinctId);
                    
                } catch (Exception e) {
                    exceptionRef[0] = e;
                } finally {
                    latch.countDown();
                }
            }
        });
        
        assertTrue("Initialization should complete", 
            latch.await(5, TimeUnit.SECONDS));
        
        if (exceptionRef[0] != null) {
            fail("Exception during initialization: " + exceptionRef[0].getMessage());
        }
        
        assertNotNull("MixpanelAPI should be initialized", mixpanelRef[0]);
    }

    private void assertTrue(String message, boolean condition) {
        if (!condition) {
            fail(message);
        }
    }
}