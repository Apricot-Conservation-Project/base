package aabase;

import arc.*;
import arc.util.*;
import mindustry.*;
import mindustry.entities.type.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.plugin.Plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import static mindustry.Vars.*;

public class AABase extends Plugin{

    private final Random rand = new Random(System.currentTimeMillis());

    private final static int minuteTime = 60 * 60;
    private final static int timerMinute = 0;
    private final Interval interval = new Interval(10);

    private final DBInterface networkDB = new DBInterface("player_data", true);

    private final HashMap<String, Player> uuidMapping = new HashMap<>();

    private final StringHandler stringHandler = new StringHandler();
    private final PipeHandler hubPipe = new PipeHandler(readConfig("data/pipe.txt"));

    //register event handlers and create variables in the constructor
    public void init(){

        networkDB.connect("../network-files/network_data.db");

        if(!hubPipe.invalid){
            hubPipe.on("test", (e) ->{
                Log.info("Recieved test from hub with argument: " + e);
                Call.sendMessage("Pipe test");

            });

            hubPipe.on("say", Call::sendMessage);

            hubPipe.on("donation", (information) ->{
                String[] info = information.split(",");
                updateDonator(info[0], Integer.parseInt(info[1]));
            });

            hubPipe.beginRead();
        }else{
            Log.info("Pipe not found. Assuming this server is the hub server");
        }




        Events.on(EventType.PlayerConnect.class, event ->{
            for(Player ply : playerGroup.all()){
                if(event.player.uuid.equals(ply.uuid)){
                    Call.onInfoMessage(event.player.con, "[scarlet]Already connected to this server");
                    Call.onConnect(event.player.con, "aamindustry.play.ai", 6567);
                    return;
                }
            }
        });

        Events.on(EventType.Trigger.class, event ->{
            if(interval.get(timerMinute, minuteTime)){
                for(Player player : playerGroup.all()){
                    player.playTime += 1;
                    Call.setHudTextReliable(player.con, "[accent]Play time: [scarlet]" + player.playTime + "[accent] mins.");
                }
            }
        });

        Events.on(EventType.PlayerConnect.class, event->{
            for(String swear : StringHandler.badNames){
                if(Strings.stripColors(event.player.name.toLowerCase()).contains(swear) && !event.player.uuid.equals("rJ2w2dsR3gQAAAAAfJfvXA==")){
                    event.player.name = event.player.name.replaceAll("(?i)" + swear, "");
                }
            }
        });

        Events.on(EventType.PlayerJoin.class, event ->{
            // Databasing stuff first:
            if(!networkDB.hasRow(event.player.uuid)){
                Log.info("New player, adding to network tables...");
                networkDB.addRow(event.player.uuid);
            }

            networkDB.loadRow(event.player.uuid);

            uuidMapping.put(event.player.uuid, event.player);



            // Check for donation expiration
            int dLevel = (int) networkDB.safeGet(event.player.uuid,"donatorLevel");
            if(dLevel != 0 && donationExpired(event.player.uuid)){
                event.player.sendMessage("\n[accent]You're donator rank has expired!");
                networkDB.safePut(event.player.uuid,"donatorLevel", 0);
                dLevel = 0;
            }

            // Save name to database

            networkDB.safePut(event.player.uuid,"latestName", event.player.name);
            networkDB.saveRow(event.player.uuid, false);

            event.player.name = stringHandler.donatorMessagePrefix(dLevel) + Strings.stripColors(event.player.name);

            event.player.playTime = (int) networkDB.safeGet(event.player.uuid,"playTime");
            event.player.donateLevel = dLevel;

            Call.setHudTextReliable(event.player.con, "[accent]Play time: [scarlet]" + event.player.playTime + "[accent] mins.");

            Events.fire(new EventType.PlayerJoinSecondary(event.player));
        });

        Events.on(EventType.PlayerLeave.class, event -> savePlayerData(event.player.uuid));


    }

    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){

        handler.register("setplaytime", "<uuid> <playtime>", "Set the play time of a player", args -> {
            int newTime;
            try{
                newTime = Integer.parseInt(args[1]);
            }catch(NumberFormatException e){
                Log.info("Invalid playtime input '" + args[1] + "'");
                return;
            }
            if(!networkDB.hasRow(args[0])){
                Log.info("Invalid uuid: " + args[0]);
                return;
            }

            Player ply = uuidMapping.get(args[0]);

            networkDB.loadRow(args[0]);
            networkDB.safePut(args[0],"playtime", newTime);
            networkDB.saveRow(args[0]);

            if(!(ply == null)){
                ply.playTime = newTime;
                Call.setHudTextReliable(ply.con, "[accent]Play time: [scarlet]" + ply.playTime + "[accent] mins.");
            }
            Log.info("Set uuid " + args[0] + " to have play time of " + args[1] + " minutes");

        });

        handler.register("endgame", "Ends the game", args ->{
            Call.sendMessage("[scarlet]Server [accent]has force ended the game. Ending in 10 seconds...");

            Log.info("Ending game...");
            Time.runTask(60f * 10f, () -> {

                for(Player player : playerGroup.all()) {
                    Call.onConnect(player.con, "aamindustry.play.ai", 6567);
                }

                // I shouldn't need this, all players should be gone since I connected them to hub
                // netServer.kickAll(KickReason.serverRestarting);
                Log.info("Game ended successfully.");
                Time.runTask(5f, () -> System.exit(2));
            });
        });


        handler.register("crash", "<name/uuid>", "Crashes the name/uuid", args ->{

            for(Player player : playerGroup.all()){
                if(player.uuid.equals(args[0]) || Strings.stripColors(player.name).equals(args[0])){
                    player.sendMessage(null);
                    Log.info("Done.");
                    return;
                }
            }
            Log.info("Player not found!");
        });
    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler) {

        if(!hubPipe.invalid){
            handler.<Player>register("d", "<key>", "Activate a donation key", (args, player) ->{
                player.sendMessage("[accent]Go to [scarlet]/hub [accent]to activate your key");
            });
        }


        handler.<Player>register("assim", "Connect to the Assimilation server", (args, player) -> {
            Call.onConnect(player.con, "aamindustry.play.ai", 6568);
        });

        handler.<Player>register("discord", "Prints the discord link", (args, player) -> {
            player.sendMessage("[purple]https://discord.gg/GEnYcSv");
        });

        handler.<Player>register("uuid", "Prints the your uuid", (args, player) -> {
            player.sendMessage("[scarlet]" + player.uuid);
        });

        handler.<Player>register("hub", "Connect to the AA hub server", (args, player) -> {
            Call.onConnect(player.con, "aamindustry.play.ai", 6567);
        });

        handler.<Player>register("donate", "Donate to the server", (args, player) -> {
            player.sendMessage("[accent]Donate to gain [green]double xp[accent], the ability to " +
                    "[green]start events[accent] and [green]donator commands[accent]!\n\nYou can donate at:\n" +
                    "[gold]Donator [scarlet]1[accent]: https://shoppy.gg/product/i4PeGjP\n" +
                    "[gold]Donator [scarlet]2[accent]: https://shoppy.gg/product/x1tMDJE\n\nThese links are also on the discord server");
        });


        handler.<Player>register("kill", "[sky]Destroy yourself (donator only)", (args, player) ->{
            if(player.donateLevel < 1){
                player.sendMessage("[accent]Only donators have access to this command");
                return;
            }
            player.kill();
        });

        handler.<Player>register("tp", "[player/id]", "[sky]Teleport to player (donator only)", (args, player) -> {


            if (args.length == 0) {
                String s = "[accent]Use [orange]/tp [player/id][accent] to teleport to a players location.\n";
                s += "You are able to tp to the following players:";
                for (Player ply : Vars.playerGroup) {
                    s += "\n[accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id;
                }
                player.sendMessage(s);
                return;
            }

            if(player.donateLevel < 1){
                player.sendMessage("[accent]Only donators have access to this command");
                return;
            }

            Player other;
            try {
                other = Vars.playerGroup.getByID(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                other = null;
            }

            if (other == null) {
                other = Vars.playerGroup.find(p -> p.name.equalsIgnoreCase(args[0]));
                if (other == null) {
                    String s = "[accent]No player by name [white]" + args[0] + "[accent] or id [white]" + args[0] + "[accent].\n";
                    s += "You are able to tp to the following players:";
                    for (Player ply : Vars.playerGroup) {
                        s += "\n[accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id;
                    }
                    player.sendMessage(s);
                    return;
                }
            }
            Call.onPositionSet(player.con, other.x, other.y);

            Log.info(player.x + ", " + player.y);



            player.sendMessage("[accent]Tp'd you to [white]" + other.name);


        });

    }

    void savePlayerData(String uuid){
        Log.info("Saving " + uuid + " data...");
        Player player = uuidMapping.get(uuid);
        networkDB.loadRow(uuid);
        if((int) networkDB.safeGet(uuid, "playTime") < player.playTime) networkDB.safePut(uuid,"playTime", player.playTime);
        networkDB.saveRow(uuid);
    }

    // All long term stuff here:

    boolean donationExpired(String uuid){ return (int) networkDB.safeGet(uuid,"donateExpire") <= System.currentTimeMillis()/1000; }

    public void updateDonator(String uuid, int level){
        uuidMapping.get(uuid).donateLevel = level;
    }

    public String readConfig(String name){
        String s = null;
        try {
            File myObj = new File(name);
            Scanner myReader = new Scanner(myObj);
            s = myReader.nextLine();
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("File not found.");
        }
        return s;
    }
}
