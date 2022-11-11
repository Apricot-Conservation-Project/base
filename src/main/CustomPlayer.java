package main;

import arc.math.Mathf;
import arc.util.Time;
import mindustry.game.Team;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.world.Block;

import java.time.Instant;

public class CustomPlayer{

    protected Player player;

    public int playTime;
    public int donatorLevel;
    public boolean connected;
    public String rawName;
    public boolean historyMode = false;
    public int lastvoteBan;
    public boolean destroyMode = false;
    public String namePrefix;
    public boolean rainbow = false;
    public int dTime;
    int brokenBlocks = 0;
    boolean sus = false;


    public boolean hudEnabled = true;


    public CustomPlayer(Player player){
        this.player = player;
        this.rawName = player.name;
        this.lastvoteBan = (int) Instant.now().getEpochSecond() - 60 * 5;
    }

    public void addBroken(){

        if(sus) return;
        brokenBlocks ++;
        if(brokenBlocks > 100 && player.playTime < 10){
            Call.sendMessage("[red]Player [white]" + player.name() + "[red] is new and has broken too many blocks! " +
                    "They are blocked from building for the next 2 minutes!");
            sus = true;
            Time.runTask(60f * 120, this::resetSus);
        }
    }

    public void resetBroken(){
        brokenBlocks = 0;
    }

    public void resetSus(){
        sus = false;
    }

}
