package smp.aeternum.carpet.motd;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Provides Aeternum Network's active uptime string for the MOTD overlay card. */
public final class JoinMotd {
    private static final LocalDate FOUNDATION_DATE = LocalDate.of(2021, 5, 12);

    private JoinMotd() { }

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
