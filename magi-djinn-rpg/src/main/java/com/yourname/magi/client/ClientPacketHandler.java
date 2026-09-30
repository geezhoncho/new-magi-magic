package com.yourname.magi.client;

import net.minecraft.nbt.CompoundTag;

/** Only ever invoked through DistExecutor on the physical client. */
public final class ClientPacketHandler {
    public static void handleSync(CompoundTag tag) {
        ClientDjinnState.DATA.deserializeNBT(tag);
    }

    private ClientPacketHandler() {}
}
