package io.hynix.events.impl;

import net.minecraft.network.IPacket;

public class CancelEvent {

    public IPacket<?> packet;
    private boolean isCancel;

    public void cancel() {
        isCancel = true;
    }
    public void open() {
        isCancel = false;
    }
    public boolean isCancel() {
        return isCancel;
    }

}
