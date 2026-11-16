package github.com.gengyoubo.tcmodern.tc4.entity;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import github.com.gengyoubo.tcmodern.tc4.entity.item.AspectOrb;
import github.com.gengyoubo.tcmodern.tc4.entity.item.FollowingItem;
import github.com.gengyoubo.tcmodern.tc4.entity.item.SpecialItem;
import github.com.gengyoubo.tcmodern.tc4.entity.projectile.*;

/**
 * Modern hosts for the remaining original TC4 entity classes. EntityWatcher has no host: TC4 never registers or
 * spawns it. EntityGolemBobber keeps MFCL's own host because original golems still drive it; modern golems cast
 * their own {@link github.com.gengyoubo.tcmodern.tc4.entity.golem.GolemBobber}.
 */
public final class Tc4EntityHosts {
    private Tc4EntityHosts() { }

    public static void install(LegacyRuntime runtime) {
        TravelingTrunk.host(runtime);
        FireBat.host(runtime);
        EldritchGuardian.host(runtime);
        EldritchCrab.host(runtime);
        MindSpider.host(runtime);
        ThaumicSlime.host(runtime);
        CultistCleric.host(runtime);
        CultistKnight.host(runtime);
        Wisp.host(runtime);
        Pech.host(runtime);
        CultistPortal.host(runtime);
        CultistLeader.host(runtime);
        EldritchGolem.host(runtime);
        EldritchWarden.host(runtime);
        Dart.host(runtime);
        FrostShard.host(runtime);
        ExplosiveOrb.host(runtime);
        ShockOrb.host(runtime);
        GolemOrb.host(runtime);
        PechBlast.host(runtime);
        EldritchOrb.host(runtime);
        PrimalOrb.host(runtime);
        PrimalArrow.host(runtime);
        BottleTaint.host(runtime);
        Alumentum.host(runtime);
        Ember.host(runtime);
        AspectOrb.host(runtime);
        FollowingItem.host(runtime);
        SpecialItem.host(runtime);
    }
}
