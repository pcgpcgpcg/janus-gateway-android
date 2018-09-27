package org.appspot.apprtc.janus;

import org.json.JSONObject;

import java.math.BigInteger;

public interface JanusRTCEvents {
    void onPublisherJoined(BigInteger handleId);
    void onPublisherRemoteJsep(BigInteger handleId, JSONObject sdp);
    void subscriberHandleRemoteJsep(BigInteger handleId,JSONObject jsep);
    void onLeaving(BigInteger handleId);
    void onChannelClose();
    void onChannelError(String errorMessage);
}
