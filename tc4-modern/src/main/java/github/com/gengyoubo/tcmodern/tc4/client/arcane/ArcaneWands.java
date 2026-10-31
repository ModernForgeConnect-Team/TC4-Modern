package github.com.gengyoubo.tcmodern.tc4.client.arcane;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.TcModernApi;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import github.com.gengyoubo.tcmodern.api.recipe.TcRecipeAccessors;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static github.com.gengyoubo.tcmodern.tc4.client.arcane.ArcaneWorkbenchAccessors.*;

/** Wand checks for recipe transfer, answered by the original ItemWandCasting with the client player's discounts. */
public final class ArcaneWands {
    private ArcaneWands() {}
    static boolean isWand(LegacyRuntime runtime,LegacyObject stack){
        if(stack==null)return false;var item=runtime.accessor(Holder.class,stack).item();
        return item!=null&&runtime.values().isInstance(item,WAND);
    }
    /**
     * Index of the first candidate the worktable's wand slot accepts (a wand, not a staff) whose vis covers
     * {@code cost} without consuming it, or -1. Empty candidates and modern-only items are skipped.
     */
    public static int firstCovering(List<ItemStack> candidates,Map<String,Integer> cost){
        if(cost.isEmpty())return -1;
        var available=TcModernApi.runtime();if(available.isEmpty())return -1;
        var runtime=available.get();
        java.util.function.Function<LegacyObject,Integer> search=player->{
            var list=runtime.accessor(TcRecipeAccessors.AspectListFactory.class).create();var add=runtime.accessor(TcRecipeAccessors.Aspects.class,list);
            var lookup=runtime.accessor(TcAspectAccessors.AspectLookup.class);
            for(var entry:cost.entrySet()){var aspect=lookup.find(entry.getKey());if(aspect==null)return -1;add.add(aspect,entry.getValue());}
            for(int i=0;i<candidates.size();i++){
                var candidate=candidates.get(i);if(candidate.isEmpty())continue;
                try{
                    var stack=runtime.mappings().toLegacy(LegacyMappings.ITEM_STACK,candidate).orElse(null);
                    if(!isWand(runtime,stack))continue;
                    var wand=runtime.accessor(Wand.class,runtime.accessor(Holder.class,stack).item());
                    if(!wand.staff(stack)&&wand.covers(stack,player,list,false))return i;
                }catch(RuntimeException unsupported){/* A stack without a legacy form is never a wand. */}
            }
            return -1;
        };
        // Without a client player the original skips worn vis discounts, which can only under-select.
        return runtime.supports(LegacyFeatures.CLIENT_PLAYER)?runtime.withClientPlayer(search):search.apply(null);
    }
}
