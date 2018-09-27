package org.appspot.apprtc.janus;


import org.json.JSONObject;

import java.math.BigInteger;

public class JanusHandle {
    public interface OnJoined{
        void onJoined(JanusHandle janusHandle);
    }

    public interface OnRemoteJsep{
        void onRemoteJsep(JanusHandle janusHandle, JSONObject jsep);
    }
    public BigInteger handleId;
    public BigInteger feedId;
    public String display;

    public OnJoined onJoined;
    public OnRemoteJsep onRemoteJsep;
    public OnJoined onLeaving;
}
