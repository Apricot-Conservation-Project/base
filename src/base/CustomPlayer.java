package base;

import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
import mindustry.content.Blocks;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.gen.Unit;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;

public class CustomPlayer {

    public Player player;
    public int playTime;
    public boolean connected;
    public boolean historyMode = false;
    public int lastvoteBan;
    public boolean destroyMode = false;
    public String rawName;
    public Team team;
    public Seq<Unit> followers = new Seq<>();
    public boolean hudEnabled = true;
    public int xp;
    public int wins;
    int brokenBlocks = 0;
    boolean sus = false;

    private HashMap<Team, String> colorMapping = new HashMap<Team, String>() {
        {
            put(Team.malis, "[scarlet]");
            put(Team.blue, "[royal]");
        }
    };

    public CustomPlayer(Player player) {
        this.player = player;
        this.rawName = player.plainName();
        player.color = Color.white;
        this.lastvoteBan = (int) Instant.now().getEpochSecond() - 60 * 5;
    }

    public void showHud() {
        Call.infoPopup(player.con,
                "[accent]Play time: [scarlet]" + Base.formatTime(Duration.ofMinutes(playTime)) + "[accent].",
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
        String team = colorMapping.getOrDefault(player.team(), "[olive]");
        player.name = String.format("[accent]<%s%d[accent]>%s %s", rank(), secondaryRank(), team,
                Strings.stripColors(this.rawName));
    }

    public int secondaryRank() {
        return secondaryRank(xp);
    }

    public int secondaryRank(int of) {
        return (of / 10000) % 3 + 1;
    }

    public void addXP(int add, String message) {
        if (!connected) {
            return;
        }
        xp += add;
        player.sendMessage(message);
        if (((xp - add) / 10000) != xp / 10000) {
            Call.infoMessage(player.con(), String.format("[gold]You ranked up to [accent]<%s%d[accent]>[gold]!",
                    rank(), secondaryRank()));
            updateName();
        }
    }

    public void reset() {
        updateName();
        player.team(Team.blue);
    }

    public String rank() {
        return rank(xp);
    }

    public String rank(int of) {
        switch (of / 30000) {
            case 0:
                return "[white]" + Blocks.duo.emoji();
            case 1:
                return "[white]" + Blocks.arc.emoji();
            case 2:
                return "[white]" + Blocks.hail.emoji();
            case 3:
                return "[white]" + Blocks.lancer.emoji();
            case 4:
                return "[white]" + Blocks.fuse.emoji();
            case 5:
                return "[white]" + Blocks.ripple.emoji();
            case 6:
                return "[white]" + Blocks.cyclone.emoji();
            case 7:
                return "[white]" + Blocks.tsunami.emoji();
            case 8:
                return "[white]" + Blocks.swarmer.emoji();
            case 9:
                return "[white]" + Blocks.foreshadow.emoji();
            case 10:
                return "[gold]" + Blocks.scorch.emoji();
            default:
                return "[green]Horde Slayer";
        }
    }

}
