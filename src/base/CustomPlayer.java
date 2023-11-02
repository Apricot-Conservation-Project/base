package base;

import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Unit;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;

public class CustomPlayer {
    public Player player;
    public int playTime;
    public boolean connected;
    public boolean historyMode = false;
    public int lastvoteBan;
    public boolean destroyMode = false;
    protected String rawName;
    public Team team;
    public Seq<Unit> followers = new Seq<>();
    public boolean hudEnabled = true;
    public int survXp;
    public int plagueXp;
    public int wins;
    int brokenBlocks = 0;
    boolean sus = false;

    public CustomPlayer(Player player) {
        this.player = player;
        this.rawName = player.plainName();
        player.color = Color.white;
        this.lastvoteBan = (int) Instant.now().getEpochSecond() - 60 * 5;
    }

    protected void showHud(String with) {
        if (player.team() == Team.blue) {
        	Call.infoPopup(player.con, "[accent]Play time: [scarlet]" + Base.formatTime(Duration.ofMinutes(playTime)) + "[accent].\n" + with, 55, 10, 90, 0, 100, 0);
        	return;
        }
        int next = 10000 * (xp() / 10000 + 1);
        Call.infoPopup(player.con,
                "[accent]Play time: [scarlet]" + Base.formatTime(Duration.ofMinutes(playTime)) + "[accent].\n" +
                        "[accent]XP: " + rank() + " [scarlet]"
                        + NumberFormat.getInstance().format(xp()) + "[] / [scarlet] "
                        + NumberFormat.getInstance().format(next) + rank(next) + "\n" + with,
                55, 10, 90, 0, 100, 0);
    }

    public void addBroken() {
        if (sus)
            return;
        brokenBlocks++;
        if (brokenBlocks > 100 && playTime < 10) {
            Call.sendMessage("[red]Player [white]" + player.name() + "[red] is new and has broken too many blocks! " +
                    "They are blocked from building for the next 2 minutes!");
            sus = true;
            Time.runTask(60f * 120, this::resetSus);
        }
    }

    public void resetBroken() {
        brokenBlocks = 0;
    }

    public void resetSus() {
        sus = false;
    }

    public void updateName() {
        if (player.team() == Team.blue) {
            player.name = "[royal]" + rawName;
        } else if (player.team() == Team.malis) {
            player.name = rank() + "[scarlet]" + " " + rawName;
        } else {
            player.name = rank() + "[olive]" + " " + rawName;
        }
    }

    static int secondaryRank(int of) {
        return (of / 10000) % 3 + 1;
    }

    public boolean plague() {
        return this.player.team() == Team.malis;
    }

    public int xp() {
        return plague() ? plagueXp : survXp;
    }

    public void addXP(int add, String message) {
        addXP(add, message, false);
    }

    public void addXP(int add, String message, boolean toast) {
        if (!connected) {
            return;
        }

        if (plague()) {
            plagueXp += add;
        } else {
            survXp += add;
        }
        if (toast) {
            Call.infoToast(player.con(), message, 0.75f);
        } else {
            player.sendMessage(message);
        }
        if (((xp() - add) / 10000) != xp() / 10000) {
            Call.infoMessage(player.con(), "[gold]You ranked up to [gold]" + rank());
            updateName();
        }
    }

    public void reset() {
        updateName();
        player.team(Team.blue);
    }

    /**
     * ends with accent
     */
    public String rank() {
        return rank(xp());
    }

