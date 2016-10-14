package net.frozenorb.potpvp.match;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import com.mongodb.client.MongoCollection;

import net.frozenorb.potpvp.PotPvPSI;
import net.frozenorb.potpvp.arena.Arena;
import net.frozenorb.potpvp.kittype.KitType;
import net.frozenorb.potpvp.match.event.MatchCountdownStartEvent;
import net.frozenorb.potpvp.match.event.MatchEndEvent;
import net.frozenorb.potpvp.match.event.MatchStartEvent;
import net.frozenorb.potpvp.util.InventoryUtils;
import net.frozenorb.qlib.nametag.FrozenNametagHandler;
import net.frozenorb.qlib.qLib;
import net.frozenorb.qlib.util.PlayerUtils;

import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

public final class Match {

    private static final int MATCH_END_DELAY_SECONDS = 5;

    @Getter private final KitType kitType;
    @Getter private final Arena arena;
    @Getter private final String id = UUID.randomUUID().toString();
    private final Map<String, MatchTeam> teams = new ConcurrentHashMap<>();
    private final Set<UUID> spectators = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Getter private MatchTeam winner;
    @Getter private MatchEndReason endReason;
    @Getter private MatchState state;
    @Getter private Instant startedAt;
    @Getter private Instant endedAt;

    private final Map<UUID, Integer> potionCountAtDeath = new ConcurrentHashMap<>();
    private final Map<UUID, EndedGcdMatchPlayer> endedMetaAtDeath = new ConcurrentHashMap<>();

    public Match(KitType kitType, Arena arena, List<MatchTeam> teams) {
        this.kitType = Preconditions.checkNotNull(kitType, "kitType");
        this.arena = Preconditions.checkNotNull(arena, "arena");

        for (MatchTeam team : teams) {
            this.teams.put(team.getId(), team);
        }
    }

    private void startCountdown() {
        state = MatchState.COUNTDOWN;

        Bukkit.getPluginManager().callEvent(new MatchCountdownStartEvent(this));

        new BukkitRunnable() {

            int countdownTimeRemaining = 5;

            public void run() {
                if (state != MatchState.COUNTDOWN) {
                    cancel();
                    return;
                }

                if (countdownTimeRemaining == 0) {
                    playSoundAll(Sound.NOTE_PLING, 2F);
                    startMatch();
                } else if (countdownTimeRemaining <= 3) {
                    playSoundAll(Sound.NOTE_PLING, 1F);
                }

                messageAll(ChatColor.YELLOW.toString() + countdownTimeRemaining + "...");
                countdownTimeRemaining--;
            }

        }.runTaskTimer(PotPvPSI.getInstance(), 0L, 20L);
    }

    private void startMatch() {
        state = MatchState.IN_PROGRESS;
        startedAt = Instant.now();

        Bukkit.getPluginManager().callEvent(new MatchStartEvent(this));
        messageAll(ChatColor.GREEN + "Match started.");
    }

    public void endMatch(MatchEndReason reason) {
        state = MatchState.ENDING;
        endedAt = Instant.now();
        endReason = reason;

        int delayTicks = MATCH_END_DELAY_SECONDS * 20;

        Bukkit.getPluginManager().callEvent(new MatchEndEvent(this));
        Bukkit.getScheduler().runTaskLater(PotPvPSI.getInstance(), () -> terminateMatch(reason), delayTicks);
    }

    public void terminateMatch(MatchEndReason reason) {
        state = MatchState.TERMINATED;

        // if endedAt wasn't set before (if terminateMatch was called directly)
        // we want to make sure we set an ending time.
        if (endedAt != null) {
            endedAt = Instant.now();
        }

        // if endReason wasn't set before (if terminateMatch was called directly)
        // we want to make sure we set an end reason.
        if (endReason != null) {
            endReason = reason;
        }

        // TODO: Clean up items from arena.
            /*
            // force load all arena chunks so that we can clean arenas correctly (most should still be loaded)
            map.getCachedArenaChunks().stream().filter(chunk -> !chunk.isLoaded()).forEach(Chunk::load);

            // clear all items inside the map
            BaseMap.getWorld().getEntitiesByClass(Item.class).stream().filter(item -> map.getBounds().contains(item.getLocation())).forEach(Entity::remove);
            */

        Document document = Document.parse(qLib.PLAIN_GSON.toJson(this));

        document.remove("id");
        document.put("_id", getId());

        document.put("winner", winner != null ? winner.getId() : null);
        document.put("map", map == null ? "none" : map.getSchematic());
        document.put("startedAt", new Date(startedAt));
        document.put("endedAt", new Date(endedAt));
        document.put("playerInfo", Document.parse(qLib.PLAIN_GSON.toJson(playerInfo)));
        document.put("playerData", Document.parse(qLib.PLAIN_GSON.toJson(playerData)));

        Bukkit.getScheduler().runTaskAsynchronously(PotPvPSlave.getInstance(), () -> {
            MongoCollection<Document> endedMatches = PotPvPSlave.getInstance().getMongoDatabase().getCollection("EndedMatches");
            endedMatches.insertOne(document);
        });

        PotPvPSI.getInstance().getMatchHandler().removeMatch(this);
    }

