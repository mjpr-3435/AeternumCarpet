package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record CarpetRulesDataPayload(String json, boolean openScreen, String statusMessage, boolean statusError) implements CustomPayload {
    public static final Id<CarpetRulesDataPayload> ID = new Id<>(AeternumCarpet.id("carpet_rules_data"));
    public static final PacketCodec<RegistryByteBuf, CarpetRulesDataPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.string(524_288), CarpetRulesDataPayload::json,
        PacketCodecs.BOOLEAN, CarpetRulesDataPayload::openScreen,
        PacketCodecs.STRING, CarpetRulesDataPayload::statusMessage,
        PacketCodecs.BOOLEAN, CarpetRulesDataPayload::statusError,
        CarpetRulesDataPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
