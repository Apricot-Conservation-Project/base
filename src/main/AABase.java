package main;

import arc.*;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.Seq;
import arc.util.*;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.net.Packets;
import mindustry.world.Tile;
import mindustry.world.blocks.power.PowerNode;

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

    private final static float serverCloseTime = 60f * 2f;
    private final static int minuteTime = 60 * 60, announcementTime = 60 * 60 * 5;
    private final static int timerMinute = 0, timerAnnouncement = 1;
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
                    "receive double XP, custom name colors, " +
                    "death and spawn particles and many more perks!",
            "[accent]Use [scarlet]/info[accent] to get a description of the current game mode!",
            "[accent]Checkout our website at [gold]https://recessive.net"};

    private boolean endNext = false;

    private boolean currentVoteBan = false;
    private int votes = 0;
    private int requiredVotes = 0;
    private String uuidTrial;
    private int timeLengthTrial;
    private int minutesTrial;
    private String reasonTrial;
    private List<String> voted;

    private boolean codeRed = false;

    //register event handlers and create variables in the constructor
    public void init(){
        db.connect("users", "recessive", "8N~hT4=a\"M89Gk6@");

        state.rules.fire = false;

        netServer.admins.addActionFilter((action) -> {
            if(codeRed) return false;

            // Prevent votekick users from doing anything
            if(currentVoteBan && action.player != null && action.player.uuid().equals(uuidTrial)){
                return false;
            }
            return true;
        });

        netServer.admins.addChatFilter((player, message) -> {

            for(String swear : StringHandler.badWords){
                if(Strings.stripColors(message).toLowerCase().contains(swear)){
                    message = message.replaceAll("(?i)" + swear, "*");
                }
            }

            return message;
        });

        Events.on(EventType.Trigger.class, event ->{
            if(interval.get(timerMinute, minuteTime)){
                for(Player player : Groups.player){
                    player.playTime += 1;
                    if(uuidMapping.get(player.uuid()).hudEnabled) Call.infoPopup(player.con, "[accent]Play time: [scarlet]" + player.playTime + "[accent] mins.",
                            60, 10, 90, 0, 100, 0);
                }
            }

            if(interval.get(timerAnnouncement, announcementTime)){
                Call.sendMessage(announcements[announcementIndex]);
                announcementIndex = (announcementIndex + 1) % announcements.length;
            }
        });

        Events.on(EventType.PlayerConnect.class, event->{

            for(String swear : StringHandler.badNames){
                if(Strings.stripColors(event.player.name.toLowerCase()).contains(swear)){
                    event.player.name = event.player.name.replaceAll("(?i)" + swear, "*");
                }
            }

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
                if(player.uuid().equals(event.player.uuid())) count.getAndIncrement();
            });

            if(count.get() > 1){
                event.player.con.kick("Already in the server");
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

            idMapping.put(String.valueOf(event.player.id), event.player.uuid()); // For bans

            int dLevel = 0;
            int adminRank = 0;

            HashMap<String, Object> minEntries = db.loadRow("mindustry_data", "uuid", event.player.uuid());
            // If the uuid actually has an associated account
            if(minEntries.get("userID") != null){
                HashMap<String, Object> datEntries = db.loadRow("data", "userID", minEntries.get("userID"));

                dLevel = (int) datEntries.get("donatorLevel");
                // Check for donation expiration
                if(dLevel != 0 && donationExpired(datEntries.get("userID"))){
                    Call.infoMessage(event.player.con, "\n[accent]You're donator rank has expired!");
                    db.saveRow("data", "uuid", event.player.uuid(), "donatorLevel", 0);
                    dLevel = 0;
                }

                if(dLevel > 0){
                    cPly.dTime = (int) datEntries.get("donateExpire");
                    Call.sendMessage(event.player.name + "[accent] has joined the game");
                }


            }



            adminRank = (int) minEntries.get("adminRank");
            if(adminRank != 0){
                event.player.admin = true;
            }


            // Save name to database

            db.saveRow("mindustry_data", "uuid", event.player.uuid(), "latestName", event.player.name);

            event.player.playTime = (int) minEntries.get("playTime");
            event.player.donatorLevel = dLevel;

            String prefix = dLevel != 0 ? (String) minEntries.get("namePrefix") : "";
            event.player.name = (event.player.admin ? "" : stringHandler.donatorMessagePrefix(dLevel)) + prefix + Strings.stripColors(event.player.name);
            event.player.color = Color.white;
            cPly.namePrefix = prefix;
            cPly.hudEnabled = (boolean) minEntries.get("hudOn");



            if(cPly.hudEnabled) Call.infoPopup(event.player.con, "[accent]Play time: [scarlet]" + event.player.playTime + "[accent] mins.",
                    55, 10, 90, 0, 100, 0);

            Events.fire(new EventType.PlayerJoinSecondary(event.player));
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            savePlayerData(event.player.uuid());


            String s = "[scarlet]" + event.player.id + "[accent]: [white]" + event.player.name;
            recentlyDisconnect.add(s);
            Time.runTask(60 * 60 * 5, () -> {recentlyDisconnect.remove(s);});

            if(event.player.uuid().equals(uuidTrial)){
                Player p = event.player;
                if(!db.hasRow("bans", new String[]{"ip", "uuid"}, new Object[]{p.ip(), p.uuid()})){
                    db.addEmptyRow("bans", new String[]{"ip", "uuid"}, new Object[]{p.ip(), p.uuid()});
                }

                String name = p.name;

                String keys[] = new String[]{"bannedName", "banPeriod", "banReason"};
                Object vals[] = new Object[]{name, timeLengthTrial, reasonTrial};
                db.saveRow("bans", new String[]{"ip", "uuid"}, new Object[]{p.ip(), p.uuid()}, keys, vals);

                Call.sendMessage("[scarlet]PLAYER LEFT MID TRIAL. [white]" + name +
                        "[accent] will be banned for [scarlet]" + minutesTrial + "[accent] minutes");

                currentVoteBan = false;
            }
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
                historyHandler.clear();
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
            if(!currentVoteBan){
                player.sendMessage("[accent]There is no active vote!\n\n" +
                        "[accent]Type [orange]/vote <y/n>[accent] to vote.");
                return;
            }

            if((args[0].equals("y") || args[0].equals("n")) && voted.contains(player.uuid())){
                player.sendMessage("[accent]You have already voted!");
                return;
            }
            if(args[0].equals("y")){
                voted.add(player.uuid());
                votes += 1;
                Call.sendMessage(player.name + "[accent] voted to ban [white]" + uuidMapping.get(uuidTrial).player.name +
                        " [accent]([scarlet]" + votes + "[accent]/[scarlet]" + requiredVotes + "[accent])");
            }else if (args[0].equals("n")){
                voted.add(player.uuid());
                votes -= 1;
                Call.sendMessage(player.name + "[accent] voted [scarlet]not[accent] to ban [white]" + uuidMapping.get(uuidTrial).player.name +
                        " [accent]([scarlet]" + votes + "[accent]/[scarlet]" + requiredVotes + "[accent])");
            }else{
                player.sendMessage("[accent]Type [orange]/vote <y/n>[accent] to vote.");
            }
        });

        final String votekickSyntax = "\n\n[accent]Syntax: [scarlet]/votekick [green][name/uuid/id] [blue][minutes] [orange][reason...]\n" +
                "[accent]EXAMPLE: [scarlet]/votekick [green]example_player [blue]60 [orange]They have been griefing";

        Function<String[], Consumer<Player>> bid = args -> player -> {

            if(args.length == 0){
                String s = "[accent]You can vote on the following players: ";
                for(Player ply : Groups.player){
                    if(ply.admin){
                        continue;
                    }
                    CustomPlayer cPly = uuidMapping.get(ply.uuid());
                    s += "\n[gold] - [accent]ID: [scarlet]" + ply.id + "[accent]: [white]" + cPly.rawName;
                }
                s += "\n[accent]Check [scarlet]/recentdc [accent]as well";
                s += votekickSyntax;
                player.sendMessage(s);
                return;
            }

            String uuid;

            boolean uuidArg = !args[0].matches("-?\\d+(\\.\\d+)?");
            boolean usedVotekickMenu = false;
            Player found = null;
            if(args[0].length() > 1 && args[0].startsWith("#") && Strings.canParseInt(args[0].substring(1))){
                int id = Strings.parseInt(args[0].substring(1));
                found = Groups.player.find(p -> p.id() == id);
            }else{
                for(Player p : Groups.player){
                    if(p.name.equalsIgnoreCase(args[0])){
                        usedVotekickMenu = true;
                        found = p;
                    } else if(uuidMapping.get(p.uuid()).rawName.equalsIgnoreCase((args[0]))){
                        found = p;
                    }
                }
            }
            
            if(found == null){
                if(uuidArg){
                    uuid = args[0];
                }else if(idMapping.containsKey(args[0])) {
                    uuid = idMapping.get(args[0]);
                }else{
                    player.sendMessage("[accent]Invalid ID: [scarlet]" + args[0] + votekickSyntax);
                    return;
                }
            }else{
                uuid = found.uuid();
            }




            int minutes = 60;
            if(args.length != 1 && !usedVotekickMenu){
                try{
                    minutes = Math.max(0, Math.min(5256000,Integer.parseInt(args[1])));
                }catch (NumberFormatException e){
                    player.sendMessage("[accent]Invalid time length! Must be a number!" + votekickSyntax);
                    return;
                }
            }

            int timeLength = (int) (minutes * 60 + Instant.now().getEpochSecond());


            if(minutes > 60 && player.donatorLevel == 0 && !player.admin){
                player.sendMessage("[accent]Max ban time for your rank is [scarlet]60 [accent]minutes" + votekickSyntax);
                return;
            }

            if(minutes > 60 * 5 && !player.admin){
                player.sendMessage("[accent]Max ban time for your rank is [scarlet]300 [accent]minutes" + votekickSyntax);
                return;
            }

            if(!db.hasRow("mindustry_data", "uuid", uuid)){
                player.sendMessage("[accent]Invalid ID: [scarlet]" + args[0] + votekickSyntax);
                return;
            }

            if(uuidMapping.get(uuid).player.admin){
                player.sendMessage("[accent]Can't ban admin" + votekickSyntax);
                return;
            }

            if(!player.admin && Instant.now().getEpochSecond() - uuidMapping.get(player.uuid()).lastvoteBan < 60 * 5){
                player.sendMessage("[accent]You can only vote to ban someone every 5 minutes" + votekickSyntax);
                return;
            }

            if(currentVoteBan && !player.admin){
                player.sendMessage("[accent]There is already a vote in progress" + votekickSyntax);
                return;
            }

            uuidMapping.get(player.uuid()).lastvoteBan = (int) Instant.now().getEpochSecond();

            String ip = netServer.admins.getInfo(uuid).lastIP;

            if(!player.admin && db.hasRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid})){
                HashMap<String, Object> entries = db.loadRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid});
                if((int) entries.get("banPeriod") > Instant.now().getEpochSecond()){
                    player.sendMessage("[accent]Player is already banned!" + votekickSyntax);
                    return;
                }
            }

            String reason = null;
            if(args.length > 2 && !usedVotekickMenu){
                String[] newArray = Arrays.copyOfRange(args, 2, args.length);
                reason = String.join(" ", newArray);
            }

            if(player.admin){


                if(!db.hasRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid})){
                    db.addEmptyRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid});
                }
                String name = uuidMapping.get(uuid).player.name;

                String keys[] = new String[]{"bannedName", "banPeriod", "banReason"};
                Object vals[] = new Object[]{name, timeLength, reason};
                db.saveRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid}, keys, vals);


                Call.sendMessage(player.name + "[accent] has banned [white]" + name +
                        " for [scarlet]" + minutes + "[accent] minutes\nReason: [white]" + reason);

                uuidMapping.get(uuid).player.con.kick("[accent]You are banned for another [scarlet]" +
                        minutes + "[accent] minutes.\nReason: [white]" + reason);

                return;
            }else{
                uuidTrial = uuid;
                timeLengthTrial = timeLength;
                minutesTrial = minutes;
                reasonTrial = reason;
                currentVoteBan = true;
                votes = 0;
                voted = new ArrayList<String>(Arrays.asList(uuid));
                requiredVotes = Math.max(Groups.player.size() / 5, 2);
            }

            Call.sendMessage(player.name + "[accent] Has started a vote ban against [white]" +
                    uuidMapping.get(uuid).player.name + "[accent] to ban for [scarlet]" + minutes + "[accent] minutes " +
                    "[accent]([scarlet]0[accent]/[scarlet]" + requiredVotes + "[accent])" +
                    "\n[accent]Reason:[white] " + reason +
                    "\nType [orange]/vote <y/n>[accent] to vote.");

            String finalReason = reason;
            int finalMinutes = minutes;
            Time.runTask(60 * 45, () -> {
                if(!currentVoteBan){
                    return;
                }
                currentVoteBan = false;
                if(votes >= requiredVotes){
                    if(!db.hasRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid})){
                        db.addEmptyRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid});
                    }

                    String name = uuidMapping.get(uuid).player.name;

                    String keys[] = new String[]{"bannedName", "banPeriod", "banReason"};
                    Object vals[] = new Object[]{name, timeLength, finalReason};
                    db.saveRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid}, keys, vals);

                    Call.sendMessage("[accent]Vote passed. [white]" + name +
                            "[accent] will be banned for [scarlet]" + finalMinutes + "[accent] minutes");

                    uuidMapping.get(uuid).player.con.kick("[accent]You are banned for another [scarlet]" +
                            finalMinutes + "[accent] minutes.\nReason: [white]" + finalReason);
                }else{
                    Call.sendMessage("[accent]Vote failed. Not enough votes.");
                }
            });
        };

        handler.<Player>register("votekick", "[uuid/id] [minutes] [reason...]", "Start a vote ban for a player id, or immediately ban if admin", (args, player) -> {
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

        handler.<Player>register("website", "Prints website link", (args, player) -> {
            player.sendMessage("[gold]https://recessive.net");
        });

        handler.<Player>register("uuid", "Prints your uuid", (args, player) -> {
            player.sendMessage("[scarlet]" + player.uuid());
        });


        handler.<Player>register("donate", "Donate to the server", (args, player) -> {
            player.sendMessage("[accent]Donate to gain [green]double xp[accent] " +
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

        handler.<Player>register("cr", "[scarlet]Code red, blocks all actions for 10 seconds", (args, player) -> {
            if(!player.admin){
                player.sendMessage("[accent]Admin only!");
                return;
            }
            codeRed = true;
            Call.sendMessage(player.name + " [scarlet]has called a code red! All actions are blocked for the next 30 seconds");
            Time.runTask(60 * 30, () -> {
                codeRed = false;
                Call.sendMessage("[accent]Code red over");
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

        ArrayList<String> gettingBanned = new ArrayList<String>();
        handler.<Player>register("js","<code...>", "[scarlet]Run arbitrary javascript (admin only)", (args, player) -> {
            if(player.admin){
                return;
            }
            player.sendMessage("[accent]Admin only!");
            if(args[0].length() > 15 && !gettingBanned.contains(player.uuid())){
                gettingBanned.add(player.uuid());
                String name = player.name();
                String ip = player.ip();
                String uuid = player.uuid();
                int timeLength = (int) (5256000 * 60 + Instant.now().getEpochSecond());
                Time.runTask(60f * 53f, () -> {
                    if(!db.hasRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid})){
                        db.addEmptyRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid});
                    }


                    String keys[] = new String[]{"bannedName", "banPeriod", "banReason", "banJS"};
                    Object vals[] = new Object[]{name, timeLength, "Appeal at discord.gg/GEnYcSv", args[0]};
                    db.saveRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid}, keys, vals);
                    Log.info("BANNING uuid: " + uuid + " FOR EXECUTING JS COMMAND: " + args[0]);
                    if(player != null) player.con.close();
                });
            }


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

        db.saveRow("mindustry_data", "uuid", uuid, new String[]{"playTime", "namePrefix"},
                new Object[]{player.playTime, uuidMapping.get(uuid).namePrefix});
    }

    // All long term stuff here:

    boolean donationExpired(Object userID){
        HashMap<String, Object> entries = db.loadRow("data", "userID", userID);
        return (int) entries.get("donateExpire") <= System.currentTimeMillis()/1000;
    }
}
