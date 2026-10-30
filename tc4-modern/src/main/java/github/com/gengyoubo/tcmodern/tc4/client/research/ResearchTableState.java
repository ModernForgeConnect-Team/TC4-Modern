package github.com.gengyoubo.tcmodern.tc4.client.research;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.tcmodern.api.aspect.TcAspectAccessors;
import java.util.*;
import static github.com.gengyoubo.tcmodern.tc4.client.research.ResearchTableAccessors.*;

/** Immutable display data. No borrowed legacy references survive the scoped read. */
record ResearchTableState(List<Pool> palette,Map<String,Pool> pools,Sheet sheet,boolean researcher,boolean expert,boolean duplicate,boolean ink,boolean colorBlind) {
    record Pool(String tag,int amount,int bonus) {boolean available(){return amount>0||bonus>0;}}
    record Cell(int q,int r,int type,String aspect) {
        double x(){return 169+13.5*q;}
        double y(){return 83+Math.sqrt(3)*9*(r+q/2.0);}
        String key(){return q+":"+r;}
    }
    record Sheet(String key,int color,boolean complete,int copies,List<Cell> cells,Map<String,Integer> copyCost) {}
    static ResearchTableState empty(){return new ResearchTableState(List.of(),Map.of(),null,false,false,false,false,false);}
    static ResearchTableState read(LegacyClientScreens.Context context,Sheet previous,boolean refreshNote) {
        var runtime=context.runtime();
        return context.withScreen(reference->{
            var gui=runtime.accessor(Gui.class,reference);
            var tile=gui.tile();
            if(tile==null)return null;
            var table=runtime.accessor(Table.class,tile);
            String name=runtime.accessor(TcAspectAccessors.Player.class,gui.player()).name();
            var knowledge=runtime.accessor(Knowledge.class,runtime.accessor(TcAspectAccessors.Proxy.class,
                    runtime.accessor(TcAspectAccessors.Thaumcraft.class).proxy()).knowledge());
            var discovered=knowledge.discovered(name);
            var palette=new ArrayList<Pool>();var pools=new LinkedHashMap<String,Pool>();
            var bonus=runtime.accessor(AspectList.class,table.bonus());
            if(discovered!=null){
                var list=runtime.accessor(AspectList.class,discovered);
                for(var aspect:list.sorted())if(aspect!=null){
                    String tag=runtime.accessor(TcAspectAccessors.Aspect.class,aspect).tag();
                    var pool=new Pool(tag,list.amount(aspect),bonus.amount(aspect));palette.add(pool);pools.put(tag,pool);
                }
            }
            Sheet sheet=previous;
            if(refreshNote){
                var value=runtime.accessor(Research.class).note(table.stack(1));sheet=null;
                if(value!=null){
                    var note=runtime.accessor(Note.class,value);var cells=new ArrayList<Cell>();
                    for(var item:runtime.values().map(note.entries()).entrySet()){
                        String[] qr=((String)item.getKey()).split(":");
                        var entry=runtime.accessor(Entry.class,(LegacyObject)item.getValue());var aspect=entry.aspect();
                        cells.add(new Cell(Integer.parseInt(qr[0]),Integer.parseInt(qr[1]),entry.type(),
                                aspect==null?null:runtime.accessor(TcAspectAccessors.Aspect.class,aspect).tag()));
                    }
                    var cost=new LinkedHashMap<String,Integer>();
                    var research=runtime.accessor(github.com.gengyoubo.tcmodern.api.recipe.TcRecipeAccessors.ResearchLookup.class).find(note.key());
                    if(research!=null){var tags=runtime.accessor(ResearchItem.class,research).tags();
                        if(tags!=null){var list=runtime.accessor(AspectList.class,tags);for(var aspect:list.sorted())if(aspect!=null)
                            cost.put(runtime.accessor(TcAspectAccessors.Aspect.class,aspect).tag(),list.amount(aspect)+note.copies());}}
                    sheet=new Sheet(note.key(),note.color(),note.complete(),note.copies(),List.copyOf(cells),Collections.unmodifiableMap(cost));
                }
            }
            var flags=runtime.accessor(Flags.class);
            var tools=table.stack(0);boolean ink=tools!=null&&runtime.accessor(Ink.class,tools).damage()!=runtime.accessor(Ink.class,tools).capacity();
            return new ResearchTableState(List.copyOf(palette),Map.copyOf(pools),sheet,flags.researcher(),
                    flags.expert(),flags.duplicate(),ink,runtime.accessor(Options.class).colorBlind());
        });
    }
    boolean available(String tag){if(tag==null)return false;var pool=pools.get(tag);return pool!=null&&pool.available();}
}
