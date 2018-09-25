package org.appspot.apprtc.janus;


import org.json.JSONObject;



public class JanusTransaction {
    public interface TransactionCallbackSuccess{
        void success(JSONObject jo);
    }

    public interface TransactionCallbackError{
        void error(JSONObject jo);
    }
    public String tid;
    public TransactionCallbackSuccess success;
    public TransactionCallbackError error;
}
