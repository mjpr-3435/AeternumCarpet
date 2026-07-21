package smp.aeternum.carpet.finder;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import smp.aeternum.carpet.AeternumCarpet;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/** Native, incremental container scan. Inventory contents are snapshotted at scan time. */
public final class FinderScanner {
    private static final Queue<Job> JOBS = new ArrayDeque<>();
    private FinderScanner() { }
    public static boolean start(ServerWorld world, FinderPlace place, UUID requester) {
        if (JOBS.stream().anyMatch(j -> j.place.name.equals(place.name))) return false;
        JOBS.add(new Job(world, place, requester)); return true;
    }
    public static void tick(MinecraftServer server) {
        Job job = JOBS.peek(); if (job == null) return;
        try { if (job.tick()) { FinderStore.save(); JOBS.remove(); job.message(server, "Escaneo terminado: " + job.place.containers.size() + " contenedores en " + job.place.name + ".", Formatting.GREEN); } else job.progress(server); }
        catch (RuntimeException e) { JOBS.remove(); AeternumCarpet.LOGGER.error("Finder scan failed: {}", job.place.name, e); job.message(server, "El escaneo de " + job.place.name + " falló; revisa el log.", Formatting.RED); }
    }
    private static final class Job {
        final ServerWorld world; final FinderPlace place; final UUID requester;
        final int minCX, maxCX, minCZ, maxCZ, total;
        int cx, cz, done; long lastProgress;
        Job(ServerWorld world, FinderPlace place, UUID requester) {
            this.world=world; this.place=place; this.requester=requester;
            minCX=Math.floorDiv(place.minX,16); maxCX=Math.floorDiv(place.maxX,16); minCZ=Math.floorDiv(place.minZ,16); maxCZ=Math.floorDiv(place.maxZ,16);
            cx=minCX; cz=minCZ; total=(maxCX-minCX+1)*(maxCZ-minCZ+1); place.containers.clear();
        }
        boolean tick() { // One full chunk per tick: bounded work and no permanent forceload.
            if (cz > maxCZ) { place.updatedAt=System.currentTimeMillis(); return true; }
            WorldChunk chunk=world.getChunkManager().getWorldChunk(cx,cz,true);
            Set<BlockPos> seen=new HashSet<>();
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (!seen.add(be.getPos()) || !(be instanceof Inventory inventory) || !place.contains(be.getPos()) || place.excluded.contains(place.key(be.getPos()))) continue;
                String block=Registries.BLOCK.getId(be.getCachedState().getBlock()).toString();
                if (!isStorage(block)) continue;
                FinderContainer found=new FinderContainer(be.getPos().getX(),be.getPos().getY(),be.getPos().getZ(),block);
                for (int slot=0; slot<inventory.size(); slot++) snapshotStack(found, inventory.getStack(slot), 1);
                place.containers.add(found);
            }
            done++; if (++cx>maxCX) { cx=minCX; cz++; } return false;
        }

        /**
         * Shulker contents live on the item as the CONTAINER data component, not in a world
         * block entity. Fold them into their outer container so /find reports the chest's
         * coordinates, including deliberately nested shulkers.
         */
        private void snapshotStack(FinderContainer found, ItemStack stack, int copies) {
            if (stack.isEmpty() || copies <= 0) return;
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            found.items.merge(id, Math.multiplyExact(stack.getCount(), copies), Integer::sum);
            found.stacks.merge(id, copies, Integer::sum);
            ContainerComponent contents = stack.get(DataComponentTypes.CONTAINER);
            if (contents == null) return;
            int nestedCopies = Math.multiplyExact(copies, stack.getCount());
            for (ItemStack nested : contents.iterateNonEmpty()) snapshotStack(found, nested, nestedCopies);
        }
        
        void progress(MinecraftServer server) { if (System.currentTimeMillis()-lastProgress<2000) return; lastProgress=System.currentTimeMillis(); message(server,"Progreso " + done + "/" + total + " chunks (" + (done*100/total) + "%).",Formatting.GRAY); }
        void message(MinecraftServer server,String message,Formatting color) { ServerPlayerEntity p=server.getPlayerManager().getPlayer(requester); if(p!=null) p.sendMessage(Text.empty().append(Text.literal("[Finder] ").formatted(Formatting.DARK_GRAY)).append(Text.literal(message).formatted(color)),false); }
    }

    public static boolean isStorage(String id) { return id.equals("minecraft:chest") || id.equals("minecraft:trapped_chest") || id.equals("minecraft:barrel") || id.equals("minecraft:dispenser") || id.equals("minecraft:dropper") || id.endsWith("_shulker_box"); }
}
