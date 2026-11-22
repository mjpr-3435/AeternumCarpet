package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record StatsRegistryActionPayload(String action, String uuid, String value) implements CustomPayload {
    public static final Id<StatsRegistryActionPayload> ID = new Id<>(AeternumCarpet.id("stats_registry_action"));
    public static final PacketCodec<RegistryByteBuf, StatsRegistryActionPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.string(32), StatsRegistryActionPayload::action,
        PacketCodecs.string(64), StatsRegistryActionPayload::uuid,
        PacketCodecs.string(64), StatsRegistryActionPayload::value,
        StatsRegistryActionPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
