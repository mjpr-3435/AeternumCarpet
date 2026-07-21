package smp.aeternum.carpet.pistonaxe;

import net.minecraft.block.PistonBlock;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Independent server-side implementation of the familiar piston-head bedrock routine.
 * The target is never edited by this class; it only performs normal player interactions.
 * Behavioural reference: bunnyi116/fabric-bedrock-miner (GPL-3), reimplemented here.
 */
public final class PistonAxeRunner {
    private static final List<Task> TASKS = new ArrayList<>();
    private PistonAxeRunner() { }
    public static boolean start(ServerPlayerInteractionManager manager,ServerPlayerEntity player,ServerWorld world,BlockPos target){
        if(TASKS.stream().anyMatch(t->t.player.getUuid().equals(player.getUuid())))return false;
        if(!requiredPick(player)){notice(player,"Debes atacar con pico de netherita Eficiencia V.");return true;}
        if(player.getStatusEffect(StatusEffects.HASTE)==null||player.getStatusEffect(StatusEffects.HASTE).getAmplifier()<1){notice(player,"La rutina requiere Prisa II.");return true;}
        if(!hasHotbar(player,Items.PISTON)||!hasHotbar(player,Items.REDSTONE_TORCH)||!hasHotbar(player,Items.SLIME_BLOCK)){notice(player,"Pon piston, antorcha y slime en la barra rapida.");return true;}
        TASKS.add(new Task(manager,player,world,target.toImmutable()));return true;
    }
    public static boolean canUse(ServerPlayerEntity player){return requiredPick(player)&&player.getStatusEffect(StatusEffects.HASTE)!=null&&player.getStatusEffect(StatusEffects.HASTE).getAmplifier()>=1;}
    public static void tick(MinecraftServer ignored){Iterator<Task>it=TASKS.iterator();while(it.hasNext())if(it.next().tick())it.remove();}
    private static boolean hasHotbar(ServerPlayerEntity p,Item item){for(int i=0;i<9;i++)if(p.getInventory().getStack(i).isOf(item))return true;return false;}
    private static boolean requiredPick(ServerPlayerEntity p){return p.getMainHandStack().isOf(Items.NETHERITE_PICKAXE)&&level(p.getMainHandStack(),Enchantments.EFFICIENCY)>=5;}
    private static int level(ItemStack s,RegistryKey<Enchantment> key){for(var e:s.getEnchantments().getEnchantmentEntries())if(e.getKey().matchesKey(key))return e.getIntValue();return 0;}
    private static void notice(ServerPlayerEntity p,String s){p.sendMessage(Text.empty().append(Text.literal("[PistonAxe] ").formatted(Formatting.DARK_GRAY)).append(Text.literal(s).formatted(Formatting.RED)),false);}
    private enum Stage { FIND, PISTON, SLIME, TORCH, WAIT, EXECUTE, CLEANUP }
    private static final class Task {
        final ServerPlayerInteractionManager manager; final ServerPlayerEntity player; final ServerWorld world; final BlockPos target; final int oldSlot;
        Plan plan; Stage stage=Stage.FIND; int delay; int wait; final Map<BlockPos,Item> placed=new LinkedHashMap<>(); final List<Item> consumed=new ArrayList<>();
        Task(ServerPlayerInteractionManager m,ServerPlayerEntity p,ServerWorld w,BlockPos target){manager=m;player=p;world=w;this.target=target;oldSlot=selectedSlot(p);}
        boolean tick(){
            if(!player.isAlive()||player.getWorld()!=world||!PistonAxeStore.enabled(player.getUuid()))return done();
            if(delay-->0)return false;
            if(world.getBlockState(target).isAir())stage=Stage.CLEANUP;
            switch(stage){
                case FIND -> {plan=Plan.pick(world,target);if(plan==null)return done();stage=Stage.PISTON;}
                case PISTON -> {if(!place(plan.piston,plan.pistonFace,Items.PISTON))return retry();stage=Stage.SLIME;delay=3;}
                case SLIME -> {if(!place(plan.slime,plan.torchFace,Items.SLIME_BLOCK))return retry();stage=Stage.TORCH;}
                case TORCH -> {if(!place(plan.torch,plan.torchFace,Items.REDSTONE_TORCH))return retry();stage=Stage.WAIT;delay=3;}
                case WAIT -> {if(++wait>25)return retry();if(world.getBlockState(plan.piston).getBlock() instanceof PistonBlock&&world.getBlockState(plan.piston).get(PistonBlock.EXTENDED))stage=Stage.EXECUTE;}
                case EXECUTE -> {if(world.getBlockState(plan.piston).getBlock() instanceof PistonBlock){select(oldSlot);manager.tryBreakBlock(plan.piston);place(plan.piston,plan.triggerFace,Items.PISTON);}stage=Stage.CLEANUP;delay=4;}
                case CLEANUP -> {cleanup();return done();}
            }
            return false;
        }
        // A failed attempt stops here. Never cycle through plans while altering the surroundings.
        private boolean retry(){cleanup();return done();}
        private boolean place(BlockPos pos,Direction face,Item item){
            if(!world.getBlockState(pos).isReplaceable())return false;
            if(!select(item))return false;
            orient(face);
            BlockPos hit=pos.offset(face.getOpposite());
            ActionResult result=manager.interactBlock(player,world,player.getMainHandStack(),Hand.MAIN_HAND,new BlockHitResult(Vec3d.ofCenter(hit),face,hit,false));
            boolean placedNow=result.isAccepted()&&!world.getBlockState(pos).isReplaceable();
            if(placedNow){placed.put(pos,item);consumed.add(item);}
            return placedNow;
        }
        private boolean select(Item item){for(int i=0;i<9;i++)if(player.getInventory().getStack(i).isOf(item)){select(i);return true;}return false;}
        private void select(int slot){player.getInventory().setSelectedSlot(slot);}
        private void orient(Direction d){switch(d){case UP->player.setPitch(90);case DOWN->player.setPitch(-90);case NORTH->{player.setYaw(180);player.setPitch(0);}case SOUTH->{player.setYaw(0);player.setPitch(0);}case WEST->{player.setYaw(90);player.setPitch(0);}case EAST->{player.setYaw(-90);player.setPitch(0);}}}
        private void cleanup(){for(var entry:placed.entrySet()){BlockPos p=entry.getKey();Item item=entry.getValue();if(!p.equals(target)&&isOwnTemporary(p,item)){world.breakBlock(p,false);player.getInventory().offerOrDrop(new ItemStack(item));}}placed.clear();consumed.clear();}
        private boolean isOwnTemporary(BlockPos pos,Item item){return item instanceof BlockItem blockItem&&world.getBlockState(pos).isOf(blockItem.getBlock());}
        private boolean done(){select(oldSlot);return true;}
    }
    private static int selectedSlot(ServerPlayerEntity player){ItemStack hand=player.getMainHandStack();for(int i=0;i<9;i++)if(player.getInventory().getStack(i)==hand)return i;return 0;}
    private record Plan(BlockPos piston,Direction pistonFace,BlockPos torch,Direction torchFace,BlockPos slime,Direction triggerFace){
        static Plan pick(ServerWorld world,BlockPos target){
            // A plan is a piston next to the target, a powered torch adjacent to the piston,
            // and a full-block support below/behind the torch. Both vertical piston faces are tried.
            for(Direction side:Direction.values())for(Direction pistonFace:new Direction[]{Direction.UP,Direction.DOWN}){
                BlockPos piston=target.offset(side);if(piston.offset(pistonFace).equals(target)||!open(world,piston)||!open(world,piston.offset(pistonFace)))continue;
                for(Direction torchOffset:Direction.values())for(Direction torchFace:usableTorchFaces()){
                    BlockPos torch=piston.offset(torchOffset);if(torch.equals(target)||torch.equals(piston.offset(pistonFace))||!open(world,torch))continue;
                    BlockPos slime=torch.offset(torchFace.getOpposite());
                    if(slime.equals(target)||!open(world,slime))continue;
                    if(supported(world,piston.offset(pistonFace.getOpposite()),pistonFace)&&supported(world,slime.offset(torchFace.getOpposite()),torchFace))return new Plan(piston,pistonFace,torch,torchFace,slime,side.getOpposite());
                }
            }
            return null;
        }
        private static Direction[] usableTorchFaces(){return new Direction[]{Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};}
        private static boolean open(World world,BlockPos pos){return world.getBlockState(pos).isReplaceable();}
        private static boolean supported(World world,BlockPos pos,Direction face){return world.getBlockState(pos).isSideSolidFullSquare(world,pos,face);}
    }
}
