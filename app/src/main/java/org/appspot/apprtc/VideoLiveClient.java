package org.appspot.apprtc;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import org.appspot.apprtc.WebSocketChannelClient.WebSocketChannelEvents;
import org.appspot.apprtc.janus.JanusCommon;
import org.appspot.apprtc.janus.JanusCommon.JanusConnectionParameters;
import org.appspot.apprtc.janus.JanusCommon.JanusServerState;
import org.appspot.apprtc.janus.JanusHandle;
import org.appspot.apprtc.janus.JanusRTCEvents2;
import org.appspot.apprtc.janus.JanusTransaction2;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.math.BigInteger;
import java.util.concurrent.ConcurrentHashMap;

import static org.appspot.apprtc.janus.JanusUtils.convertJsonToCandidate;
import static org.appspot.apprtc.janus.JanusUtils.convertSdpToJson;
import static org.appspot.apprtc.janus.JanusUtils.jsonPut;
import static org.appspot.apprtc.janus.JanusUtils.randomString;

public class VideoLiveClient implements WebSocketChannelEvents {

    private static final String TAG = "VideoRoomClient";

    private final Handler handler;
    private WebSocketChannelClient wsClient;
    private JanusRTCEvents2 events;
    private JanusServerState state;
    private JanusConnectionParameters connectionParameters;

    private ConcurrentHashMap<String, JanusTransaction2> transactionMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<BigInteger, JanusHandle> handleMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<BigInteger, BigInteger> feedMap = new ConcurrentHashMap<>();
    private BigInteger sessionId, privateId;

