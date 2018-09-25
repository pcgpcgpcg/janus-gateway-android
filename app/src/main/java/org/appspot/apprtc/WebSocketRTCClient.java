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

import javax.annotation.Nullable;
import org.appspot.apprtc.WebSocketChannelClient.WebSocketChannelJEvents;
import org.appspot.apprtc.WebSocketChannelClient.WebSocketConnectionState;
import org.appspot.apprtc.WebSocketChannelClient.WebSocketChannelTranscationMsg;
import org.appspot.apprtc.util.AppRTCUtils;
import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.AsyncHttpURLConnection.AsyncHttpEvents;
import org.appspot.apprtc.janus.JanusHandle;
import org.appspot.apprtc.janus.JanusRTCInterface;
import org.appspot.apprtc.janus.JanusTransaction;

import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
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
public class WebSocketRTCClient implements WebSocketChannelJEvents {
    private static final String TAG = "WSRTCClient";
    private static final String ROOM_JOIN = "join";
    private static final String ROOM_MESSAGE = "message";
    private static final String ROOM_LEAVE = "leave";

    private enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }

    private final Handler handler;
    private JanusRTCInterface rtcInterfaces;
    private WebSocketChannelClient wsClient;
    private ConnectionState roomState;

    private BigInteger sessionId;

    //采用线程安全的hashmap
    private ConcurrentHashMap<String, JanusTransaction> transactions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<BigInteger, JanusHandle> handles = new ConcurrentHashMap<>();
    private ConcurrentHashMap<BigInteger, JanusHandle> feeds = new ConcurrentHashMap<>();

    public WebSocketRTCClient(JanusRTCInterface interfaces) {
        this.rtcInterfaces=interfaces;
        roomState = ConnectionState.NEW;
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    // --------------------------------------------------------------------
    // AppRTCClient interface implementation.
    // Asynchronously connect to an AppRTC room URL using supplied connection
    // parameters, retrieves room parameters and connect to WebSocket server.
    public void connectToRoom(final String roomUrl) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal(roomUrl);
            }
        });
    }

    public void disconnectFromRoom() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
                handler.getLooper().quit();
            }
        });
    }

    // Connects to room - function runs on a local looper thread.
    private void connectToRoomInternal(final String roomUrl) {
        roomState = ConnectionState.NEW;
        wsClient = new WebSocketChannelClient(handler, this);
        final String[] subProtocols={"janus-protocol"};
        // Connect and register WebSocket client.
        wsClient.connect(roomUrl,subProtocols);
        createSession();
    }

    // Disconnect from room and send bye messages - runs on a local looper thread.
    private void disconnectFromRoomInternal() {
        Log.d(TAG, "Disconnect. Room state: " + roomState);
        if (roomState==ConnectionState.CONNECTED) {
            Log.d(TAG, "Closing room.");
            //sendPostMessage(MessageType.LEAVE, leaveUrl, null);
        }
        roomState = ConnectionState.CLOSED;
        //if (wsClient != null) {
            //wsClient.disconnect(true);
        //}
    }

    private void keepAlive() {
        String transactionID=AppRTCUtils.randomString(12);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "keepalive");
            msg.putOpt("session_id", sessionId);
            msg.putOpt("transaction", transactionID);
        } catch (JSONException e) {
            Log.e(TAG,"WebSocket message JSON parsing error: " + e.toString());
        }
        //Log.d(TAG, "C->WSS: " + msg.toString());
        wsClient.send(msg.toString());
    }

    private Runnable fireKeepAlive = new Runnable() {
        @Override
        public void run() {
            keepAlive();
            handler.postDelayed(fireKeepAlive, 25000);
        }
    };

    //send create message to janus
    //{
    //        "janus" : "create",
    //        "transaction" : "<random alphanumeric string>"
    //}
    public void createSession() {
        checkIfCalledOnValidThread();
        if (roomState != ConnectionState.CONNECTED) {
            Log.e(TAG,"Sending Create Janus in non connected state.");
            return;
        }
        String transactionID=AppRTCUtils.randomString(12);
        Log.d(TAG, "sending create msg to Janus " + ". transactionID: " + transactionID);
        JanusTransaction jt = new JanusTransaction();
        jt.tid =  transactionID;
        jt.success = new JanusTransaction.TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject jo) {
                sessionId = new BigInteger(jo.optJSONObject("data").optString("id"));
                handler.post(fireKeepAlive);
                publisherCreateHandle();
            }
        };
        jt.error = new JanusTransaction.TransactionCallbackError() {
            @Override
            public void error(JSONObject jo) {
                String code = jo.optJSONObject("error").optString("code");
                String reason = jo.optJSONObject("error").optString("reason");
                Log.e(TAG,"Ooops: " + code + " " + reason);
                //callbacks.error(json["error"].reason);// FIXME
            }
        };
        transactions.put(transactionID, jt);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "create");
            msg.putOpt("transaction", transactionID);
        } catch (JSONException e) {
            Log.e(TAG,"WebSocket message JSON parsing error: " + e.toString());
        }
        wsClient.send(msg.toString());
    }

    //send attach to echo test message to Janus
    public void publisherCreateHandle() {
        checkIfCalledOnValidThread();
        String transactionID=AppRTCUtils.randomString(12);
        Log.d(TAG, "publisherCreateHandle" + " transactionID: " + transactionID);
        JanusTransaction jt = new JanusTransaction();
        jt.tid = transactionID;
        jt.success = new JanusTransaction.TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject jo) {
                JanusHandle janusHandle = new JanusHandle();
                janusHandle.handleId = new BigInteger(jo.optJSONObject("data").optString("id"));
                janusHandle.onJoined = new JanusHandle.OnJoined() {
                    @Override
                    public void onJoined(JanusHandle jh) {
                        Log.d(TAG,"Wow,joined!handleId="+jh.handleId);
                        rtcInterfaces.onPublisherJoined(jh.handleId);
                    }
                };
                janusHandle.onRemoteJsep = new JanusHandle.OnRemoteJsep() {
                    @Override
                    public void onRemoteJsep(JanusHandle jh,  JSONObject jsep) {
                        rtcInterfaces.onPublisherRemoteJsep(jh.handleId, jsep);
                    }
                };
                handles.put(janusHandle.handleId, janusHandle);
                publisherJoinRoom(janusHandle);
            }
        };
        jt.error = new JanusTransaction.TransactionCallbackError() {
            @Override
            public void error(JSONObject jo) {
                Log.d(TAG,"publisherCreateHandle return error:"+jo.toString());
            }
        };
        transactions.put(transactionID, jt);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "attach");
            msg.putOpt("plugin", "janus.plugin.videoroom");
            msg.putOpt("transaction", transactionID);
            msg.putOpt("session_id", sessionId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "C->WSS: " + msg.toString());
        wsClient.send(msg.toString());
    }

    public void publisherJoinRoom(JanusHandle handle){
        String transactionID=AppRTCUtils.randomString(12);
        Log.d(TAG, "publisherJoinRoom" + " transactionID: " + transactionID);
        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            body.putOpt("request", "join");
            body.putOpt("room", 1234);
            body.putOpt("ptype", "publisher");
            body.putOpt("display", "Android webrtc");

            msg.putOpt("janus", "message");
            msg.putOpt("body", body);
            msg.putOpt("transaction", transactionID);
            msg.putOpt("session_id", sessionId);
            msg.putOpt("handle_id", handle.handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "C->WSS: " + msg.toString());
        wsClient.send(msg.toString());
    }

    // Send local offer SDP to the other participant.
    public void publisherCreateOffer(final BigInteger handleId, final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String transactionID = AppRTCUtils.randomString(12);
                Log.d(TAG, "publisherCreateOffer" + " transactionID: " + transactionID);
                JSONObject publish = new JSONObject();
                JSONObject jsep = new JSONObject();
                JSONObject message = new JSONObject();
                try {
                    publish.putOpt("request", "configure");
                    publish.putOpt("audio", true);
                    publish.putOpt("video", true);

                    jsep.putOpt("type", sdp.type);
                    jsep.putOpt("sdp", sdp.description);

                    message.putOpt("janus", "message");
                    message.putOpt("body", publish);
                    message.putOpt("jsep", jsep);
                    message.putOpt("transaction", transactionID);
                    message.putOpt("session_id", sessionId);
                    message.putOpt("handle_id", handleId);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "C->WSS: " + message.toString());
                wsClient.send(message.toString());
            }
        });

    }

    public void subscriberCreateAnswer(final BigInteger handleId, final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String transactionID = AppRTCUtils.randomString(12);
                Log.d(TAG, "subscriberCreateAnswer" + " transactionID: " + transactionID);
                JSONObject body = new JSONObject();
                JSONObject jsep = new JSONObject();
                JSONObject message = new JSONObject();

                try {
                    body.putOpt("request", "start");
                    body.putOpt("room", 1234);

                    jsep.putOpt("type", sdp.type);
                    jsep.putOpt("sdp", sdp.description);
                    message.putOpt("janus", "message");
                    message.putOpt("body", body);
                    message.putOpt("jsep", jsep);
                    message.putOpt("transaction", transactionID);
                    message.putOpt("session_id", sessionId);
                    message.putOpt("handle_id", handleId);
                    Log.e(TAG, "-------------" + message.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "C->WSS: " + message.toString());
                wsClient.send(message.toString());
            }
        });
    }

    public void trickleCandidate(final BigInteger handleId, final IceCandidate iceCandidate) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String transactionID = AppRTCUtils.randomString(12);
                Log.d(TAG, "trickleCandidate" + " transactionID: " + transactionID);
                JSONObject candidate = new JSONObject();
                JSONObject message = new JSONObject();
                try {
                    candidate.putOpt("candidate", iceCandidate.sdp);
                    candidate.putOpt("sdpMid", iceCandidate.sdpMid);
                    candidate.putOpt("sdpMLineIndex", iceCandidate.sdpMLineIndex);

                    message.putOpt("janus", "trickle");
                    message.putOpt("candidate", candidate);
                    message.putOpt("transaction", transactionID);
                    message.putOpt("session_id", sessionId);
                    message.putOpt("handle_id", handleId);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                Log.d(TAG, "C->WSS: " + message.toString());
                wsClient.send(message.toString());
            }
        });
    }

    public void trickleCandidateComplete(final BigInteger handleId) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String transactionID=AppRTCUtils.randomString(12);
                Log.d(TAG, "trickleCandidateComplete" + " transactionID: " + transactionID);
                JSONObject candidate = new JSONObject();
                JSONObject message = new JSONObject();
                try {
                    candidate.putOpt("completed", true);

                    message.putOpt("janus", "trickle");
                    message.putOpt("candidate", candidate);
                    message.putOpt("transaction", transactionID);
                    message.putOpt("session_id", sessionId);
                    message.putOpt("handle_id", handleId);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            });

    }

    private void subscriberCreateHandle(final BigInteger feed, final String display) {
        String transactionID=AppRTCUtils.randomString(12);
        Log.d(TAG, "trickleCandidateComplete" + " transactionID: " + transactionID);
        JanusTransaction jt = new JanusTransaction();
        jt.tid = transactionID;
        jt.success = new JanusTransaction.TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject jo) {
                JanusHandle janusHandle = new JanusHandle();
                janusHandle.handleId = new BigInteger(jo.optJSONObject("data").optString("id"));
                janusHandle.feedId = feed;
                janusHandle.display = display;
                janusHandle.onRemoteJsep = new JanusHandle.OnRemoteJsep() {
                    @Override
                    public void onRemoteJsep(JanusHandle jh, JSONObject jsep) {
                        rtcInterfaces.subscriberHandleRemoteJsep(jh.handleId, jsep);
                    }
                };
                janusHandle.onLeaving = new JanusHandle.OnJoined() {
                    @Override
                    public void onJoined(JanusHandle jh) {
                        subscriberOnLeaving(jh);
                    }
                };
                handles.put(janusHandle.handleId, janusHandle);
                feeds.put(janusHandle.feedId, janusHandle);
                subscriberJoinRoom(janusHandle);
            }
        };
        jt.error = new JanusTransaction.TransactionCallbackError() {
            @Override
            public void error(JSONObject jo) {
            }
        };

        transactions.put(transactionID, jt);
        JSONObject msg = new JSONObject();
        try {
            msg.putOpt("janus", "attach");
            msg.putOpt("plugin", "janus.plugin.videoroom");
            msg.putOpt("transaction", transactionID);
            msg.putOpt("session_id", sessionId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "C->WSS: " + msg.toString());
        wsClient.send(msg.toString());
    }

    private void subscriberJoinRoom(JanusHandle handle) {
        String transactionID=AppRTCUtils.randomString(12);
        Log.d(TAG, "subscriberJoinRoom" + " transactionID: " + transactionID);
        JSONObject msg = new JSONObject();
        JSONObject body = new JSONObject();
        try {
            body.putOpt("request", "join");
            body.putOpt("room", 1234);
            body.putOpt("ptype", "listener");
            body.putOpt("feed", handle.feedId);

            msg.putOpt("janus", "message");
            msg.putOpt("body", body);
            msg.putOpt("transaction", transactionID);
            msg.putOpt("session_id", sessionId);
            msg.putOpt("handle_id", handle.handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "C->WSS: " + msg.toString());
        wsClient.send(msg.toString());
    }

    private void subscriberOnLeaving(final JanusHandle handle) {
        String transactionID=AppRTCUtils.randomString(12);
        Log.d(TAG, "subscriberJoinRoom" + " transactionID: " + transactionID);

        JanusTransaction jt = new JanusTransaction();
        jt.tid = transactionID;
        jt.success = new JanusTransaction.TransactionCallbackSuccess() {
            @Override
            public void success(JSONObject jo) {
                rtcInterfaces.onLeaving(handle.handleId);
                handles.remove(handle.handleId);
                feeds.remove(handle.feedId);
            }
        };
        jt.error = new JanusTransaction.TransactionCallbackError() {
            @Override
            public void error(JSONObject jo) {
            }
        };

        transactions.put(transactionID, jt);

        JSONObject jo = new JSONObject();
        try {
            jo.putOpt("janus", "detach");
            jo.putOpt("transaction", transactionID);
            jo.putOpt("session_id", sessionId);
            jo.putOpt("handle_id", handle.handleId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "C->WSS: " + jo.toString());
        wsClient.send(jo.toString());
    }


    // --------------------------------------------------------------------
    // WebSocketChannelJEvents interface implementation.
    // All events are called by WebSocketChannelClient on a local looper thread
    // (passed to WebSocket client constructor).
    @Override
    public void onWebSocketMessage(final String msg) {
        Log.i(TAG,"Got wsmsg:"+msg);
        if (wsClient.getState() != WebSocketConnectionState.CONNECTED) {
            Log.e(TAG, "Got WebSocket message in non connected state.");
            return;
        }
        //在此处解析msg,仿照janus.js
        try {
            JSONObject jo = new JSONObject(msg);
            String janus=jo.optString("janus");
            if(janus.equals("keepalive")){
                // Nothing happened
                Log.i(TAG,"Got a keepalive on session " + sessionId);
                return;
            }else if(janus.equals("ack")) {
                // Just an ack, we can probably ignore
                Log.i(TAG,"Got an ack on session " + sessionId);
            }else if(janus.equals("success")) {
                String transaction = jo.optString("transaction");
                JanusTransaction jt = transactions.get(transaction);
                if (jt.success != null) {
                    jt.success.success(jo);
                }
                transactions.remove(transaction);
            }else if(janus.equals("trickle")) {
                // We got a trickle candidate from Janus
            }else if(janus.equals("webrtcup")) {
                // The PeerConnection with the gateway is up! Notify this
                Log.d(TAG,"Got a webrtcup event on session " + sessionId);
            } else if(janus.equals("hangup")) {
                // A plugin asked the core to hangup a PeerConnection on one of our handles
                Log.d(TAG,"Got a hangup event on session " + sessionId);
            } else if(janus.equals("detached")) {
                // A plugin asked the core to detach one of our handles
                Log.d(TAG,"Got a detached event on session " + sessionId);
            } else if(janus.equals("media")) {
                // Media started/stopped flowing
                Log.d(TAG,"Got a media event on session " + sessionId);
            } else if(janus.equals("slowlink")) {
                Log.d(TAG,"Got a slowlink event on session " + sessionId);
            } else if(janus.equals("error")) {
                // Oops, something wrong happened
                String transaction = jo.optString("transaction");
                JanusTransaction jt = transactions.get(transaction);
                if (jt.error != null) {
                    jt.error.error(jo);
                }
                transactions.remove(transaction);
            }  else {
                JanusHandle handle = handles.get(new BigInteger(jo.optString("sender")));
                if (handle == null) {
                    Log.e(TAG, "missing handle");
                } else if (janus.equals("event")) {
                    JSONObject plugin = jo.optJSONObject("plugindata").optJSONObject("data");
                    if (plugin.optString("videoroom").equals("joined")) {
                        handle.onJoined.onJoined(handle);
                    }

                    JSONArray publishers = plugin.optJSONArray("publishers");
                    if (publishers != null && publishers.length() > 0) {
                        for (int i = 0, size = publishers.length(); i <= size - 1; i++) {
                            JSONObject publisher = publishers.optJSONObject(i);
                            BigInteger feed = new BigInteger(publisher.optString("id"));
                            String display = publisher.optString("display");
                            subscriberCreateHandle(feed, display);
                        }
                    }

                    String leaving = plugin.optString("leaving");
                    if (!TextUtils.isEmpty(leaving)) {
                        JanusHandle jhandle = feeds.get(new BigInteger(leaving));
                        jhandle.onLeaving.onJoined(jhandle);
                    }

                    JSONObject jsep = jo.optJSONObject("jsep");
                    if (jsep != null) {
                        handle.onRemoteJsep.onRemoteJsep(handle, jsep);
                    }

                } else if (janus.equals("detached")) {
                    handle.onLeaving.onJoined(handle);
                }
            }
        } catch (JSONException e) {
            reportError("WebSocket message JSON parsing error: " + e.toString());
        }
    }

    @Override
    public void onWebSocketOpen() {
        Log.i(TAG,"onWebsocketOpen..");
        roomState = ConnectionState.CONNECTED;
        createSession();
    }

    @Override
    public void onWebSocketClose() {
        //events.onChannelClose();
    }

    @Override
    public void onWebSocketError(String description) {
        reportError("WebSocket error: " + description);
    }

    // --------------------------------------------------------------------
    // Helper functions.
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    //events.onChannelError(errorMessage);
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

    private void checkIfCalledOnValidThread() {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }
}

