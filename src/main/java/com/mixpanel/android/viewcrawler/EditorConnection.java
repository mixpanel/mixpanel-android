package com.mixpanel.android.viewcrawler;

import android.util.Log;

import com.mixpanel.android.java_websocket.client.WebSocketClient;
import com.mixpanel.android.java_websocket.drafts.Draft_17;
import com.mixpanel.android.java_websocket.exceptions.NotSendableException;
import com.mixpanel.android.java_websocket.exceptions.WebsocketNotConnectedException;
import com.mixpanel.android.java_websocket.framing.Framedata;
import com.mixpanel.android.java_websocket.handshake.ServerHandshake;
import com.mixpanel.android.mpmetrics.MPConfig;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;

/**
 * EditorClient should handle all communication to and from the socket. It should be fairly naive and
 * only know how to delegate messages to the ABHandler class.
 */
/* package */ class EditorConnection {

    public class EditorConnectionException extends IOException {
        private static final long serialVersionUID = -1884953175346045636L;

        public EditorConnectionException(Throwable cause) {
            super(cause.getMessage()); // IOException(cause) is only available in API level 9!
        }
    }

    public interface Editor {
        public void sendSnapshot(JSONObject message);
        public void performEdit(JSONObject message);
        public void clearEdits(JSONObject message);
        public void bindEvents(JSONObject message);
        public void setTweaks(JSONObject message);
        public void sendDeviceInfo();
        public void cleanup();
    }

    public EditorConnection(URI uri, Editor service, Socket sslSocket)
            throws EditorConnectionException {
        mService = service;
        mURI = uri;
        try {
            mClient = new EditorClient(uri, CONNECT_TIMEOUT, sslSocket);
            mClient.connectBlocking();
        } catch (final InterruptedException e) {
            throw new EditorConnectionException(e);
        }
    }

    public boolean isValid() {
        return !mClient.isClosed() && !mClient.isClosing() && !mClient.isFlushAndClose();
    }

    public BufferedOutputStream getBufferedOutputStream() {
        return new BufferedOutputStream(new WebSocketOutputStream());
    }

    private class EditorClient extends WebSocketClient {
        public EditorClient(URI uri, int connectTimeout, Socket sslSocket) throws InterruptedException {
            super(uri, new Draft_17(), null, connectTimeout);
            setSocket(sslSocket);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            if (MPConfig.DEBUG) {
                Log.v(LOGTAG, "Websocket connected");
            }
        }

        @Override
        public void onMessage(String message) {
            if (MPConfig.DEBUG) {
                Log.v(LOGTAG, "Received message from editor:\n" + message);
            }
            try {
                final JSONObject messageJson = new JSONObject(message);
                final String type = messageJson.getString("type");
                if (type.equals("device_info_request")) {
                    mService.sendDeviceInfo();
                } else if (type.equals("snapshot_request")) {
                    mService.sendSnapshot(messageJson);
                } else if (type.equals("change_request")) {
                    mService.performEdit(messageJson);
                } else if (type.equals("event_binding_request")) {
                    mService.bindEvents(messageJson);
                } else if (type.equals("clear_request")) {
                    mService.clearEdits(messageJson);
                } else if (type.equals("tweak_request")) {
                    mService.setTweaks(messageJson);
                }
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Bad JSON received:" + message, e);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            if (MPConfig.DEBUG) {
                Log.v(LOGTAG, "WebSocket closed. Code: " + code + ", reason: " + reason + "\nURI: " + mURI);
            }
            mService.cleanup();
        }

        @Override
        public void onError(Exception ex) {
            if (ex != null && ex.getMessage() != null) {
                Log.e(LOGTAG, "Websocket Error: " + ex.getMessage());
            } else {
                Log.e(LOGTAG, "Unknown websocket error occurred");
            }
        }
    }

    private class WebSocketOutputStream extends OutputStream {
        @Override
        public void write(int b)
            throws EditorConnectionException {
            // This should never be called.
            final byte[] oneByte = new byte[1];
            oneByte[0] = (byte) b;
            write(oneByte, 0, 1);
        }

        @Override
        public void write(byte[] b)
            throws EditorConnectionException {
            write(b, 0, b.length);
        }

        @Override
        public void write(byte[] b, int off, int len)
            throws EditorConnectionException {
            final ByteBuffer message = ByteBuffer.wrap(b, off, len);
            try {
                mClient.sendFragmentedFrame(Framedata.Opcode.TEXT, message, false);
            } catch (final WebsocketNotConnectedException e) {
                throw new EditorConnectionException(e);
            } catch (final NotSendableException e) {
                throw new EditorConnectionException(e);
            }
        }

        @Override
        public void close()
            throws EditorConnectionException {
            try {
                mClient.sendFragmentedFrame(Framedata.Opcode.TEXT, EMPTY_BYTE_BUFFER, true);
            } catch (final WebsocketNotConnectedException e) {
                throw new EditorConnectionException(e);
            } catch (final NotSendableException e) {
                throw new EditorConnectionException(e);
            }
        }
    }

    private final Editor mService;
    private final EditorClient mClient;
    private final URI mURI;

    private static final int CONNECT_TIMEOUT = 5000;
    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);

    private static final String LOGTAG = "MixpanelAPI.EditorCnctn";
}
