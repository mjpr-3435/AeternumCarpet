package smp.aeternum.carpet.client;

import com.google.gson.Gson;import com.google.gson.reflect.TypeToken;import net.minecraft.client.gui.DrawContext;import net.minecraft.client.gui.screen.Screen;import net.minecraft.client.gui.widget.ButtonWidget;import net.minecraft.client.gui.widget.EditBoxWidget;import net.minecraft.client.gui.widget.TextFieldWidget;import net.minecraft.text.Text;import smp.aeternum.carpet.guides.GuideDocument;import java.lang.reflect.Type;import java.util.ArrayList;import java.util.List;

/** Personal guide reader; OPs can switch the same screen into an auto-saving editor. */
public final class GuideScreen extends Screen {
 private static final Gson GSON=new Gson();private static final Type TYPE=new TypeToken<List<GuideDocument>>(){}.getType();
 private List<GuideDocument> guides;private boolean canEdit,editing,helpOpen;private int selected,scroll;private EditBoxWidget editor;private TextFieldWidget titleField;private long saveAt;private boolean dirty;
 public GuideScreen(String json,boolean canEdit){super(Text.literal("Guías"));guides=decode(json);this.canEdit=canEdit;}
 public void replaceData(String json,boolean canEdit){if(editing&&dirty)return;guides=decode(json);this.canEdit=canEdit;selected=Math.min(selected,Math.max(0,guides.size()-1));}
 @Override protected void init(){build();}
 private void build(){clearChildren();editor=null;titleField=null;int l=width/2-260,t=height/2-170;if(editing&&canEdit){if(current()!=null){titleField=new TextFieldWidget(textRenderer,l+174,t+42,300,18,Text.literal("Nombre de archivo"));titleField.setText(current().title);titleField.setMaxLength(64);addDrawableChild(titleField);editor=EditBoxWidget.builder().x(l+174).y(t+68).hasOverlay(false).build(textRenderer,300,230,Text.literal("Markdown"));editor.setText(current().markdown);editor.setMaxLength(100_000);editor.setChangeListener(value->{dirty=true;saveAt=System.currentTimeMillis()+700;});addDrawableChild(editor);
   addDrawableChild(ButtonWidget.builder(Text.literal("Renombrar"),b->{if(titleField!=null&&!titleField.getText().equals(current().title))GuideRequest.send("rename",current().title,titleField.getText());}).dimensions(l+36,t+302,72,20).build());
   ButtonWidget del=ButtonWidget.builder(Text.literal("X"),b->{if(hasShiftDown()&&current()!=null)GuideRequest.send("delete",current().title,"");}).dimensions(l+112,t+302,20,20).build();del.active=false;addDrawableChild(del);}
   addDrawableChild(ButtonWidget.builder(Text.literal("+"),b->{GuideRequest.send("create","Guia nueva","");}).dimensions(l+12,t+302,20,20).build());
   addDrawableChild(ButtonWidget.builder(Text.literal("?"),b->{helpOpen=!helpOpen;}).dimensions(l+450,t+42,20,18).build());
 } else if(canEdit)addDrawableChild(ButtonWidget.builder(Text.literal("Editar"),b->{editing=true;build();}).dimensions(l+400,t+302,74,20).build());
 }
 private GuideDocument current(){return guides.isEmpty()?null:guides.get(Math.max(0,Math.min(selected,guides.size()-1)));}
 @Override public void tick(){if(dirty&&editor!=null&&System.currentTimeMillis()>=saveAt&&current()!=null){GuideRequest.send("save",current().title,editor.getText());dirty=false;}}
 @Override public void renderBackground(DrawContext c,int x,int y,float d){}
 @Override public void render(DrawContext c,int mx,int my,float d){int l=width/2-260,t=height/2-170;c.fill(0,0,width,height,0x90000000);c.fill(l,t,l+520,t+340,0xE0101010);c.drawBorder(l,t,520,340,0xFF909090);c.drawTextWithShadow(textRenderer,editing?"EDITOR DE GUÍAS":"GUÍAS",l+14,t+14,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,"↻",l+482,t+14,canEdit?0xFFFFFFFF:0xFF555555);c.drawTextWithShadow(textRenderer,"X",l+502,t+14,0xFFFFFFFF);
  int visible=13;scroll=Math.max(0,Math.min(scroll,Math.max(0,guides.size()-visible)));for(int row=0;row<visible&&row+scroll<guides.size();row++){int index=row+scroll,y=t+42+row*19;boolean active=index==selected;if(active)c.fill(l+9,y-2,l+160,y+16,0xFF30465A);c.drawTextWithShadow(textRenderer,guides.get(index).title,l+14,y+1,active?0xFFFFFFFF:0xFFAAAAAA);}if(guides.isEmpty())c.drawTextWithShadow(textRenderer,"No hay guías.",l+14,t+48,0xFFAAAAAA);
  if(!editing&&current()!=null){renderMarkdown(c,current().markdown,l+174,t+43,300);}
  if(editing&&canEdit){for(var child:children())if(child instanceof ButtonWidget b&&b.getMessage().getString().equals("X")&&b.getY()==t+302)b.active=hasShiftDown();c.drawTextWithShadow(textRenderer,dirty?"Guardando…":"Guardado",l+174,t+306,dirty?0xFFFFD080:0xFF55FF55);if(helpOpen){c.fill(l+185,t+76,l+465,t+286,0xF0181818);c.drawBorder(l+185,t+76,280,210,0xFF909090);String[] help={"SINTAXIS MARKDOWN","# Título  |  ## Subtítulo","**negrita**  *cursiva*  `código`","- elemento  |  1. elemento","--- separador","[texto](https://ejemplo.com)","[otra guía](guide:reglas)","``` bloque de código ```"};for(int i=0;i<help.length;i++)c.drawTextWithShadow(textRenderer,help[i],l+195,t+88+i*20,i==0?0xFFFFFFFF:0xFFCCCCCC);}}
  if(mx>=l+480&&mx<l+500&&my>=t+8&&my<t+30)c.drawTooltip(textRenderer,Text.literal(canEdit?"Recargar archivos":"Solo OP puede recargar."),mx,my);super.render(c,mx,my,d);}
 private void renderMarkdown(DrawContext c,String markdown,int x,int y,int w){int lineY=y;for(String raw:markdown.split("\\R")){String line=raw.replaceFirst("^#{1,3}\\s*","").replace("**","").replace("*","");if(line.startsWith("- "))line="• "+line.substring(2);for(var wrapped:textRenderer.wrapLines(Text.literal(line),w)){if(lineY>height/2+145)return;c.drawTextWithShadow(textRenderer,wrapped,x,lineY,raw.startsWith("#")?0xFFFFFFFF:0xFFCCCCCC);lineY+=10;}lineY+=2;}}
 @Override public boolean mouseClicked(double mx,double my,int button){int l=width/2-260,t=height/2-170;if(button==0&&mx>=l+500&&mx<l+520&&my>=t&&my<t+32){close();return true;}if(button==0&&canEdit&&mx>=l+480&&mx<l+500&&my>=t&&my<t+32){GuideRequest.send("reload","","");return true;}if(button==0&&mx>=l+9&&mx<l+160&&my>=t+42&&my<t+289){selected=Math.min(guides.size()-1,scroll+(int)((my-(t+42))/19));if(editing){dirty=false;build();}return true;}return super.mouseClicked(mx,my,button);}
 @Override public boolean mouseScrolled(double mx,double my,double h,double v){int l=width/2-260,t=height/2-170;if(mx>=l&&mx<l+165&&my>=t+40&&my<t+290){scroll-=Math.signum(v);return true;}return super.mouseScrolled(mx,my,h,v);}
 @Override public boolean shouldPause(){return false;}
 @Override public void close(){client.setScreen(null);}
 private static List<GuideDocument> decode(String json){List<GuideDocument> r=GSON.fromJson(json,TYPE);return r==null?new ArrayList<>():r;}
}
