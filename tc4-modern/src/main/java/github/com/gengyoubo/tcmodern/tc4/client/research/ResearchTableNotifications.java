package github.com.gengyoubo.tcmodern.tc4.client.research;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import java.util.*;

/** Detached snapshots of the original notifications already shown by GuiResearchTable's foreground layer. */
record ResearchTableNotifications(int delay,int limit,List<Notice> entries) {
    record Notice(String text,String image,int color,long created,long expire) {}
    @LegacyType("thaumcraft.client.lib.PlayerNotifications") public interface Queue {
        @LegacyMethod(name="getListAndUpdate",descriptor="(J)Ljava/util/ArrayList;",isStatic=true) LegacyObject current(long time);
    }
    @LegacyType("thaumcraft.client.lib.PlayerNotifications$Notification") public interface Entry {
        @LegacyField(name="text",descriptor="Ljava/lang/String;") String text();
        @LegacyField(name="image",descriptor="Lnet/minecraft/util/ResourceLocation;") LegacyObject image();
        @LegacyField(name="color",descriptor="I") int color();
        @LegacyField(name="created",descriptor="J") long created();
        @LegacyField(name="expire",descriptor="J") long expire();
    }
    @LegacyType("thaumcraft.common.config.Config") public interface Settings {
        @LegacyField(name="notificationDelay",descriptor="I",isStatic=true) int delay();
        @LegacyField(name="notificationMax",descriptor="I",isStatic=true) int limit();
    }
    static ResearchTableNotifications empty(){return new ResearchTableNotifications(1,0,List.of());}
    static ResearchTableNotifications read(LegacyClientScreens.Context context){
        var runtime=context.runtime();
        return context.withScreen(ignored->{
            var settings=runtime.accessor(Settings.class);var notices=new ArrayList<Notice>();
            for(var value:runtime.values().list(runtime.accessor(Queue.class).current(System.nanoTime()/1_000_000L))){
                var entry=runtime.accessor(Entry.class,(LegacyObject)value);var icon=entry.image();String image=null;
                if(icon!=null){var path=runtime.accessor(TcAspectAccessors.Resource.class,icon);image=(path.namespace()+":"+path.path()).toLowerCase(Locale.ROOT);}
                notices.add(new Notice(entry.text(),image,entry.color(),entry.created(),entry.expire()));
            }
            return new ResearchTableNotifications(settings.delay(),settings.limit(),List.copyOf(notices));
        });
    }
}
