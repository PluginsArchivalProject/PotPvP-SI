package net.frozenorb.potpvp.lobby;

import net.frozenorb.potpvp.PotPvPSI;
import net.frozenorb.potpvp.lobby.listener.LobbyGeneralListener;
import net.frozenorb.potpvp.lobby.listener.LobbyItemListener;
import net.frozenorb.potpvp.util.InventoryUtils;
import net.frozenorb.potpvp.util.VisibilityUtils;
import net.frozenorb.qlib.nametag.FrozenNametagHandler;
import net.frozenorb.qlib.util.PlayerUtils;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class LobbyHandler {

    public LobbyHandler() {
        Bukkit.getPluginManager().registerEvents(new LobbyGeneralListener(), PotPvPSI.getInstance());
        Bukkit.getPluginManager().registerEvents(new LobbyItemListener(), PotPvPSI.getInstance());
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

        VisibilityUtils.updateVisibility(player);
        PlayerUtils.resetInventory(player, GameMode.SURVIVAL);
        InventoryUtils.resetInventoryDelayed(player);
    }

    public Location getLobbyLocation() {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        spawn.add(0.5, 0.5, 0.5); // 'prettify' so players spawn in middle of block
        return spawn;
    }

}