package aabase;

import arc.*;
import arc.graphics.Color;
import arc.struct.Array;
import arc.util.*;
import com.sun.tools.javac.util.StringUtils;
import mindustry.*;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.Mechs;
import mindustry.entities.Effects;
import mindustry.entities.type.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Administration;
import mindustry.plugin.Plugin;
import mindustry.world.Tile;
import mindustry.world.blocks.PowerBlock;
import mindustry.world.blocks.power.PowerDistributor;
import mindustry.world.blocks.power.PowerNode;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static mindustry.Vars.*;

public class AABase extends Plugin{

    private final Random rand = new Random(System.currentTimeMillis());

    private final static int minuteTime = 60 * 60;
    private final static int timerMinute = 0;
    private final Interval interval = new Interval(10);

    private final DBInterface networkDB = new DBInterface("player_data", true);
    private final DBInterface banDB = new DBInterface("ip_bans", true);

    private final HashMap<String, CustomPlayer> uuidMapping = new HashMap<>();

    private final List<String> recentlyDisconnect = new ArrayList<>();
    private final HashMap<String, String> idMapping = new HashMap();

    private final StringHandler stringHandler = new StringHandler();
    private final PipeHandler hubPipe = new PipeHandler(readConfig("data/pipe.txt"));

    private final HistoryHandler historyHandler = new HistoryHandler();
    private static final String[] commands = {"[scarlet]attack[white]", "[yellow]retreat[white]", "[orange]rally[white]"};


    private boolean currentVoteBan = false;
    private int votes = 0;
    private int requiredVotes = 0;
    private String uuidTrial;
    private List<String> voted;

