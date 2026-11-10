package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.util.*;

/** Invokes the real TESR directly, preserving original failures rather than dispatcher crash-report failures. */
public final class LegacyTileRendering {
    private final LegacyServices services;
    private final Object dispatcher;
    private final Method renderer, draw;
    public LegacyTileRendering(LegacyServices services) throws Exception {
        this.services = services; var loader = services.loader();
        var type = loader.loadClass("net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher");
        dispatcher = type.getField("field_147556_a").get(null);
        renderer = type.getMethod("func_147547_b", loader.loadClass("net.minecraft.tileentity.TileEntity"));
        draw = loader.loadClass("net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer").getMethod("func_147500_a", loader.loadClass("net.minecraft.tileentity.TileEntity"), double.class,double.class,double.class,float.class);
    }
    public boolean hasRenderer(Object tile) throws Exception { return tile != null && renderer.invoke(dispatcher, tile) != null; }
    public List<LegacyVertexStream.Primitive> capture(Object tile, float partialTick) throws Exception {
        return capture(tile, partialTick, 0,0,0);
    }
    public List<LegacyVertexStream.Primitive> capture(Object tile, float partialTick, double x, double y, double z) throws Exception {
        return services.items().inLegacy(() -> {
            Object implementation = tile == null ? null : renderer.invoke(dispatcher, tile);
            if (implementation == null) return List.of();
            try (var frame = LegacyWorldGraphics.begin()) {
                LegacyWorldGraphics.translate(-x,-y,-z);
                draw.invoke(implementation, tile, x,y,z,partialTick);
                return services.items().resolveIcons(frame.primitives());
            }
        });
    }
    public void view(Object world, double x, double y, double z, float yaw, float pitch, int ticks) throws Exception {
        view(world,x,y,z,yaw,pitch,ticks,null,false);
    }
    /** Includes the current host hand, even when the held model is hidden or has no legacy renderer. */
    public void view(Object world, double x, double y, double z, float yaw, float pitch, int ticks, LegacyClientData.Stack held) throws Exception {
        view(world,x,y,z,yaw,pitch,ticks,held,true);
    }
    private void view(Object world, double x, double y, double z, float yaw, float pitch, int ticks, LegacyClientData.Stack held, boolean mirrorHeld) throws Exception {
        services.items().inLegacy(() -> {
            var loader = services.loader();
            services.containers().preparePlayer();
            var mc = loader.loadClass("net.minecraft.client.Minecraft"); Object client = mc.getMethod("func_71410_x").invoke(null);
            Object player = mc.getField("field_71439_g").get(client);
            if(mirrorHeld)services.playerInventory().held(player,held);
            var entity = loader.loadClass("net.minecraft.entity.Entity");
            entity.getField("field_70170_p").set(player,world);
            for (String name : List.of("field_70165_t","field_70169_q","field_70142_S")) entity.getField(name).setDouble(player,x);
            for (String name : List.of("field_70163_u","field_70167_r","field_70137_T")) entity.getField(name).setDouble(player,y);
            for (String name : List.of("field_70161_v","field_70166_s","field_70136_U")) entity.getField(name).setDouble(player,z);
            entity.getField("field_70177_z").setFloat(player,yaw); entity.getField("field_70125_A").setFloat(player,pitch); entity.getField("field_70173_aa").setInt(player,ticks);
            mc.getField("field_71451_h").set(client,player);
            var dispatch = loader.loadClass("net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher");
            dispatch.getField("field_147550_f").set(dispatcher,world); dispatch.getField("field_147551_g").set(dispatcher,player);
            dispatch.getField("field_147554_b").setDouble(null,x); dispatch.getField("field_147555_c").setDouble(null,y); dispatch.getField("field_147552_d").setDouble(null,z);
            var managerType = loader.loadClass("net.minecraft.client.renderer.entity.RenderManager"); var manager = managerType.getField("field_78727_a").get(null);
            managerType.getField("field_78722_g").set(manager,world); managerType.getField("field_78734_h").set(manager,player);
            managerType.getField("field_78735_i").setFloat(manager,yaw); managerType.getField("field_78732_j").setFloat(manager,pitch);
            managerType.getField("field_78725_b").setDouble(null,x); managerType.getField("field_78726_c").setDouble(null,y); managerType.getField("field_78723_d").setDouble(null,z);
            var active = loader.loadClass("net.minecraft.client.renderer.ActiveRenderInfo");
            double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
            // Same assignments as ActiveRenderInfo.updateRenderInfo: rotationX (_d), rotationZ (_f), rotationYZ (_g),
            // rotationXY (_h), rotationXZ (_e). Billboards (particles, node beams) are built from these five.
            active.getField("field_74588_d").setFloat(null,(float)Math.cos(ry)); active.getField("field_74586_f").setFloat(null,(float)Math.sin(ry));
            active.getField("field_74587_g").setFloat(null,(float)(-Math.sin(ry)*Math.sin(rp))); active.getField("field_74596_h").setFloat(null,(float)(Math.cos(ry)*Math.sin(rp)));
            active.getField("field_74589_e").setFloat(null,(float)Math.cos(rp));
            return null;
        });
    }
}
