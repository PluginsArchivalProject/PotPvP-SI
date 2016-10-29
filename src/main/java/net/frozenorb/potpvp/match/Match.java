package net.frozenorb.potpvp.match;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import net.frozenorb.potpvp.PotPvPSI;
import net.frozenorb.potpvp.arena.Arena;
import net.frozenorb.potpvp.kittype.KitType;
import net.frozenorb.potpvp.match.event.MatchCountdownStartEvent;
import net.frozenorb.potpvp.match.event.MatchEndEvent;
import net.frozenorb.potpvp.match.event.MatchSpectatorJoinEvent;
import net.frozenorb.potpvp.match.event.MatchSpectatorLeaveEvent;
import net.frozenorb.potpvp.match.event.MatchStartEvent;
import net.frozenorb.potpvp.match.event.MatchTerminateEvent;
import net.frozenorb.potpvp.postmatchinv.PostMatchPlayer;
import net.frozenorb.potpvp.util.InventoryUtils;
import net.frozenorb.potpvp.util.MongoUtils;
import net.frozenorb.potpvp.util.VisibilityUtils;
import net.frozenorb.qlib.nametag.FrozenNametagHandler;
import net.frozenorb.qlib.qLib;
import net.frozenorb.qlib.util.PlayerUtils;

import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;

public final class Match {

    private static final int MATCH_END_DELAY_SECONDS = 2;

    @Getter private final String id;
    @Getter private final KitType kitType;
    @Getter private final Arena arena;
    @Getter private final List<MatchTeam> teams; // immutable so @Getter is ok
    private final Map<UUID, PostMatchPlayer> postMatchPlayers = new HashMap<>();
    private final Set<UUID> spectators = new HashSet<>();

    @Getter private MatchTeam winner;
    @Getter private MatchEndReason endReason;
    @Getter private MatchState state;
    @Getter private Instant startedAt;
    @Getter private Instant endedAt;

    // we track if matches we started by a duel (/dueL), so the RematchHandler can
    // only give rematches to 1v1s started by that command. prior to this change,
    // scenarios like a team split of a 3 man team (with one sitting out) would get
    // counted as a 1v1 when calculating rematches.
    // https://github.com/FrozenOrb/PotPvP-SI/issues/19
    @Getter private boolean startedViaDuel;

    public Match(KitType kitType, Arena arena, List<MatchTeam> teams, boolean startedViaDuel) {
        this.id = UUID.randomUUID().toString();
        this.kitType = Preconditions.checkNotNull(kitType, "kitType");
        this.arena = Preconditions.checkNotNull(arena, "arena");
        this.teams = ImmutableList.copyOf(teams);
        this.startedViaDuel = startedViaDuel;
    }

    void startCountdown() {
        state = MatchState.COUNTDOWN;

        for (Player player : Bukkit.getOnlinePlayers()) {
            MatchTeam team = getTeam(player.getUniqueId());

            if (team == null) {
                continue;
            }

            Location spawn = team == teams.get(0) ? arena.getTeam1Spawn() : arena.getTeam2Spawn();

            player.teleport(spawn);
            player.getInventory().setHeldItemSlot(0);

            FrozenNametagHandler.reloadPlayer(player);
            FrozenNametagHandler.reloadOthersFor(player);

            VisibilityUtils.updateVisibility(player);
            PlayerUtils.resetInventory(player, GameMode.SURVIVAL);
        }

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
                    return; // so we don't send '0...' message
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

        messageAll(ChatColor.GREEN + "Match started.");
        Bukkit.getPluginManager().callEvent(new MatchStartEvent(this));
    }

    public void endMatch(MatchEndReason reason) {
        state = MatchState.ENDING;
        endedAt = Instant.now();
        endReason = reason;

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUuid = player.getUniqueId();
            MatchTeam team = getTeam(playerUuid);

            if (team != null) {
                postMatchPlayers.computeIfAbsent(playerUuid, v -> new PostMatchPlayer(player));
            }
        }

        int delayTicks = MATCH_END_DELAY_SECONDS * 20;

