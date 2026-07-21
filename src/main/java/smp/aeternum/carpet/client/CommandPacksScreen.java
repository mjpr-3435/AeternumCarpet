package smp.aeternum.carpet.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import smp.aeternum.carpet.actions.CommandPack;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The whole catalog: packages are the things an administrator creates and runs. */
public final class CommandPacksScreen extends Screen {
    private static final Gson GSON=new Gson(); private static final Type TYPE=new TypeToken<List<CommandPack>>(){}.getType();
    private List<CommandPack> packs;
    private boolean canEdit;
    private int scroll;
    private boolean draggingScrollbar;
    public CommandPacksScreen(String json,boolean canEdit){super(Text.literal("Acciones"));packs=decode(json);this.canEdit=canEdit;}
    public void replaceData(String json,boolean canEdit){packs=decode(json);this.canEdit=canEdit;}
    @Override protected void init(){ButtonWidget add=ButtonWidget.builder(Text.literal("+ Nuevo paquete"),b->{CommandPack p=new CommandPack();packs.add(p);client.setScreen(new CommandPackEditorScreen(this,packs,p,canEdit));}).dimensions(width/2-75,height/2+126,150,20).build();add.active=canEdit;addDrawableChild(add);}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){}
    @Override public void render(DrawContext c,int mx,int my,float d){c.fill(0,0,width,height,0x90000000);int l=width/2-205,t=height/2-140;c.fill(l,t,l+410,t+280,0xE0101010);c.drawBorder(l,t,410,280,0xFF909090);c.drawTextWithShadow(textRenderer,"PAQUETES DE ACCIONES",l+14,t+14,0xFFFFFFFF);
        c.drawTextWithShadow(textRenderer,canEdit?"Selecciona un paquete para editarlo o ejecutarlo":"Selecciona un paquete para ver o ejecutar",l+14,t+29,0xFFAAAAAA);
        if(packs.isEmpty())c.drawCenteredTextWithShadow(textRenderer,"No hay paquetes todavía.",width/2,t+122,0xFFAAAAAA);
        int visible=10, max=Math.max(0,packs.size()-visible);scroll=Math.max(0,Math.min(scroll,max));
        for(int row=0;row<visible&&row+scroll<packs.size();row++){CommandPack p=packs.get(row+scroll);int y=t+53+row*19;boolean h=mx>=l+10&&mx<l+384&&my>=y-2&&my<y+16;if(h)c.fill(l+9,y-2,l+385,y+16,0xFF30465A);else if(row%2==0)c.fill(l+10,y-2,l+384,y+16,0x40202020);c.drawTextWithShadow(textRenderer,p.name.isBlank()?"(sin nombre)":p.name,l+16,y+1,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,p.commands.size()+" pasos",l+324,y+1,0xFFAAAAAA);}
        if(max>0){int listTop=t+51,listBottom=t+243,track=listBottom-listTop,barH=Math.max(20,track*visible/packs.size()),barY=listTop+(track-barH)*scroll/max;c.fill(l+390,listTop,l+396,listBottom,0xFF333333);c.fill(l+390,barY,l+396,barY+barH,0xFFAAAAAA);}super.render(c,mx,my,d);}
    @Override public boolean mouseClicked(double mx,double my,int b){int l=width/2-205,t=height/2-140,visible=10,max=Math.max(0,packs.size()-visible);if(b==0&&max>0&&mx>=l+387&&mx<=l+400&&my>=t+51&&my<=t+243){draggingScrollbar=true;updateScrollFromMouse(my);return true;}int row=(int)((my-(t+53))/19),i=row+scroll;if(b==0&&row>=0&&row<visible&&i<packs.size()&&mx>=l+10&&mx<l+384){client.setScreen(new CommandPackEditorScreen(this,packs,packs.get(i),canEdit));return true;}return super.mouseClicked(mx,my,b);}
    @Override public boolean mouseScrolled(double mx,double my,double h,double v){int l=width/2-205,t=height/2-140;if(mx>=l+9&&mx<l+401&&my>=t+51&&my<t+243){scroll-= (int)Math.signum(v);return true;}return super.mouseScrolled(mx,my,h,v);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(draggingScrollbar&&button==0){updateScrollFromMouse(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(button==0&&draggingScrollbar){draggingScrollbar=false;return true;}return super.mouseReleased(mx,my,button);}
    @Override public boolean shouldPause(){return false;}
    private void updateScrollFromMouse(double mouseY){int visible=10,max=Math.max(0,packs.size()-visible);if(max==0)return;int top=height/2-140,listTop=top+51,listBottom=top+243,track=listBottom-listTop,barH=Math.max(20,track*visible/packs.size());double fraction=(mouseY-listTop-barH/2.0)/Math.max(1.0,track-barH);scroll=(int)Math.round(Math.max(0.0,Math.min(1.0,fraction))*max);}
    private static List<CommandPack> decode(String json){List<CommandPack> r=GSON.fromJson(json,TYPE);if(r==null)r=new ArrayList<>();r.sort(Comparator.comparing(p->p.name==null?"":p.name,String.CASE_INSENSITIVE_ORDER));return r;}
}
