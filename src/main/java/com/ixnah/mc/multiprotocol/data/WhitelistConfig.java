package com.ixnah.mc.multiprotocol.data;

import java.util.List;

public class WhitelistConfig extends AbstractConfig {
    private List<String> addresses;

    public List<String> getAddresses() {
        return addresses;
    }

    public void setAddresses(List<String> addresses) {
        this.addresses = addresses;
    }
}
