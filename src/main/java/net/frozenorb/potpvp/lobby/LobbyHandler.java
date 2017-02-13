package net.frozenorb.potpvp.lobby;

import net.frozenorb.potpvp.PotPvPSI;
import net.frozenorb.potpvp.lobby.listener.LobbyGeneralListener;
import net.frozenorb.potpvp.lobby.listener.LobbyItemListener;
import net.frozenorb.potpvp.lobby.listener.LobbySpecModeListener;
import net.frozenorb.potpvp.util.InventoryUtils;
import net.frozenorb.potpvp.util.VisibilityUtils;
import net.frozenorb.qlib.nametag.FrozenNametagHandler;
import net.frozenorb.qlib.util.PlayerUtils;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class LobbyHandler {

    /**
     * Stores players who are in "spectator mode", which gives them fly mode
     * and a different lobby hotbar. This setting is purely cosmetic, it doesn't
     * change what a player can/can't do (with the exception of not giving them
     * certain clickable items - but that's just a UX decision)
     */
    private final Set<UUID> spectatorMode = new HashSet<>();

    public LobbyHandler() {
        Bukkit.getPluginManager().registerEvents(new LobbyGeneralListener(), PotPvPSI.getInstance());
        Bukkit.getPluginManager().registerEvents(new LobbyItemListener(this), PotPvPSI.getInstance());
        Bukkit.getPluginManager().registerEvents(new LobbySpecModeListener(), PotPvPSI.getInstance());
    }

    /**
     * Returns a player to the main lobby. This includes performing
     * the teleport, clearing their inventory, updating their nametag,
     * etc. etc.
     * @param player the player who is to be returned
     */
    public void returnToLobby(Player player) {
        player.teleport(getLobbyLocation());
        
        player.getInventory().setHeldItemSlot(0);

        FrozenNametagHandler.reloadPlayer(player);
        FrozenNametagHandler.reloadOthersFor(player);

        spectatorMode.remove(player.getUniqueId()); // before the inv reset
        VisibilityUtils.updateVisibility(player);
        PlayerUtils.resetInventory(player, GameMode.SURVIVAL);
        InventoryUtils.resetInventoryDelayed(player);

        PotPvPSI.getInstance().getLogger().info("Teleported " + player.getName() + " to spawn");
    }

    public boolean isInSpectatorMode(Player player) {
        return spectatorMode.contains(player.getUniqueId());
    }

    public void setSpectatorMode(Player player, boolean mode) {
        boolean changed;

        if (mode) {
            changed = spectatorMode.add(player.getUniqueId());
        } else {
            changed = spectatorMode.remove(player.getUniqueId());
        }

        if (changed) {
            // fly mode is toggled in the inventory reset method
            InventoryUtils.resetInventoryNow(player);
        }
    }

    public Location getLobbyLocation() {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        spawn.add(0.5, 0.5, 0.5); // 'prettify' so players spawn in middle of block
        return spawn;
    }

}