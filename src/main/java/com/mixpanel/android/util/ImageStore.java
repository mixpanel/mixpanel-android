package com.mixpanel.android.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import com.mixpanel.android.mpmetrics.MPConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLSocketFactory;

/**
 * ABSOLUTELY NOT thread, or even process safe.
 * Writes and reads files and directories at known paths, and uses a shared instance of MessageDigest.
 */
public class ImageStore {
    public static class CantGetImageException extends Exception {
        public CantGetImageException(String message) {
            super(message);
        }

        public CantGetImageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public ImageStore(Context context, String moduleName) {
        this(context, DEFAULT_DIRECTORY_PREFIX + moduleName, new HttpService());
    }

    public ImageStore(Context context, String directoryName, RemoteService poster) {
        mDirectory = context.getDir(directoryName, Context.MODE_PRIVATE);
        mPoster = poster;
        mConfig = MPConfig.getInstance(context);
        MessageDigest useDigest;
        try {
            useDigest = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            Log.w(LOGTAG, "Images won't be stored because this platform doesn't supply a SHA1 hash function");
            useDigest = null;
        }

        mDigest = useDigest;
    }

    public Bitmap getImage(String url) throws CantGetImageException {
        final File file = storedFile(url);
        byte[] bytes = null;
        if (null == file || !file.exists()) {
            try {
                final SSLSocketFactory factory = mConfig.getSSLSocketFactory();
                bytes = mPoster.performRequest(url, null, factory);
            } catch (IOException e) {
                throw new CantGetImageException("Can't download bitmap", e);
            } catch (RemoteService.ServiceUnavailableException e) {
                throw new CantGetImageException("Couldn't download image due to service availability", e);
            }
        }

        final Bitmap bitmap;
        if (null != bytes) {
            if (null != file && bytes.length < MAX_BITMAP_SIZE) {
                OutputStream out = null;
                try {
                    out = new FileOutputStream(file);
                    out.write(bytes);
                } catch (FileNotFoundException e) {
                    throw new CantGetImageException("It appears that ImageStore is misconfigured, or disk storage is unavailable- can't write to bitmap directory", e);
                } catch (IOException e) {
                    throw new CantGetImageException("Can't store bitmap", e);
                } finally {
                    if (null != out) {
                        try {
                            out.close();
                        } catch (IOException e) {
                            Log.w(LOGTAG, "Problem closing output file", e);
                        }
                    }
                }
            }

            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (null == bitmap) {
                throw new CantGetImageException("Downloaded data could not be interpreted as a bitmap");
            }
        } else {
            BitmapFactory.Options option = new BitmapFactory.Options();
            option.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), option);
            Long imageSize = new Long(option.outHeight * option.outWidth);
            Long freeMemory = Runtime.getRuntime().freeMemory();
            if (imageSize > freeMemory) {
                throw new CantGetImageException("Do not have enough memory for the image");
            }

            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (null == bitmap) {
                final boolean ignored = file.delete();
                throw new CantGetImageException("Bitmap on disk can't be opened or was corrupt");
            }
        }

        return bitmap;
    }

    public void clearStorage() {
        File[] files = mDirectory.listFiles();
        int length = files.length;
        for (int i = 0; i < length; i++) {
            final File file = files[i];
            final String filename = file.getName();
            if (filename.startsWith(FILE_PREFIX)) {
                final boolean ignored = file.delete();
            }
        }
    }

    public void deleteStorage(String url) {
        final File file = storedFile(url);
        if (null != file) {
            final boolean ignored = file.delete();
        }
    }

    private File storedFile(String url) {
        if (null == mDigest) {
            return null;
        }

        final byte[] hashed = mDigest.digest(url.getBytes());
        final String safeName = FILE_PREFIX + Base64.encodeToString(hashed, Base64.URL_SAFE | Base64.NO_WRAP);
        return new File(mDirectory, safeName);
    }

    private final File mDirectory;
    private final RemoteService mPoster;
    private final MessageDigest mDigest;
    private final MPConfig mConfig;

    private static final String DEFAULT_DIRECTORY_PREFIX = "MixpanelAPI.Images.";
    private static final int MAX_BITMAP_SIZE = 10000000; // 10 MB
    private static final String FILE_PREFIX = "MP_IMG_";

    @SuppressWarnings("unused")
    private static final String LOGTAG = "MixpanelAPI.ImageStore";
}
