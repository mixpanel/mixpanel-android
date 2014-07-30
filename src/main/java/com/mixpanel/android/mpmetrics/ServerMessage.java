package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/* package */ class ServerMessage {

    public boolean isOnline(Context context) {
        boolean isOnline;
        try {
            final ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final NetworkInfo netInfo = cm.getActiveNetworkInfo();
            isOnline = netInfo != null && netInfo.isConnectedOrConnecting();
            if (MPConfig.DEBUG) Log.d(LOGTAG, "ConnectivityManager says we " + (isOnline ? "are" : "are not") + " online");
        } catch (final SecurityException e) {
            isOnline = true;
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Don't have permission to check connectivity, assuming online");
        }
        return isOnline;
    }

    public byte[] getUrls(Context context, String[] urls) {
        if (! isOnline(context)) {
            return null;
        }

        byte[] response = null;
        for (String url : urls) {
            try {
                response = performRequest(url, null);
                break;
            } catch (final MalformedURLException e) {
                Log.e(LOGTAG, "Cannot interpret " + url + " as a URL.", e);
            } catch (final IOException e) {
                if (MPConfig.DEBUG)
                    Log.d(LOGTAG, "Cannot get " + url + ".", e);
            } catch (final OutOfMemoryError e) {
                Log.e(LOGTAG, "Out of memory when getting to " + url + ".", e);
                break;
            }
        }

        return response;
    }

    public byte[] performRequest(String endpointUrl, List<NameValuePair> params) throws IOException {
        if (MPConfig.DEBUG) Log.d(LOGTAG, "Attempting request to " + endpointUrl);
        byte[] response = null;

        // the while(retries) loop is a workaround for a bug in some Android HttpURLConnection
        // libraries- The underlying library will attempt to reuse stale connections,
        // meaning the second (or every other) attempt to connect fails with an EOFException.
        // Apparently this nasty retry logic is the current state of the workaround art.
        int retries = 0;
        boolean succeeded = false;
        while (retries < 3 && !succeeded) {
            InputStream in = null;
            OutputStream out = null;
            BufferedOutputStream bout = null;
            HttpURLConnection connection = null;

            try {
                final URL url = new URL(endpointUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(2000);
                connection.setReadTimeout(10000);
                if (null != params) {
                    connection.setDoOutput(true);
                    final UrlEncodedFormEntity form = new UrlEncodedFormEntity(params, "UTF-8");
                    connection.setRequestMethod("POST");
                    connection.setFixedLengthStreamingMode((int)form.getContentLength());
                    out = connection.getOutputStream();
                    bout = new BufferedOutputStream(out);
                    form.writeTo(bout);
                    bout.close();
                    bout = null;
                    out.close();
                    out = null;
                }
                in = connection.getInputStream();
                response = slurp(in);
                in.close();
                in = null;
                succeeded = true;
            } catch (final EOFException e) {
                if (MPConfig.DEBUG) Log.d(LOGTAG, "Failure to connect, likely caused by a known issue with Android lib. Retrying.");
                retries = retries + 1;
            } finally {
                if (null != bout)
                    try { bout.close(); } catch (final IOException e) { ; }
                if (null != out)
                    try { out.close(); } catch (final IOException e) { ; }
                if (null != in)
                    try { in.close(); } catch (final IOException e) { ; }
                if (null != connection)
                    connection.disconnect();
            }
        }
        return response;
    }

    // Does not close input streamq
    private byte[] slurp(final InputStream inputStream)
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

    private static final String LOGTAG = "MixpanelAPI";
}
