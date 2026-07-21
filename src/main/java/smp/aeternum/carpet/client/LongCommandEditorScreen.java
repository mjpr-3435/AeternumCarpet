package smp.aeternum.carpet.client;
import java.util.function.Consumer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
/** Multiline editor for command JSON and other long action lines. */
final class LongCommandEditorScreen extends Screen {
 private final Screen parent; private final Consumer<String> apply; private final boolean editable; private EditBoxWidget text;
 LongCommandEditorScreen(Screen parent,String value,boolean editable,Consumer<String> apply){super(Text.literal("Editar comando"));this.parent=parent;this.editable=editable;this.apply=apply;this.value=value;} private final String value;
 @Override protected void init(){int x=width/2-230,y=height/2-150;text=EditBoxWidget.builder().x(x+12).y(y+38).hasOverlay(false).build(textRenderer,436,220,Text.literal("Comando"));text.setText(value);text.setMaxLength(2048);addDrawableChild(text);addDrawableChild(ButtonWidget.builder(Text.literal("Volver"),b->client.setScreen(parent)).dimensions(x+250,y+270,94,20).build());ButtonWidget save=ButtonWidget.builder(Text.literal("Aplicar"),b->{apply.accept(text.getText());client.setScreen(parent);}).dimensions(x+350,y+270,86,20).build();save.active=editable;addDrawableChild(save);}
 @Override public void render(net.minecraft.client.gui.DrawContext c,int mx,int my,float d){int x=width/2-230,y=height/2-150;c.fill(0,0,width,height,0x90000000);c.fill(x,y,x+460,y+300,0xE0101010);c.drawBorder(x,y,460,300,0xFF909090);c.drawTextWithShadow(textRenderer,"COMANDO",x+12,y+14,0xFFFFFFFF);super.render(c,mx,my,d);}@Override public boolean shouldPause(){return false;}
}
