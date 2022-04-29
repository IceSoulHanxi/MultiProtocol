package com.ixnah.mc.multiprotocol.data;

import java.util.List;

public class WsConfig extends AbstractConfig {
    private String path;
    private boolean ipFromHttp;
    private List<String> realIpHeaders;
    private int maxContentLength;
    private WsFallbackConfig fallback;
    private SslConfig ssl;
    private WhitelistConfig whitelist;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isIpFromHttp() {
        return ipFromHttp;
    }

    public void setIpFromHttp(boolean ipFromHttp) {
        this.ipFromHttp = ipFromHttp;
    }

    public List<String> getRealIpHeaders() {
        return realIpHeaders;
    }

    public void setRealIpHeaders(List<String> realIpHeaders) {
        this.realIpHeaders = realIpHeaders;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public WsFallbackConfig getFallback() {
        return fallback;
    }

    public void setFallback(WsFallbackConfig fallback) {
        this.fallback = fallback;
    }

    public SslConfig getSsl() {
        return ssl;
    }

    public void setSsl(SslConfig ssl) {
        this.ssl = ssl;
    }

    public WhitelistConfig getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(WhitelistConfig whitelist) {
        this.whitelist = whitelist;
    }
}
