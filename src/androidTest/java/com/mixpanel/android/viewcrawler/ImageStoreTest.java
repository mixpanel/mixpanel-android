package com.mixpanel.android.viewcrawler;


import android.content.Context;
import android.graphics.Bitmap;
import android.test.AndroidTestCase;
import android.util.Base64;

import com.mixpanel.android.util.ImageStore;
import com.mixpanel.android.util.RemoteService;

import org.apache.http.NameValuePair;

import java.io.IOException;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

public class ImageStoreTest extends AndroidTestCase {
    public void setUp() {
        mService = new PretendService();
        mService.online = true;
        mImageStore = new ImageStore(getContext(), "TEST_DIRECTORY", mService);
        mImageStore.clearStorage();

    }

    public void testFreshImageLoaded() throws ImageStore.CantGetImageException {
        final Bitmap image = mImageStore.getImage("HELLO");
        int[] pixels = new int[100];
        image.getPixels(pixels, 0, 10, 0, 0, 10, 10);

        for (int i = 25; i < pixels.length; i++) {
            assertEquals("Pixel " + i, 0xFF00FF00, pixels[i]);
        }
    }

    public void testWriteWhenRead() throws ImageStore.CantGetImageException {
        final Bitmap image1 = mImageStore.getImage("HELLO");
        assertEquals(1, mService.queries);

        final Bitmap image2 = mImageStore.getImage("HELLO");
        assertEquals(1, mService.queries);
    }

    public void testNoResponse() {
        final byte[] goodResponse = mService.response;
        mService.response = null;
        try {
            final Bitmap image1 = mImageStore.getImage("HELLO");
            fail("Expected exception to be thrown");
        } catch (ImageStore.CantGetImageException e) {
            ; // OK
        }

        assertEquals(1, mService.queries);

        mService.response = goodResponse;
        try {
            final Bitmap image2 = mImageStore.getImage("HELLO");
        } catch (ImageStore.CantGetImageException e) {
            fail("Unexpected exception thrown");
        }

        assertEquals(2, mService.queries);
    }

    private static class PretendService implements RemoteService {

        public PretendService() {
            response = Base64.decode(IMAGE_BASE64_10x10_GREEN.getBytes(), 0);
            online = true;
            queries = 0;
        }

        @Override
        public boolean isOnline(final Context context) {
            return online;
        }

        @Override
        public byte[] performRequest(final String endpointUrl, final List<NameValuePair> params, SSLSocketFactory socketFactory)
                throws ServiceUnavailableException, IOException {
            queries++;
            return response;
        }

        public byte[] response;
        public boolean online;
        public int queries;
    }

    private ImageStore mImageStore;
    private PretendService mService;
    private String mTestName;
    private static final String IMAGE_BASE64_10x10_GREEN = "R0lGODlhCgAKALMAAAAAAIAAAACAAICAAAAAgIAAgACAgMDAwICAgP8AAAD/AP//AAAA//8A/wD//////ywAAAAACgAKAAAEClDJSau9OOvNe44AOw==";
}
