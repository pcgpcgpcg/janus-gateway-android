package org.appspot.apprtc.janus;

import org.json.JSONObject;
import java.math.BigInteger;

public interface JanusRTCEvents2 {
    void onPublisherJoined(BigInteger handleId);
    void onRemoteJsep(BigInteger handleId, JSONObject jsep);
    void onLeft(BigInteger handleId);
    void onNotification(String notificationMessage);

    void onChannelClose();
    void onChannelError(String errorMessage);
}
