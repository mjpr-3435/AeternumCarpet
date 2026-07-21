package smp.aeternum.carpet.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import smp.aeternum.carpet.AeternumCarpetSettings;
import smp.aeternum.carpet.pistonaxe.PistonAxeStore;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public final class PistonAxeCommand {
    private PistonAxeCommand() { }
    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(literal("pistonaxe").requires(s->AeternumCarpetSettings.pistonAxe).executes(PistonAxeCommand::toggle)
            .then(literal("whitelist")
                .then(literal("add").then(argument("block",StringArgumentType.word()).executes(c->change(c,true))))
                .then(literal("remove").then(argument("block",StringArgumentType.word()).executes(c->change(c,false))))
                .then(literal("list").executes(PistonAxeCommand::list))));
    }
    private static int toggle(CommandContext<ServerCommandSource> c)throws CommandSyntaxException{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();boolean on=PistonAxeStore.toggle(p.getUuid());feedback(c,on?"Piston Axe activada.":"Piston Axe desactivada.",on?Formatting.GREEN:Formatting.GRAY);return 1;}
    private static int change(CommandContext<ServerCommandSource> c,boolean add)throws CommandSyntaxException{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();String raw=StringArgumentType.getString(c,"block");Identifier id=Identifier.tryParse(raw.contains(":")?raw:"minecraft:"+raw);if(id==null||!Registries.BLOCK.containsId(id)){feedback(c,"Bloque inválido: "+raw,Formatting.RED);return 0;}boolean changed=add?PistonAxeStore.add(p.getUuid(),id.toString()):PistonAxeStore.remove(p.getUuid(),id.toString());feedback(c,changed?(add?"Añadido a tu lista: ":"Quitado de tu lista: ")+id:(add?"Ya estaba en tu lista.":"No estaba en tu lista."),changed?Formatting.GREEN:Formatting.GRAY);return changed?1:0;}
    private static int list(CommandContext<ServerCommandSource> c)throws CommandSyntaxException{ServerPlayerEntity p=c.getSource().getPlayerOrThrow();var blocks=PistonAxeStore.list(p.getUuid());feedback(c,blocks.isEmpty()?"Tu lista blanca está vacía.":"Tu lista blanca: "+String.join(", ",blocks),blocks.isEmpty()?Formatting.GRAY:Formatting.AQUA);return 1;}
    private static void feedback(CommandContext<ServerCommandSource> c,String s,Formatting f){c.getSource().sendFeedback(()->Text.empty().append(Text.literal("[PistonAxe] ").formatted(Formatting.DARK_GRAY)).append(Text.literal(s).formatted(f)),false);}
}
