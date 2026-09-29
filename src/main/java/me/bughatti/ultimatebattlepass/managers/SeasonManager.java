package me.bughatti.ultimatebattlepass.managers;

import me.bughatti.ultimatebattlepass.UltimateBattlepass;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class SeasonManager {

    public enum WeekState {
        UPCOMING,
        ACTIVE,
        ENDED
    }

    private static final String DEFAULT_ZONE = "America/Argentina/Cordoba";

    private final UltimateBattlepass plugin;

    private ZoneId zone;
    private LocalTime resetTime;
    private LocalDate startDate;
    private int weeks;
    private String seasonName;

    public SeasonManager(UltimateBattlepass plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String zoneId = plugin.getConfig().getString("timezone", DEFAULT_ZONE);
        try {
            zone = ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            plugin.getLogger().warning("Zona horaria inválida '" + zoneId + "'. Usando " + DEFAULT_ZONE);
            zone = ZoneId.of(DEFAULT_ZONE);
        }

        String time = plugin.getConfig().getString("daily-reset-time", "06:00");
        try {
            resetTime = LocalTime.parse(time);
        } catch (DateTimeException e) {
            plugin.getLogger().warning("daily-reset-time inválido '" + time + "'. Usando 06:00");
            resetTime = LocalTime.of(6, 0);
        }

        String date = plugin.getConfig().getString("season.start-date", "");
        try {
            startDate = LocalDate.parse(date);
        } catch (DateTimeException e) {
            plugin.getLogger().warning("season.start-date inválido '" + date + "'. Usando la fecha de hoy.");
            startDate = LocalDate.now(zone);
        }

        weeks = Math.max(1, Math.min(4, plugin.getConfig().getInt("season.weeks", 4)));
        seasonName = plugin.getConfig().getString("season.name", "Temporada 1");
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(zone);
    }

    public ZonedDateTime weekStart(int week) {
        return startDate.atTime(resetTime).atZone(zone).plusWeeks(week - 1L);
    }

    public ZonedDateTime weekEnd(int week) {
        return weekStart(week).plusWeeks(1);
    }

    public ZonedDateTime seasonEnd() {
        return weekEnd(weeks);
    }

    public boolean isWeekEnabled(int week) {
        return week >= 1 && week <= weeks;
    }

    public WeekState getWeekState(int week) {
        ZonedDateTime now = now();
        if (now.isBefore(weekStart(week))) {
            return WeekState.UPCOMING;
        }
        if (now.isBefore(weekEnd(week))) {
            return WeekState.ACTIVE;
        }
        return WeekState.ENDED;
    }

    /**
     * Devuelve la semana actual (1 a weeks), 0 si la temporada todavía no empezó
     * o -1 si ya terminó.
     */
    public int getCurrentWeek() {
        ZonedDateTime now = now();
        if (now.isBefore(weekStart(1))) {
            return 0;
        }
        for (int week = 1; week <= weeks; week++) {
            if (now.isBefore(weekEnd(week))) {
                return week;
            }
        }
        return -1;
    }

    public boolean isSeasonRunning() {
        return getCurrentWeek() >= 1;
    }

    /**
     * Clave del "día de misiones". Cambia a la hora de reinicio (por defecto 06:00),
     * no a medianoche.
     */
    public String getDailyKey() {
        ZonedDateTime shifted = now()
                .minusHours(resetTime.getHour())
                .minusMinutes(resetTime.getMinute());
        return shifted.toLocalDate().toString();
    }

    public ZonedDateTime nextDailyReset() {
        ZonedDateTime now = now();
        ZonedDateTime next = now.with(resetTime);
        if (!next.isAfter(now)) {
            next = next.plusDays(1);
        }
        return next;
    }

    public long secondsUntil(ZonedDateTime target) {
        return Math.max(0, Duration.between(now(), target).getSeconds());
    }

    public long secondsUntilDailyReset() {
        return secondsUntil(nextDailyReset());
    }

    /**
     * Formato tipo "13h 53m 34s".
     */
    public static String formatDuration(long totalSeconds) {
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            builder.append(hours).append("h ");
        }
        if (days > 0 || hours > 0 || minutes > 0) {
            builder.append(minutes).append("m ");
        }
        builder.append(seconds).append("s");
        return builder.toString();
    }

    /**
     * Inicia una nueva temporada con el día de misiones actual como Semana #1.
     */
    public void startNewSeasonToday() {
        LocalDate day = LocalDate.parse(getDailyKey());
        plugin.getConfig().set("season.start-date", day.toString());
        plugin.saveConfig();
        reload();
    }

    public String getSeasonId() {
        return startDate.toString();
    }

    public String getSeasonName() {
        return seasonName;
    }

    public int getWeeks() {
        return weeks;
    }

    public ZoneId getZone() {
        return zone;
    }

    public LocalTime getResetTime() {
        return resetTime;
    }
              }
