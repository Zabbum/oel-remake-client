package com.github.zabbum.oelremakeclient;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmAction extends Confirm {
    private String action = "";

    public synchronized void confirm(final String action) {
        this.confirmStatus = true;
        this.action = action;
        notifyAll();
    }
}
