package net.frozenorb.potpvp.duels.command;

import com.google.common.collect.ImmutableList;

import net.frozenorb.potpvp.PotPvPSI;
import net.frozenorb.potpvp.duels.DuelHandler;
import net.frozenorb.potpvp.duels.DuelInvite;
import net.frozenorb.potpvp.duels.DuelLang;
import net.frozenorb.potpvp.match.Match;
import net.frozenorb.potpvp.match.MatchTeam;
import net.frozenorb.potpvp.party.Party;
import net.frozenorb.qlib.command.Command;
import net.frozenorb.qlib.command.Param;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class DuelAcceptCommand {

    @Command(names = {"accept"}, permission = "")
    public static void accept(Player sender, @Param(name = "player") Player target) {
        if (sender == target) {
            sender.sendMessage(DuelLang.CANT_ACCEPT_DUEL_FROM_YOURSELF.toString());
            return;
        }

        DuelInvite invite = DuelHandler.instance().inviteBy(target);

        if (invite == null || !invite.sentTo().equals(sender.getUniqueId())) {
            sender.sendMessage(DuelLang.NO_INVITE_HAS_BEEN_SENT.fill(target.getName()));
            return;
        }

        Party party = PotPvPSI.getInstance().getPartyHandler().getParty(sender);

        if (party != null && !party.isLeader(sender.getUniqueId())) {
            party.leave(sender);
        }

        DuelHandler.instance().purgeInvitesFrom(target);
        Set<UUID> senderTeam = teamFor(sender.getUniqueId());
        Set<UUID> targetTeam = teamFor(target.getUniqueId());

        Match match = PotPvPSI.getInstance().getMatchHandler().startMatch(
                ImmutableList.of(
                    new MatchTeam(UUID.randomUUID().toString(), senderTeam),
                    new MatchTeam(UUID.randomUUID().toString(), targetTeam)
                ),
                invite.kitType(),
                true // see Match#startedViaDuel
        );

        if (match == null) {
            sender.sendMessage(DuelLang.ERROR_STARTING_MATCH.toString());
            target.sendMessage(DuelLang.ERROR_STARTING_MATCH.toString());
        }
    }

    private static Set<UUID> teamFor(UUID player) {
        Party party = PotPvPSI.getInstance().getPartyHandler().getParty(Bukkit.getPlayer(player));

        if (party != null && party.isLeader(player)) {
            return party.getMembers();
        }

        Set<UUID> members = new HashSet<>();

        members.add(player);
        return members;
    }

}