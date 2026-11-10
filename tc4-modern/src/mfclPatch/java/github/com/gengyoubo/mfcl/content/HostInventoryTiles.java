package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.Callable;

/** Live old IInventory views of native item handlers for old neighbor lookups. Cached views never outlive their native tile. */
final class HostInventoryTiles {
    private final ModernContentBridge content;
    private final ServerLevel level;
    private final Map<BlockEntity,WeakReference<Object>> peers=new WeakHashMap<>();
    HostInventoryTiles(ModernContentBridge content,ServerLevel level){this.content=content;this.level=level;}
    private <T> T host(Callable<T> action) {
        if(!level.getServer().isSameThread())throw new IllegalStateException("Item handler bridge requires the server thread");
        Thread thread=Thread.currentThread();ClassLoader previous=thread.getContextClassLoader();
        try{thread.setContextClassLoader(getClass().getClassLoader());return action.call();}
        catch(RuntimeException failure){throw failure;}catch(Exception failure){throw new IllegalStateException("Cannot access native item handler",failure);}
        finally{thread.setContextClassLoader(previous);}
    }
    Object find(BlockPos position) {
        return host(()->{
            if(!level.hasChunkAt(position))return null;
            var tile=level.getBlockEntity(position);
            if(tile==null||tile.isRemoved()||tile instanceof LegacyBlockEntity)return null;
            if(!tile.getCapability(ForgeCapabilities.ITEM_HANDLER,null).isPresent()){peers.remove(tile);return null;}
            var cached=peers.get(tile);Object peer=cached==null?null:cached.get();if(peer!=null)return peer;
            // A native chest becomes an old TileEntityChest (generators cast to it before filling loot). Old code joins double
            // chests itself through the neighbouring chest, so that view covers this half only, not Forge's combined handler.
            boolean chest=tile instanceof ChestBlockEntity;
            peer=LegacyInventoryPeers.create(content.services(),content.services().worlds().bound(level),position.getX(),position.getY(),position.getZ(),new Endpoint(tile,chest),chest);
            peers.put(tile,new WeakReference<>(peer));return peer;
        });
    }
    private final class Endpoint implements HostInventoryAccess {
        private final BlockEntity tile;
        private final BlockPos position;
        private final IItemHandler single;
        Endpoint(BlockEntity tile,boolean chest){this.tile=tile;position=tile.getBlockPos().immutable();single=chest?new InvWrapper((Container)tile):null;}
        private IItemHandler handler() {
            if(content.services().worlds().findBound(level)==null||tile.isRemoved()||!level.hasChunkAt(position)||level.getBlockEntity(position)!=tile)return null;
            return single!=null?single:tile.getCapability(ForgeCapabilities.ITEM_HANDLER,null).orElse(null);
        }
        @Override public boolean live(){return host(()->handler()!=null);}
        @Override public int size(){return host(()->{var items=handler();return items==null?0:items.getSlots();});}
        @Override public LegacyClientData.Stack get(int slot) {
            return host(()->{
                var items=handler();if(items==null||slot<0||slot>=items.getSlots())return null;
                var stack=items.getStackInSlot(slot);return stack.isEmpty()?null:content.toLegacy(stack);
            });
        }
        @Override public int limit(int slot){return host(()->{var items=handler();return items==null||slot<0||slot>=items.getSlots()?0:items.getSlotLimit(slot);});}
        @Override public int insert(int slot,LegacyClientData.Stack stack,boolean simulate) {
            if(stack==null||stack.count()<=0)return 0;
            return host(()->{
                var items=handler();if(items==null||slot<0||slot>=items.getSlots())return 0;
                ItemStack offered;
                try{offered=content.gameplayStack(stack);}catch(RuntimeException unmapped){return 0;}
                Callable<Integer> action=()->{
                    int moved=offered.getCount()-items.insertItem(slot,offered,simulate).getCount();
                    if(!simulate&&moved>0)tile.setChanged();return moved;
                };
                return simulate?action.call():content.logicTransaction(level,position,action,false);
            });
        }
        @Override public int extract(int slot,int amount,boolean simulate) {
            if(amount<=0)return 0;
            return host(()->{
                var items=handler();if(items==null||slot<0||slot>=items.getSlots())return 0;
                Callable<Integer> action=()->{
                    int moved=items.extractItem(slot,amount,simulate).getCount();
                    if(!simulate&&moved>0)tile.setChanged();return moved;
                };
                return simulate?action.call():content.logicTransaction(level,position,action,false);
            });
        }
    }
}
