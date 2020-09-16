package aabase;

import mindustry.entities.type.Player;
import mindustry.game.Team;

public class CustomPlayer{

    protected Player player;
    public boolean connected;
    public int eventCalls = 0;
    public String rawName;


    public CustomPlayer(Player player, int eventCalls){
        this.player = player;
        this.rawName = player.name;
        this.connected = true;
        this.eventCalls = eventCalls;
    }

}
