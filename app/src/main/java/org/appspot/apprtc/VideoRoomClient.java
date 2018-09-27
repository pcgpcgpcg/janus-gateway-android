/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;


import org.appspot.apprtc.WebSocketChannelClient.WebSocketChannelEvents;
import org.appspot.apprtc.janus.JanusHandle;
import org.appspot.apprtc.janus.JanusRTCEvents;
import org.appspot.apprtc.janus.JanusCommon.JanusConnectionParameters;
import org.appspot.apprtc.janus.JanusCommon.ServerState;


import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.appspot.apprtc.janus.JanusTransaction;
import org.appspot.apprtc.util.AppRTCUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Negotiates signaling for chatting with https://appr.tc "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 *
 * <p>To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once room connection is established
 * onConnectedToRoom() callback with room parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can
 * be sent after WebSocket connection is established.
 */
public class VideoRoomClient {

    private static final String TAG = "VideoRoomClient";

    private final Handler handler;
    private JanusRTCEvents events;
    private WebSocketChannelClient wsClient;
    private JanusConnectionParameters connectionParameters;
    private ServerState state;
    private ConcurrentHashMap<String, JanusTransaction> transactionMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<BigInteger, JanusHandle> handleMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<BigInteger, JanusHandle> feedMap = new ConcurrentHashMap<>();
    private BigInteger sessionId;

    public VideoRoomClient(JanusRTCEvents events) {
        this.events = events;
        this.sessionId = BigInteger.ZERO;
        this.state = ServerState.NEW;

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }
}
