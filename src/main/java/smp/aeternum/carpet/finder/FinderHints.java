package smp.aeternum.carpet.finder;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Short-lived server particles replacing Finder's old RCON display entities. */
public final class FinderHints {
    private static final List<Hint> HINTS=new ArrayList<>();
    private static final List<Marker> MARKERS=new ArrayList<>();
    private static int markerSequence;
    private FinderHints() { }
    public static void preview(ServerPlayerEntity player,FinderPlace place){HINTS.removeIf(h->h.player.equals(player.getUuid())&&h.kind==Kind.PREVIEW);HINTS.add(new Hint(player.getUuid(),player.getWorld(),edges(place),60,Kind.PREVIEW,ParticleTypes.END_ROD));}
    /** Finder.py drew temporary End Rod routes from the querying player to nearby matches. */
    public static void mark(ServerPlayerEntity player,FinderPlace place,List<BlockPos> found){HINTS.removeIf(h->h.player.equals(player.getUuid())&&h.kind==Kind.FOUND);MARKERS.removeIf(m->{if(m.player.equals(player.getUuid())){kill(m);return true;}return false;});for(BlockPos pos:found){String tag="aeternum_finder_found_"+player.getUuidAsString().replace('-','_')+"_"+(++markerSequence);String nbt="{block_state:{Name:\"minecraft:lime_stained_glass\"},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[1f,1f,1f],translation:[0f,0f,0f]},Glowing:1b,Invisible:1b,Invulnerable:1b,PersistenceRequired:1b,Silent:1b,Tags:[\""+tag+"\"]}";run(player.getWorld(),"summon minecraft:block_display "+(pos.getX()+".0")+" "+(pos.getY()+".0")+" "+(pos.getZ()+".0")+" "+nbt);MARKERS.add(new Marker(player.getUuid(),player.getWorld(),tag,160));}List<Vec3d> route=new ArrayList<>();Vec3d origin=player.getPos().add(0,.8,0);for(BlockPos target:found){if(target.getSquaredDistance(player.getBlockPos())>128*128)continue;addLine(route,origin,Vec3d.ofCenter(target).add(0,.3,0),1);if(route.size()>2600)break;}if(!route.isEmpty())HINTS.add(new Hint(player.getUuid(),player.getWorld(),route,160,Kind.FOUND,ParticleTypes.END_ROD));}
    public static void flash(ServerWorld world,ServerPlayerEntity player,BlockPos pos,boolean good){if(player!=null)HINTS.add(new Hint(player.getUuid(),world,List.of(Vec3d.ofCenter(pos)),40,Kind.FLASH,good?ParticleTypes.HAPPY_VILLAGER:ParticleTypes.ANGRY_VILLAGER));}
    public static int clean(MinecraftServer server){MARKERS.clear();HINTS.removeIf(h->h.kind==Kind.FOUND);int removed=0;for(ServerWorld world:server.getWorlds()){for(DisplayEntity.BlockDisplayEntity entity:world.getEntitiesByType(EntityType.BLOCK_DISPLAY,e->true)){entity.discard();removed++;}}return removed;}
    public static void tick(MinecraftServer server){Iterator<Marker>markers=MARKERS.iterator();while(markers.hasNext()){Marker m=markers.next();if(--m.ticks<0){kill(m);markers.remove();}}Iterator<Hint>it=HINTS.iterator();while(it.hasNext()){Hint h=it.next();ServerPlayerEntity p=server.getPlayerManager().getPlayer(h.player);if(p==null||p.getWorld()!=h.world||--h.ticks<0){it.remove();continue;}int interval=h.kind==Kind.FOUND?40:5;if(h.ticks%interval!=0)continue;for(Vec3d pos:h.positions)h.world.spawnParticles(p,h.particle,true,false,pos.x,pos.y,pos.z,1,h.kind==Kind.PREVIEW?.03:0,h.kind==Kind.PREVIEW?.03:0,h.kind==Kind.PREVIEW?.03:0,0);}}
    private static List<Vec3d> edges(FinderPlace p){List<Vec3d>out=new ArrayList<>();int step=Math.max(1,Math.max(Math.max(p.maxX-p.minX,p.maxY-p.minY),p.maxZ-p.minZ)/48);for(int x=p.minX;x<=p.maxX;x+=step)for(int y:new int[]{p.minY,p.maxY})for(int z:new int[]{p.minZ,p.maxZ})out.add(new Vec3d(x,y,z));for(int y=p.minY;y<=p.maxY;y+=step)for(int x:new int[]{p.minX,p.maxX})for(int z:new int[]{p.minZ,p.maxZ})out.add(new Vec3d(x,y,z));for(int z=p.minZ;z<=p.maxZ;z+=step)for(int x:new int[]{p.minX,p.maxX})for(int y:new int[]{p.minY,p.maxY})out.add(new Vec3d(x,y,z));return out;}
    private static void addLine(List<Vec3d>out,Vec3d from,Vec3d to,int step){double dx=to.x-from.x,dy=to.y-from.y,dz=to.z-from.z;int n=Math.max(1,(int)(Math.sqrt(dx*dx+dy*dy+dz*dz)/step));for(int i=0;i<=n;i++){double t=(double)i/n;out.add(new Vec3d(from.x+dx*t,from.y+dy*t,from.z+dz*t));}}
    private enum Kind { PREVIEW, FOUND, FLASH }
    private static final class Hint {final UUID player;final ServerWorld world;final List<Vec3d>positions;int ticks;final Kind kind;final net.minecraft.particle.ParticleEffect particle;Hint(UUID p,ServerWorld w,List<Vec3d>ps,int t,Kind k,net.minecraft.particle.ParticleEffect e){player=p;world=w;positions=ps;ticks=t;kind=k;particle=e;}}
    private static void run(ServerWorld world,String command){world.getServer().getCommandManager().executeWithPrefix(world.getServer().getCommandSource().withWorld(world).withSilent(),command);}
    private static void kill(Marker marker){run(marker.world,"kill @e[tag="+marker.tag+"]");}
    private static final class Marker {final UUID player;final ServerWorld world;final String tag;int ticks;Marker(UUID p,ServerWorld w,String tag,int t){player=p;world=w;this.tag=tag;ticks=t;}}
}
