package smp.aeternum.carpet.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import smp.aeternum.carpet.AeternumCarpet;

public record ExplorationMapMaxRequestsPayload(int value) implements CustomPayload {
	public static final Id<ExplorationMapMaxRequestsPayload> ID = new Id<>(AeternumCarpet.id("exploration_map_max_requests"));
	public static final PacketCodec<RegistryByteBuf, ExplorationMapMaxRequestsPayload> CODEC = PacketCodec.of(
		(payload, buffer) -> buffer.writeVarInt(payload.value),
		buffer -> new ExplorationMapMaxRequestsPayload(Math.clamp(buffer.readVarInt(), 1, 64)));
	@Override public Id<? extends CustomPayload> getId() { return ID; }
}