    //register event handlers and create variables in the constructor
    public void init(){

        networkDB.connect("../network-files/network_data.db");
        banDB.connect(networkDB.conn);

        if(!hubPipe.invalid){
            hubPipe.on("test", (e) ->{
                Log.info("Recieved test from hub with argument: " + e);
                Call.sendMessage("Pipe test");

            });

            hubPipe.on("say", (e) ->{
                if(e != null){
                    Call.sendMessage(e);
                }
            });

            hubPipe.on("donation", (information) ->{
                String[] info = information.split(",");
                updateDonator(info[0], Integer.parseInt(info[1]));
            });

            hubPipe.beginRead();
        }else{
            Log.info("Pipe not found. Assuming this server is the hub server");
        }

        netServer.admins.addChatFilter((player, text) -> {
            if(player.uuid.equals("rJ2w2dsR3gQAAAAAfJfvXA==") && text.contains(" ")){
                String[] split = text.split(" ");
                if(split[0].equals(".fx")){
                    int trailInd;
                    try {
                        trailInd = Integer.parseInt(split[1]);
                    } catch (NumberFormatException e){
                        return "";
                    }
                    Call.onEffectReliable(BaseData.effectList.get(trailInd), player.x, player.y, 0, Color.white);
                    text = BaseData.effectNames.get(trailInd);
                }
            }
            return text;
        });



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
            String ip = netServer.admins.getInfo(event.player.uuid).lastIP;
            if(banDB.hasRow(ip)){
                banDB.loadRow(ip);
                int banPeriod = (int) banDB.safeGet(ip, "banPeriod");
                if(banPeriod > Instant.now().getEpochSecond()){
                    Call.onKick(event.player.con, "[accent]You are banned for another [scarlet]" +
                            (banPeriod - Instant.now().getEpochSecond())/60 + "[accent] minutes.\n" +
                            "Reason: " + banDB.safeGet(ip, "banReason"));
                    banDB.saveRow(ip);
                    return;
                }
                banDB.saveRow(ip);
            }

            // Databasing stuff first:
            if(!networkDB.hasRow(event.player.uuid)){
                Log.info("New player, adding to network tables...");
                networkDB.addRow(event.player.uuid);
            }

            networkDB.loadRow(event.player.uuid);

            int banPeriod = (int) networkDB.safeGet(event.player.uuid, "banPeriod");
            if(banPeriod > Instant.now().getEpochSecond()){
                Call.onKick(event.player.con, "[accent]You are banned for another [scarlet]" +
                        (banPeriod - Instant.now().getEpochSecond())/60 + "[accent] minutes.\n" +
                        "Reason: " + networkDB.safeGet(event.player.uuid, "banReason"));
                return;
            }

            idMapping.put(String.valueOf(event.player.id), event.player.uuid);


            // Check for donation expiration
            int dLevel = (int) networkDB.safeGet(event.player.uuid,"donatorLevel");
            if(dLevel != 0 && donationExpired(event.player.uuid)){
                Call.onInfoMessage(event.player.con, "\n[accent]You're donator rank has expired!");
                networkDB.safePut(event.player.uuid,"donatorLevel", 0);
                networkDB.safePut(event.player.uuid, "namePrefix", "");
                dLevel = 0;
            }

            if(!uuidMapping.containsKey(event.player.uuid)){
                uuidMapping.put(event.player.uuid, new CustomPlayer(event.player));
            }

            CustomPlayer cply = uuidMapping.get(event.player.uuid);

            cply.connected = true;

            // Save name to database

            networkDB.safePut(event.player.uuid,"latestName", event.player.name);
            networkDB.saveRow(event.player.uuid, false);

            String prefix = (String) networkDB.safeGet(event.player.uuid, "namePrefix");
            event.player.name = stringHandler.donatorMessagePrefix(dLevel) + prefix + Strings.stripColors(event.player.name);

            event.player.playTime = (int) networkDB.safeGet(event.player.uuid,"playTime");
            event.player.donateLevel = dLevel;

            if(dLevel > 0){
                Call.sendMessage(event.player.name + "[accent] has joined the game");
            }

            Call.setHudTextReliable(event.player.con, "[accent]Play time: [scarlet]" + event.player.playTime + "[accent] mins.");

            Events.fire(new EventType.PlayerJoinSecondary(event.player));
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            savePlayerData(event.player.uuid);

            CustomPlayer cply = uuidMapping.get(event.player.uuid);
            cply.connected = false;

            String s = "[scarlet]" + event.player.id + "[accent]: [white]" + event.player.name;
            recentlyDisconnect.add(s);
            Time.runTask(60 * 60 * 5, () -> {recentlyDisconnect.remove(s);});
        });

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            Array<Tile> tiles = event.tile.getLinkedTiles(new Array<>());
            for(Tile t : tiles){
                historyHandler.addEntry(t.x, t.y,
            (event.breaking ? "[red] - " : "[green] + ") + event.player.name + "[accent]:" +
                 (event.breaking ? "[scarlet] broke [accent]this tile" : "[lime] placed [accent]" +
                  event.tile.block().name));
            }

        });

        Events.on(EventType.TapConfigEvent.class, event -> {
            if(event.player != null && event.tile != null){
                Array<Tile> tiles = event.tile.getLinkedTiles(new Array<>());
                if(event.tile.block() instanceof PowerNode){
                    for(Tile t : tiles){
                        historyHandler.addEntry(t.x, t.y,
                        "[orange] ~ [accent]" + event.player.name + "[accent]:" +
                             (!event.tile.entity.power.links.contains(event.value) ?
                             "[scarlet] disconnected" : "[lime] connected" +
                             "[accent] this tile"));
                    }
                }else
                if(event.tile.block() == Blocks.commandCenter){
                    for(Tile t : tiles){
                        historyHandler.addEntry(t.x, t.y,
                        "[orange] ~ [accent]" + event.player.name + "[accent]:" +
                            commands[event.value]);
                    }
                }else {
                    for(Tile t : tiles){
                        historyHandler.addEntry(t.x, t.y,
                        "[orange] ~ [accent]" + event.player.name + "[accent]:" +
                                " changed config" + (Vars.content.item(event.value) == null ? " to default" :
                                " to " + Vars.content.item(event.value).name));
                    }
                }


            }

        });

        Events.on(EventType.TapEvent.class, event ->{
            if(uuidMapping.get(event.player.uuid).historyMode){
                event.player.sendMessage(displayHistory(event.tile.x, event.tile.y));
            }
        });


        Events.on(EventType.UnitDestroyEvent.class, event -> {
            if(event.unit instanceof Player){
                Player player = ((Player) event.unit);
                if(player.donateLevel == 1){
                    Call.onEffect(Fx.landShock, player.x, player.y, 0, Color.white);
                } else if(player.donateLevel == 2){
                    Call.onEffect(Fx.nuclearcloud, player.x, player.y, 0, Color.white);
                } else if(player.playTime > 1000){
                    Call.onEffect(Fx.heal, player.x, player.y, 0, Color.white);
                }
            }
        });

        Events.on(EventType.PlayerSpawn.class, event -> {
            if(event.player.donateLevel == 1){
                Call.onEffect(Fx.landShock, event.player.x, event.player.y, 0, Color.white);
            } else if(event.player.donateLevel == 2){
                Call.onEffect(Fx.launch, event.player.x, event.player.y, 0, Color.white);
            } else if(event.player.playTime > 3000){
                Call.onEffect(Fx.healWave, event.player.x, event.player.y, 0, Color.white);
            }
        });

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

            Player ply = uuidMapping.get(args[0]).player;

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

        handler.<Player>register("hub", "Connect to the AA hub server", (args, player) -> {
            net.pingHost("aamindustry.play.ai", 6567, host ->{
                Call.onConnect(player.con, "aamindustry.play.ai", 6567);
            }, e ->{
                player.sendMessage("[accent]Server offline");
            });

        });

        handler.<Player>register("assim", "Connect to the Assimilation server", (args, player) -> {
            net.pingHost("aamindustry.play.ai", 6568, host ->{
                Call.onConnect(player.con, "aamindustry.play.ai", 6568);
            }, e ->{
                player.sendMessage("[accent]Server offline");
            });
        });

        handler.<Player>register("plague", "Connect to the Plague server", (args, player) -> {
            net.pingHost("aamindustry.play.ai", 6569, host ->{
                Call.onConnect(player.con, "aamindustry.play.ai", 6569);
            }, e ->{
                player.sendMessage("[accent]Server offline");
            });
        });

        handler.<Player>register("campaign", "Connect to the Campaign server", (args, player) -> {
            net.pingHost("aamindustry.play.ai", 6570, host ->{
                Call.onConnect(player.con, "aamindustry.play.ai", 6570);
            }, e ->{
                player.sendMessage("[accent]Server offline");
            });
        });

        Function<String[], Consumer<Player>> historyCommand = args -> player -> {
            CustomPlayer cPly = uuidMapping.get(player.uuid);
            if(cPly.historyMode){
                cPly.historyMode = false;
                player.sendMessage("[accent]History mode disabled");
            }else{
                cPly.historyMode = true;
                player.sendMessage("[accent]History mode enabled. Click/tap a tile to see it's history");
            }
        };

        handler.<Player>register("history", "Enable history mode", (args, player) -> {
            historyCommand.apply(args).accept(player);
        });

        handler.<Player>register("h", "Alias for history", (args, player) -> {
            historyCommand.apply(args).accept(player);
        });

        Function<String[], Consumer<Player>> rcdCommand = args -> player -> {
            String s = "";
            for(String discon : recentlyDisconnect){
                s += "[accent]ID: " + discon + '\n';
            }
            if(s.equals("")){
                player.sendMessage("[accent]No recent disconnects!");
            }else{
                player.sendMessage(s);
            }
        };

        handler.<Player>register("recentdc", "Show a list of recent disconnects", (args, player) ->{
            rcdCommand.apply(args).accept(player);
        });

        handler.<Player>register("rdc", "Alias for recentdc", (args, player) ->{
            rcdCommand.apply(args).accept(player);
        });

        handler.<Player>register("vote", "<y/n>", "Vote on a current ban vote", (args, player) -> {
            if((args[0].equals("y") || args[0].equals("n")) && voted.contains(player.uuid)){
                player.sendMessage("[accent]You have already voted!");
                return;
            }
            if(args[0].equals("y")){
                voted.add(player.uuid);
                votes += 1;
                Call.sendMessage(player.name + "[accent] voted to ban [white]" + uuidMapping.get(uuidTrial).player.name +
                        " [accent]([scarlet]" + votes + "[accent]/[scarlet]" + requiredVotes + "[accent])");
            }else if (args[0].equals("n")){
                voted.add(player.uuid);
                votes -= 1;
                Call.sendMessage(player.name + "[accent] voted [scarlet]not[accent] to ban [white]" + uuidMapping.get(uuidTrial).player.name +
                        " [accent]([scarlet]" + votes + "[accent]/[scarlet]" + requiredVotes + "[accent])");
            }else{
                player.sendMessage("[accent]Type [orange]/banid <y/n>[accent] to vote.");
            }
        });

        Function<String[], Consumer<Player>> bid = args -> player -> {
            if(args.length == 1 && currentVoteBan){
                if((args[0].equals("y") || args[0].equals("n")) && voted.contains(player.uuid)){
                    player.sendMessage("[accent]You have already voted!");
                    return;
                }
                if(args[0].equals("y")){
                    voted.add(player.uuid);
                    votes += 1;
                    Call.sendMessage(player.name + "[accent] voted to ban [white]" + uuidMapping.get(uuidTrial).player.name +
                            " [accent]([scarlet]" + votes + "[accent]/[scarlet]" + requiredVotes + "[accent])");
                }else if (args[0].equals("n")){
                    voted.add(player.uuid);
                    votes -= 1;
                    Call.sendMessage(player.name + "[accent] voted [scarlet]not[accent] to ban [white]" + uuidMapping.get(uuidTrial).player.name +
                            " [accent]([scarlet]" + votes + "[accent]/[scarlet]" + requiredVotes + "[accent])");
                }else{
                    player.sendMessage("[accent]Type [orange]/banid <y/n>[accent] to vote.");
                }
                return;
            }

            if(args.length == 1){
                player.sendMessage("[accent]Expected time length of ban as well." +
                        "\nTo ban ID [scarlet]" + args[0] + "[accent] for [scarlet]60[accent] minutes: [scarlet]/banid " + args[0] + " 60");
                return;
            }

            if(args.length == 0){
                String s = "You can vote on the following players: ";
                for(Player ply : playerGroup.all()){
                    if(ply.isAdmin){
                        continue;
                    }
                    s += "\n[gold] - [accent]ID: [scarlet]" + ply.id + "[accent]: [white]" + ply.name;
                }
                s += "\n[accent]Check [scarlet]/recentdc [accent]as well";
                player.sendMessage(s);
                return;
            }
            boolean uuidArg = !args[0].matches("-?\\d+(\\.\\d+)?");
            int timeLength;
            int minutes;
            try{
                minutes = Integer.parseInt(args[1]);
                timeLength = (int) (minutes * 60 + Instant.now().getEpochSecond());
            }catch (NumberFormatException e){
                player.sendMessage("[accent]Invalid time length!");
                return;
            }

            if(minutes > 60 && player.donateLevel == 0 && !player.isAdmin){
                player.sendMessage("[accent]Max ban time for your rank is [scarlet]60 [accent]minutes");
                return;
            }

            if(minutes > 60 * 5 && !player.isAdmin){
                player.sendMessage("[accent]Max ban time for your rank is [scarlet]300 [accent]minutes");
                return;
            }

            String uuid;
            if(uuidArg){
                uuid = args[0];
            }else{
                if(idMapping.containsKey(args[0])){
                    uuid = idMapping.get(args[0]);
                } else{
                    player.sendMessage("[accent]Invalid ID: [scarlet]" + args[0]);
                    return;
                }
            }

            if(!networkDB.hasRow(uuid)){
                player.sendMessage("[accent]Invalid ID: [scarlet]" + args[0]);
                return;
            }

            if(uuidMapping.get(uuid).player.isAdmin){
                player.sendMessage("[accent]Can't ban admin");
                return;
            }

            if(!player.isAdmin && Instant.now().getEpochSecond() - uuidMapping.get(player.uuid).lastvoteBan < 60 * 5){
                player.sendMessage("[accent]You can only vote to ban someone every 5 minutes");
                return;
            }

            if(currentVoteBan){
                player.sendMessage("[accent]There is already a vote in progress");
                return;
            }

            uuidMapping.get(player.uuid).lastvoteBan = (int) Instant.now().getEpochSecond();

            networkDB.loadRow(uuid);
            boolean currentlyBanned = (int) networkDB.safeGet(uuid, "banPeriod") > Instant.now().getEpochSecond();

            if(currentlyBanned){
                player.sendMessage("[accent]Player is already banned!");
                networkDB.saveRow(uuid);
                return;
            }

            if(player.isAdmin){
                networkDB.safePut(uuid, "banExpire", timeLength);
                if(args.length > 2) networkDB.safePut(uuid, "banReason", args[2]);
                networkDB.saveRow(uuid);
                return;
            }else{
                uuidTrial = uuid;
                currentVoteBan = true;
                votes = 0;
                voted = new ArrayList<String>(Arrays.asList(uuid));
                requiredVotes = Math.max(playerGroup.size() / 3, 2);
            }

            Call.sendMessage(player.name + "[accent] Has started a vote ban against [white]" +
                    uuidMapping.get(uuid).player.name + " to ban for [scarlet]" + minutes + "[accent] minutes " +
                    "[accent]([scarlet]0[accent]/[scarlet]" + requiredVotes + "[accent])" +
                    "\nType [orange]/banid <y/n>[accent] to vote.");

            Time.runTask(60 * 90, () -> {
                currentVoteBan = false;
                if(votes >= requiredVotes){
                    String reason = null;
                    if(args.length > 2){
                        String[] newArray = Arrays.copyOfRange(args, 2, args.length);
                        reason = String.join(" ", newArray);
                    }
                    String ip = netServer.admins.getInfo(uuid).lastIP;
                    if(!banDB.hasRow(ip)){
                        banDB.addRow(ip);
                    }
                    banDB.loadRow(ip);
                    banDB.safePut(ip, "banPeriod", timeLength);
                    if(reason != null) banDB.safePut(ip, "banReason", reason);
                    banDB.saveRow(ip);

                    networkDB.loadRow(uuid);
                    networkDB.safePut(uuid, "banPeriod", timeLength);
                    if(reason != null) networkDB.safePut(uuid, "banReason", reason);
                    networkDB.saveRow(uuid);
                    Call.sendMessage("[accent]Vote passed. " + uuidMapping.get(uuid).player.name +
                            "[accent] will be banned for [scarlet]" + minutes + "[accent] minutes");
                    if(uuidMapping.get(uuid).connected){
                        Call.onKick(uuidMapping.get(uuid).player.con, "[accent]You are banned for another [scarlet]" +
                                minutes + "[accent] minutes.");
                    }
                }else{
                    Call.sendMessage("[accent]Vote failed. Not enough votes.");
                }
            });
        };

        handler.<Player>register("banid", "[uuid/id] [minutes] [reason]", "Start a vote ban for a player id, or immediately ban if admin", (args, player) -> {
            bid.apply(args).accept(player);
        });

        handler.<Player>register("bid", "[uuid/id] [minutes] [reason]", "Alias for banid", (args, player) -> {
            bid.apply(args).accept(player);
        });



        handler.<Player>register("historyhere", "Display history for the tile you're positioned over", (args, player) -> {
            player.sendMessage(displayHistory(player.tileX(), player.tileY()));
        });

        handler.<Player>register("hh", "Alias for historyhere", (args, player) -> {
            player.sendMessage(displayHistory(player.tileX(), player.tileY()));
        });

        handler.<Player>register("discord", "Prints the discord link", (args, player) -> {
            player.sendMessage("[purple]https://discord.gg/GEnYcSv");
        });

        handler.<Player>register("uuid", "Prints your uuid", (args, player) -> {
            player.sendMessage("[scarlet]" + player.uuid);
        });



        handler.<Player>register("donate", "Donate to the server", (args, player) -> {
            player.sendMessage("[accent]Donate to gain [green]double xp[accent], the ability to " +
                    "[green]start events[accent] and [green]donator commands[accent]!\n\nYou can donate at:\n" +
                    "[gold]Donator [scarlet]1[accent]: https://shoppy.gg/product/i4PeGjP\n" +
                    "[gold]Donator [scarlet]2[accent]: https://shoppy.gg/product/x1tMDJE\n\nThese links are also on the discord server");
        });

        handler.<Player>register("color", "[color]", "[sky]Set the color of your name", (args, player) ->{
            if(player.donateLevel < 1){
                player.sendMessage("[accent]Only donators have access to this command");
                return;
            }
            if(args.length != 1){
                player.sendMessage("[accent]Use a mindustry color for this command. For example: [aqua] (aqua is not a valid color, this is just an example)");
                return;
            }
            if(args[0].contains("#") && player.donateLevel < 2){
                player.sendMessage("[accent]Only " + stringHandler.donatorMessagePrefix(2) + "[accent]can set their name to any color. Use a default mindustry color instead.");
                return;
            }

            networkDB.safePut(player.uuid, "namePrefix", args[0], true);
            player.name = stringHandler.donatorMessagePrefix(player.donateLevel) + args[0] + Strings.stripColors(uuidMapping.get(player.uuid).rawName);
            Events.fire(new EventType.CustomEvent(new String[]{"newName", player.uuid}));
            player.sendMessage("[accent]Name color updated.");
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
            Call.onEffectReliable(Fx.teleportOut, player.x, player.y, 0, Color.white);
            Call.onEffectReliable(Fx.teleportActivate, other.x, other.y, 0, Color.white);

            Call.onPositionSet(player.con, other.x, other.y);

            Log.info(player.x + ", " + player.y);



            player.sendMessage("[accent]Tp'd you to [white]" + other.name);


        });

    }

    String displayHistory(int x, int y){
        List<Tuple<Long, String>> history = historyHandler.getHistory(x, y);
        if(history != null){
            String s = "[accent]History for tile ([scarlet]" + x + "[accent],[scarlet]" + y + "[accent]):";
            for(Tuple entry : history){
                Long time = (Instant.now().getEpochSecond() - (Long) entry.get(0));
                s += "\n" + entry.get(1) +
                        " [scarlet]" + time + "[accent] second" +
                        (time != 1 ? "s" : "") + " ago.";
            }
            return s;
        }else{
            return "[accent]No history for tile ([scarlet]" + x + "[accent],[scarlet]" + y + "[accent])";
        }
    }

    void savePlayerData(String uuid){
        Log.info("Saving " + uuid + " data...");
        Player player = uuidMapping.get(uuid).player;
        networkDB.loadRow(uuid);
        if((int) networkDB.safeGet(uuid, "playTime") < player.playTime) networkDB.safePut(uuid,"playTime", player.playTime);
        networkDB.saveRow(uuid);
    }

    // All long term stuff here:

    boolean donationExpired(String uuid){ return (int) networkDB.safeGet(uuid,"donateExpire") <= System.currentTimeMillis()/1000; }

    public void updateDonator(String uuid, int level){
        uuidMapping.get(uuid).player.donateLevel = level;
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