        messageAll(ChatColor.RED + "Match ended.");
        Bukkit.getPluginManager().callEvent(new MatchEndEvent(this));
        Bukkit.getScheduler().runTaskLater(PotPvPSI.getInstance(), () -> terminateMatch(reason), delayTicks);
    }

    private void terminateMatch(MatchEndReason reason) {
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

        Document document = Document.parse(qLib.PLAIN_GSON.toJson(this));

        // rename 'id' to '_id' (for mongo)
        document.put("_id", document.remove("id"));

        // overwrite fields which would normally serialize too much
        document.put("winner", winner != null ? winner.getId() : null);
        document.put("arena", arena.getSchematic());

        Bukkit.getPluginManager().callEvent(new MatchTerminateEvent(this, document));
        Bukkit.getScheduler().runTaskAsynchronously(PotPvPSI.getInstance(), () -> {
            MongoUtils.getCollection("EndedMatches").insertOne(document);
        });

        PotPvPSI.getInstance().getArenaHandler().releaseArena(arena);
        PotPvPSI.getInstance().getMatchHandler().removeMatch(this);

        // purposely placed after .removeMatch so any code after here
        // doesn't see that this match exists
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUuid = player.getUniqueId();

            if (getTeam(playerUuid) != null || spectators.contains(playerUuid)) {
                PotPvPSI.getInstance().getLobbyHandler().returnToLobby(player);
            }
        }
    }

    public Set<UUID> getSpectators() {
        return ImmutableSet.copyOf(spectators);
    }

    public Map<UUID, PostMatchPlayer> getPostMatchPlayers() {
        return ImmutableMap.copyOf(postMatchPlayers);
    }

    private void checkEnded() {
        List<MatchTeam> teamsAlive = new ArrayList<>();

        for (MatchTeam team : teams) {
            if (!team.getAliveMembers().isEmpty()) {
                teamsAlive.add(team);
            }
        }

        if (teamsAlive.size() == 1) {
            this.winner = teamsAlive.get(0);
            endMatch(MatchEndReason.ENEMIES_ELIMINATED);
        }
    }

    public boolean isSpectator(UUID uuid) {
        return spectators.contains(uuid);
    }

    public void addSpectator(Player player, Player target) {
        addSpectator(player, target, true);
    }

    public void addSpectator(Player player, Player target, boolean teleportPlayer) {
        spectators.add(player.getUniqueId());

        if (teleportPlayer) {
            player.teleport(target != null ? target.getLocation() : arena.getSpectatorSpawn());
        }

        player.getInventory().setHeldItemSlot(0);

        /*
        MatchSpectator specData = match.getSpectators().get(player.getUniqueId());

        if (!specData.isHidden() && match.getState() == MatchState.IN_PROGRESS) {
            SettingHandler settingHandler = PotPvPSlave.getInstance().getSettingHandler();

            for (Player onlinePlayer : PotPvPSlave.getInstance().getServer().getOnlinePlayers()) {
                if (onlinePlayer == player) {
                    continue;
                }

                boolean sameMatch = match.isSpectator(onlinePlayer.getUniqueId()) || match.getCurrentTeam(onlinePlayer) != null;
                boolean spectatorMessagesEnabled = settingHandler.isSettingEnabled(onlinePlayer.getUniqueId(), Setting.SHOW_SPECTATOR_JOIN_MESSAGES);

                if (sameMatch && spectatorMessagesEnabled) {
                    onlinePlayer.sendMessage(ChatColor.AQUA + player.getName() + ChatColor.YELLOW + " is now spectating.");
                }
            }
        }
        */

        FrozenNametagHandler.reloadPlayer(player);
        FrozenNametagHandler.reloadOthersFor(player);

        VisibilityUtils.updateVisibility(player);
        PlayerUtils.resetInventory(player, GameMode.CREATIVE);
        InventoryUtils.resetInventoryDelayed(player);

        Bukkit.getPluginManager().callEvent(new MatchSpectatorJoinEvent(player, this));
    }

    public void removeSpectator(Player player) {
        spectators.remove(player.getUniqueId());
        PotPvPSI.getInstance().getLobbyHandler().returnToLobby(player);

        Bukkit.getPluginManager().callEvent(new MatchSpectatorLeaveEvent(player, this));
    }

    public void markDead(Player player) {
        MatchTeam team = getTeam(player.getUniqueId());

        if (team != null) {
            postMatchPlayers.put(player.getUniqueId(), new PostMatchPlayer(player));
            team.markDead(player.getUniqueId());
            checkEnded();
        }
    }

    public MatchTeam getTeam(UUID playerUuid) {
        for (MatchTeam team : teams) {
            if (team.isAlive(playerUuid)) {
                return team;
            }
        }

        return null;
    }

    public MatchTeam getPreviousTeam(UUID playerUuid) {
        for (MatchTeam team : teams) {
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