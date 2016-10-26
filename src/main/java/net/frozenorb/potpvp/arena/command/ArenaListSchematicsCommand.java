package net.frozenorb.potpvp.arena.command;

import net.frozenorb.potpvp.PotPvPSI;
import net.frozenorb.potpvp.arena.Arena;
import net.frozenorb.potpvp.arena.ArenaHandler;
import net.frozenorb.potpvp.arena.PotPvPSchematic;
import net.frozenorb.qlib.command.Command;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public final class ArenaListSchematicsCommand {

    @Command(names = { "arena listSchematics" }, permission = "op")
    public static void arenaListSchematics(Player sender) {
        ArenaHandler arenaHandler = PotPvPSI.getInstance().getArenaHandler();

        sender.sendMessage(ChatColor.RED + "------ " + ChatColor.WHITE + "Schematics" + ChatColor.RED + " ------");

        for (PotPvPSchematic schematic : arenaHandler.getSchematics()) {
            int totalCopies = 0;
            int inUseCopies = 0;

            for (Arena arena : arenaHandler.getArenas(schematic)) {
                totalCopies++;

                if (arena.isInUse()) {
                    inUseCopies++;
                }
            }

            String inUseStr = inUseCopies + "/" + totalCopies;
            String rankedStr = schematic.isSupportsRanked() ? ChatColor.GREEN + "Ranked allowed" : ChatColor.RED + "Unranked only";

            sender.sendMessage(schematic.getName() + ": " + ChatColor.GREEN + inUseStr + ChatColor.GRAY + " - " + rankedStr);
        }
    }

}