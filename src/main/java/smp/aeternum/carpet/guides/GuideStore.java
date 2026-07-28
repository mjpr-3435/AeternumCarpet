package smp.aeternum.carpet.guides;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import smp.aeternum.carpet.AeternumCarpet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class GuideStore {
	private static final Pattern TITLE = Pattern.compile("[A-Za-z0-9 _-]{1,64}");
	private static final List<String> LANGS = List.of("en", "es");
	private static Path baseDir;
	private static final Map<String, LangStore> stores = new LinkedHashMap<>();
	private GuideStore() {}

	public static void load(MinecraftServer server) {
		baseDir = server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve("aeternum_guides");
		stores.clear();
		for (String lang : LANGS) stores.put(lang, new LangStore(baseDir.resolve(lang)));
		reload();
	}

	public static List<GuideDocument> all(String lang) { LangStore s = stores.get(lang); return s == null ? List.of() : List.copyOf(s.guides); }
	public static List<String> availableLangs() { List<String> out = new ArrayList<>(); for (String lang : LANGS) if (stores.containsKey(lang) && !stores.get(lang).guides.isEmpty()) out.add(lang); return out; }
	public static String defaultLang() { return "es"; }

	public static void reload() { for (LangStore s : stores.values()) s.reload(); }

	public static boolean save(String lang, String title, String markdown) {
		LangStore s = stores.get(lang); if (s == null || !valid(title) || markdown == null || markdown.length() > 100_000) return false;
		try { Files.createDirectories(s.dir); Path target = s.path(title), temp = s.dir.resolve("." + title + ".tmp"); Files.writeString(temp, markdown); Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); s.reload(); return true; } catch (Exception e) { AeternumCarpet.LOGGER.error("Could not save guide {} in {}", title, lang, e); return false; }
	}

	public static boolean create(String lang, String title) {
		LangStore s = stores.get(lang); if (s == null || !valid(title)) return false;
		String base = title; int suffix = 2; while (Files.exists(s.path(title))) title = base + " " + suffix++;
		return save(lang, title, "# " + title + "\n\n");
	}

	public static boolean rename(String lang, String oldTitle, String newTitle) {
		LangStore s = stores.get(lang); if (s == null || !valid(oldTitle) || !valid(newTitle) || Files.exists(s.path(newTitle))) return false;
		try { Files.move(s.path(oldTitle), s.path(newTitle)); List<String> changed = new ArrayList<>(s.order); int index = changed.indexOf(oldTitle); if (index >= 0) changed.set(index, newTitle); s.writeOrder(changed); s.reload(); return true; } catch (Exception e) { return false; }
	}

	public static boolean delete(String lang, String title) {
		LangStore s = stores.get(lang); if (s == null || !valid(title)) return false;
		try { boolean ok = Files.deleteIfExists(s.path(title)); if (ok) { List<String> changed = new ArrayList<>(s.order); changed.remove(title); s.writeOrder(changed); s.reload(); } return ok; } catch (Exception e) { return false; }
	}

	public static boolean move(String lang, String title, int direction) {
		LangStore s = stores.get(lang); if (s == null) return false;
		int index = s.order.indexOf(title), target = index + direction;
		if (index < 0 || target < 0 || target >= s.order.size()) return false;
		List<String> changed = new ArrayList<>(s.order);
		java.util.Collections.swap(changed, index, target);
		try { s.writeOrder(changed); s.reload(); return true; } catch (Exception e) { return false; }
	}

	public static boolean moveTo(String lang, String title, int targetIndex) {
		LangStore s = stores.get(lang); if (s == null) return false;
		int index = s.order.indexOf(title);
		if (index < 0 || targetIndex < 0 || targetIndex >= s.order.size() || index == targetIndex) return false;
		List<String> changed = new ArrayList<>(s.order);
		changed.remove(index);
		changed.add(targetIndex, title);
		try { s.writeOrder(changed); s.reload(); return true; } catch (Exception e) { return false; }
	}

	private static boolean valid(String title) { return title != null && TITLE.matcher(title).matches() && !title.equals(".") && !title.equals(".."); }

	private static final class LangStore {
		final Path dir, orderFile; List<GuideDocument> guides = List.of(); List<String> order = List.of();
		LangStore(Path dir) { this.dir = dir; this.orderFile = dir.resolve(".aeternum_guide_order"); }
		Path path(String title) { return dir.resolve(title + ".md"); }

		void reload() {
			List<GuideDocument> loaded = new ArrayList<>();
			try { Files.createDirectories(dir); try (var files = Files.list(dir)) { files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".md")).forEach(p -> { try { String name = p.getFileName().toString(); String title = name.substring(0, name.length() - 3); if (TITLE.matcher(title).matches()) loaded.add(new GuideDocument(title, Files.readString(p))); } catch (Exception e) { AeternumCarpet.LOGGER.warn("Could not read guide {}", p, e); } }); } } catch (Exception e) { AeternumCarpet.LOGGER.error("Could not reload guides from {}", dir, e); }
			loaded.sort(Comparator.comparing(g -> g.title, String.CASE_INSENSITIVE_ORDER));
			List<String> requested = readOrder();
			List<GuideDocument> arranged = new ArrayList<>();
			for (String title : requested) for (GuideDocument guide : loaded) if (guide.title.equals(title)) { arranged.add(guide); break; }
			for (GuideDocument guide : loaded) if (!arranged.contains(guide)) arranged.add(guide);
			guides = List.copyOf(arranged); order = arranged.stream().map(g -> g.title).toList();
		}

		List<String> readOrder() { try { return Files.isRegularFile(orderFile) ? Files.readAllLines(orderFile) : List.of(); } catch (Exception e) { return List.of(); } }
		void writeOrder(List<String> value) throws java.io.IOException { Files.createDirectories(dir); Files.write(orderFile, value); }
	}
}
