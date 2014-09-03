package com.mixpanel.android.java_websocket.handshake;

public interface ServerHandshake extends Handshakedata {
	public short getHttpStatus();
	public String getHttpStatusMessage();
}
