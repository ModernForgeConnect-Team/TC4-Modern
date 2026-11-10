package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ClientContentBridge {
    private ClientContentBridge() {}
    public static void install(ModernContentBridge bridge, IEventBus bus) {
        ClientLegacyAudio.install(bridge.services());
        ClientLegacyChat.install(bridge.services());
        ExtraUtilitiesWings.install(bridge,bus);
        if(Boolean.getBoolean("mfcl.test.generator"))GeneratorHostSmokeProbe.install(bridge);
        if(Boolean.getBoolean("mfcl.test.filing"))FilingCabinetHostSmokeProbe.install(bridge);
        if(Boolean.getBoolean("mfcl.test.guiSync"))GuiInventorySyncHostSmokeProbe.install(bridge);
        if(Boolean.getBoolean("mfcl.test.mekanismEnergy"))MekanismEnergyHostSmokeProbe.install(bridge);
        if(Boolean.getBoolean("mfcl.test.deepDark"))DeepDarkHostSmokeProbe.install(bridge);
        if(Boolean.getBoolean("mfcl.test.itemAudit"))ItemUseHostAudit.install(bridge);
        if(Boolean.getBoolean("mfcl.test.recipeItems"))RecipeItemHostSmokeProbe.install(bridge);
        if (Boolean.getBoolean("mfcl.test.exitAtTitleScreen") && !Boolean.getBoolean("mfcl.test.neiUiSmoke"))
            ClientStartupSmokeProbe.install(bridge);
        LegacyScanNotifications.install(bridge);
        LegacyItemTooltips.install(bridge);
        LegacyEffects.install(bridge);
        LegacyPotionPostEffects.install(bridge,bus);
        bus.addListener((net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) -> {
            event.registerBlockEntityRenderer(bridge.tileType(), context -> new LegacyBlockEntityRenderer(bridge));
            event.registerEntityRenderer(bridge.legacyEntityType(), context -> new LegacyHostedEntityRenderer(context, bridge));
        });
        bus.addListener((net.minecraftforge.client.event.ModelEvent.ModifyBakingResult event) -> {
            for (var declaration : bridge.declarations().blocks()) for (var state : bridge.blocks().get(declaration.key()).getStateDefinition().getPossibleStates()) {
                int metadata = state.getValue(ModernContentBridge.METADATA);
                if (!bridge.blockRendering().supports(declaration.key(), metadata)) continue;
                var location = net.minecraft.client.renderer.block.BlockModelShaper.stateToModelLocation(ModernContentBridge.id(declaration.key()), state);
                event.getModels().computeIfPresent(location, (id, original) -> new LegacyBlockModel(original, bridge, declaration.key(), metadata));
            }
        });
        bus.addListener((FMLClientSetupEvent event) -> event.enqueueWork(() -> {
            net.minecraft.client.gui.screens.MenuScreens.register(bridge.deviceMenuType(),LegacyDeviceScreen::new);
            for(var fluid:bridge.legacyFluids())net.minecraft.client.renderer.ItemBlockRenderTypes.setRenderLayer(fluid,net.minecraft.client.renderer.RenderType.cutout());
            for (var entry : bridge.declarations().items()) ItemProperties.register(bridge.items().get(entry.key()), ResourceLocation.fromNamespaceAndPath("mfcl", "metadata"),
                    (stack, level, entity, seed) -> ModernContentBridge.metadata(stack));
            // Ticks drawn so far, matching the original held-use icon frames sampled per item.
            for (var entry : bridge.declarations().items()) if (!entry.pulls().isEmpty()) ItemProperties.register(bridge.items().get(entry.key()), ResourceLocation.fromNamespaceAndPath("mfcl", "pull"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? stack.getUseDuration() - entity.getUseItemRemainingTicks() : 0);
        }));
        bus.addListener((net.minecraftforge.client.event.RegisterColorHandlersEvent.Item event) -> {
            for (var entry : bridge.declarations().items()) event.register((stack, tint) -> {
                try { return bridge.services().items().itemColor(bridge.renderSnapshot(stack), tint); }
                catch (Exception failure) { throw new IllegalStateException("Cannot evaluate original item tint: " + entry.key(), failure); }
            }, bridge.items().get(entry.key()));
        });
        bus.addListener((net.minecraftforge.client.event.RegisterColorHandlersEvent.Block event) -> {
            var leaves = bridge.blocks().get("Thaumcraft:blockMagicalLeaves");
            if (leaves != null) event.register((state, level, pos, tint) -> {
                if ((state.getValue(ModernContentBridge.METADATA) & 1) != 0) return 0x8899aa;
                if (level == null || pos == null) return 0x48b518;
                return net.minecraft.client.renderer.BiomeColors.getAverageFoliageColor(level, pos);
            }, leaves);
        });
        if (Boolean.getBoolean("mfcl.test.potions")) PotionHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.elementalTools")) ElementalToolsHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.enchantments")) EnchantmentHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.focusPouch")) FocusPouchHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.droppedItemFluid")) DroppedItemFluidHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.fireFocus")) FireFocusHostProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.wandAudit")) WandHostAuditProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.multiblock")) MultiblockHostProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.biomes")) BiomeHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.device")) DeviceHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.tooltip")) TooltipHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.api")) ApiHostSmokeProbe.install();
        else if (Boolean.getBoolean("mfcl.test.outer")) OuterTravelSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.crafting")) CraftingHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.scanner")) ScannerHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.book")) BookHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.logic")) LogicHostSmokeProbe.install(bridge);
        else if (Boolean.getBoolean("mfcl.test.content")) ContentHostSmokeProbe.install(bridge);
    }
}
