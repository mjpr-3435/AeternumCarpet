package smp.aeternum.carpet.materials;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import smp.aeternum.carpet.AeternumCarpet;

/**
 * Single-threaded daemon executor for all disk I/O and heavy .litematic parsing
 * so the main server tick thread never stalls on GZIP/NBT decompression or JSON writes.
 */
public final class MaterialsAsyncIO {
	private static ExecutorService executor;

	private MaterialsAsyncIO() {}

	private static synchronized ExecutorService ensureExecutor() {
		if (executor == null || executor.isShutdown() || executor.isTerminated()) {
			executor = Executors.newSingleThreadExecutor(r -> {
				Thread t = new Thread(r, "Aeternum-Projects-IO");
				t.setDaemon(true);
				return t;
			});
		}
		return executor;
	}

	public static void execute(Runnable task) {
		ensureExecutor().execute(() -> {
			try {
				task.run();
			} catch (Throwable t) {
				AeternumCarpet.LOGGER.error("Unhandled error in Aeternum-Projects-IO", t);
			}
		});
	}

	public static void writeAtomicAsync(Path target, String content) {
		if (target == null || content == null) return;
		execute(() -> writeAtomicSync(target, content));
	}

	public static void writeBytesAtomicAsync(Path target, byte[] bytes) {
		if (target == null || bytes == null) return;
		execute(() -> writeBytesAtomicSync(target, bytes));
	}

	public static void deleteAsync(Path... paths) {
		if (paths == null || paths.length == 0) return;
		Path[] copy = paths.clone();
		execute(() -> {
			for (Path p : copy) {
				if (p == null) continue;
				try {
					Files.deleteIfExists(p);
				} catch (IOException e) {
					AeternumCarpet.LOGGER.warn("Could not delete {}", p, e);
				}
			}
		});
	}

	public static void submitParse(
		byte[] bytes,
		MinecraftServer server,
		Consumer<LitematicParser.RawSchematic> onSuccess,
		Consumer<Throwable> onError
	) {
		execute(() -> {
			try {
				LitematicParser.RawSchematic raw = LitematicParser.parseRaw(bytes);
				server.execute(() -> onSuccess.accept(raw));
			} catch (Throwable t) {
				server.execute(() -> onError.accept(t));
			}
		});
	}

	public static void writeAtomicSync(Path target, String content) {
		try {
			Path parent = target.getParent();
			if (parent != null) Files.createDirectories(parent);
			Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
			Files.writeString(tmp, content);
			moveReplace(tmp, target);
		} catch (IOException e) {
			AeternumCarpet.LOGGER.error("Could not atomically write {}", target, e);
		}
	}

	public static void writeBytesAtomicSync(Path target, byte[] bytes) {
		try {
			Path parent = target.getParent();
			if (parent != null) Files.createDirectories(parent);
			Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
			Files.write(tmp, bytes);
			moveReplace(tmp, target);
		} catch (IOException e) {
			AeternumCarpet.LOGGER.error("Could not atomically write bytes to {}", target, e);
		}
	}

	private static void moveReplace(Path tmp, Path target) throws IOException {
		try {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	public static synchronized void flushAndShutdown() {
		if (executor == null || executor.isShutdown()) return;
		executor.shutdown();
		try {
			if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
				AeternumCarpet.LOGGER.warn("Aeternum-Projects-IO did not finish pending writes within 10s");
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			executor.shutdownNow();
		} finally {
			executor = null;
		}
	}
}
