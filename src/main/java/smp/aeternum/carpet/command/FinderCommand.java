package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.finder.FinderContainer;
import smp.aeternum.carpet.finder.FinderHints;
import smp.aeternum.carpet.finder.FinderItemNames;
import smp.aeternum.carpet.finder.FinderPlace;
import smp.aeternum.carpet.finder.FinderScanner;
import smp.aeternum.carpet.finder.FinderStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Native Finder commands. Areas are explicit: no hidden pos1/pos2 state. */
public final class FinderCommand {
    private static final String NEAR_ME = "near_me";
    private static final int NEAR_ME_RADIUS = 20;
    private FinderCommand() { }
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("find").requires(s -> AeternumCarpetSettings.commandFinder)
            .then(argument("lugar", StringArgumentType.word()).suggests(FinderCommand::suggestQueryPlaces)
                .then(argument("texto", StringArgumentType.greedyString()).executes(c -> query(c, false)))));
        d.register(literal("count").requires(s -> AeternumCarpetSettings.commandFinder)
            .then(argument("lugar", StringArgumentType.word()).suggests(FinderCommand::suggestQueryPlaces)
                .then(argument("texto", StringArgumentType.greedyString()).executes(c -> query(c, true)))));
        d.register(literal("finder").requires(s -> AeternumCarpetSettings.commandFinder && s.hasPermissionLevel(2))
            .then(literal("help").executes(FinderCommand::help))
            .then(literal("list").executes(FinderCommand::list))
            .then(literal("remove").then(argument("name",StringArgumentType.word()).suggests(FinderCommand::suggestStoredPlaces).executes(FinderCommand::remove)))
            .then(literal("check").executes(c -> check(c,null)).then(argument("pos",BlockPosArgumentType.blockPos()).executes(c -> check(c,BlockPosArgumentType.getBlockPos(c,"pos")))))
            .then(literal("toggle-block").executes(c -> toggle(c,null)).then(argument("pos",BlockPosArgumentType.blockPos()).executes(c -> toggle(c,BlockPosArgumentType.getBlockPos(c,"pos")))))
            .then(literal("show_excludes").executes(FinderCommand::excludes))
            .then(literal("clean").executes(FinderCommand::clean))
            .then(literal("scan")
                .then(argument("coord1",BlockPosArgumentType.blockPos()).then(argument("coord2",BlockPosArgumentType.blockPos()).then(argument("name",StringArgumentType.word()).executes(FinderCommand::scan))))));
    }
    private static CompletableFuture<Suggestions> suggestQueryPlaces(CommandContext<ServerCommandSource> c, SuggestionsBuilder b){return CommandSource.suggestMatching(Stream.concat(Stream.of(NEAR_ME),FinderStore.all().stream().map(p->p.name)),b);}
    private static CompletableFuture<Suggestions> suggestStoredPlaces(CommandContext<ServerCommandSource> c, SuggestionsBuilder b){return CommandSource.suggestMatching(FinderStore.all().stream().map(p->p.name),b);}
    private static int help(CommandContext<ServerCommandSource> c){feedback(c,"/finder scan <coord1> <coord2> <nombre>: crea o actualiza un área.",Formatting.GRAY);feedback(c,"/finder list: muestra las áreas registradas.",Formatting.GRAY);feedback(c,"/finder check, toggle-block, show_excludes, clean y remove <nombre>: inspección y mantenimiento.",Formatting.GRAY);feedback(c,"/find <lugar|near_me> <texto> y /count <lugar|near_me> <texto>: buscan en un área o a 20 bloques.",Formatting.GRAY);return 1;}
    private static int scan(CommandContext<ServerCommandSource> c)throws CommandSyntaxException {
        String name=StringArgumentType.getString(c,"name"); if(!FinderStore.isValidName(name)){error(c,"Nombre inválido: usa solo letras (a-z, A-Z) y números (0-9), sin espacios ni símbolos.");return 0;}
        BlockPos a=BlockPosArgumentType.getBlockPos(c,"coord1"), b=BlockPosArgumentType.getBlockPos(c,"coord2"); ServerWorld world=c.getSource().getWorld();
        long volume=((long)Math.abs(a.getX()-b.getX())+1)*((long)Math.abs(a.getY()-b.getY())+1)*((long)Math.abs(a.getZ()-b.getZ())+1);
        if(volume>10_000_000L){error(c,"Región demasiado grande: "+volume+" bloques. Límite: 10000000.");return 0;}
        FinderPlace old=FinderStore.get(name); FinderPlace place=new FinderPlace(name,world.getRegistryKey(),a,b);
        if(old!=null){place.createdAt=old.createdAt;place.excluded.addAll(old.excluded);}
        if(place.chunks()>256){error(c,"Región demasiado grande: "+place.chunks()+" chunks. Límite: 256 (16x16).");return 0;}
        UUID requester=player(c)==null?null:player(c).getUuid();if(requester==null){error(c,"El escaneo debe iniciarlo un jugador.");return 0;}
        FinderStore.put(place);
        if(!FinderScanner.start(world,place,requester)){error(c,"Ya hay un scan activo para "+name+".");return 0;}
        feedback(c,"Scan iniciado: "+place.name+" §8| §f"+volume+" bloques §8| §f"+place.chunks()+" chunks.",Formatting.GRAY);return 1;
    }
    private static int list(CommandContext<ServerCommandSource> c){if(FinderStore.all().isEmpty()){feedback(c,"No hay lugares guardados. Usa /finder scan <coord1> <coord2> <nombre>.",Formatting.GRAY);return 1;}feedback(c,"Lugares guardados:",Formatting.GRAY);for(FinderPlace p:FinderStore.all()){String run="/finder scan "+p.minX+" "+p.minY+" "+p.minZ+" "+p.maxX+" "+p.maxY+" "+p.maxZ+" "+p.name;c.getSource().sendFeedback(()->Text.empty().append(Text.literal("• ").formatted(Formatting.DARK_GRAY)).append(Text.literal(p.name).formatted(Formatting.AQUA)).append(Text.literal(" | "+p.containers.size()+" contenedores | "+p.dimension).formatted(Formatting.GRAY)).append(Text.literal(" [↻]").setStyle(Style.EMPTY.withColor(Formatting.AQUA).withClickEvent(new ClickEvent.RunCommand(run)).withHoverEvent(new HoverEvent.ShowText(Text.literal("Actualizar este scan"))))).append(Text.literal(" [X]").setStyle(Style.EMPTY.withColor(Formatting.RED).withClickEvent(new ClickEvent.RunCommand("/finder remove "+p.name)).withHoverEvent(new HoverEvent.ShowText(Text.literal("Eliminar este lugar"))))),false);}return 1;}
    private static int remove(CommandContext<ServerCommandSource> c){String n=StringArgumentType.getString(c,"name");if(!FinderStore.remove(n)){error(c,"No existe el lugar: "+n);return 0;}success(c,"Lugar eliminado: "+n);list(c);return 1;}
    private static int check(CommandContext<ServerCommandSource> c,BlockPos pos){ServerPlayerEntity p=player(c);if(pos==null){if(p==null)return 0;pos=p.getBlockPos().down();}String id=Registries.BLOCK.getId(c.getSource().getWorld().getBlockState(pos).getBlock()).toString();boolean valid=FinderScanner.isStorage(id);FinderHints.flash(c.getSource().getWorld(),p,pos,valid);if(valid)success(c,"finder check §7["+pos.toShortString()+"]§f: entra como contenedor (§e"+id+"§f).");else error(c,"finder check §7["+pos.toShortString()+"]§f: no es un contenedor soportado (§e"+id+"§f).");return valid?1:0;}
    private static int toggle(CommandContext<ServerCommandSource> c,BlockPos pos){ServerPlayerEntity p=player(c);if(pos==null){if(p==null)return 0;pos=p.getBlockPos().down();}int changed=0;boolean excluded=false;for(FinderPlace place:FinderStore.all())if(place.belongsTo(c.getSource().getWorld().getRegistryKey())&&place.contains(pos)){String key=place.key(pos);excluded=place.excluded.add(key);if(!excluded)place.excluded.remove(key);changed++;}if(changed==0){error(c,"La posición no pertenece a ningún lugar.");return 0;}FinderStore.save();FinderHints.flash(c.getSource().getWorld(),p,pos,!excluded);if(excluded)feedback(c,"Bloque excluido de scan: §7["+pos.toShortString()+"]",Formatting.YELLOW);else success(c,"Bloque removido de exclusión: §7["+pos.toShortString()+"]");return 1;}
    private static int excludes(CommandContext<ServerCommandSource> c){int n=0;for(FinderPlace p:FinderStore.all())if(!p.excluded.isEmpty()){feedback(c,p.name+": "+String.join("; ",p.excluded),Formatting.GRAY);n+=p.excluded.size();}if(n==0)feedback(c,"No hay bloques excluidos.",Formatting.GRAY);return 1;}
    private static int clean(CommandContext<ServerCommandSource> c){int removed=FinderHints.clean(c.getSource().getServer());if(removed>0)success(c,"Se eliminaron §e"+removed+"§f entidades block_display.");else feedback(c,"No se encontraron entidades block_display cargadas.",Formatting.GRAY);return removed;}
    private static int query(CommandContext<ServerCommandSource> c,boolean count){String placeName=StringArgumentType.getString(c,"lugar");String raw=StringArgumentType.getString(c,"texto");String item=normalize(raw);if(item==null){error(c,"Ítem no válido: §e"+raw);return 0;}ServerPlayerEntity player=player(c);FinderPlace p;if(NEAR_ME.equalsIgnoreCase(placeName)){if(player==null){error(c,"near_me solo puede usarlo un jugador.");return 0;}p=FinderScanner.scanAround(player.getWorld(),player.getBlockPos(),NEAR_ME_RADIUS);}else{p=FinderStore.get(placeName);if(p==null){error(c,"No existe el lugar: §e"+placeName);return 0;}if(!refreshForQuery(c,p))return 0;}int total=0,stacks=0,containers=0;List<BlockPos> found=new ArrayList<>();for(FinderContainer f:p.containers){int amount=f.items.getOrDefault(item,0);if(amount==0)continue;total+=amount;stacks+=f.stacks.getOrDefault(item,0);containers++;found.add(new BlockPos(f.x,f.y,f.z));if(!count)c.getSource().sendFeedback(()->Text.empty().append(Text.literal("• "+f.blockId.replace("minecraft:","")+" ").formatted(Formatting.WHITE)).append(Text.literal(f.x+", "+f.y+", "+f.z).formatted(Formatting.YELLOW)).append(Text.literal(" ("+amount+")").formatted(Formatting.GRAY)).setStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Text.literal("Contenedor encontrado; ruta temporal marcada con partículas.")))),false);}if(!count&&player!=null&&!found.isEmpty())FinderHints.mark(player,p,found);if(count)feedback(c,"§e"+item+"§7 en §b"+p.name+"§7: §f"+total+" items | §e"+stacks+" stacks",Formatting.GRAY);else if(containers>0)success(c,"Coincidencias: §e"+containers+"§f. Ruta temporal marcada.");else feedback(c,"En §b"+p.name+"§7 no hay §f"+item+"§7.",Formatting.GRAY);return 1;}
    /** Every query takes a fresh snapshot; Finder deliberately has no inventory cache. */
    private static boolean refreshForQuery(CommandContext<ServerCommandSource> c,FinderPlace place){ServerWorld world=c.getSource().getServer().getWorld(RegistryKey.of(RegistryKeys.WORLD,Identifier.of(place.dimension)));if(world==null){error(c,"No se pudo abrir la dimensión de "+place.name+".");return false;}try{FinderScanner.refreshNow(world,place);return true;}catch(RuntimeException exception){error(c,"No se pudo actualizar Finder para "+place.name+".");return false;}}
    private static String normalize(String raw){return FinderItemNames.resolve(raw);}
    private static ServerPlayerEntity player(CommandContext<ServerCommandSource> c){return c.getSource().getEntity() instanceof ServerPlayerEntity p?p:null;}
    private static void feedback(CommandContext<ServerCommandSource> c,String text,Formatting color){c.getSource().sendFeedback(()->Text.empty().append(Text.literal("[Finder] ").formatted(Formatting.DARK_GRAY)).append(Text.literal(text).formatted(color)),false);}
    private static void success(CommandContext<ServerCommandSource> c,String text){c.getSource().sendFeedback(()->Text.empty().append(Text.literal("✔ ").formatted(Formatting.GREEN)).append(Text.literal(text).formatted(Formatting.WHITE)),false);}
    private static void error(CommandContext<ServerCommandSource> c,String text){c.getSource().sendFeedback(()->Text.empty().append(Text.literal("✖ ").formatted(Formatting.RED)).append(Text.literal(text).formatted(Formatting.WHITE)),false);}
}
