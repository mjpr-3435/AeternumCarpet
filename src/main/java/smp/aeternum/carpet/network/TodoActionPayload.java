package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record TodoActionPayload(String action, int index, String text) implements CustomPayload {
    public static final Id<TodoActionPayload> ID = new Id<>(AeternumCarpet.id("todo_action"));
    public static final PacketCodec<RegistryByteBuf, TodoActionPayload> CODEC = PacketCodec.of(
        (p, b) -> {
            b.writeString(p.action, 16);
            b.writeVarInt(p.index);
            b.writeString(p.text, 256);
        },
        b -> new TodoActionPayload(b.readString(16), b.readVarInt(), b.readString(256))
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
