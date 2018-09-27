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
public class VideoRoomClient implements WebSocketChannelEvents {

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

    // --------------------------------------------------------------------
    // AppRTCClient interface implementation.
    // Asynchronously connect to an AppRTC room URL using supplied connection
    // parameters, retrieves room parameters and connect to WebSocket server.
    @Override
    public void connectToServer(JanusConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        handler.post(new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
    }

    @Override
    public void disconnectFromServer() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnect();
                handler.getLooper().quit();
            }
        });
    }

    private void init() {
        wsClient = new WebSocketChannelClient(handler, this);
        wsClient.connect(connectionParameters.wsServerUrl, connectionParameters.subProtocols);
    }

    private void create() {
        checkIfCalledOnValidThread();

        if(state != ServerState.NEW) {
            Log.w(TAG, "create() in a error state -- " + state);
            return;
        }

        JanusTransaction janusTransaction = new JanusTransaction();
        janusTransaction.transactionId = AppRTCUtils.randomString(12);
        janusTransaction.success = new JanusTransaction.TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject json) {
                sessionId = new BigInteger(json.optJSONObject("data").optString("id"));
                //fixme: reduce status
                setState(ServerState.CREATED);
                handler.post(fireKeepAlive);
                attachPublisher();
            }
        };
        janusTransaction.error = new JanusTransaction.TransactionCallbackError() {
            @Override
            public void error(JSONObject json) {
                //fixme: retry
                String code = json.optJSONObject("error").optString("code");
                String reason = json.optJSONObject("error").optString("reason");
                Log.e(TAG,"Transaction error: " + code + " " + reason);
                reportError(reason);
            }
        };

        transactionMap.put(janusTransaction.transactionId, janusTransaction);

        JSONObject json = new JSONObject();
        jsonPut(json, "janus", "create");
        jsonPut(json, "transaction", janusTransaction.transactionId);

        wsClient.send(json.toString());
    }

    //每隔25秒持续发送心跳包
    private void keepAlive() {
        checkIfCalledOnValidThread();

        if(state == ServerState.NEW || state == ServerState.ERROR) {
            Log.w(TAG, "keepalive() in a error state -- " + state);
            return;
        }

        JSONObject json = new JSONObject();
        jsonPut(json, "janus", "keepalive");
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "transaction", AppRTCUtils.randomString(12));

        wsClient.send(json.toString());
    }

    private Runnable fireKeepAlive = new Runnable() {
        @Override
        public void run() {
            keepAlive();
            handler.postDelayed(fireKeepAlive, 25000);
        }
    };

    public void attachPublisher(){
        checkIfCalledOnValidThread();

        if(state != ServerState.CREATED) {
            Log.w(TAG, "attach() in a error state -- " + state);
            return;
        }

        JanusTransaction janusTransaction = new JanusTransaction();
        janusTransaction.transactionId = AppRTCUtils.randomString(12);
        janusTransaction.success = new JanusTransaction.TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject json) {
                JanusHandle janusHandle = new JanusHandle();
                janusHandle.handleId = new BigInteger(json.optJSONObject("data").optString("id"));
                janusHandle.feedId = janusHandle.handleId;
                janusHandle.onJoined = new JanusHandle.OnJoined() {
                    @Override
                    public void onJoined(JanusHandle janusHandle) {
                        events.onPublisherJoined(janusHandle.handleId);
                    }
                };
                janusHandle.onRemoteJsep = new JanusHandle.OnRemoteJsep() {
                    @Override
                    public void onRemoteJsep(JanusHandle janusHandle,  JSONObject jsep) {
                        SessionDescription.Type type = SessionDescription.Type.fromCanonicalForm(jsep.optString("type"));
                        SessionDescription sdp = new SessionDescription(type, jsep.optString("sdp"));
                        events.onPublisherRemoteJsep(janusHandle.handleId, sdp);
                    }
                };
                handleMap.put(janusHandle.handleId, janusHandle);
                setState(ServerState.ATTACHED);
                join();
            }
        };
        janusTransaction.error = new JanusTransaction.TransactionCallbackError() {
            @Override
            public void error(JSONObject json) {
                Log.d(TAG,"publisherCreateHandle return error:"+json.toString());
                String code = json.optJSONObject("error").optString("code");
                String reason = json.optJSONObject("error").optString("reason");
                Log.e(TAG,"Transaction error: " + code + " " + reason);
                reportError(reason);
            }
        };

        transactionMap.put(janusTransaction.transactionId, janusTransaction);

        JSONObject json = new JSONObject();
        jsonPut(json, "janus", "attach");
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "plugin", "janus.plugin.videoroom");
        jsonPut(json, "transaction", janusTransaction.transactionId);

        wsClient.send(json.toString());
    }

    private void destroy() {
        checkIfCalledOnValidThread();

        sessionId = BigInteger.ZERO;

        if(state == ServerState.NEW || state == ServerState.ERROR) {
            Log.w(TAG, "destroy() in a error state -- " + state);
            return;
        }
        state = ServerState.NEW;

        JSONObject json = new JSONObject();
        jsonPut(json, "janus", "destroy");
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "transaction", AppRTCUtils.randomString(12));

        wsClient.send(json.toString());
    }

    private void disconnect() {
        destroy();

        if (wsClient != null) {
            wsClient.disconnect(true);
        }
    }

    private void checkIfCalledOnValidThread() {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }

    private void setState(ServerState state) {
        if(state != ServerState.ERROR)
            this.state = state;
    }

    // Send local offer SDP to the other participant.
    public void publisherCreateOffer(final BigInteger handleId, final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                transClientVector.get(0).createOffer(sdp.description);
            }
        });
    }

    // fixme: Send local answer SDP to the other participant.
    public void subscriberCreateAnswer(final BigInteger handleId, final SessionDescription sdp){
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (connectionParameters.loopback) {
                    Log.e(TAG, "Sending answer in loopback mode.");
                    return;
                }
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "answer");
                wsClient.send(json.toString());
            }
        });
    }

    // Send Ice candidate to the other participant.
    public void trickleCandidate(final BigInteger handleId, final IceCandidate iceCandidate) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (true)
                    transClientVector.get(0).trickle(iceCandidate.sdpMLineIndex, iceCandidate.sdpMid, iceCandidate.sdp);
                else {
                    // Call receiver sends ice candidates to websocket server.
                    //wsClient.send(json.toString());
                }
            }
        });
    }

    // Send removed Ice candidates to the other participant.
    @Override
    public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                /*
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "remove-candidates");
                JSONArray jsonArray = new JSONArray();
                for (final IceCandidate candidate : candidates) {
                    jsonArray.put(toJsonCandidate(candidate));
                }
                jsonPut(json, "candidates", jsonArray);
                if (initiator) {
                    // Call initiator sends ice candidates to GAE server.
                    if (roomState != ConnectionState.CONNECTED) {
                        reportError("Sending ICE candidate removals in non connected state.");
                        return;
                    }
                    sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
                    if (connectionParameters.loopback) {
                        events.onRemoteIceCandidatesRemoved(candidates);
                    }
                } else {
                    // Call receiver sends ice candidates to websocket server.
                    wsClient.send(json.toString());
                }*/
            }
        });
    }

    // --------------------------------------------------------------------
    // WebSocketChannelJEvents interface implementation.
    // All events are called by WebSocketChannelJClient on a local looper thread
    // (passed to WebSocket client constructor).
    @Override
    public void onWebSocketMessage(final String msg) {
        if (wsClient.getState() != WebSocketChannelClient.WebSocketConnectionState.CONNECTED) {
            Log.e(TAG, "Got WebSocket message in error state.");
            return;
        }
        try {
            JSONObject json = new JSONObject(msg);

            String response = json.getString("janus");
            if(response.equals("ack")) return; // fixme: handleACK(json);

            if(!json.isNull("sender")) {
                long sender = Long.parseLong(json.getString("sender"));
                if(transIndexMap.containsKey(sender)) {
                    String sessionId = json.getString("session_id");

                    if(json.isNull("transaction")) return; // fixme: handleServiceNotify(json);
                    else {
                        String jsep = json.isNull("jsep") ? "" : json.getJSONObject("jsep").getString("sdp");
                        String transaction = json.getString("transaction");
                        if(json.isNull("plugindata")) json = json.getJSONObject("data");
                        else json = json.getJSONObject("plugindata");
                        transClientVector.get(transIndexMap.get(sender)).parseJson( response, transaction, json, jsep);
                    }
                }
                else Log.d(TAG, "Unexpected json message: Sender is invalid -- " + sender);
                return;
            }

            if(json.isNull("transaction")) return; // fixme: handleServiceNotifyForInit(json);
            String transaction = json.getString("transaction");
            String res = checkTransaction(transaction);

            switch (res) {
                case "create" :
                    if(json.getString("janus").equals("success")) {
                        SessionID = Long.parseLong(json.getJSONObject("data").getString("id"));
                        setState(ServerState.CREATED);
                        handler.post(fireKeepAlive);
                        attachPublisher();
                    }
                    else
                        reportError("Unexpected json message: create() failed");
                    break;
                case "attachPublisher":
                    if(json.getString("janus").equals("success")) {
                        setState(ServerState.ATTACHED);
                        long HandleID = Long.parseLong(json.getJSONObject("data").getString("id"));
                        transIndexMap.put(HandleID, 0);
                        transClientVector.get(0).init(SessionID, HandleID, connectionParameters.roomId, "test", state);
                        transClientVector.get(0).join();
                    }
                    else
                        reportError("Unexpected json message: attach() failed");
                    break;
                case "detach": //fixme
                    setState(ServerState.CREATED);
                    break;
                case "destroy":
                    break;
                case "error":
                    reportError("Unexpected json message: Not found the transaction.");
                    break;
                default:
                    reportError("Unexpected json message: " + res);
                    break;
            }
            removeTransaction(transaction);
        } catch (JSONException e) {
            reportError("WebSocket message JSON parsing error: " + e.toString());
        }
    }

    @Override
    public void onWebSocketOpen() {
        create();
    }

    @Override
    public void onWebSocketClose() {
        events.onChannelClose();
    }

    @Override
    public void onWebSocketError(String description) {
        reportError("WebSocket error: " + description);
    }

    @Override
    public void onSendMessage(String message){
        wsClient.send(message);
    }

    @Override
    public void onPrepareSDP(boolean initiator, String clientId){
        Log.d(TAG, "onPrepareSDP");

        // Fire connection and signaling parameters events.
        events.onPublisherJoined(handleId);
    }

    @Override
    public void onRemoteDescription(String jsep) {
        SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm("answer"), jsep.toString());
        events.onRemoteDescription(sdp);
    }

    @Override
    public void onReportTranactionError(long handleId, String msg) {

    }

    // --------------------------------------------------------------------
    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != ServerState.ERROR) {
                    destroy();
                    state = ServerState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    // Put a |key|->|value| mapping in |json|.
    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Converts a Java candidate to a JSONObject.
    private JSONObject toJsonCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    // Converts a JSON candidate to a Java object.
    IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
                json.getString("id"), json.getInt("label"), json.getString("candidate"));
    }
}
