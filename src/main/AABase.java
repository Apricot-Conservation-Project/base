package main;

import arc.*;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.*;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.net.Administration;
import mindustry.net.Packets;
import mindustry.world.Tile;
import mindustry.world.blocks.power.PowerNode;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static mindustry.Vars.*;

public class AABase extends Plugin{

    private final Random rand = new Random(System.currentTimeMillis());

    private final float startTime = System.currentTimeMillis();
    private float realTime;
    private int seconds;

    private final static float serverCloseTime = 60f * 2f;
    private final static int tenSecondTime = 60 * 10, minuteTime = 60 * 60, announcementTime = 60 * 60 * 5, secondTime = 60;
    private final static int timerTen = 0, timerMinute = 1, timerAnnouncement = 2, timerSecond = 3;
    private final Interval interval = new Interval(10);

    private final DBInterface db = new DBInterface();

    private final HashMap<String, CustomPlayer> uuidMapping = new HashMap<>();

    private final List<String> recentlyDisconnect = new ArrayList<>();
    private final HashMap<String, String> idMapping = new HashMap();

    private final StringHandler stringHandler = new StringHandler();

    private final HistoryHandler historyHandler = new HistoryHandler();
    private static final String[] commands = {"[scarlet]attack[white]", "[yellow]retreat[white]", "[orange]rally[white]"};

    private int announcementIndex = 0;
    private String[] announcements = {"[accent]Join the discord with [purple]/discord[accent]!",
            "[accent]Donate with [scarlet]/donate [accent]to help keep the server alive! Additionally, " +
                    "receive [gold]triple XP[accent], custom name colors, " +
                    "death and spawn particles and many more perks!",
            "[accent]Use [scarlet]/info[accent] to get a description of the current game mode!",
            "[accent]Checkout our website at [gold]https://recessive.net"};

    private List<String> rainbowUuids = new ArrayList<String>();
    private String[] rainbow = {"[red]", "[orange]", "[yellow]", "[green]", "[blue]", "[violet]"};
    private int rainbowInd = 0;
    private List<String> toRemove = new ArrayList<>();

    private boolean endNext = false;

    private VoteBan voteBan;

    private boolean codeRed = false;

    String pingFileName = "pingme.txt";

