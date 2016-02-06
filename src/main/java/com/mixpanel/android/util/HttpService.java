package com.mixpanel.android.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.Log;

import com.mixpanel.android.mpmetrics.MPConfig;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * An HTTP utility class for internal use in the Mixpanel library. Not thread-safe.
 */
public class HttpService implements RemoteService {

    private static boolean sIsMixpanelBlocked;

    @Override
    public void checkIsMixpanelBlocked() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                try {
                    InetAddress apiMixpanelInet = InetAddress.getByName("api.mixpanel.com");
                    InetAddress decideMixpanelInet = InetAddress.getByName("decide.mixpanel.com");
                    sIsMixpanelBlocked = apiMixpanelInet.isLoopbackAddress() ||
                            apiMixpanelInet.isAnyLocalAddress() ||
                            decideMixpanelInet.isLoopbackAddress() ||
                            decideMixpanelInet.isAnyLocalAddress();
                    if (MPConfig.DEBUG && sIsMixpanelBlocked) {
                        Log.v(LOGTAG, "AdBlocker is enabled. Won't be able to use Mixpanel services.");
                    }
                } catch (UnknownHostException e) {
                }
            }
        });

        t.start();
    }

    @Override
    public boolean isOnline(Context context) {
        if (sIsMixpanelBlocked) return false;

        boolean isOnline;
        try {
            final ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            final NetworkInfo netInfo = cm.getActiveNetworkInfo();
            isOnline = netInfo != null && netInfo.isConnectedOrConnecting();
            if (MPConfig.DEBUG) {
                Log.v(LOGTAG, "ConnectivityManager says we " + (isOnline ? "are" : "are not") + " online");
            }
        } catch (final SecurityException e) {
            isOnline = true;
            if (MPConfig.DEBUG) {
                Log.v(LOGTAG, "Don't have permission to check connectivity, will assume we are online");
            }
        }
        return isOnline;
    }

    @Override
    public byte[] performRequest(String endpointUrl, Map<String, Object> params, SSLSocketFactory socketFactory) throws ServiceUnavailableException, IOException {
        if (MPConfig.DEBUG) {
            Log.v(LOGTAG, "Attempting request to " + endpointUrl);
        }
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
                if (null != socketFactory && connection instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) connection).setSSLSocketFactory(socketFactory);
                }

                connection.setConnectTimeout(2000);
                connection.setReadTimeout(10000);
                if (null != params) {
                    Uri.Builder builder = new Uri.Builder();
                    for (Map.Entry<String, Object> param : params.entrySet()) {
                        builder.appendQueryParameter(param.getKey(), param.getValue().toString());
                    }
                    String query = builder.build().getEncodedQuery();

                    connection.setFixedLengthStreamingMode(query.getBytes().length);
                    connection.setDoOutput(true);
                    connection.setRequestMethod("POST");
                    out = connection.getOutputStream();
                    bout = new BufferedOutputStream(out);
                    bout.write(query.getBytes("UTF-8"));
                    bout.flush();
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
                if (MPConfig.DEBUG) {
                    Log.d(LOGTAG, "Failure to connect, likely caused by a known issue with Android lib. Retrying.");
                }
                retries = retries + 1;
            } catch (final IOException e) {
                if (connection.getResponseCode() >= 500 && connection.getResponseCode() <= 599) {
                    throw new ServiceUnavailableException("Service Unavailable", connection.getHeaderField("Retry-After"));
                } else {
                    throw e;
                }
            }
            finally {
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
        if (MPConfig.DEBUG) {
            if (retries >= 3) {
                Log.v(LOGTAG, "Could not connect to Mixpanel service after three retries.");
            }
        }
        return response;
    }

    private static byte[] slurp(final InputStream inputStream)
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

    private static final String LOGTAG = "MixpanelAPI.Message";
}
