package com.apps4net.proxy.shared;

import java.io.Serializable;

public class ProxyResponse implements Serializable {
    private int statusCode;
    private String body;

    public ProxyResponse(int statusCode, String body) {
        this.statusCode = statusCode;
        this.body = body;
    }

    public int getStatusCode() { return statusCode; }
    public String getBody() { return body; }

    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    public void setBody(String body) { this.body = body; }
}
