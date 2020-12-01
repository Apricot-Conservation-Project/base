package main;

import mindustry.game.Team;
import mindustry.gen.Player;

import java.time.Instant;

public class CustomPlayer{

    protected Player player;
    public boolean connected;
    public String rawName;
    public boolean historyMode = false;
    public int lastvoteBan;


    public CustomPlayer(Player player){
        this.player = player;
        this.rawName = player.name;
        this.connected = true;
        this.lastvoteBan = (int) Instant.now().getEpochSecond() - 60 * 5;
    }

}
