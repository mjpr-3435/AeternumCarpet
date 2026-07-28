package smp.aeternum.carpet.materials;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MaterialItemResolver {
	private static final String BASE = "https://raw.githubusercontent.com/misode/mcmeta/1.21.6-assets/assets/minecraft/lang/";
	private static final Gson GSON = new Gson();
	private static final Map<String, String> NAME_TO_ID = new HashMap<>();
	private static boolean loaded;

	private MaterialItemResolver() {}

	public static void load(MinecraftServer server) {
		if (loaded) return;
		Path cache = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_material_names.json");
		if (Files.exists(cache)) {
			try {
				JsonObject data = JsonParser.parseString(Files.readString(cache)).getAsJsonObject();
				for (var e : data.entrySet()) NAME_TO_ID.put(e.getKey(), e.getValue().getAsString());
				if (!NAME_TO_ID.isEmpty()) { loaded = true; return; }
			} catch (Exception ignored) {}
		}
		try {
			String body = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(BASE + "en_us.json")).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			for (var item : Registries.ITEM) {
				String id = Registries.ITEM.getId(item).toString();
				String path = Registries.ITEM.getId(item).getPath();
				var nameEl = json.get("item.minecraft." + path);
				if (nameEl == null) nameEl = json.get("block.minecraft." + path);
				String name = nameEl != null ? nameEl.getAsString() : path.replace('_', ' ');
				NAME_TO_ID.put(normalize(name), id);
			}
			JsonObject cacheObj = new JsonObject();
			for (var e : NAME_TO_ID.entrySet()) cacheObj.addProperty(e.getKey(), e.getValue());
			Files.createDirectories(cache.getParent());
			Files.writeString(cache, GSON.toJson(cacheObj), StandardCharsets.UTF_8);
			loaded = true;
		} catch (IOException | InterruptedException e) {
			AeternumCarpet.LOGGER.warn("Could not download English item names for material resolver", e);
		}
	}

	public static String resolve(String name) {
		return NAME_TO_ID.get(normalize(name));
	}

	private static String normalize(String s) {
		return s.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
	}
}
