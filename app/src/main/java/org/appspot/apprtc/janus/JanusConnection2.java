package org.appspot.apprtc.janus;

import org.appspot.apprtc.PeerConnectionClient2;
import org.webrtc.PeerConnection;
import org.webrtc.VideoTrack;

import java.math.BigInteger;

public class JanusConnection2 {
    public BigInteger handleId;
    public PeerConnection peerConnection;
    public PeerConnectionClient2.SDPObserver sdpObserver;
    public VideoTrack videoTrack;
    public boolean type;
}
