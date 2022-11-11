package main;

import mindustry.gen.Player;

public class CustomEvents {

    public static class PlayerJoinSecondary{
        public final Player player;
        public final int playTime;
        public final int donatorLevel;

        public PlayerJoinSecondary(Player player, int playTime, int donatorLevel){
            this.player = player;
            this.playTime = playTime;
            this.donatorLevel = donatorLevel;
        }
    }

    public static class NewName{
        public final String uuid;

        public NewName(String uuid) {
            this.uuid = uuid;
        }
    }

    public static class HudToggle{
        public final String uuid;
        public final boolean enabled;

        public HudToggle(String uuid, boolean enabled) {
            this.uuid = uuid;
            this.enabled = enabled;
        }
    }

    public static class GameOver{
    }
}