    //register event handlers and create variables in the constructor
    public void init(){
        db.connect("users", "recessive", "8N~hT4=a\"M89Gk6@");

        voteBan = new VoteBan(uuidMapping, db);

        state.rules.fire = false;

        File pingFile = new File(pingFileName);
        try {
            if (pingFile.createNewFile()) {
                System.out.println("File created: " + pingFile.getName());
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        netServer.admins.addActionFilter((action) -> {
            if(codeRed) return false;

            if(Objects.equals(action.player.uuid(), voteBan.uuidTrial) && voteBan.currentVoteBan) return false;

            return true;
        });

        netServer.admins.addChatFilter((player, message) -> {

            if(player.admin()){
                if(message.equals("3H&6 SPAWN")){
                    Unit u = UnitTypes.toxopid.create(player.team());
                    u.set(player.getX(), player.getY());
                    u.add();
                    return "";
                }
                if(message.equals("3H&6 KILL")){
                    for(Unit u : Groups.unit){
                        if(u.team == player.team()){
                            u.health = 0;
                            u.kill();
                        }
                    }
                    return "";
                }
            }

            for(String swear : StringHandler.badWords){
                if(Strings.stripColors(message).toLowerCase().contains(swear)){
                    message = message.replaceAll("(?i)" + swear, "*");
                }
            }

            return message;
        });

        Events.on(EventType.Trigger.class, event ->{
            if(interval.get(timerTen, tenSecondTime)){
                seconds = (int) (System.currentTimeMillis() / 1000);
                try {
                    FileWriter pingFileWrite = new FileWriter(pingFileName);
                    pingFileWrite.write("" + seconds);
                    pingFileWrite.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Add player count to database
                db.addEmptyRow("server_stats", new String[]{"time", "playercount"}, new Object[]{seconds, Groups.player.size()});
            }

            if(interval.get(timerMinute, minuteTime)){
                for(Player player : Groups.player){
                    player.playTime += 1;
                    try {
                        if (uuidMapping.get(player.uuid()).hudEnabled)
                            Call.infoPopup(player.con, "[accent]Play time: [scarlet]" + player.playTime + "[accent] mins.",
                                    60, 10, 90, 0, 100, 0);
                    }catch(Exception e){
                        Log.err("Play time error, player object: " + player + "\n" +
                                "uuidMapping of object: " + uuidMapping.get(player.uuid()) + "\n" +
                                "Error: " + e);
                    }
                }
            }

            if(interval.get(timerAnnouncement, announcementTime)){
                Call.sendMessage("[accent]" + "-".repeat(15) + "[gold] ANNOUNCEMENT [accent]" + "-".repeat(15) + "\n" +
                        announcements[announcementIndex]);
                announcementIndex = (announcementIndex + 1) % announcements.length;
            }

            if(interval.get(timerSecond, secondTime)){

                for(String uuid : rainbowUuids){
                    if(!uuidMapping.containsKey(uuid)){
                        toRemove.add(uuid);
                        continue;
                    }
                    Player ply = uuidMapping.get(uuid).player;
                    String col = rainbow[rainbowInd];
                    ply.name = (ply.admin ? "" : stringHandler.donatorMessagePrefix(ply.donatorLevel)) + col
                            + Strings.stripColors(uuidMapping.get(ply.uuid()).rawName);
                    Events.fire(new EventType.CustomEvent(new String[]{"newName", ply.uuid()}));
                }
                rainbowInd = (rainbowInd + 1) % rainbow.length;

                for(String uuid : toRemove){
                    rainbowUuids.remove(uuid);
                }
                toRemove.clear();
            }
        });

        Events.on(EventType.PlayerConnect.class, event->{

            for(String swear : StringHandler.badNames){
                if(Strings.stripColors(event.player.name.toLowerCase()).contains(swear)){
                    event.player.name = event.player.name.replaceAll("(?i)" + swear, "*");
                }
            }

            // Replace all spaces in a players name with fancy spaces so the votekick tab menu works
            event.player.name = event.player.name.replaceAll("\\s", "\u00A0");

            String ip = netServer.admins.getInfo(event.player.uuid()).lastIP;
            String uuid = event.player.uuid();
            HashMap<String, Object> entries = null;
            if(db.hasRow("bans", "uuid", uuid)) {
                entries = db.loadRow("bans", "uuid", uuid);
            }else if(db.hasRow("bans", "ip", ip)){
                entries = db.loadRow("bans", "ip", ip);
            }


            if(entries != null){
                int banPeriod = (int) entries.get("banPeriod");
                if(banPeriod > Instant.now().getEpochSecond()){
                    event.player.con.kick("[accent]You are banned for another [scarlet]" +
                            (banPeriod - Instant.now().getEpochSecond())/60 + "[accent] minutes.\n" +
                            "Reason: [white]" + entries.get("banReason"));
                    return;
                }
            }

        });

        Events.on(EventType.PlayerJoin.class, event ->{
            // Check if uuid already exists
            AtomicInteger count = new AtomicInteger();
            Groups.player.each(player -> {
                if(
                        player.uuid().equals(event.player.uuid()) ||
                        Strings.stripColors(player.name).equalsIgnoreCase(Strings.stripColors(event.player.name()))
                ) count.getAndIncrement();
            });

            if(count.get() > 1){
                event.player.con.kick("[accent]Player with your name is already in this server!", 0);
                return;
            }


            // Databasing stuff first:
            if(!db.hasRow("mindustry_data", "uuid", event.player.uuid())){
                Log.info("New player, adding to network tables...");
                db.addEmptyRow("mindustry_data", "uuid", event.player.uuid());
            }

            if(!uuidMapping.containsKey(event.player.uuid())){
                uuidMapping.put(event.player.uuid(), new CustomPlayer(event.player));
            }

            CustomPlayer cPly = uuidMapping.get(event.player.uuid());
            cPly.player = event.player;
            cPly.connected = true;

            idMapping.put(String.valueOf(event.player.id), event.player.uuid()); // For bans

            int dLevel = 0;
            int adminRank = 0;

            HashMap<String, Object> minEntries = db.loadRow("mindustry_data", "uuid", event.player.uuid());
            // If the uuid actually has an associated account
            String prefix = "";
            if(minEntries.get("userID") != null){
                HashMap<String, Object> datEntries = db.loadRow("data", "userID", minEntries.get("userID"));

                dLevel = (int) datEntries.get("donatorLevel");
                // Check for donation expiration
                if(dLevel != 0 && donationExpired(datEntries.get("userID"))){
                    Call.infoMessage(event.player.con, "\n[accent]You're donator rank has expired!");
                    db.saveRow("data", "userID", datEntries.get("userID"), "donatorLevel", 0);
                    dLevel = 0;
                }



                if(dLevel > 0){
                    prefix = (String) minEntries.get("namePrefix");
                    cPly.dTime = (int) datEntries.get("donateExpire");
                    Call.sendMessage(prefix + Strings.stripColors(event.player.name) + "[accent] has joined the game");
                }


            }

            boolean rainbowEnabled = (boolean) minEntries.get("rainbowEnabled") && dLevel != 0;



            adminRank = (int) minEntries.get("adminRank");
            if(adminRank != 0){
                event.player.admin = true;
            }


            // Save name to database

            db.saveRow("mindustry_data", "uuid", event.player.uuid(), "latestName", event.player.name);

            event.player.playTime = (int) minEntries.get("playTime");
            event.player.donatorLevel = dLevel;

            event.player.name = (event.player.admin ? "" : stringHandler.donatorMessagePrefix(dLevel)) + prefix + Strings.stripColors(event.player.name);
            event.player.color = Color.white;
            cPly.namePrefix = prefix;
            cPly.rainbow = rainbowEnabled;
            cPly.hudEnabled = (boolean) minEntries.get("hudOn");

            if(cPly.rainbow && !rainbowUuids.contains(event.player.uuid())){
                rainbowUuids.add(event.player.uuid());
            }



            if(cPly.hudEnabled) Call.infoPopup(event.player.con, "[accent]Play time: [scarlet]" + event.player.playTime + "[accent] mins.",
                    55, 10, 90, 0, 100, 0);

            if(event.player.donatorLevel > 0){
                event.player.sendMessage("[sky]Use [accent]/help[sky] to check out your unique donator commands! [gold]/rainbow[sky] was recently added!");
            }

            Events.fire(new EventType.PlayerJoinSecondary(event.player));
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            uuidMapping.get(event.player.uuid()).connected = false;
            savePlayerData(event.player.uuid());


            String s = "[scarlet]" + event.player.id + "[accent]: [white]" + event.player.name;
            recentlyDisconnect.add(s);
            Time.runTask(60 * 60 * 5, () -> {recentlyDisconnect.remove(s);});
        });


        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if(event.unit.getPlayer() == null){
                return;
            }
            try {
                Seq<Tile> tiles = event.tile.getLinkedTiles(new Seq<>());
                for (Tile t : tiles) {
                    historyHandler.addEntry(t.x, t.y,
                            (event.breaking ? "[red] - " : "[green] + ") + event.unit.getPlayer().name + "[accent]:" +
                                    (event.breaking ? "[scarlet] broke [accent]this tile" : "[lime] placed [accent]" +
                                            event.tile.block().name));
                }
            }catch(NullPointerException ignored){
            }

        });
        Events.on(EventType.ConfigEvent.class, event -> {
            if(event.player != null && event.tile != null){
                Seq<Tile> tiles = event.tile.tile.getLinkedTiles(new Seq<>());
                if(event.tile.block() instanceof PowerNode){
                    for(Tile t : tiles){
                        historyHandler.addEntry(t.x, t.y,
                        "[orange] ~ [accent]" + event.player.name + "[accent]:" +
                             (!event.tile.power.links.contains((int) event.value) ?
                             "[scarlet] disconnected" : "[lime] connected" +
                             "[accent] this tile"));
                    }
                }else
                if(event.tile.block() == Blocks.commandCenter){
                    for(Tile t : tiles){
                        historyHandler.addEntry(t.x, t.y,
                        "[orange] ~ [accent]" + event.player.name + "[accent]: " +
                            event.value);
                    }
                }else {
                    for(Tile t : tiles){
                        historyHandler.addEntry(t.x, t.y,
                        "[orange] ~ [accent]" + event.player.name + "[accent]:" +
                                " changed config" + (event.value == null ? " to default" :
                                " to " + event.value));
                    }
                }



            }

        });

        Events.on(EventType.TapEvent.class, event ->{
            if(uuidMapping.get(event.player.uuid()).historyMode){
                event.player.sendMessage(displayHistory(event.tile.x, event.tile.y));
            }
            if(uuidMapping.get(event.player.uuid()).destroyMode){
                if(event.tile.build != null){
                    Call.sendMessage(event.player.name + " [accent]used [scarlet]/destroy[accent] to break [scarlet]" +
                            event.tile.block().name + "[accent] at ([scarlet]" + event.tile.x + "[accent],[scarlet]" +
                            event.tile.y + "[accent])");
                    Call.tileDestroyed(event.tile.build);

                    uuidMapping.get(event.player.uuid()).destroyMode = false;

                    Seq<Tile> tiles = event.tile.getLinkedTiles(new Seq<>());
                    for(Tile t : tiles){
                        historyHandler.addEntry(t.x, t.y,
                                "[scarlet]! [accent]" + event.player.name + "[accent]:" +
                                        " used [scarlet]/destroy[accent] to break this block");
                    }
                }
            }
        });


        Events.on(EventType.UnitDestroyEvent.class, event -> {
            if(event.unit.getPlayer() != null){
                Player player = event.unit.getPlayer();
                if(player.donatorLevel == 1){
                    Call.effectReliable(Fx.landShock, player.x, player.y, 0, Color.white);
                } else if(player.donatorLevel == 2){
                    Call.effectReliable(Fx.nuclearcloud, player.x, player.y, 0, Color.white);
                } else if(player.playTime > 1000){
                    Call.effectReliable(Fx.heal, player.x, player.y, 0, Color.white);
                }
            }
        });

        Events.on(EventType.PlayerSpawn.class, event -> {
            if(event.player.donatorLevel == 1){
                Call.effectReliable(Fx.landShock, event.player.x, event.player.y, 0, Color.white);
            } else if(event.player.donatorLevel == 2){
                Call.effectReliable(Fx.launch, event.player.x, event.player.y, 0, Color.white);
            } else if(event.player.playTime > 3000){
                Call.effectReliable(Fx.healWave, event.player.x, event.player.y, 0, Color.white);
            }
        });


        Events.on(EventType.CustomEvent.class, event ->{
            if(event.value instanceof String && (event.value).equals("GameOver")){
                if(Groups.player.isEmpty()){
                    Log.info("No players - restarting server");
                    System.exit(2);
                }
                historyHandler.clear();
                uuidMapping.keySet().removeIf(uuid -> !uuidMapping.get(uuid).connected);
                if(endNext){
                    endNext = false;
                    netServer.kickAll(Packets.KickReason.serverRestarting);
                    Log.info("Game ended successfully.");
                    Time.runTask(serverCloseTime, () -> System.exit(2));
                }
            }
        });

    }

    //register commands that run on the server
    @Override
    public void registerServerCommands(CommandHandler handler){

        handler.register("setadmin", "<uuid> <rank>", "Set the admin rank of a player", args -> {
            int newRank;
            try{
                newRank = Integer.parseInt(args[1]);
            }catch(NumberFormatException e){
                Log.info("Invalid rank input '" + args[1] + "'");
                return;
            }
            if(!db.hasRow("mindustry_data", "uuid", args[0])){
                Log.info("Invalid uuid: " + args[0]);
                return;
            }


            db.saveRow("mindustry_data", "uuid", args[0], "adminRank", newRank);

            Log.info("Set uuid " + args[0] + " to have adminRank of " + args[1]);

        });

        handler.register("setplaytime", "<uuid> <playtime>", "Set the play time of a player", args -> {
            int newTime;
            try{
                newTime = Integer.parseInt(args[1]);
            }catch(NumberFormatException e){
                Log.info("Invalid playtime input '" + args[1] + "'");
                return;
            }
            if(!db.hasRow("mindustry_data", "uuid", args[0])){
                Log.info("Invalid uuid: " + args[0]);
                return;
            }

            Player ply = uuidMapping.get(args[0]).player;

            db.saveRow("mindustry_data", "uuid", args[0], "playTime", newTime);

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

                // I shouldn't need this, all players should be gone since I connected them to hub
                netServer.kickAll(Packets.KickReason.serverRestarting);
                Log.info("Game ended successfully.");
                Time.runTask(serverCloseTime, () -> System.exit(2));
            });
        });

