package org.appspot.apprtc.janus;

public class JanusCommon {

    // Janus keepalive interval
    public static final int delayMillis = 25000;

    // Got value by JanusConnectionParameters object
    private static final String[] subProtocols = {"janus-protocol"};

    // Janus transaction status
    public enum JanusServerState { NEW, CONNECTED, CLOSED, ERROR }

    public static class JanusConnectionParameters {
        public final String wsServerUrl;
        public final String[] subProtocols;
        public final long roomId;
        public final String userDisplay;
        public final int maxUserForRoom;     // videoroom plugin?   // fixme: 0: no limit

        public JanusConnectionParameters(String wsServerUrl, long roomId, String userDisplay, int maxUserForRoom) {
            this.wsServerUrl = wsServerUrl;
            this.subProtocols = JanusCommon.subProtocols;
            this.roomId = roomId;
            this.userDisplay = userDisplay;
            this.maxUserForRoom = maxUserForRoom;
        }

        public JanusConnectionParameters(String wsServerUrl, long roomId, String userDisplay) {
            this(wsServerUrl, roomId, userDisplay,  0 /* maxUserForRoom */);
        }
    }
}
