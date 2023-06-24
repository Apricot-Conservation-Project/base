package base;

import arc.graphics.Color;
import arc.struct.Seq;
import arc.util.Strings;
import arc.util.Time;
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
    public long controlledT5;
    public int bannedT5 = -1000;
    public boolean hudEnabled = true;
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
        player.name = team + Strings.stripColors(this.rawName);
    }

    public void reset() {
        updateName();
        player.team(Team.blue);
    }
}
