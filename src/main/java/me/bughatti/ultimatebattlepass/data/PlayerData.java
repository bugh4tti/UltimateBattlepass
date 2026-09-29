package me.bughatti.ultimatebattlepass.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private String name = "";
    private String seasonId = "";
    private String dailyKey = "";
    private int points = 0;

    // Booster activo (se guarda con la hora exacta en que vence)
    private String boosterName = "";
    private double boosterMultiplier = 1.0;
    private double boosterChance = 100.0;
    private long boosterExpires = 0L;

    private final Set<Integer> claimedFree = new HashSet<>();
    private final Set<Integer> claimedPremium = new HashSet<>();
    private final Map<String, Integer> progress = new HashMap<>();
    private final Set<String> completed = new HashSet<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String getSeasonId() {
        return seasonId;
    }

    public void setSeasonId(String seasonId) {
        this.seasonId = seasonId;
    }

    public String getDailyKey() {
        return dailyKey;
    }

    public void setDailyKey(String dailyKey) {
        this.dailyKey = dailyKey;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = Math.max(0, points);
    }

    public void addPoints(int amount) {
        setPoints(this.points + amount);
    }

    public Set<Integer> getClaimedFree() {
        return claimedFree;
    }

    public Set<Integer> getClaimedPremium() {
        return claimedPremium;
    }

    public Map<String, Integer> getProgress() {
        return progress;
    }

    public Set<String> getCompleted() {
        return completed;
    }

    public int getProgress(String key) {
        return progress.getOrDefault(key, 0);
    }

    public void setProgress(String key, int value) {
        progress.put(key, value);
    }

    public int addProgress(String key, int amount) {
        int value = getProgress(key) + amount;
        progress.put(key, value);
        return value;
    }

    public boolean isCompleted(String key) {
        return completed.contains(key);
    }

    public void markCompleted(String key) {
        completed.add(key);
    }

    // ------------------------------------------------------------------
    // Booster
    // ------------------------------------------------------------------

    public String getBoosterName() {
        return boosterName;
    }

    public double getBoosterMultiplier() {
        return boosterMultiplier;
    }

    public double getBoosterChance() {
        return boosterChance;
    }

    public long getBoosterExpires() {
        return boosterExpires;
    }

    public boolean hasActiveBooster() {
        return boosterExpires > System.currentTimeMillis();
    }

    public long getBoosterRemainingSeconds() {
        return Math.max(0L, (boosterExpires - System.currentTimeMillis()) / 1000L);
    }

    public void setBooster(String name, double multiplier, double chance, long expires) {
        this.boosterName = name == null ? "" : name;
        this.boosterMultiplier = multiplier;
        this.boosterChance = chance;
        this.boosterExpires = expires;
    }

    public void clearBooster() {
        this.boosterName = "";
        this.boosterMultiplier = 1.0;
        this.boosterChance = 100.0;
        this.boosterExpires = 0L;
    }

    // ------------------------------------------------------------------
    // Reinicios
    // ------------------------------------------------------------------

    /**
     * Borra el progreso de todas las misiones diarias (claves "daily_...").
     */
    public void clearDaily() {
        Iterator<String> progressIterator = progress.keySet().iterator();
        while (progressIterator.hasNext()) {
            if (progressIterator.next().startsWith("daily_")) {
                progressIterator.remove();
            }
        }

        Iterator<String> completedIterator = completed.iterator();
        while (completedIterator.hasNext()) {
            if (completedIterator.next().startsWith("daily_")) {
                completedIterator.remove();
            }
        }
    }

    /**
     * Reinicia todo el progreso de la temporada. El booster activo se conserva.
     */
    public void resetSeason() {
        points = 0;
        claimedFree.clear();
        claimedPremium.clear();
        progress.clear();
        completed.clear();
    }
    }