    public VideoLiveClient(JanusRTCEvents2 events) {
        this.events = events;
        this.sessionId = BigInteger.ZERO;
        this.privateId = BigInteger.ZERO;
        this.state = JanusServerState.NEW;

        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    // ----------------------------------------------------------------------------
    // Basic functions by activity calling
    // ----------------------------------------------------------------------------
    public void connectToServer(JanusConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;
        handler.post(new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
    }

    public void disconnectFromServer() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnect();
                handler.getLooper().quit();
            }
        });
    }

    public void publisherCreateOffer(final BigInteger handleId, final SessionDescription sdp) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                createOffer(handleId, sdp);
            }
        });
    }

    public void subscriberCreateAnswer(final BigInteger handleId, final SessionDescription sdp){
        handler.post(new Runnable() {
            @Override
            public void run() {
                createAnswer(handleId, sdp);
            }
        });
    }

    public void trickleCandidate(final BigInteger handleId, final IceCandidate iceCandidate) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                trickle(handleId, iceCandidate);
            }
        });
    }

    public void trickleCandidateComplete(final BigInteger handleId) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                trickleComplete(handleId);
            }
        });
    }

    // ----------------------------------------------------------------------------
    // Internal transaction functions
    // ----------------------------------------------------------------------------
    private void init() {
        wsClient = new WebSocketChannelClient(handler, this);
        wsClient.connect(connectionParameters.wsServerUrl, connectionParameters.subProtocols);
    }

    private void create() {
        checkIfCalledOnValidThread();

        if(state != JanusServerState.NEW && state != JanusServerState.CLOSED) {
            Log.w(TAG, "create() in a error state -- " + state);
            return;
        }

        JanusTransaction2 JanusTransaction2 = new JanusTransaction2();
        JanusTransaction2.transactionId = randomString(12);
        JanusTransaction2.events = new JanusTransaction2.TransactionEvents() {
            @Override
            public void success(BigInteger id) {
                sessionId = id;
                setState(JanusServerState.CONNECTED);
                handler.post(fireKeepAlive);
                attach(BigInteger.ZERO, connectionParameters.userDisplay);
            }

            @Override
            public void error(String reason, String code) {
                //fixme: retry
                Log.e(TAG,"Transaction error: " + code + " " + reason);
                reportError(reason);
            }
        };

        transactionMap.put(JanusTransaction2.transactionId, JanusTransaction2);

        JSONObject json = new JSONObject();
        jsonPut(json, "janus", "create");
        jsonPut(json, "transaction", JanusTransaction2.transactionId);

        wsClient.send(json.toString());
    }

    private void keepAlive() {
        checkIfCalledOnValidThread();

        if(state != JanusServerState.CONNECTED) {
            Log.w(TAG, "keepalive() in a error state -- " + state);
            return;
        }

        JSONObject json = new JSONObject();
        jsonPut(json, "janus", "keepalive");
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "transaction", randomString(12));

        wsClient.send(json.toString());
    }

    private Runnable fireKeepAlive = new Runnable() {
        @Override
        public void run() {
            keepAlive();
            handler.postDelayed(fireKeepAlive, JanusCommon.delayMillis);
        }
    };

    private void attach(final BigInteger feedId, final String display){
        checkIfCalledOnValidThread();

        if(state != JanusServerState.CONNECTED) {
            Log.w(TAG, "attach() in a error state -- " + state);
            return;
        }

        JanusTransaction2 JanusTransaction2 = new JanusTransaction2();
        JanusTransaction2.transactionId = randomString(12);
        JanusTransaction2.events = new JanusTransaction2.TransactionEvents() {
            @Override
            public void success(BigInteger id) {
                JanusHandle janusHandle = new JanusHandle();

                janusHandle.handleId = id;
                if(feedId == BigInteger.ZERO) janusHandle.feedId = id; // attach publisher
                else janusHandle.feedId = feedId; // attach subscriber
                janusHandle.display = display;

                handleMap.put(janusHandle.handleId, janusHandle);
                feedMap.put(janusHandle.feedId, janusHandle.handleId);

                join(janusHandle.handleId, feedId);
            }

            @Override
            public void error(String reason, String code) {
                //fixme: retry
                Log.e(TAG,"Transaction error: " + code + " " + reason);
                reportError(reason);
            }
        };

        transactionMap.put(JanusTransaction2.transactionId, JanusTransaction2);

        JSONObject json = new JSONObject();
        jsonPut(json, "janus", "attach");
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "plugin", "janus.plugin.videoroom");
        jsonPut(json, "transaction", JanusTransaction2.transactionId);

        wsClient.send(json.toString());
    }

    private void join(BigInteger handleId, BigInteger feedId) {
        checkIfCalledOnValidThread();

        if(state != JanusServerState.CONNECTED) {
            Log.w(TAG, "join() in a error state -- " + state);
            return;
        }

        JanusTransaction2 JanusTransaction2 = new JanusTransaction2();
        JanusTransaction2.transactionId = randomString(12);
        JanusTransaction2.events = new JanusTransaction2.TransactionEvents() {
            // publisher joined
            @Override
            public void success(BigInteger id) {
                if (handleMap.get(id) == null) {
                    Log.e(TAG, "onWebSocketMessage: missing handle " + id);
                } else {
                    events.onPublisherJoined(id);
                }
            }

            // subscriber joined
            @Override
            public void success(BigInteger id, JSONObject jsep) {
                if (handleMap.get(id) == null) {
                    Log.e(TAG, "onWebSocketMessage: missing handle " + id);
                } else {
                    events.onRemoteJsep(handleId, jsep);
                }
            }

            @Override
            public void error(String reason, String code) {
                //fixme: retry
                Log.e(TAG,"Transaction error: " + code + " " + reason);
                reportError(reason);
            }
        };

        transactionMap.put(JanusTransaction2.transactionId, JanusTransaction2);

        JSONObject json = new JSONObject();
        JSONObject jsonBody = new JSONObject();

        jsonPut(jsonBody, "request", "join");
        jsonPut(jsonBody, "room", connectionParameters.roomId);
        if(feedId == BigInteger.ZERO) {
            jsonPut(jsonBody, "ptype", "publisher");
            jsonPut(jsonBody, "display", connectionParameters.userDisplay);
        }
        else {
            jsonPut(jsonBody, "ptype", "subscriber");
            jsonPut(jsonBody, "feed", feedId);
            jsonPut(jsonBody, "private_id", privateId);
        }

        jsonPut(json, "janus", "message");
        jsonPut(json, "body", jsonBody);
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "handle_id", handleId);
        jsonPut(json, "transaction", JanusTransaction2.transactionId);

        wsClient.send(json.toString());
    }

    private void createOffer(BigInteger handleId, SessionDescription sdp) {
        checkIfCalledOnValidThread();

        if(state != JanusServerState.CONNECTED) {
            Log.w(TAG, "join() in a error state -- " + state);
            return;
        }

        JanusTransaction2 JanusTransaction2 = new JanusTransaction2();
        JanusTransaction2.transactionId = randomString(12);
        JanusTransaction2.events = new JanusTransaction2.TransactionEvents() {
            @Override
            public void success(BigInteger id, JSONObject jsep) {
                if (handleMap.get(id) == null) {
                    Log.e(TAG, "offerConfigured: missing handle " + id);
                } else {
                    events.onRemoteJsep(id, jsep);
                }
            }

            @Override
            public void error(String reason, String code) {
                //fixme: retry
                Log.e(TAG,"Transaction error: " + code + " " + reason);
                reportError(reason);
            }
        };

        transactionMap.put(JanusTransaction2.transactionId, JanusTransaction2);

        JSONObject json = new JSONObject();
        JSONObject jsonBody = new JSONObject();

        jsonPut(jsonBody, "request", "configure");
        jsonPut(jsonBody, "audio", true);
        jsonPut(jsonBody, "video", true);

        jsonPut(json, "janus", "message");
        jsonPut(json, "body", jsonBody);
        jsonPut(json, "jsep", convertSdpToJson(sdp));
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "handle_id", handleId);
        jsonPut(json, "transaction", JanusTransaction2.transactionId);

        wsClient.send(json.toString());
    }

    private void createAnswer(BigInteger handleId, SessionDescription sdp) {
        checkIfCalledOnValidThread();

        if(state != JanusServerState.CONNECTED) {
            Log.w(TAG, "join() in a error state -- " + state);
            return;
        }

        JanusTransaction2 JanusTransaction2 = new JanusTransaction2();
        JanusTransaction2.transactionId = randomString(12);
        JanusTransaction2.events = new JanusTransaction2.TransactionEvents() {
            @Override
            public void success(BigInteger id) {
                reportNotification("Server receive the answer message in handle " + id);
            }

            @Override
            public void error(String reason, String code) {
                //fixme: retry
                Log.e(TAG,"Transaction error: " + code + " " + reason);
                reportError(reason);
            }
        };

        transactionMap.put(JanusTransaction2.transactionId, JanusTransaction2);

        JSONObject json = new JSONObject();
        JSONObject jsonBody = new JSONObject();

        jsonPut(jsonBody, "request", "start");
        jsonPut(jsonBody, "room", connectionParameters.roomId);

        jsonPut(json, "janus", "message");
        jsonPut(json, "body", jsonBody);
        jsonPut(json, "jsep", convertSdpToJson(sdp));
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "handle_id", handleId);
        jsonPut(json, "transaction", JanusTransaction2.transactionId);

        wsClient.send(json.toString());
    }

    private void trickle(BigInteger handleId, IceCandidate iceCandidate) {
        checkIfCalledOnValidThread();

        if(state != JanusServerState.CONNECTED) {
            Log.w(TAG, "keepalive() in a error state -- " + state);
            return;
        }

        JSONObject json = new JSONObject();

        jsonPut(json, "janus", "trickle");
        jsonPut(json, "candidate", convertJsonToCandidate(iceCandidate));
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "handle_id", handleId);
        jsonPut(json, "transaction", randomString(12));

        wsClient.send(json.toString());
    }

    private void trickleComplete(BigInteger handleId) {
        checkIfCalledOnValidThread();

        if(state != JanusServerState.CONNECTED) {
            Log.w(TAG, "keepalive() in a error state -- " + state);
            return;
        }

        JSONObject json = new JSONObject();
        JSONObject jsonCandidate = new JSONObject();

        jsonPut(jsonCandidate, "completed", true);

        jsonPut(json, "janus", "trickle");
        jsonPut(json, "candidate", jsonCandidate);
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "handle_id", handleId);
        jsonPut(json, "transaction", randomString(12));

        wsClient.send(json.toString());
    }

    private void detach(final BigInteger handleId) {
        checkIfCalledOnValidThread();

        if(state != JanusServerState.CONNECTED) {
            Log.w(TAG, "detach() in a error state -- " + state);
            return;
        }

        events.onLeft(handleId);

        JanusTransaction2 JanusTransaction2 = new JanusTransaction2();
        JanusTransaction2.transactionId = randomString(12);
        JanusTransaction2.events = new JanusTransaction2.TransactionEvents() {
            @Override
            public void success(BigInteger id) {
                Log.d(TAG, "detach a handle by remote stream " + handleId);
            }

            @Override
            public void error(String reason, String code) {
                //fixme: retry
                Log.e(TAG,"Transaction error: " + code + " " + reason);
            }
        };

        transactionMap.put(JanusTransaction2.transactionId, JanusTransaction2);

        JSONObject json = new JSONObject();

        jsonPut(json, "janus", "detach");
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "handle_id", handleId);
        jsonPut(json, "transaction", JanusTransaction2.transactionId);

        wsClient.send(json.toString());

        // free some object
        JanusHandle janusHandle = handleMap.get(handleId);
        if(janusHandle == null) return;

        feedMap.remove(janusHandle.feedId);
        handleMap.remove(janusHandle.handleId);
    }

    private void destroy() {
        checkIfCalledOnValidThread();

        if(sessionId == BigInteger.ZERO) {
            Log.w(TAG, "destroy() for sessionid 0");
            return;
        }

        JSONObject json = new JSONObject();
        jsonPut(json, "janus", "destroy");
        jsonPut(json, "session_id", sessionId);
        jsonPut(json, "transaction", randomString(12));

        wsClient.send(json.toString());

        setState(JanusServerState.CLOSED);
        sessionId = BigInteger.ZERO;
    }

    private void disconnect() {
        destroy();

        transactionMap.clear();
        handleMap.clear();
        feedMap.clear();

        if (wsClient != null) {
            wsClient.disconnect(true);
        }
    }

    // ----------------------------------------------------------------------------
    // / WebSocketChannelEvents interface implementation.
    // / All events are called by WebSocketChannelClient on a local looper thread
    // / (passed to WebSocket client constructor).
    // ----------------------------------------------------------------------------
    @Override
    public void onWebSocketMessage(final String msg) {
        if (wsClient.getState() != WebSocketChannelClient.WebSocketConnectionState.CONNECTED) {
            Log.e(TAG, "onWebSocketMessage: got WebSocket message in error state.");
            return;
        }

        String transaction = null;
        Boolean isAck = false;

        try {
            JSONObject json = new JSONObject(msg);

            String janus = json.optString("janus");
            String sender = json.optString("sender");
            transaction = json.optString("transaction");
            JanusTransaction2 JanusTransaction2 = transactionMap.get(transaction);

            // this branch will handle sender message, include server notification and server response.
            if(!sender.equals("")) {
                BigInteger senderId = new BigInteger(sender);

                if (janus.equals("event")) {
                    JSONObject data = json.optJSONObject("plugindata").optJSONObject("data");

                    JSONArray publishers = data.optJSONArray("publishers");
                    if (publishers != null && publishers.length() > 0) {
                        for (int i = 0; i < publishers.length(); i++) {
                            JSONObject publisher = publishers.optJSONObject(i);
                            BigInteger feedId = new BigInteger(publisher.optString("id"));
                            String display = publisher.optString("display");
                            //attach(feedId, display);
                        }
                    }

                    String videoroom = data.optString("videoroom");
                    if (videoroom.equals("joined")) {
                        String pid = data.optString("private_id");
                        if(!pid.equals("")) privateId = new BigInteger(pid);
                        JanusTransaction2.events.success(senderId);
                    } else if (videoroom.equals("attached")) {
                        JanusTransaction2.events.success(senderId, json.optJSONObject("jsep"));
                    } else if (videoroom.equals("event")) {
                        String configured = data.optString("configured");
                        if(!configured.equals("") && JanusTransaction2 != null && JanusTransaction2.events != null) {
                            if(configured.equals("ok")) {
                                JanusTransaction2.events.success(senderId, json.optJSONObject("jsep"));
                            }else {
                                json = json.optJSONObject("error");
                                JanusTransaction2.events.error(
                                        checkError(json, "reason", "configured is " + configured),
                                        checkError(json, "code", "createOffer")
                                );
                            }
                            return;
                        }

                        String started = data.optString("started");
                        if(!started.equals("") && JanusTransaction2 != null && JanusTransaction2.events != null) {
                            if(started.equals("ok")) {
                                JanusTransaction2.events.success(senderId);
                            } else {
                                json = json.optJSONObject("error");
                                JanusTransaction2.events.error(
                                        checkError(json, "reason", "started is " + started),
                                        checkError(json, "code", "createAnswer")
                                );
                            }
                            return;
                        }

                        String unpublished = data.optString("unpublished");
                        if(!unpublished.equals("")) {
                            if (unpublished.equals("ok")) {
                                //fixme: this branch is unused, add function later.
                            } else {
                                BigInteger id = feedMap.get(new BigInteger(unpublished));
                                if(id != null) detach(id);
                            }
                            return;
                        }

                        String leaving = data.optString("leaving");
                        if (!leaving.equals("")) {
                            BigInteger id = feedMap.get(new BigInteger(leaving));
                            if(id != null) detach(id);
                            return;
                        }
                    } else if (videoroom.equals("slow_link")) {
                        reportNotification("onWebSocketMessage: Got a slow_link event on session " + sessionId);
                    } else if (videoroom.equals("error")) {
                        if (JanusTransaction2 != null && JanusTransaction2.events != null) {
                            JanusTransaction2.events.error("unknown error", "videoroom");
                        }
                    } else {
                        reportError("onWebSocketMessage: unrecognized protocol.");
                    }
                } else if (janus.equals("webrtcup")) {
                    reportNotification("onWebSocketMessage: webrtc peerConnection is up now.");
                } else if (janus.equals("slowlink")) {
                    reportNotification("onWebSocketMessage: Got a slowlink event on session " + sessionId);
                } else if (janus.equals("media")) {
                    reportNotification("onWebSocketMessage: Got a media event on session " + sessionId + ", media type is " + json.optString("type") + ", receiving is " + json.optBoolean("receiving"));
                } else if (janus.equals("hangup")) {
                    Log.d(TAG, "onWebSocketMessage: Got a hangup event on session " + sessionId + ", feedid is " + sender);
                } else if (janus.equals("detached")) {
                    //detach(senderId);
                } else if (janus.equals("error")) {
                    json = json.optJSONObject("error");
                    if (JanusTransaction2 != null && JanusTransaction2.events != null && json != null) {
                        String reason = json.optString("reason");
                        String code = json.optString("code");
                        JanusTransaction2.events.error(reason, code);
                    }
                } else {
                    reportError("onWebSocketMessage: unrecognized protocol.");
                }

                return;
            }

            // this branch will handle basic message
            if(janus.equals("ack")) {
                // Just an ack, we can probably ignore
                Log.i(TAG,"Got an ack on session  " + sessionId);
                isAck = true;
            }else if(janus.equals("success")) {
                if (JanusTransaction2 != null && JanusTransaction2.events != null) {
                    json = json.optJSONObject("data");
                    if(json != null) {
                        String id = json.optString("id");
                        JanusTransaction2.events.success(new BigInteger(id));
                    } else {
                        JanusTransaction2.events.success(BigInteger.ZERO);
                    }
                }
            } else if(janus.equals("error")) {
                // something wrong happened
                json = json.optJSONObject("error");
                if(json == null) return;

                String reason = json.optString("reason");
                String code = json.optString("code");
                if(JanusTransaction2 == null || JanusTransaction2.events == null) {
                    Log.e(TAG, "onWebSocketMessage:error, Code:" + code + ", reason: " + reason);
                } else {
                    JanusTransaction2.events.error(reason, code);
                }
            } else {
                Log.d(TAG, "onWebSocketMessage: unrecognized protocol.");
            }
        } catch (JSONException e) {
            reportError("WebSocket message JSON parsing error: " + e.toString());
        } finally {
            if(!isAck) transactionMap.remove(transaction);
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

    // ----------------------------------------------------------------------------
    // Helper functions.
    // ----------------------------------------------------------------------------
    private void checkIfCalledOnValidThread() {
        if (Thread.currentThread() != handler.getLooper().getThread()) {
            throw new IllegalStateException("WebSocket method is not called on valid thread");
        }
    }
    
    private void setState(JanusServerState state) {
        if(state != JanusServerState.ERROR)
            this.state = state;
    }
    
    private String checkError(JSONObject json, String checkMessage, String defaultMessage) {
        if (json == null) return defaultMessage;
        else {
            String res = json.optString(checkMessage);
            return res.equals("") ? defaultMessage : res;
        }
    }
    
    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (state != JanusServerState.ERROR) {
                    destroy();
                    setState(JanusServerState.ERROR);
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    private void reportNotification(final String notificationMessage) {

    }
}
