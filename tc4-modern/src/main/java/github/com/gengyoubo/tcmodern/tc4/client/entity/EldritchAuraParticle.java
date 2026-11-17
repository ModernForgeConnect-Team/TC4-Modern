package github.com.gengyoubo.tcmodern.tc4.client.entity;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.entity.Entity;

/** Modern particle-engine host for TC4's dark, entity-following FXWispEG. */
public final class EldritchAuraParticle extends TextureSheetParticle {
    private final Entity target;
    private static final ParticleRenderType TYPE = new ParticleRenderType() {
        @Override public void begin(BufferBuilder buffer, TextureManager textures) {
            RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader); RenderSystem.setShaderTexture(0, Tc4Fx.PARTICLES);
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }
        @Override public void end(Tesselator tessellator) { tessellator.end(); RenderSystem.depthMask(true); }
    };
    private EldritchAuraParticle(ClientLevel level, Entity target) {
        super(level, target.getX() + (level.random.nextFloat() - level.random.nextFloat()) * 0.2,
                target.getY() + 0.24 * target.getBbHeight(), target.getZ() + (level.random.nextFloat() - level.random.nextFloat()) * 0.2);
        this.target = target;
        xd = random.nextGaussian() * 0.03; yd = -0.05; zd = random.nextGaussian() * 0.03;
        lifetime = 40 + random.nextInt(18); quadSize = 0.2F; hasPhysics = true;
        rCol = random.nextFloat() * 0.05F; gCol = random.nextFloat() * 0.05F; bCol = random.nextFloat() * 0.05F;
    }
    public static void spawn(Entity target) {
        if (target.level() instanceof ClientLevel level) Minecraft.getInstance().particleEngine.add(new EldritchAuraParticle(level, target));
    }
    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime || target.isRemoved()) { remove(); return; }
        if (!onGround) setPos(x + target.getDeltaMovement().x, y, z + target.getDeltaMovement().z);
        move(xd, yd, zd); xd *= 0.98; yd *= 0.98; zd *= 0.98;
        if (onGround) { xd *= 0.85; zd *= 0.85; }
        alpha = 0.2F * (1.0F - (float) age / lifetime);
    }
    @Override protected float getU0() { return (age % 13) / 16.0F; }
    @Override protected float getU1() { return getU0() + 0.0624375F; }
    @Override protected float getV0() { return 0.1875F; }
    @Override protected float getV1() { return 0.2499375F; }
    @Override protected int getLightColor(float partialTick) { return LightTexture.FULL_BRIGHT; }
    @Override public ParticleRenderType getRenderType() { return TYPE; }
}
