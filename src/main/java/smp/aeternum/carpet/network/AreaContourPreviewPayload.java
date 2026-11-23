package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record AreaContourPreviewPayload(
    boolean ok,
    String message,
    String dimension,
    int columnCount,
    int previewSize,
    String previewGrid,
    String blockId
) implements CustomPayload {
    public static final Id<AreaContourPreviewPayload> ID = new Id<>(AeternumCarpet.id("area_contour_preview"));
    public static final PacketCodec<RegistryByteBuf, AreaContourPreviewPayload> CODEC = PacketCodec.of(
        (p, b) -> {
            b.writeBoolean(p.ok);
            b.writeString(p.message != null ? p.message : "", 512);
            b.writeString(p.dimension != null ? p.dimension : "", 128);
            b.writeVarInt(p.columnCount);
            b.writeVarInt(p.previewSize);
            b.writeString(p.previewGrid != null ? p.previewGrid : "", 4096);
            b.writeString(p.blockId != null ? p.blockId : "", 128);
        },
        b -> new AreaContourPreviewPayload(
            b.readBoolean(),
            b.readString(512),
            b.readString(128),
            b.readVarInt(),
            b.readVarInt(),
            b.readString(4096),
            b.readString(128)
        )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
