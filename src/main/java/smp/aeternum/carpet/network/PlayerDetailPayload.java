package smp.aeternum.carpet.network;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.AeternumCarpet;

public record PlayerDetailPayload(String uuid, String json) implements CustomPayload {
    public static final Id<PlayerDetailPayload> ID =
        new Id<>(Identifier.of(AeternumCarpet.MOD_ID, "player_detail"));
    private static final int MAX_COMPRESSED_BYTES = 8 * 1024 * 1024;

    public static final PacketCodec<RegistryByteBuf, PlayerDetailPayload> CODEC = PacketCodec.of(
        (payload, buf) -> {
            buf.writeString(payload.uuid != null ? payload.uuid : "", 128);
            buf.writeByteArray(gzipCompress(payload.json != null ? payload.json : "{}"));
        },
        buf -> {
            String uuid = buf.readString(128);
            byte[] compressed = buf.readByteArray(MAX_COMPRESSED_BYTES);
            return new PlayerDetailPayload(uuid, gzipDecompress(compressed));
        }
    );

    private static byte[] gzipCompress(String text) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to compress PlayerDetailPayload", e);
        }
        return baos.toByteArray();
    }

    private static String gzipDecompress(byte[] bytes) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decompress PlayerDetailPayload", e);
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
