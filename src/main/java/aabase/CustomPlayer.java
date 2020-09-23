package aabase;

import mindustry.entities.type.Player;
import mindustry.game.Team;

public class CustomPlayer{

    protected Player player;
    public boolean connected;
    public String rawName;
    public boolean historyMode = false;


    public CustomPlayer(Player player){
        this.player = player;
        this.rawName = player.name;
        this.connected = true;
    }

}