    public List<MatchTeam> getTeams() {
        return ImmutableList.copyOf(teams.values());
    }

    public Set<UUID> getSpectators() {
        return ImmutableSet.copyOf(spectators);
    }

    public boolean checkEnded() {
        List<MatchTeam> teamsAlive = new ArrayList<>();

        for (MatchTeam team : teams) {
            if (!team.getAliveMembers().isEmpty()) {
                teamsAlive.add(team);
            }
        }

        if (teamsAlive.size() != 1) {
            return false;
        }

        MatchTeam winnerTeam = teamsAlive.get(0);

        this.winner = winnerTeam;
        endMatch(MatchEndReason.ENEMIES_ELIMINATED);

        return true;
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.contains(uuid);
    }

    public void addSpectator(Player player) {
        spectators.add(player.getUniqueId());

        FrozenNametagHandler.reloadPlayer(player);
        FrozenNametagHandler.reloadOthersFor(player);

        player.getInventory().setHeldItemSlot(0);

        MatchUtility.updateVisibility(player);
        PlayerUtils.resetInventory(player, GameMode.CREATIVE);
        InventoryUtils.resetInventory(player);
    }

    public void removeSpectator(Player player) {
        spectators.remove(player.getUniqueId());

        FrozenNametagHandler.reloadPlayer(player);
        FrozenNametagHandler.reloadOthersFor(player);

        PlayerUtils.resetInventory(player);
    }

    public MatchTeam getTeam(UUID playerUuid) {
        for (MatchTeam team : teams.values()) {
            if (team.isAlive(playerUuid)) {
                return team;
            }
        }

        return null;
    }

    public MatchTeam getPreviousTeam(UUID playerUuid) {
        for (MatchTeam team : teams.values()) {
            if (team.getAllMembers().contains(playerUuid)) {
                return team;
            }
        }

        return null;
    }

    /**
     * Sends a basic chat message to all alive participants and spectators
     * @param message the message to send
     */
    public void messageAll(String message) {
        messageAlive(message);
        messageSpectators(message);
    }

    /**
     * Plays a sound for all alive participants and spectators
     * @param sound the Sound to play
     * @param pitch the pitch to play the provided sound at
     */
    public void playSoundAll(Sound sound, float pitch) {
        playSoundAlive(sound, pitch);
        playSoundSpectators(sound, pitch);
    }

    /**
     * Sends a basic chat message to all spectators
     * @param message the message to send
     */
    public void messageSpectators(String message) {
        for (UUID spectator : spectators) {
            Player spectatorBukkit = Bukkit.getPlayer(spectator);

            if (spectatorBukkit != null) {
                spectatorBukkit.sendMessage(message);
            }
        }
    }

    /**
     * Plays a sound for all spectators
     * @param sound the Sound to play
     * @param pitch the pitch to play the provided sound at
     */
    public void playSoundSpectators(Sound sound, float pitch) {
        for (UUID spectator : spectators) {
            Player spectatorBukkit = Bukkit.getPlayer(spectator);

            if (spectatorBukkit != null) {
                spectatorBukkit.playSound(spectatorBukkit.getEyeLocation(), sound, 10F, pitch);
            }
        }
    }

    /**
     * Sends a basic chat message to all alive participants
     * @see MatchTeam#messageAlive(String)
     * @param message the message to send
     */
    public void messageAlive(String message) {
        for (MatchTeam team : teams) {
            team.messageAlive(message);
        }
    }

    /**
     * Plays a sound for all alive participants
     * @param sound the Sound to play
     * @param pitch the pitch to play the provided sound at
     */
    public void playSoundAlive(Sound sound, float pitch) {
        for (MatchTeam team : teams) {
            team.playSoundAlive(sound, pitch);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Match && ((Match) o).id.equals(id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}