package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

/** Full snapshots are intentionally small and only sent when the UI is opened or data changes. */
public record BlockerDataPayload(String json, boolean openScreen, boolean canDelete) implements CustomPayload {
    public static final CustomPayload.Id<BlockerDataPayload> ID = new CustomPayload.Id<>(AeternumCarpet.id("blocker_data"));
    public static final PacketCodec<RegistryByteBuf, BlockerDataPayload> CODEC = PacketCodec.of(
        (payload, buf) -> { buf.writeString(payload.json, 1_000_000); buf.writeBoolean(payload.openScreen); buf.writeBoolean(payload.canDelete); },
        buf -> new BlockerDataPayload(buf.readString(1_000_000), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
