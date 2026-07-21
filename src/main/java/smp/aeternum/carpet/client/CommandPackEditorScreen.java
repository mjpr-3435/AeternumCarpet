package smp.aeternum.carpet.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;
import smp.aeternum.carpet.actions.CommandPack;
import smp.aeternum.carpet.actions.CommandStep;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The second screen works as both an OP editor and a safe read-only runner. */
final class CommandPackEditorScreen extends Screen {
    private final Screen parent; private final List<CommandPack> packs; private final CommandPack pack; private final boolean canEdit;
    private TextFieldWidget name,description; private final List<TextFieldWidget> stepFields=new ArrayList<>();
    private ButtonWidget deleteButton, saveButton; private String runFeedback="";
    private int commandScroll;
    private boolean draggingCommandScrollbar;
    private boolean dirty;
    CommandPackEditorScreen(Screen parent,List<CommandPack> packs,CommandPack pack,boolean canEdit){super(Text.literal("Editar paquete"));this.parent=parent;this.packs=packs;this.pack=pack;this.canEdit=canEdit;}
    @Override protected void init(){int l=width/2-245,t=height/2-170;stepFields.clear();
        name=field(l+14,t+46,452,pack.name,80,"Nombre del paquete");description=field(l+14,t+80,452,pack.description,240,"Descripción opcional");
        commandScroll=Math.max(0,Math.min(commandScroll,maxCommandScroll()));
        for(int i=0;i<6&&i+commandScroll<pack.commands.size();i++){int index=i+commandScroll,y=t+132+i*22;CommandStep step=pack.commands.get(index);
            ButtonWidget type=ButtonWidget.builder(Text.literal(step.type==CommandStep.Type.COMMAND?"CMD":"ESPERA"),b->{commit();CommandStep current=pack.commands.get(index);current.type=current.type==CommandStep.Type.COMMAND?CommandStep.Type.WAIT:CommandStep.Type.COMMAND;current.value=current.type==CommandStep.Type.WAIT?"1":"say ";dirty=true;clearAndInit();}).dimensions(l+14,y,68,18).build();type.setTooltip(Tooltip.of(Text.literal("Alternar entre comando y espera")));type.active=canEdit;addDrawableChild(type);
            if(step.type==CommandStep.Type.COMMAND)stepFields.add(field(l+88,y,330,step.value,2048,"Comando"));else stepFields.add(field(l+88,y,330,step.value,8,"Segundos"));
            ButtonWidget remove=ButtonWidget.builder(Text.literal("X"),b->{commit();if(index<pack.commands.size()){pack.commands.remove(index);dirty=true;}clearAndInit();}).dimensions(l+424,y,42,18).build();remove.setTooltip(Tooltip.of(Text.literal("Eliminar esta línea")));remove.active=canEdit;addDrawableChild(remove);}
        ButtonWidget newLine=ButtonWidget.builder(Text.literal("+ Nueva línea"),b->{commit();pack.commands.add(new CommandStep(CommandStep.Type.COMMAND,"say "));dirty=true;commandScroll=maxCommandScroll();clearAndInit();}).dimensions(l+14,t+326,112,20).build();newLine.active=canEdit;addDrawableChild(newLine);
        addDrawableChild(ButtonWidget.builder(Text.literal("Ejecutar"),b->{commit();CommandPacksRequest.run(pack);runFeedback="Ejecutando paquete…";}).dimensions(l+278,t+326,88,20).build());
        saveButton=ButtonWidget.builder(Text.literal("Guardar"),b->{commit();sortAndSave();dirty=false;}).dimensions(l+372,t+326,94,20).build();saveButton.active=false;addDrawableChild(saveButton);
        deleteButton=ButtonWidget.builder(Text.literal("Eliminar"),b->{if(!canEdit||!hasShiftDown())return;packs.remove(pack);sortAndSave();client.setScreen(parent);}).dimensions(l+204,t+326,68,20).build();deleteButton.active=false;addDrawableChild(deleteButton);}
    private TextFieldWidget field(int x,int y,int w,String v,int max,String hint){TextFieldWidget f=new TextFieldWidget(textRenderer,x,y,w,18,Text.literal(hint));f.setText(v==null?"":v);f.setMaxLength(max);f.setEditable(canEdit);if(canEdit)f.setChangedListener(value->dirty=true);addDrawableChild(f);return f;}
    private void commit(){if(name==null)return;pack.name=name.getText().trim();pack.description=description.getText().trim();int visible=Math.min(stepFields.size(),pack.commands.size()-commandScroll);for(int i=0;i<visible;i++)pack.commands.get(commandScroll+i).value=stepFields.get(i).getText().trim();}
    private void sortAndSave(){packs.sort(Comparator.comparing(p->p.name==null?"":p.name,String.CASE_INSENSITIVE_ORDER));CommandPacksRequest.save(packs);}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){}
    @Override public void render(DrawContext c,int mx,int my,float d){deleteButton.active=canEdit&&hasShiftDown();saveButton.active=canEdit&&dirty;c.fill(0,0,width,height,0x90000000);int l=width/2-245,t=height/2-170;c.fill(l,t,l+480,t+365,0xE0101010);c.drawBorder(l,t,480,365,0xFF909090);c.drawTextWithShadow(textRenderer,"PAQUETE",l+14,t+14,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,"Nombre",l+14,t+38,0xFFAAAAAA);c.drawTextWithShadow(textRenderer,"Descripción",l+14,t+72,0xFFAAAAAA);c.drawTextWithShadow(textRenderer,"PASOS  ·  CMD se ejecuta como servidor  ·  ESPERA usa segundos",l+14,t+110,0xFFAAAAAA);if(pack.commands.isEmpty())c.drawCenteredTextWithShadow(textRenderer,"Añade una línea; usa el botón CMD para alternar a espera.",width/2,t+188,0xFFAAAAAA);int max=maxCommandScroll();if(max>0){int listTop=t+130,listBottom=t+260,track=listBottom-listTop,barH=Math.max(20,track*6/pack.commands.size()),barY=listTop+(track-barH)*commandScroll/max;c.fill(l+470,listTop,l+476,listBottom,0xFF333333);c.fill(l+470,barY,l+476,barY+barH,0xFFAAAAAA);}if(!canEdit)c.drawTextWithShadow(textRenderer,"Solo lectura: puedes ejecutar este paquete.",l+14,t+306,0xFFFFD080);else if(!runFeedback.isEmpty())c.drawTextWithShadow(textRenderer,runFeedback,l+14,t+306,0xFF55FF55);if(mx>=l+204&&mx<l+272&&my>=t+326&&my<t+346)c.drawTooltip(textRenderer,Text.literal(canEdit&&hasShiftDown()?"Haz clic para eliminar este paquete.":"Solo OP: mantén Shift para habilitar Eliminar."),mx,my);super.render(c,mx,my,d);}
    @Override public boolean mouseClicked(double mx,double my,int button){int l=width/2-245,t=height/2-170;if(button==0&&maxCommandScroll()>0&&mx>=l+467&&mx<=l+479&&my>=t+130&&my<=t+260){commit();draggingCommandScrollbar=true;updateCommandScroll(my);return true;}return super.mouseClicked(mx,my,button);}
    @Override public boolean mouseScrolled(double mx,double my,double h,double v){int l=width/2-245,t=height/2-170;if(mx>=l+14&&mx<l+479&&my>=t+130&&my<t+260&&maxCommandScroll()>0){commit();commandScroll=Math.max(0,Math.min(maxCommandScroll(),commandScroll-(int)Math.signum(v)));clearAndInit();return true;}return super.mouseScrolled(mx,my,h,v);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){if(draggingCommandScrollbar&&button==0){updateCommandScroll(my);return true;}return super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseReleased(double mx,double my,int button){if(button==0&&draggingCommandScrollbar){draggingCommandScrollbar=false;return true;}return super.mouseReleased(mx,my,button);}
    private int maxCommandScroll(){return Math.max(0,pack.commands.size()-6);}
    private void updateCommandScroll(double mouseY){int max=maxCommandScroll();if(max==0)return;int t=height/2-170,listTop=t+130,listBottom=t+260,track=listBottom-listTop,barH=Math.max(20,track*6/pack.commands.size());double fraction=(mouseY-listTop-barH/2.0)/Math.max(1.0,track-barH);commandScroll=(int)Math.round(Math.max(0.0,Math.min(1.0,fraction))*max);clearAndInit();}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){if(canEdit)commit();client.setScreen(parent);}
}