    public static String plagueRank(int of) {
        switch (of / 30000) {
            // give me macros
            case 0:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.dagger.emoji(), secondaryRank(of));
            case 1:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.mace.emoji(), secondaryRank(of));
            case 2:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.fortress.emoji(), secondaryRank(of));
            case 3:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.scepter.emoji(), secondaryRank(of));
            case 4:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.reign.emoji(), secondaryRank(of));
            case 5:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.nova.emoji(), secondaryRank(of));
            case 6:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.pulsar.emoji(), secondaryRank(of));
            case 7:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.quasar.emoji(), secondaryRank(of));
            case 8:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.vela.emoji(), secondaryRank(of));
            case 9:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.risso.emoji(), secondaryRank(of));
            case 10:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.minke.emoji(), secondaryRank(of));
            case 11:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.sei.emoji(), secondaryRank(of));
            case 12:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.omura.emoji(), secondaryRank(of));
            case 13:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.retusa.emoji(), secondaryRank(of));
            case 14:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.oxynoe.emoji(), secondaryRank(of));
            case 15:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.cyerce.emoji(), secondaryRank(of));
            case 16:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.aegires.emoji(), secondaryRank(of));
            case 17:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.navanax.emoji(), secondaryRank(of));
            case 18:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.flare.emoji(), secondaryRank(of));
            case 19:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.horizon.emoji(), secondaryRank(of));
            case 20:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.zenith.emoji(), secondaryRank(of));
            case 21:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.mono.emoji(), secondaryRank(of));
            case 22:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.poly.emoji(), secondaryRank(of));
            case 23:
                return String.format("[accent]<[white]%s%d[]>", UnitTypes.mega.emoji(), secondaryRank(of));
            case 24:
                return String.format("[accent]<[gold]%s[white]%d[accent]>", UnitTypes.quad.emoji(), secondaryRank(of));
            default:
                return String.format(
                        "[accent]<[#ce9153]W[#cc8a50]o[#ca834d]r[#c87c4a]l[#c67547]d[#c46e44] [#c26741]B[#c0603e]u[#be593b]r[#bc5238]n[#b64230]er[white]%d[accent]>",
                        ((of - 750000) / 30000) + 1);
        }
    }

    public static String survRank(int of) {
        switch (of / 30000) {
            case 0:
                return String.format("[accent]<[white]%s%d[]>", Blocks.duo.emoji(), secondaryRank(of));
            case 1:
                return String.format("[accent]<[white]%s%d[]>", Blocks.arc.emoji(), secondaryRank(of));
            case 2:
                return String.format("[accent]<[white]%s%d[]>", Blocks.wave.emoji(), secondaryRank(of));
            case 3:
                return String.format("[accent]<[white]%s%d[]>", Blocks.scatter.emoji(), secondaryRank(of));
            case 4:
                return String.format("[accent]<[white]%s%d[]>", Blocks.hail.emoji(), secondaryRank(of));
            case 5:
                return String.format("[accent]<[white]%s%d[]>", Blocks.lancer.emoji(), secondaryRank(of));
            case 6:
                return String.format("[accent]<[white]%s%d[]>", Blocks.salvo.emoji(), secondaryRank(of));
            case 7:
                return String.format("[accent]<[white]%s%d[]>", Blocks.fuse.emoji(), secondaryRank(of));
            case 8:
                return String.format("[accent]<[white]%s%d[]>", Blocks.ripple.emoji(), secondaryRank(of));
            case 9:
                return String.format("[accent]<[white]%s%d[]>", Blocks.tsunami.emoji(), secondaryRank(of));
            case 10:
                return String.format("[accent]<[white]%s%d[]>", Blocks.spectre.emoji(), secondaryRank(of));
            case 11:
                return String.format("[accent]<[white]%s%d[]>", Blocks.meltdown.emoji(), secondaryRank(of));
            case 12:
                return String.format("[accent]<[white]%s%d[]>", Blocks.foreshadow.emoji(), secondaryRank(of));
            case 13:
                return String.format("[accent]<[gold]%s[white]%d[accent]>", Blocks.scorch.emoji(), secondaryRank(of));
            default:
                return String.format(
                        "[accent]<[#a3e3d3]H[#a3e2cb]o[#a3e1c3]r[#a3e0bb]d[#a3dfb3]e[#a3deab] [#a3dda3]S[#a3dc9b]l[#a3db93]a[#a3da8b]y[#a1d073]er[white]%d[accent]>",
                        ((of - 420000) / 30000) + 1);
        }
    }

    public String rank(int of) {
        if (player.team() == Team.malis)
            return plagueRank(of);
        return survRank(of);
    }

}
