package com.yourname.magi.client;

import com.yourname.magi.capability.PlayerDjinnData;

/** Read-only client mirror of the local player's Djinn data. Never used for authority decisions. */
public final class ClientDjinnState {
    public static final PlayerDjinnData DATA = new PlayerDjinnData();

    private ClientDjinnState() {}
}
