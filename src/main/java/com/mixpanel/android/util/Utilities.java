package com.mixpanel.android.util;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Utilities {
    // Does not close input stream
    public static byte[] slurp(final InputStream inputStream)
        throws IOException {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[8192];

        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        buffer.flush();
        return buffer.toByteArray();
    }
}
