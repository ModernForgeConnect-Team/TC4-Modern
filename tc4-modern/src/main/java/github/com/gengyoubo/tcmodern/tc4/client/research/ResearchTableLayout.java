package github.com.gengyoubo.tcmodern.tc4.client.research;

/** Original 255x255 canvas and column-major palette, independent of rendering and Minecraft. */
final class ResearchTableLayout {
    record Hex(int q,int r) {}
    static Hex hexAt(double x,double y){
        double q=(x-169)/13.5,r=(y-83)/(Math.sqrt(3)*9)-q/2,c=-q-r;
        int rq=(int)Math.round(q),rr=(int)Math.round(r),rc=(int)Math.round(c);
        double dq=Math.abs(rq-q),dr=Math.abs(rr-r),dc=Math.abs(rc-c);
        if(dq>dr&&dq>dc)rq=-rr-rc;else if(dr>dc)rr=-rq-rc;
        return new Hex(rq,rr);
    }
    static int lastPage(int aspects){return (aspects-20)/5;}
    static int paletteIndex(double x,double y,int page,int count){
        if(x<10||x>=90||y<40||y>=120)return -1;
        int index=page*5+(int)((x-10)/16)*5+(int)((y-40)/16);
        return index<count?index:-1;
    }
    static boolean inside(double x,double y,int left,int top,int width,int height){return x>=left&&x<left+width&&y>=top&&y<top+height;}
    static boolean contains(ResearchTableState.Cell cell,double x,double y){
        double dx=Math.abs(x-cell.x()),dy=Math.abs(y-cell.y());
        return dx<=9&&dy<=Math.sqrt(3)*4.5&&Math.sqrt(3)*dx+dy<=Math.sqrt(3)*9;
    }
}
