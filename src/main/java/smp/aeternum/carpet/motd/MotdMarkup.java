package smp.aeternum.carpet.motd;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import java.util.Map;

/** Compatibility markup for MOTD tasks: \red, \bold, \reset, etc. */
public final class MotdMarkup {
    private static final Map<String, Formatting> COLORS=Map.ofEntries(
        Map.entry("black",Formatting.BLACK),Map.entry("dark_blue",Formatting.DARK_BLUE),Map.entry("dark_green",Formatting.DARK_GREEN),Map.entry("dark_aqua",Formatting.DARK_AQUA),Map.entry("dark_red",Formatting.DARK_RED),Map.entry("dark_purple",Formatting.DARK_PURPLE),Map.entry("gold",Formatting.GOLD),Map.entry("gray",Formatting.GRAY),Map.entry("dark_gray",Formatting.DARK_GRAY),Map.entry("blue",Formatting.BLUE),Map.entry("green",Formatting.GREEN),Map.entry("aqua",Formatting.AQUA),Map.entry("red",Formatting.RED),Map.entry("light_purple",Formatting.LIGHT_PURPLE),Map.entry("yellow",Formatting.YELLOW),Map.entry("white",Formatting.WHITE));
    private MotdMarkup(){}
    public static Text parse(String input){MutableText result=Text.empty();Style style=Style.EMPTY;StringBuilder plain=new StringBuilder();for(int i=0;i<input.length();){if(input.charAt(i)=='\\'){int end=i+1;while(end<input.length()&&(Character.isLetter(input.charAt(end))||input.charAt(end)=='_'))end++;String token=input.substring(i+1,end);if(end>i+1&&(COLORS.containsKey(token)||token.equals("bold")||token.equals("obfuscated")||token.equals("strikethrough")||token.equals("underline")||token.equals("italic")||token.equals("reset"))){if(!plain.isEmpty()){result.append(Text.literal(plain.toString()).setStyle(style));plain.setLength(0);}if(COLORS.containsKey(token))style=style.withColor(COLORS.get(token));else if(token.equals("bold"))style=style.withBold(true);else if(token.equals("obfuscated"))style=style.withObfuscated(true);else if(token.equals("strikethrough"))style=style.withStrikethrough(true);else if(token.equals("underline"))style=style.withUnderline(true);else if(token.equals("italic"))style=style.withItalic(true);else style=Style.EMPTY;i=end;continue;}}plain.append(input.charAt(i++));}if(!plain.isEmpty())result.append(Text.literal(plain.toString()).setStyle(style));return result;}
}
