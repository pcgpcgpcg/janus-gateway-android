package org.appspot.apprtc.janus;

import org.json.JSONObject;

public class JanusTransaction {
    public interface TransactionCallbackSuccess{
        void success(JSONObject jo);
    }

    public interface TransactionCallbackEvent{
        void event(JSONObject jo);
    }

    public interface TransactionCallbackError{
        void error(JSONObject jo);
    }
    public String transactionId;
    public TransactionCallbackSuccess success;
    public TransactionCallbackEvent event;
    public TransactionCallbackError error;
}
