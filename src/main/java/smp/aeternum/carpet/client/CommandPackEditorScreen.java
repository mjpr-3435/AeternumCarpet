package smp.aeternum.carpet.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import smp.aeternum.carpet.actions.CommandPack;
import smp.aeternum.carpet.actions.CommandStep;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Second and final screen. It edits a package and its ordered command fields in one place. */
final class CommandPackEditorScreen extends Screen {
    private final Screen parent; private final List<CommandPack> packs; private final CommandPack pack;
    private TextFieldWidget name,description; private final List<TextFieldWidget> stepFields=new ArrayList<>();
    private ButtonWidget deleteButton;
    private String runFeedback="";
    CommandPackEditorScreen(Screen parent,List<CommandPack> packs,CommandPack pack){super(Text.literal("Editar paquete"));this.parent=parent;this.packs=packs;this.pack=pack;}
    @Override protected void init(){int l=width/2-245,t=height/2-170;stepFields.clear();
        name=field(l+14,t+48,452,pack.name,80,"Nombre del paquete");description=field(l+14,t+82,452,pack.description,240,"Descripción opcional");
        for(int i=0;i<Math.min(7,pack.commands.size());i++){int index=i,y=t+139+i*22;CommandStep step=pack.commands.get(i);
            addDrawableChild(ButtonWidget.builder(Text.literal(step.type==CommandStep.Type.COMMAND?"CMD":"ESPERA"),b->{commit();CommandStep current=pack.commands.get(index);current.type=current.type==CommandStep.Type.COMMAND?CommandStep.Type.WAIT:CommandStep.Type.COMMAND;current.value=current.type==CommandStep.Type.WAIT?"1":"say ";clearAndInit();}).dimensions(l+14,y,68,18).build());
            stepFields.add(field(l+88,y,330,step.value,step.type==CommandStep.Type.COMMAND?2048:8,step.type==CommandStep.Type.COMMAND?"Comando de servidor":"Segundos"));
            addDrawableChild(ButtonWidget.builder(Text.literal("X"),b->{commit();if(index<pack.commands.size())pack.commands.remove(index);clearAndInit();}).dimensions(l+424,y,42,18).build());}
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Nueva línea"),b->{commit();pack.commands.add(new CommandStep(CommandStep.Type.COMMAND,"say "));clearAndInit();}).dimensions(l+14,t+326,112,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Ejecutar"),b->{commit();CommandPacksRequest.run(pack);runFeedback="Ejecutando paquete… mira el chat para el resultado.";}).dimensions(l+278,t+326,88,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Guardar"),b->{commit();sortAndSave();client.setScreen(parent);}).dimensions(l+372,t+326,94,20).build());
        deleteButton=ButtonWidget.builder(Text.literal("Eliminar"),b->{if(!hasShiftDown())return;packs.remove(pack);sortAndSave();client.setScreen(parent);}).dimensions(l+204,t+326,68,20).build();
        deleteButton.active=false;addDrawableChild(deleteButton);}
    private TextFieldWidget field(int x,int y,int w,String v,int max,String hint){TextFieldWidget f=new TextFieldWidget(textRenderer,x,y,w,18,Text.literal(hint));f.setText(v==null?"":v);f.setMaxLength(max);addDrawableChild(f);return f;}
    private void commit(){if(name==null)return;pack.name=name.getText().trim();pack.description=description.getText().trim();int visible=Math.min(stepFields.size(),pack.commands.size());for(int i=0;i<visible;i++)pack.commands.get(i).value=stepFields.get(i).getText().trim();}
    private void sortAndSave(){packs.sort(Comparator.comparing(p->p.name==null?"":p.name,String.CASE_INSENSITIVE_ORDER));CommandPacksRequest.save(packs);}
    @Override public void renderBackground(DrawContext c,int x,int y,float d){}
    @Override public void render(DrawContext c,int mx,int my,float d){deleteButton.active=hasShiftDown();c.fill(0,0,width,height,0x90000000);int l=width/2-245,t=height/2-170;c.fill(l,t,l+480,t+365,0xE0101010);c.drawBorder(l,t,480,365,0xFF909090);c.drawTextWithShadow(textRenderer,"PAQUETE",l+14,t+14,0xFFFFFFFF);c.drawTextWithShadow(textRenderer,"Nombre",l+14,t+34,0xFFAAAAAA);c.drawTextWithShadow(textRenderer,"Descripción",l+14,t+68,0xFFAAAAAA);c.drawTextWithShadow(textRenderer,"PASOS  ·  CMD se ejecuta como servidor  ·  ESPERA usa segundos",l+14,t+118,0xFFAAAAAA);if(pack.commands.isEmpty())c.drawCenteredTextWithShadow(textRenderer,"Añade una línea; usa el botón CMD para alternar a espera.",width/2,t+205,0xFFAAAAAA);if(!runFeedback.isEmpty())c.drawTextWithShadow(textRenderer,runFeedback,l+14,t+306,0xFF55FF55);if(mx>=l+204&&mx<l+272&&my>=t+326&&my<t+346)c.drawTooltip(textRenderer,Text.literal(hasShiftDown()?"Haz clic para eliminar este paquete.":"Mantén Shift para habilitar Eliminar."),mx,my);super.render(c,mx,my,d);}
    @Override public void close(){commit();client.setScreen(parent);}
}
