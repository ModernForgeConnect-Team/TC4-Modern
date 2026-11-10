package github.com.gengyoubo.mfcl.api;

import java.util.function.Function;

/** Native client views for original device menus. Inventory slots, server validation and synchronization stay hosted by MFCL. */
public interface LegacyClientScreens {
    /** Registers an exact original GUI class name. Registration may happen before a world exists. Duplicate owners are rejected. */
    AutoCloseable register(String originalGuiClass, Function<Context, Controller> factory);

    interface Context {
        LegacyRuntime runtime();
        /** The host AbstractContainerScreen, for public host menu, layout and inventory access. Client thread only. */
        <H> H hostScreen(Class<H> type);
        /**
         * Scoped access to the original GUI and its fields through MLA. Derived references expire on return.
         * Original serverbound packets emitted here use this open menu's authenticated device transport.
         * Nothing is persisted locally. Return snapshots only; never invoke the original draw method.
         */
        <T> T withScreen(Function<LegacyObject, T> operation);
        /**
         * Serializes an original SimpleNetworkWrapper message and sends it through this menu's authenticated transport.
         * Use inside withScreen while borrowed arguments are live. This avoids executing old network-wrapper code
         * and keeps serialization and the active-menu identity inside MFCL.
         */
        void sendToServer(LegacyObject channel, LegacyObject packet);
        /** Sends a standard host container-button click for this menu, using the existing server handler. */
        void clickMenuButton(int button);
    }

    /** All callbacks are on the client thread. Input uses absolute scaled GUI coordinates; false defers to native slot handling. */
    interface Controller extends AutoCloseable {
        /** Host GuiGraphics. Draw background and custom content here; native inventory slots are drawn afterwards. */
        void renderBackground(Object graphics, int left, int top, int mouseX, int mouseY, float partialTick);
        /** Draws custom hover/drag content after inventory slots and native item tooltips. */
        default void renderForeground(Object graphics, int mouseX, int mouseY, float partialTick) {}
        /** Runs after the client menu mirror has been refreshed. Poll legacy snapshots here, never per rendered frame. */
        default void tick() {}
        default boolean mouseClicked(double x, double y, int button) { return false; }
        default boolean mouseReleased(double x, double y, int button) { return false; }
        default boolean mouseDragged(double x, double y, int button, double dx, double dy) { return false; }
        default boolean mouseScrolled(double x, double y, double delta) { return false; }
        /** Called on close and resize/rebind. A closed context rejects further legacy access. */
        @Override default void close() {}
    }
}
