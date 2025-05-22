package com.apps4net.proxy.shared;

import java.io.Serializable;

public class ProxyRequest implements Serializable {
    private String httpMethodType;
    private String url;
    private String body;
    private String clientName;

    public ProxyRequest(String clientName, String httpMethodType, String url, String body) {
        this.clientName = clientName;
        this.httpMethodType = httpMethodType;
        this.url = url;
        this.body = body;
    }

    // No-arg constructor for Jackson
    public ProxyRequest() {
    }

    public String getHttpMethodType() { return httpMethodType; }
    public String getUrl() { return url; }
    public String getBody() { return body; }
    public String getClientName() { return clientName; }

    public void setHttpMethodType(String httpMethodType) { this.httpMethodType = httpMethodType; }
    public void setUrl(String url) { this.url = url; }
    public void setBody(String body) { this.body = body; }
    public void setClientName(String clientName) { this.clientName = clientName; }
}
