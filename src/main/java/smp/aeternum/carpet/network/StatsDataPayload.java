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
import smp.aeternum.carpet.AeternumCarpet;

public record StatsDataPayload(String json, boolean openScreen, String statusMessage, boolean statusError) implements CustomPayload {
    public static final Id<StatsDataPayload> ID = new Id<>(AeternumCarpet.id("stats_data"));
    private static final int MAX_COMPRESSED_BYTES = 8 * 1024 * 1024;

    public static final PacketCodec<RegistryByteBuf, StatsDataPayload> CODEC = PacketCodec.of(
        (payload, buf) -> {
            byte[] compressed = gzipCompress(payload.json != null ? payload.json : "");
            buf.writeByteArray(compressed);
            buf.writeBoolean(payload.openScreen);
            buf.writeString(payload.statusMessage != null ? payload.statusMessage : "", 32767);
            buf.writeBoolean(payload.statusError);
        },
        buf -> {
            byte[] compressed = buf.readByteArray(MAX_COMPRESSED_BYTES);
            String json = gzipDecompress(compressed);
            boolean openScreen = buf.readBoolean();
            String status = buf.readString(32767);
            boolean error = buf.readBoolean();
            return new StatsDataPayload(json, openScreen, status, error);
        }
    );

    private static byte[] gzipCompress(String text) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to compress stats_data payload", e);
        }
        return baos.toByteArray();
    }

    private static String gzipDecompress(byte[] bytes) {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to decompress stats_data payload", e);
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
