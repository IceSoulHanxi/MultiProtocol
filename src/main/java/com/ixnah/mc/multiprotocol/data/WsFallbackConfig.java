package com.ixnah.mc.multiprotocol.data;

public class WsFallbackConfig extends AbstractConfig {
    private FallbackType type;
    private String url;

    public FallbackType getType() {
        return type;
    }

    public void setType(FallbackType type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public enum FallbackType {
        redirect, proxy
    }
}
