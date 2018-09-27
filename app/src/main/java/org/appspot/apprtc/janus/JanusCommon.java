package org.appspot.apprtc.janus;

public class JanusCommon {

    public enum ServerState { NEW, CREATED, ATTACHED, JOINED, ERROR }

    public class JanusConnectionParameters {
        public final String wsServerUrl;
        public final String[] subProtocols;
        public final long roomId;
        public final String userDisplay;
        public final int maxUserForRoom;     // videoroom plugin?  0: no limit

        public JanusConnectionParameters(String wsServerUrl, String[] subProtocols, long roomId, String userDisplay, int maxUserForRoom) {
            this.wsServerUrl = wsServerUrl;
            this.subProtocols = subProtocols;
            this.roomId = roomId;
            this.userDisplay = userDisplay;
            this.maxUserForRoom = maxUserForRoom;
        }

        public JanusConnectionParameters(String wsServerUrl, String[] subProtocols, long roomId, String userDisplay) {
            this(wsServerUrl, subProtocols, roomId, userDisplay,  0 /* maxUserForRoom */);
        }
    }
}
