package smp.aeternum.carpet.motd;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.util.Formatting;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A local replacement for the MCDIS join banner; deliberately contains no task/last-area data. */
public final class JoinMotd {
    private static final LocalDate FOUNDATION_DATE = LocalDate.of(2021, 5, 12);
    private JoinMotd() { }

    public static void send(ServerPlayerEntity player) {
        player.sendMessage(Text.empty()
            .append(Text.literal("Aeternum ").formatted(Formatting.WHITE, Formatting.BOLD))
            .append(Text.literal("Network").formatted(Formatting.BLUE, Formatting.BOLD)), false);
        player.sendMessage(Text.literal("--------------------------").formatted(Formatting.DARK_GRAY), false);
        MutableText servers = Text.literal("Servers: ").formatted(Formatting.WHITE);
        for (String name : new String[]{"SMP", "CMP", "MMP", "PMP"}) servers.append(serverButton(name));
        player.sendMessage(servers, false);
        player.sendMessage(Text.literal(activeTime()).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("--------------------------").formatted(Formatting.DARK_GRAY), false);
        player.sendMessage(Text.literal("MCDIS: !!mdhelp       MCDR: !!help").formatted(Formatting.GRAY), false);
        sendTasks(player);
    }

    public static void sendTasks(ServerPlayerEntity player) {
        var tasks=MotdTaskStore.all();
        if(tasks.isEmpty()) return;
        player.sendMessage(Text.literal("--------------------------").formatted(Formatting.DARK_GRAY), false);
        for(int i=0;i<tasks.size();i++) player.sendMessage(Text.literal("["+(i+1)+"] ").formatted(Formatting.DARK_GRAY).append(MotdMarkup.parse(tasks.get(i))), false);
    }

    private static Text serverButton(String name) {
        return Text.literal("[" + name + "] ").setStyle(Style.EMPTY.withColor(Formatting.WHITE).withBold(true)
            .withClickEvent(new ClickEvent.RunCommand("/server " + name))
            .withHoverEvent(new HoverEvent.ShowText(Text.literal("Conectar a " + name))));
    }

    public static String activeTime() {
        LocalDate today = LocalDate.now();
        int years = today.getYear() - FOUNDATION_DATE.getYear();
        LocalDate anniversary = FOUNDATION_DATE.plusYears(years);
        if (anniversary.isAfter(today)) anniversary = FOUNDATION_DATE.plusYears(--years);
        long days = ChronoUnit.DAYS.between(anniversary, today);
        if (years == 0) return "Tiempo activo: " + days + " días";
        if (days == 0) return "Tiempo activo: " + years + " años";
        return "Tiempo activo: " + years + " años " + days + " días";
    }
}
