package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record CarpetRuleSetPayload(String managerId, String ruleName, String value, boolean saveDefault, boolean resetToDefault) implements CustomPayload {
    public static final Id<CarpetRuleSetPayload> ID = new Id<>(AeternumCarpet.id("carpet_rule_set"));
    public static final PacketCodec<RegistryByteBuf, CarpetRuleSetPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.STRING, CarpetRuleSetPayload::managerId,
        PacketCodecs.STRING, CarpetRuleSetPayload::ruleName,
        PacketCodecs.STRING, CarpetRuleSetPayload::value,
        PacketCodecs.BOOLEAN, CarpetRuleSetPayload::saveDefault,
        PacketCodecs.BOOLEAN, CarpetRuleSetPayload::resetToDefault,
        CarpetRuleSetPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
