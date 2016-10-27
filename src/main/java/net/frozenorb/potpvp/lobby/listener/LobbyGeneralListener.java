package net.frozenorb.potpvp.lobby.listener;

import net.frozenorb.potpvp.PotPvPSI;
import net.frozenorb.potpvp.kit.menu.editkit.EditKitMenu;
import net.frozenorb.potpvp.match.MatchHandler;
import net.frozenorb.qlib.menu.Menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

import java.util.UUID;

public final class LobbyGeneralListener implements Listener {

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        PotPvPSI.getInstance().getLobbyHandler().returnToLobby(event.getPlayer());
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        MatchHandler matchHandler = PotPvPSI.getInstance().getMatchHandler();
        UUID entityUuid = event.getEntity().getUniqueId();

        if (!matchHandler.isPlayingOrSpectatingMatch(entityUuid)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        MatchHandler matchHandler = PotPvPSI.getInstance().getMatchHandler();
        UUID entityUuid = event.getEntity().getUniqueId();

        if (!matchHandler.isPlayingOrSpectatingMatch(entityUuid)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        MatchHandler matchHandler = PotPvPSI.getInstance().getMatchHandler();

        if (!matchHandler.isPlayingOrSpectatingMatch(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        MatchHandler matchHandler = PotPvPSI.getInstance().getMatchHandler();

        if (matchHandler.isPlayingOrSpectatingMatch(event.getPlayer())) {
            return;
        }

        // just remove the item for players in the kit editor, so they can 'drop' items to remove them
        if (Menu.currentlyOpenedMenus.get(event.getPlayer().getName()) instanceof EditKitMenu) {
            event.getItemDrop().remove();
        } else {
            event.setCancelled(true);
        }
    }

}