package github.com.gengyoubo.tcmodern.tc4.client.research;

import java.util.List;

/** The two original aspect tooltip lines. Amounts, bonuses and shortcut hints are not tooltip content. */
record ResearchTableTooltip(String name,String description) {
    List<String> lines(){return List.of(name,description);}
}
