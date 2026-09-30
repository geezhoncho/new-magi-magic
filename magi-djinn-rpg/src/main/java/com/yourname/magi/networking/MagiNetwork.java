package com.yourname.magi.networking;

import com.yourname.magi.MagiMod;
import com.yourname.magi.capability.PlayerDjinnData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class MagiNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            MagiMod.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(nextId++, SyncDjinnDataPacket.class,
                SyncDjinnDataPacket::encode, SyncDjinnDataPacket::decode, SyncDjinnDataPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(nextId++, EquipDjinnPacket.class,
                EquipDjinnPacket::encode, EquipDjinnPacket::decode, EquipDjinnPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void syncDjinnData(ServerPlayer player, PlayerDjinnData data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncDjinnDataPacket(data.serializeNBT()));
    }

    private MagiNetwork() {}
}
