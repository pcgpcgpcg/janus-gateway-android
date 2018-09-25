package org.appspot.apprtc.janus;

import org.json.JSONObject;
import org.webrtc.SessionDescription;

import java.math.BigInteger;

public interface JanusRTCInterface {
    void onPublisherJoined(BigInteger handleId);
    void onPublisherRemoteJsep(BigInteger handleId, JSONObject jsep);
    void subscriberHandleRemoteJsep(BigInteger handleId,JSONObject jsep);
    void onLeaving(BigInteger handleId);
}
