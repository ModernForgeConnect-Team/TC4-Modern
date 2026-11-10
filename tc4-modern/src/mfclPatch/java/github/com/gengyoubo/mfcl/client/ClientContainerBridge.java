package github.com.gengyoubo.mfcl.client;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Drives actual old GuiContainer code alongside its host screen. Contains no mod-specific layout. */
public final class ClientContainerBridge {
    private final LegacyServices services;
    private final LegacyGuiRenderer renderer;
    private final Map<Screen,Bound> bindings=new IdentityHashMap<>();
    private final Map<Object,Screen> origins=new IdentityHashMap<>();
    private Map<String,LegacyClientData.Stack> reverse;
    private final Queue<LegacyPackets.Message> pendingPackets=new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static ClientContainerBridge active;
    public static ClientContainerBridge active(){return active;}
    public static void install(LegacyServices services) {
        LegacyPackets.clientSender(services.loader(),message->{var mc=Minecraft.getInstance();if(mc.player==null||mc.getConnection()==null)return false;if(!mc.isSameThread())throw new IllegalStateException("Original client packets require the native client thread");github.com.gengyoubo.mfcl.content.ScanSyncChannel.sendLegacyPacket(message);return true;});
        if(!LegacyScreens.guiBridgeEnabled())return;
        var bridge=new ClientContainerBridge(services);active=bridge;
        MinecraftForge.EVENT_BUS.addListener(bridge::init);
        MinecraftForge.EVENT_BUS.addListener(bridge::capture);
        MinecraftForge.EVENT_BUS.addListener(bridge::render);
        MinecraftForge.EVENT_BUS.addListener(bridge::tooltip);
        MinecraftForge.EVENT_BUS.addListener(bridge::mouse);
        MinecraftForge.EVENT_BUS.addListener(bridge::release);
        MinecraftForge.EVENT_BUS.addListener(bridge::scroll);
        MinecraftForge.EVENT_BUS.addListener(bridge::character);
        MinecraftForge.EVENT_BUS.addListener(bridge::key);
        MinecraftForge.EVENT_BUS.addListener(bridge::close);
        MinecraftForge.EVENT_BUS.addListener(bridge::tick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.RecipesUpdatedEvent event)->bridge.reverse=null);
    }
    private ClientContainerBridge(LegacyServices services){this.services=services;renderer=new LegacyGuiRenderer(services);}
    public static LegacyItemUse.Result useItem(java.util.concurrent.Callable<LegacyItemUse.Result> action) throws Exception {
        var mc=Minecraft.getInstance();Object requested;boolean opened;LegacyItemUse.Result result;
        if(active!=null)active.services.items().language(mc.options.languageCode);
        try(var frame=LegacyGuiGraphics.begin(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0,0,keys())) {
            result=action.call();requested=frame.requestedScreen();opened=frame.hasScreenRequest();
        }
        if(opened) {
            if(active==null)throw new UnsupportedOperationException("Legacy GUI bridge is disabled");
            active.transition(mc.screen,requested);
            return new LegacyItemUse.Result(true,result.useDuration(),result.prompt(),result.messages(),result.feedback(),result.device());
        }
        return result;
    }
    public static void receive(LegacyPackets.Message message) {
        if(active!=null)active.pendingPackets.add(message);
    }
    private static final class Bound {
        final Object gui;final LegacyScreens.Session session;
        List<LegacyGuiGraphics.Command> commands=List.of();Throwable failure;
        boolean tooltipReady;
        net.minecraft.client.gui.GuiGraphics graphics;
        Bound(Object gui,LegacyScreens.Session session){this.gui=gui;this.session=session;}
    }
    private void init(ScreenEvent.Init.Post event) {
        if(!(event.getScreen() instanceof AbstractContainerScreen<?> screen))return;
        if(!(screen instanceof LegacyDeviceScreen)&&!services.containers().hasInputHooks())return;
        bind(screen);
    }
    private void bind(AbstractContainerScreen<?> screen) {
        try(var frame=LegacyGuiGraphics.begin(screen.width,screen.height,0,0,keys())) {
            services.items().language(Minecraft.getInstance().options.languageCode);
            Bound previous=bindings.remove(screen);if(previous!=null)previous.session.close();
            List<LegacyContainers.Slot> slots=new ArrayList<>();
            for(var slot:screen.getMenu().slots)slots.add(new LegacyContainers.Slot(slot.x,slot.y,snapshot(slot.getItem())));
            Object gui=screen instanceof LegacyDeviceScreen device?device.originalGui():services.containers().create(screen.width,screen.height,screen.getGuiLeft(),screen.getGuiTop(),screen.getXSize(),screen.getYSize(),slots);
            var session=services.screens().open(gui,screen.width,screen.height);
            services.containers().position(gui,screen.getGuiLeft(),screen.getGuiTop());
            if(!(screen instanceof LegacyDeviceScreen device)||device.getMenu().receivedContents())services.containers().update(gui,slots);
            bindings.put(screen,new Bound(gui,session));
            origins.put(gui,screen);
        } catch(Exception failure){LogUtils.getLogger().error("MFCL legacy container initialization failed",failure);}
    }
    private LegacyClientData.Stack snapshot(net.minecraft.world.item.ItemStack value) throws Exception {
        if(value.isEmpty())return null;
        var content=github.com.gengyoubo.mfcl.content.ModernContentBridge.active();
        if(content!=null){var old=content.renderSnapshot(value);if(old!=null)return old;}
        // Use the same lossless inventory transport for original rendering and click prediction.
        if(content!=null)return content.containerStack(value);
        if(value.hasTag())return null; // An unmapped tag must not be replaced with an untagged display-only entry.
        if(reverse==null){reverse=new HashMap<>();for(var stack:services.items().catalog()){var modern=ModernItemMapping.renderStack(stack);if(!modern.isEmpty()&&!modern.hasTag())reverse.putIfAbsent(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(modern.getItem()).toString(),stack);}}
        var old=reverse.get(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(value.getItem()).toString());
        if(old==null)return null; // an unknown host item leaves its mirror slot empty instead of failing the container
        return new LegacyClientData.Stack(old.key(),value.isDamageableItem()?value.getDamageValue():old.metadata(),value.getCount(),old.name(),old.tag());
    }
    private void capture(ScreenEvent.Render.Pre event) {
        Bound bound=bindings.get(event.getScreen());if(bound==null||bound.failure!=null)return;
        bound.tooltipReady=false;bound.graphics=event.getGuiGraphics();
        try(var frame=LegacyGuiGraphics.begin(event.getScreen().width,event.getScreen().height,event.getMouseX(),event.getMouseY(),keys())) {
            services.items().inLegacy(()->deviceAction(event.getScreen(),()->{if(event.getScreen() instanceof AbstractContainerScreen<?> container)services.containers().carried(snapshot(container.getMenu().getCarried()));bound.session.draw(event.getMouseX(),event.getMouseY(),event.getPartialTick());return null;}));bound.commands=frame.commands();
            followSlots(event.getScreen(),bound);
            bound.tooltipReady=frame.hasTooltip();
        } catch(Throwable failure){bound.failure=failure;LogUtils.getLogger().error("MFCL original GUI drawing failed",failure);}
    }
    private void tooltip(net.minecraftforge.client.event.RenderTooltipEvent.Pre event) {
        Bound bound=bindings.get(Minecraft.getInstance().screen);
        // Keep the host fallback unless this frame actually captured a replacement from the old GUI.
        if(bound!=null&&bound.failure==null&&bound.tooltipReady&&bound.graphics==event.getGraphics()&&!event.getItemStack().isEmpty())event.setCanceled(true);
    }
    private void render(ScreenEvent.Render.Post event) {
        Bound bound=bindings.get(event.getScreen());if(bound==null||bound.failure!=null)return;
        try {renderer.draw(event.getGuiGraphics(),bound.commands,bound.gui.getClass().getName().equals("thaumcraft.client.gui.GuiResearchBrowser"));}
        catch(Throwable failure){bound.failure=failure;LogUtils.getLogger().error("MFCL original GUI replay failed",failure);}
        finally {bound.tooltipReady=false;bound.graphics=null;}
    }
    private void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.level!=null&&mc.player!=null&&!pendingPackets.isEmpty())try {
            var content=github.com.gengyoubo.mfcl.content.ModernContentBridge.active();
            services.items().inLegacy(()->{
                content.itemUse().clientActor(services.worlds().bound(mc.level),new LegacyTiles.Placer(mc.player.getUUID(),mc.player.getName().getString(),mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot()),null);
                LegacyPackets.Message message;while((message=pendingPackets.poll())!=null)LegacyPackets.receiveClient(services,message);return null;
            });
        }catch(Exception failure){LogUtils.getLogger().error("MFCL old client packet failed",failure);pendingPackets.clear();}
        var screen=Minecraft.getInstance().screen;
        if(Minecraft.getInstance().level==null&&bindings.isEmpty()){origins.clear();reverse=null;return;}
        var bound=bindings.get(screen);if(bound==null||bound.failure!=null)return;
        if(screen instanceof AbstractContainerScreen<?> container)try {
            followSlots(screen,bound);
            var slots=new ArrayList<LegacyContainers.Slot>();
            for(var slot:container.getMenu().slots)slots.add(new LegacyContainers.Slot(slot.x,slot.y,null));
            if(!services.containers().sameLayout(bound.gui,slots))bind(container);
        }catch(Exception failure){bound.failure=failure;LogUtils.getLogger().error("MFCL legacy container layout refresh failed",failure);return;}
        dispatch(screen,0,0,b->{
            if(screen instanceof AbstractContainerScreen<?> container&&(!(screen instanceof LegacyDeviceScreen device)||device.getMenu().receivedContents())){var slots=new ArrayList<LegacyContainers.Slot>();for(var slot:container.getMenu().slots)slots.add(new LegacyContainers.Slot(slot.x,slot.y,snapshot(slot.getItem())));services.containers().update(b.gui,slots);}
            b.session.tick();return false;
        });
    }
    private void mouse(ScreenEvent.MouseButtonPressed.Pre event){if(event.getScreen() instanceof HostedScreen)return;if(dispatch(event.getScreen(),(int)event.getMouseX(),(int)event.getMouseY(),b->services.containers().mouse(b.gui,(int)event.getMouseX(),(int)event.getMouseY(),event.getButton())))event.setCanceled(true);}
    private void release(ScreenEvent.MouseButtonReleased.Pre event){if(event.getScreen() instanceof HostedScreen)return;if(dispatch(event.getScreen(),(int)event.getMouseX(),(int)event.getMouseY(),b->services.containers().release(b.gui,(int)event.getMouseX(),(int)event.getMouseY(),event.getButton())))event.setCanceled(true);}
    private void scroll(ScreenEvent.MouseScrolled.Pre event){if(dispatch(event.getScreen(),(int)event.getMouseX(),(int)event.getMouseY(),b->services.containers().scroll(b.gui,(int)event.getMouseX(),(int)event.getMouseY(),event.getScrollDelta()>0?1:-1)))event.setCanceled(true);}
    private void character(ScreenEvent.CharacterTyped.Pre event){if(event.getScreen() instanceof HostedScreen)return;int[] point=pointer(event.getScreen());if(dispatch(event.getScreen(),point[0],point[1],b->services.containers().key(b.gui,event.getCodePoint(),0)))event.setCanceled(true);}
    private void key(ScreenEvent.KeyPressed.Pre event){if(event.getScreen() instanceof HostedScreen)return;int legacy=legacyKey(event.getKeyCode());int[] point=pointer(event.getScreen());if(legacy!=0&&dispatch(event.getScreen(),point[0],point[1],b->services.containers().key(b.gui,'\0',legacy)))event.setCanceled(true);}
    private static int legacyKey(int key){return switch(key){case GLFW.GLFW_KEY_ESCAPE->1;case GLFW.GLFW_KEY_BACKSPACE->14;case GLFW.GLFW_KEY_ENTER->28;case GLFW.GLFW_KEY_R->19;case GLFW.GLFW_KEY_U->22;case GLFW.GLFW_KEY_O->24;default->0;};}
    private static int[] pointer(Screen screen){var mc=Minecraft.getInstance();return new int[]{(int)(mc.mouseHandler.xpos()*screen.width/mc.getWindow().getScreenWidth()),(int)(mc.mouseHandler.ypos()*screen.height/mc.getWindow().getScreenHeight())};}
    private interface Action{boolean run(Bound bound)throws Exception;}
    private boolean dispatch(Screen screen,int x,int y,Action action){var bound=bindings.get(screen);if(bound==null||bound.failure!=null)return false;try(var frame=LegacyGuiGraphics.begin(screen.width,screen.height,x,y,keys())){boolean handled=services.items().inLegacy(()->deviceAction(screen,()->action.run(bound)));Object requested=frame.requestedScreen();if(frame.hasScreenRequest())Minecraft.getInstance().tell(()->transition(screen,requested));if(frame.hasClickSound())Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,1f));return handled;}catch(Throwable failure){bound.failure=failure;LogUtils.getLogger().error("MFCL original GUI input failed",failure);return false;}}
    private <T> T deviceAction(Screen screen,java.util.concurrent.Callable<T> action) throws Exception {
        boolean device=screen instanceof LegacyDeviceScreen,hosted=screen instanceof HostedScreen;
        if(!device&&!hosted)return action.call();
        try(var input=new LegacyDeviceInput()){
            if(screen instanceof LegacyDeviceScreen original)original.getMenu().refreshClientEffects();
            T result=action.call();
            if(device)for(var packet:input.packets())github.com.gengyoubo.mfcl.content.ScanSyncChannel.sendDevicePacket(((LegacyDeviceScreen)screen).getMenu().containerId,packet);
            else for(var packet:input.packets())github.com.gengyoubo.mfcl.content.ScanSyncChannel.sendLegacyPacket(packet);
            return result;
        }
    }
    public boolean deviceMouse(LegacyDeviceScreen screen,int x,int y,int button,boolean release) {
        return dispatch(screen,x,y,b->{
            if(release)b.session.mouseReleased(x,y,button);else b.session.mousePressed(x,y,button);
            // Native slots retain their own click protocol; custom GUI regions use the original handler.
            for(var slot:screen.getMenu().slots)if(x>=screen.getGuiLeft()+slot.x&&x<screen.getGuiLeft()+slot.x+16&&y>=screen.getGuiTop()+slot.y&&y<screen.getGuiTop()+slot.y+16)return false;
            return x>=screen.getGuiLeft()&&x<screen.getGuiLeft()+screen.getXSize()&&y>=screen.getGuiTop()&&y<screen.getGuiTop()+screen.getYSize();
        });
    }
    /** Device slots belong to the original GUI's layout, which may move them while drawing (e.g. sorted, scrolled cabinets). */
    private void followSlots(Screen screen,Bound bound) throws Exception {
        if(screen instanceof LegacyDeviceScreen device)device.getMenu().moveSlots(services.containers().positions(bound.gui));
    }
    /** The wheel reaches GUIs that read it themselves through LWJGL's event wheel in handleMouseInput. */
    public boolean deviceScroll(LegacyDeviceScreen screen,int x,int y,double delta) {
        if(delta==0||!readsMouseInput(screen.originalGui()))return false;
        return dispatch(screen,x,y,b->{LegacyGuiGraphics.wheel(delta>0?120:-120);b.session.mouseInput();return true;});
    }
    public void deviceDrag(LegacyDeviceScreen screen,int x,int y,int button) {
        dispatch(screen,x,y,b->{b.session.mouseDragged(x,y,button);return false;});
    }
    private boolean readsMouseInput(Object gui) {
        try {
            var container=services.loader().loadClass("net.minecraft.client.gui.inventory.GuiContainer");
            for(Class<?> type=gui.getClass();type!=null&&type!=container;type=type.getSuperclass())
                for(var method:type.getDeclaredMethods())if(method.getName().equals("func_146274_d")&&method.getParameterCount()==0)return true;
            return false;
        }catch(ClassNotFoundException failure){throw new IllegalStateException(failure);}
    }
    public static void receiveCounts(int window,int[] counts) {
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.containerMenu instanceof github.com.gengyoubo.mfcl.content.LegacyDeviceMenu menu&&menu.containerId==window)menu.receiveCounts(counts);
    }
    private void transition(Screen parent,Object requested) {
        if(requested==null){Minecraft.getInstance().setScreen(null);return;}
        Screen original=origins.get(requested);if(original!=null){Minecraft.getInstance().setScreen(original);return;}
        Minecraft.getInstance().setScreen(new HostedScreen(parent,requested));
    }
    private final class HostedScreen extends Screen {
        private final Screen parent;private final Object old;
        HostedScreen(Screen parent,Object old){super(net.minecraft.network.chat.Component.empty());this.parent=parent;this.old=old;}
        @Override protected void init(){try(var frame=LegacyGuiGraphics.begin(width,height,0,0,keys())){services.items().language(Minecraft.getInstance().options.languageCode);bindings.put(this,new Bound(old,services.screens().open(old,width,height)));origins.put(old,this);}catch(Exception failure){throw new IllegalStateException("Cannot open original legacy screen",failure);}}
        @Override public void render(net.minecraft.client.gui.GuiGraphics graphics,int x,int y,float partialTick){renderBackground(graphics);}
        @Override public boolean isPauseScreen(){try{return (Boolean)old.getClass().getMethod("func_73868_f").invoke(old);}catch(Exception failure){throw new IllegalStateException("Cannot read legacy GUI pause policy",failure);}}
        @Override public boolean mouseClicked(double x,double y,int button){return dispatch(this,(int)x,(int)y,b->{b.session.mousePressed((int)x,(int)y,button);return true;});}
        @Override public boolean mouseReleased(double x,double y,int button){return dispatch(this,(int)x,(int)y,b->{b.session.mouseReleased((int)x,(int)y,button);return true;});}
        @Override public boolean keyPressed(int key,int scan,int modifiers){int[] p=pointer(this);return dispatch(this,p[0],p[1],b->{b.session.keyPressed('\0',legacyKey(key));return true;});}
        @Override public boolean charTyped(char character,int modifiers){int[] p=pointer(this);return dispatch(this,p[0],p[1],b->{if(!services.containers().key(b.gui,character,0))b.session.keyPressed(character,0);return true;});}
        @Override public void onClose(){Minecraft.getInstance().setScreen(parent);}
    }
    private void close(ScreenEvent.Closing event) {
        Bound bound=bindings.remove(event.getScreen());if(bound==null)return;
        try {services.items().inLegacy(()->{
            // The native menu returns/drops the carried stack; the original client must not do it again.
            if(event.getScreen() instanceof AbstractContainerScreen<?>)services.containers().carried(null);
            bound.session.close();return null;
        });}catch(Exception failure){LogUtils.getLogger().error("MFCL legacy GUI close failed",failure);}
    }
    private static Set<Integer> keys(){var result=new HashSet<Integer>();if(Screen.hasControlDown())result.add(29);if(Screen.hasShiftDown())result.add(42);if(Screen.hasAltDown())result.add(56);for(int button=0;button<3;button++)if(GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(),button)==GLFW.GLFW_PRESS)result.add(1000+button);return result;}
    public List<LegacyGuiGraphics.Command> commands(Screen screen){var bound=bindings.get(screen);if(bound==null)throw new IllegalStateException("No old GUI binding");if(bound.failure!=null)throw new IllegalStateException("Old GUI failed",bound.failure);return bound.commands;}
    public Object legacyScreen(Screen screen){return bindings.get(screen).gui;}
}