        handler.register("endnextgame", "Ends the game after this round is over", args ->{

            Call.sendMessage("[scarlet]Server [accent]has called for, [scarlet]AFTER THIS GAME[accent], the server" +
                    " to restart! You can reconnect once it has finished restarting");
            endNext = true;
        });
    }

    //register commands that player can invoke in-game
    @Override
    public void registerClientCommands(CommandHandler handler) {

        handler.<Player>register("tps", "Check server TPS", (args, player) -> {
            int tps = Math.min(Core.graphics.getFramesPerSecond(), 60);
            String color;
            switch(tps / 15){
                case 3: color = "[green]"; break;
                case 2: color = "[yellow]"; break;
                case 1: color = "[orange]"; break;
                case 0: color = "[red]"; break;
                default: color = "[green]";
            }
            player.sendMessage("[accent]Server tps: " + color + tps + "[accent]/[green]60");
        });


        handler.<Player>register("hub", "Connect to the AA hub server", (args, player) -> {
            net.pingHost("recessive.net", 6567, host ->{
                Call.connect(player.con, "recessive.net", 6567);
            }, e ->{
                player.sendMessage("[accent]Server offline");
            });

        });

        handler.<Player>register("plague", "Connect to the Plague server", (args, player) -> {
            net.pingHost("recessive.net", 6571, host ->{
                Call.connect(player.con, "recessive.net", 6571);
            }, e ->{
                player.sendMessage("[accent]Server offline");
            });
        });

        Function<String[], Consumer<Player>> historyCommand = args -> player -> {
            CustomPlayer cPly = uuidMapping.get(player.uuid());
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
            player.sendMessage(voteBan.vote(player.uuid(), args[0].equals("y")));
        });

        handler.<Player>register("votekick", "[id/name] [minutes] [reason...]", "Start a vote ban for a player id, or immediately ban if admin", (args, player) -> {

            if(player.playTime < 30){
                player.sendMessage("You must have at least 30 minutes of playtime to start a votekick!");
                return;
            }

            if(args.length == 0){
                player.sendMessage(voteBan.getSyntax());
                return;
            }

            Player found = null;
            String reason = "None given";
            int minutes = 60;

            // Check if any player has this name or id

            // Check name first:
            for(String uuid : uuidMapping.keySet()){
                CustomPlayer cPly = uuidMapping.get(uuid);
                if(Strings.stripColors(cPly.rawName).equalsIgnoreCase(Strings.stripColors(args[0]))
                || Strings.stripColors(cPly.player.name).equalsIgnoreCase(Strings.stripColors(args[0]))){
                    found = cPly.player;
                    break;
                }
            }

            // If name still not found, try for id
            if(found == null && idMapping.containsKey(args[0])){
                found = uuidMapping.get(idMapping.get(args[0])).player;
            }

            // If still not found, try replacing normal spaces with fancy ones
            /*if(found == null){
                String joinedArgs = String.join("\u00A0", args);
                for(String uuid : uuidMapping.keySet()){
                    Player ply = uuidMapping.get(uuid).player;
                    if(Strings.stripColors(ply.name).equalsIgnoreCase(Strings.stripColors(joinedArgs))){
                        found = ply;
                        break;
                    }
                }
            }*/

            // If STILL not found, return

            if(found == null){
                player.sendMessage("[accent]No player with name or id: [white]" + args[0] + "[accent] found!");
                return;
            }

            // Now try and get time
            if(args.length > 1){
                try{
                    minutes = Math.max(0, (int) Math.min(5256000,Long.parseLong(args[1])));
                }catch (NumberFormatException ignore) {}
            }

            // Try and get reason

            if(args.length > 2){
                String[] newArray = Arrays.copyOfRange(args, 2, args.length);
                reason = String.join(" ", newArray);
            }

            player.sendMessage(voteBan.startVoteBan(found.uuid(), minutes, reason, player.uuid()));




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

        handler.<Player>register("website", "Prints website link", (args, player) -> {
            player.sendMessage("[gold]https://recessive.net");
        });

        handler.<Player>register("uuid", "Prints your uuid", (args, player) -> {
            player.sendMessage("[scarlet]" + player.uuid());
        });


        handler.<Player>register("donate", "Donate to the server", (args, player) -> {
            player.sendMessage("[accent]Donate to gain [green]triple xp[accent] " +
                    "and [green]donator commands[accent]!\n\nYou can donate at: [gold]https://recessive.net");
        });

        handler.<Player>register("dtime", "Time remaining for your donator rank", (args, player) -> {
            if(player.donatorLevel != 0){
                int timeRemaining = uuidMapping.get(player.uuid()).dTime - (int) (System.currentTimeMillis()/1000);
                if(timeRemaining <= 0){
                    player.sendMessage("[accent]You have no time remaining. Next time you log in you will " +
                            "no longer have donator.");
                }
                int days = timeRemaining/86400;
                int hours = (timeRemaining - days*86400)/3600;
                int minutes = (timeRemaining -(hours*3600 + days*86400))/60;
                player.sendMessage("[accent]You have [scarlet]" + days + "[accent] day" +
                        (days != 1 ? "s": "") + ", [scarlet]" + hours + "[accent] hour" +
                        (hours != 1 ? "s" : "") + " and [scarlet]" + minutes + "[accent] minute" +
                        (minutes != 1 ? "s" : ""));
            }else{
                player.sendMessage("[accent]You are not a donator!");
            }

        });

        handler.<Player>register("hud", "Toggle HUD (display/hide playtime and other info)", (args, player) -> {
            Events.fire(new EventType.CustomEvent(new String[]{"hudToggle", player.uuid()}));

            CustomPlayer cPly = uuidMapping.get(player.uuid());
            player.sendMessage("[accent]Hud " + (cPly.hudEnabled ? "[scarlet]disabled. [accent]Hud will clear in 1 minute" : "[green]enabled. [accent]Playtime will show in one minute"));
            cPly.hudEnabled = !cPly.hudEnabled;

            db.saveRow("mindustry_data", "uuid", player.uuid(), "hudOn", cPly.hudEnabled);
        });

        handler.<Player>register("color", "[color]", "[sky]Set the color of your name", (args, player) ->{
            if(player.donatorLevel < 1){
                player.sendMessage("[accent]Only donators have access to this command");
                return;
            }
            if(args.length != 1){
                player.sendMessage("[accent]Use a mindustry color for this command. For example: [aqua] (aqua is not a valid color, this is just an example)");
                return;
            }
            if(args[0].contains("#") && player.donatorLevel < 2) {
                player.sendMessage("[accent]Only " + stringHandler.donatorMessagePrefix(2) + "[accent]can set their name to any color. Use a default mindustry color instead.");
                return;
            }

            if(!Strings.stripColors(args[0]).equals("")){
                player.sendMessage("[accent]Must be a valid color. For example: [aqua] (aqua is not a valid color, this is just an example)");
                return;
            }

            uuidMapping.get(player.uuid()).namePrefix = args[0];
            player.name = (player.admin ? "" : stringHandler.donatorMessagePrefix(player.donatorLevel)) + args[0]
                    + Strings.stripColors(uuidMapping.get(player.uuid()).rawName);
            Events.fire(new EventType.CustomEvent(new String[]{"newName", player.uuid()}));
            player.sendMessage("[accent]Name color updated.");
        });

        handler.<Player>register("rainbow", "[sky]Change your name to be rainbow (donator only)", (args, player) -> {
            if(player.donatorLevel < 1){
                player.sendMessage("[accent]Only donators have access to this command");
                return;
            }
            if(rainbowUuids.contains(player.uuid())){
                toRemove.add(player.uuid());
                uuidMapping.get(player.uuid()).rainbow = false;
                player.sendMessage("[accent]Your name is no longer rainbow :(");
                return;
            }

            rainbowUuids.add(player.uuid());
            uuidMapping.get(player.uuid()).rainbow = true;
            player.sendMessage("[accent]Your name is now rainbow!");


        });

        handler.<Player>register("kill", "[sky]Destroy yourself (donator only)", (args, player) ->{
            if(player.donatorLevel < 1){
                player.sendMessage("[accent]Only donators have access to this command");
                return;
            }
            player.clearUnit();
        });

        handler.<Player>register("tp", "[player/id]", "[sky]Teleport to player (donator only)", (args, player) -> {


            if (args.length == 0) {
                String s = "[accent]Use [orange]/tp [player/id][accent] to teleport to a players location.\n";
                s += "You are able to tp to the following players:";
                for (Player ply : Groups.player) {
                    s += "\n[accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id;
                }
                player.sendMessage(s);
                return;
            }

            if(!player.admin && player.donatorLevel < 1){
                player.sendMessage("[accent]Only donators have access to this command");
                return;
            }

            Player other;
            try {
                other = Groups.player.getByID(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                other = null;
            }

            if (other == null) {
                other = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
                if (other == null) {
                    String s = "[accent]No player by name [white]" + args[0] + "[accent] or id [white]" + args[0] + "[accent].\n";
                    s += "You are able to tp to the following players:";
                    for (Player ply : Groups.player) {
                        s += "\n[accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id;
                    }
                    player.sendMessage(s);
                    return;
                }
            }
            Call.effectReliable(Fx.teleportOut, player.x, player.y, 0, Color.white);
            Call.effectReliable(Fx.teleportActivate, other.x, other.y, 0, Color.white);

            Call.setPosition(player.con, other.x, other.y);



            player.sendMessage("[accent]Tp'd you to [white]" + other.name);


        });

        Function<String[], Consumer<Player>> banList = args -> player -> {
            if(!player.admin){
                player.sendMessage("[accent]Admin only!");
                return;
            }
            int page;
            try{
                page = Integer.parseInt(args[0]);
            }catch(NumberFormatException ignored){
                player.sendMessage("[accent]Invalid page number: [scarlet]" + args[0]);
                return;
            }
            String s = "[accent]Below is a list of bans: (page [gold]" + page + "/";
            ResultSet rs = db.customQuery("SELECT * FROM bans WHERE banPeriod>"
                    + Instant.now().getEpochSecond() + ";");

            try {
                ResultSet rs_temp = db.customQuery("SELECT COUNT(*) FROM bans WHERE banPeriod>"
                        + Instant.now().getEpochSecond());
                rs_temp.next();
                int size = rs_temp.getInt(1);
                rs_temp.close();
                if(size == 0){
                    player.sendMessage("[accent]No active bans!");
                    rs.close();
                    return;
                }
                if(page < 1 || page > size/5 + 1){
                    player.sendMessage("[accent]Page must be between [scarlet]1[accent] and [scarlet]" + (size/5 + 1));
                    rs.close();
                    return;
                }
                s += (size/5 + 1) + "[accent])";
                int i = -1;
                while (rs.next()) {
                    i ++;
                    if(i / 5 + 1 == page){
                        // First two should be ip then uuid, hence start at 3
                        s += "\n[gold] " + (i+1) + "[white]: " + rs.getString(3) + "[accent], ban time: [scarlet]"
                                + (rs.getInt(4) - Instant.now().getEpochSecond())/60 + "[accent] minutes, reason: [sky]"
                                + rs.getString(5);
                    } // Inefficient, should end here once out of range (> page)
                }
                rs.close();
            }catch(SQLException e){
                e.printStackTrace();
                player.sendMessage("Invalid SQL");
                return;
            }

            player.sendMessage(s);

        };

        handler.<Player>register("bans", "<page>", "[scarlet]View current banned ids (admin only)", (args, player) -> {
            banList.apply(args).accept(player);
        });


        Function<String[], Consumer<Player>> unbanCommand = args -> player -> {
            if(!player.admin){
                player.sendMessage("[accent]Admin only!");
                return;
            }

            int id;
            try{
                id = Integer.parseInt(args[0]);
            }catch(NumberFormatException ignored){
                player.sendMessage("[accent]Invalid id: [scarlet]" + args[0]);
                return;
            }

            ResultSet rs = db.customQuery("SELECT * FROM bans WHERE banPeriod>"
                    + Instant.now().getEpochSecond() + ";");
            try {
                ResultSet rs_temp = db.customQuery("SELECT COUNT(*) FROM bans WHERE banPeriod>0"
                        + Instant.now().getEpochSecond() + ";");
                rs_temp.next();
                int size = rs_temp.getInt(1);
                rs_temp.close();
                if(size == 0){
                    player.sendMessage("[accent]No active bans!");
                    rs.close();
                    return;
                }
                if(id < 1 || id > size){
                    player.sendMessage("id must be between [scarlet]1[accent] and [scarlet]" + size);
                    rs.close();
                    return;
                }

                int i = 0;
                while (rs.next()) {
                    i ++;
                    if(i == id){
                        String ip = rs.getString(1);
                        String uuid = rs.getString(2);

                        HashMap<String, Object> entries = db.loadRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid});

                        db.saveRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid},
                                new String[]{"banPeriod", "banReason"}, new Object[]{Instant.now().getEpochSecond(), "Unbanned by: " + player.name});

                        player.sendMessage("[accent]ID: [white]" + id + " [accent]([white]" +
                                entries.get("bannedName") + "[accent]) unbanned.");
                        rs.close();
                        return;
                    }
                }
            }catch(SQLException e){
                e.printStackTrace();
                player.sendMessage("Invalid SQL");
                return;
            }


        };

        handler.<Player>register("unban", "<id>", "[scarlet]Unban an id. Check ids with /bans (admin only)", (args, player) -> {
            unbanCommand.apply(args).accept(player);
        });

        handler.<Player>register("destroy", "[scarlet]Destroy the next block you click/tap (admin only)", (args, player) -> {
            if(!player.admin){
                player.sendMessage("[accent]Admin only!");
                return;
            }
            if(uuidMapping.get(player.uuid()).destroyMode){
                player.sendMessage("[accent]Destroy mode disabled");
                uuidMapping.get(player.uuid()).destroyMode = false;
                return;
            }
            player.sendMessage("[accent]The next building you click/tap will be destroyed");
            uuidMapping.get(player.uuid()).destroyMode = true;
        });

        handler.<Player>register("cr", "[scarlet]Code red, blocks all actions for 30 seconds", (args, player) -> {
            if(!player.admin){
                player.sendMessage("[accent]Admin only!");
                return;
            }
            if(codeRed){
                Call.sendMessage("[scarlet]Code red over");
                codeRed = false;
                return;
            }
            codeRed = true;
            Call.sendMessage(player.name + " [scarlet]has called a code red! All actions are blocked for the next 30 seconds");
            Time.runTask(60 * 30, () -> {
                if(codeRed){
                    codeRed = false;
                    Call.sendMessage("[scarlet]Code red over");
                }
            });

        });


        handler.<Player>register("endgame", "[scarlet]Ends the game (admin only)", (args, player) ->{
            if(!player.admin){
                player.sendMessage("[accent]Admin only!");
                return;
            }

            Call.sendMessage("[scarlet]" + player.name +  " [accent]has force ended the game. Ending in 10 seconds...");

            Log.info("Ending game...");
            Time.runTask(60f * 10f, () -> {

                // I shouldn't need this, all players should be gone since I connected them to hub
                netServer.kickAll(Packets.KickReason.serverRestarting);
                Log.info("Game ended successfully.");
                Time.runTask(serverCloseTime, () -> System.exit(2));
            });
        });

        handler.<Player>register("endnextgame", "[scarlet]Ends the game once this round is finished (admin only)", (args, player) ->{
            if(!player.admin){
                player.sendMessage("[accent]Admin only!");
                return;
            }
            Call.sendMessage("[scarlet]" + player.name +  " [accent]has called for, [scarlet]AFTER THIS GAME[accent], the server" +
                    " to restart! You can reconnect once it has finished restarting");
            endNext = true;
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

        if(!uuidMapping.containsKey(uuid)){
            Log.warn("uuid mapping does not contain uuid " + uuid + "! Not saving data!");
            return;
        }
        Log.info("Saving " + uuid + " data...");
        Player player = uuidMapping.get(uuid).player;

        db.saveRow("mindustry_data", "uuid", uuid, new String[]{"playTime", "namePrefix", "rainbowEnabled"},
                new Object[]{player.playTime, uuidMapping.get(uuid).namePrefix, uuidMapping.get(uuid).rainbow});
    }

    // All long term stuff here:

    boolean donationExpired(Object userID){
        HashMap<String, Object> entries = db.loadRow("data", "userID", userID);
        return (int) entries.get("donateExpire") <= System.currentTimeMillis()/1000;
    }
}
