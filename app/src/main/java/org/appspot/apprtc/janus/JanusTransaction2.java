package org.appspot.apprtc.janus;

import org.json.JSONObject;
import java.math.BigInteger;

public class JanusTransaction2 {
    public interface TransactionEvents{
        default void success(BigInteger id) {this.success(id, null);}
        default void success(BigInteger id, JSONObject jsep) { this.success(id);}
        default void error(String reason, String code) {};
    }

    public String transactionId;
    public TransactionEvents events;
}
