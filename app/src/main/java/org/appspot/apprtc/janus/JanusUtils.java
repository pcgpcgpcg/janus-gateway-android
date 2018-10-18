package org.appspot.apprtc.janus;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public class JanusUtils {
    // Helper method to create random identifiers (e.g., transaction)
    public static String randomString(int len){
        String charSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        String randomString = "";
        for (int i = 0; i < len; i++) {
            int randomPoz = (int)Math.floor(Math.random() * charSet.length());
            randomString += charSet.substring(randomPoz, randomPoz + 1);
        }
        return randomString;
    }


    // Put a |key|->|value| mapping in |json|.
    public static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    // Converts a java SessionDescription to a JSONObject.
    public static JSONObject convertSdpToJson(final SessionDescription sdp) {
        JSONObject json = new JSONObject();

        jsonPut(json, "type", sdp.type);
        jsonPut(json, "sdp", sdp.description);

        return json;
    }

    // Converts a JSONObject to a java SessionDescription.
    public static SessionDescription convertJsonToSdp(JSONObject json) {
        return new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(json.optString("type")),
                json.optString("sdp")
        );
    }

    // Converts a Java candidate to a JSONObject.
    public static JSONObject convertJsonToCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();

        jsonPut(json, "candidate", candidate.sdp);
        jsonPut(json, "sdpMid", candidate.sdpMid);
        jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);

        return json;
    }

    // Converts a JSON candidate to a Java candidate.
    public static IceCandidate convertCandidateToJson(JSONObject json) {
        return new IceCandidate(
                json.optString("sdpMid"),
                json.optInt("sdpMLineIndex"),
                json.optString("candidate")
        );
    }
}
