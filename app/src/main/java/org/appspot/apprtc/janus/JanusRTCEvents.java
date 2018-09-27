package org.appspot.apprtc.janus;

import org.json.JSONObject;
import org.webrtc.SessionDescription;

import java.math.BigInteger;

public interface JanusRTCEvents {
    void onPublisherJoined(BigInteger handleId);
    void onPublisherRemoteJsep(BigInteger handleId, SessionDescription sdp);
    void subscriberHandleRemoteJsep(BigInteger handleId,JSONObject jsep);
    void onLeaving(BigInteger handleId);
}
