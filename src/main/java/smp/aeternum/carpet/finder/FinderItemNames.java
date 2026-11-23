package smp.aeternum.carpet.finder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Data-driven item names: same language catalogue approach as the former Finder, without a command. */
public final class FinderItemNames {
    private static final String BASE="https://raw.githubusercontent.com/misode/mcmeta/1.21.6-assets/assets/minecraft/lang/";
    private static final Gson GSON=new Gson();
    private static final Map<String,String> NAMES=new HashMap<>();
    private static Path cache;
    private FinderItemNames() { }
    public static void load(MinecraftServer server){cache=server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_finder_item_names_1.21.6.json");if(!readCache())downloadAndCache();}
    public static String resolve(String text){
        String query=normal(text);if(query.isEmpty())return null;
        Identifier direct=Identifier.tryParse(query.contains(":")?query:"minecraft:"+query.replace(' ','_'));
        if(direct!=null&&Registries.ITEM.containsId(direct))return direct.toString();
        for(var item:Registries.ITEM){String id=Registries.ITEM.getId(item).toString();String name=NAMES.get(id);if(query.equals(name)||query.equals(normal(Registries.ITEM.getId(item).getPath().replace('_',' '))))return id;}
        return null;
    }
    private static boolean readCache(){if(cache==null||!Files.exists(cache))return false;try{JsonObject data=JsonParser.parseString(Files.readString(cache)).getAsJsonObject();for(var e:data.entrySet())NAMES.put(e.getKey(),normal(e.getValue().getAsString()));return !NAMES.isEmpty();}catch(Exception e){return false;}}
    private static void downloadAndCache(){
        try{Map<String,String> es=readRemote("es_es.json"),en=readRemote("en_us.json");for(var item:Registries.ITEM){String id=Registries.ITEM.getId(item).toString();String path=Registries.ITEM.getId(item).getPath();String itemKey="item.minecraft."+path,blockKey="block.minecraft."+path;String label=es.getOrDefault(itemKey,es.getOrDefault(blockKey,en.getOrDefault(itemKey,en.getOrDefault(blockKey,path.replace('_',' ')))));NAMES.put(id,normal(label));}Files.createDirectories(cache.getParent());Files.writeString(cache,GSON.toJson(NAMES),StandardCharsets.UTF_8);}catch(Exception e){AeternumCarpet.LOGGER.warn("Finder could not download language index; technical item ids remain available.");}}
    private static Map<String,String> readRemote(String name)throws IOException,InterruptedException{HttpRequest request=HttpRequest.newBuilder(URI.create(BASE+name)).GET().build();String body=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString()).body();JsonObject json=JsonParser.parseString(body).getAsJsonObject();Map<String,String>out=new HashMap<>();for(var e:json.entrySet())out.put(e.getKey(),e.getValue().getAsString());return out;}
    public static CompletableFuture<Suggestions> suggest(String remaining, SuggestionsBuilder builder) {
        String q = normal(remaining == null ? "" : remaining);
        int limit = 35;
        List<String> prefixMatches = new ArrayList<>();
        List<String> containsMatches = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (var item : Registries.ITEM) {
            Identifier key = Registries.ITEM.getId(item);
            String id = key.toString();
            String path = key.getPath();
            if ("air".equals(path)) continue;

            if (q.isEmpty()) {
                if (seen.add(path)) {
                    prefixMatches.add(path);
                    if (prefixMatches.size() >= limit) break;
                }
                continue;
            }

            String esName = NAMES.get(id);
            String enSpaced = path.replace('_', ' ');

            collectMatch(q, path, seen, prefixMatches, containsMatches, limit);
            if (!enSpaced.equals(path) && (q.contains(" ") || enSpaced.startsWith(q))) {
                collectMatch(q, enSpaced, seen, prefixMatches, containsMatches, limit);
            }
            if (esName != null && !esName.isEmpty()) {
                collectMatch(q, esName, seen, prefixMatches, containsMatches, limit);
            }
            if (prefixMatches.size() >= limit) break;
        }

        int added = 0;
        for (String s : prefixMatches) {
            builder.suggest(s);
            if (++added >= limit) break;
        }
        if (added < limit) {
            for (String s : containsMatches) {
                builder.suggest(s);
                if (++added >= limit) break;
            }
        }
        return builder.buildFuture();
    }

    private static void collectMatch(
        String q,
        String candidate,
        Set<String> seen,
        List<String> prefixMatches,
        List<String> containsMatches,
        int limit
    ) {
        if (candidate.startsWith(q)) {
            if (seen.add(candidate)) prefixMatches.add(candidate);
        } else if (containsMatches.size() < limit && candidate.contains(q)) {
            if (seen.add(candidate)) containsMatches.add(candidate);
        }
    }

    private static String normal(String value){return value.toLowerCase(Locale.ROOT).replace('á','a').replace('é','e').replace('í','i').replace('ó','o').replace('ú','u').replace('ü','u').trim().replaceAll("\\s+"," ");}
}
