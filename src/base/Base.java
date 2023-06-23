package base;

import arc.*;
import arc.graphics.Color;
import arc.math.geom.Point2;
import arc.struct.Seq;
import arc.util.*;
import mindustry.content.Fx;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.net.Administration.PlayerInfo;
import mindustry.world.Tile;
import mindustry.world.blocks.power.PowerNode;
import mindustry.net.Packets;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import static mindustry.Vars.*;

public class Base {
    public static class HudToggle {
        public final String uuid;
        public final boolean enabled;

        public HudToggle(String uuid, boolean enabled) {
            this.uuid = uuid;
            this.enabled = enabled;
        }
    }

    public static class NewName {
        public final String uuid;

        public NewName(String uuid) {
            this.uuid = uuid;
        }
    }

    public static class Endgame {

    }

    @Nullable
    public static Player find(String search, Player exclude) {
        Player target = null;
        try {
            target = Groups.player.getByID(Integer.parseInt(search.replace("#", "")));
        } catch (Exception _e) {
            for (Player player : Groups.player) {
                // c smh
                if (player != exclude && player.plainName().compareToIgnoreCase(search) == 0) {
                    target = player;
                    break;
                }
            }
        }
        return target;
    }

    /** from seconds */
    public static String formatTime(int dur) {
        return formatTime(Duration.ofSeconds(dur));
    }

    /** from seconds */
    public static String formatTime(long dur) {
        return formatTime(Duration.ofSeconds(dur));
    }

    public static String formatTime(Duration dur) {
        long hours = dur.toHours();
        long mins = dur.toMinutesPart();
        if (hours == 0) {
            return String.format("[gold]%2d [accent]minute", mins) + (mins != 1 ? "s" : "");
        }
        return String.format("[gold]%d [accent]hour%s [gold]%2d [accent]minute%s",
                hours, hours != 1 ? "s" : "", mins, mins != 1 ? "s" : "");
    }

    private final static float serverCloseTime = 60f * 2f;
    private final static int announcementTime = 60 * 60 * 5, brokenResetTime = 60 * 120;
    private final static int timerAnnouncement = 2,
            timerBrokenReset = 4;
    private final Interval interval = new Interval(10);

    private DBInterface db;

    private final HashMap<String, CustomPlayer> uuidMapping = new HashMap<>();

    private final List<String> recentlyDisconnect = new ArrayList<>();
    private final HashMap<String, String> idMapping = new HashMap<>();

    private final HistoryHandler historyHandler = new HistoryHandler();

    private int announcementIndex = 0;
    private String[] announcements = { "[accent]Join the discord with [purple]/discord[accent]!",
            "[accent]Use [scarlet]/info[accent] to get a description of the game mode!",
            "[accent]Checkout our website at [gold]https://apricotalliance.org" };

    private boolean endNext = false;

    private VoteBan voteBan;

    private boolean codeRed = false;

    String js(String script) {
        return mods.getScripts().runConsole(script);
    }

    // register event handlers and create variables in the constructor
    public void init(DBInterface db) {
        this.db = db;

        voteBan = new VoteBan(uuidMapping, db);

        netServer.admins.addActionFilter((action) -> {
            if (codeRed)
                return false;

            CustomPlayer cPly = uuidMapping.get(action.player.uuid());
            if (cPly.sus)
                return false;

            if (Objects.equals(action.player.uuid(), voteBan.uuidTrial) && voteBan.currentVoteBan)
                return false;

            return true;
        });

        netServer.admins.addChatFilter((player, message) -> {
            for (String swear : StringHandler.badWords) {
                if (Strings.stripColors(message).toLowerCase().contains(swear)) {
                    message = message.replaceAll("(?i)" + swear, "*");
                }
            }

            return message;
        });

        Events.run(EventType.Trigger.update, () -> {
            // if (interval.get(timerMinute, minuteTime)) {
            // for (Player player : Groups.player) {
            // CustomPlayer cPly = uuidMapping.get(player.uuid());
            // cPly.playTime += 1;
            // try {
            // if (cPly.hudEnabled)
            // Call.infoPopup(player.con,
            // "[accent]Play time: [scarlet]" + cPly.playTime + "[accent] mins.",
            // 60, 10, 90, 0, 100, 0);
            // } catch (Exception e) {
            // Log.err("Play time error, player object: " + player + "\n" +
            // "uuidMapping of object: " + uuidMapping.get(player.uuid()) + "\n" +
            // "Error: " + e);
            // }
            // }
            // }

            if (interval.get(timerAnnouncement, announcementTime)) {
                Call.sendMessage("[accent]" + "-".repeat(15) + "[gold] ANNOUNCEMENT [accent]" + "-".repeat(15) + "\n" +
                        announcements[announcementIndex]);
                announcementIndex = (announcementIndex + 1) % announcements.length;
            }

            if (interval.get(timerBrokenReset, brokenResetTime)) {
                for (CustomPlayer cPly : uuidMapping.values()) {
                    cPly.resetBroken();
                }
            }
        });

        Events.on(EventType.PlayerConnect.class, event -> {

            for (String swear : StringHandler.badNames) {
                if (Strings.stripColors(event.player.name.toLowerCase()).contains(swear)) {
                    event.player.name = event.player.name.replaceAll("(?i)" + swear, "*");
                }
            }

            // Replace all spaces in a players name with fancy spaces so the votekick tab
            // menu works
            event.player.name = event.player.name.replaceAll("\\s", "\u00A0");

            String ip = netServer.admins.getInfo(event.player.uuid()).lastIP;
            String uuid = event.player.uuid();
            HashMap<String, Object> entries = null;
            if (db.hasRow("bans", "uuid", uuid)) {
                entries = db.loadRow("bans", "uuid", uuid);
            } else if (db.hasRow("bans", "ip", ip)) {
                entries = db.loadRow("bans", "ip", ip);
            }

            if (entries != null) {
                int banPeriod = (int) entries.get("banPeriod");
                if (banPeriod > Instant.now().getEpochSecond()) {
                    event.player.con.kick("[accent]You are banned for another [scarlet]" +
                            (banPeriod - Instant.now().getEpochSecond()) / 60 + "[accent] minutes.\n" +
                            "Reason: [white]" + entries.get("banReason"));
                    return;
                }
            }

        });

        Events.on(EventType.PlayerJoin.class, event -> {
            // Check if uuid already exists
            AtomicInteger count = new AtomicInteger();
            Groups.player.each(player -> {
                if (player.uuid().equals(event.player.uuid()) ||
                        Strings.stripColors(player.name).equalsIgnoreCase(Strings.stripColors(event.player.name())))
                    count.getAndIncrement();
            });

            if (count.get() > 1) {
                event.player.con.kick("[accent]Player with your name is already in this server!", 0);
                return;
            }

            // Databasing stuff first:
            if (!db.hasRow("mindustry_data", "uuid", event.player.uuid())) {
                Log.info("New player, adding to network tables...");
                db.addEmptyRow("mindustry_data", "uuid", event.player.uuid());
            }

            if (!uuidMapping.containsKey(event.player.uuid())) {
                uuidMapping.put(event.player.uuid(), new CustomPlayer(event.player));
            }

            CustomPlayer cPly = uuidMapping.get(event.player.uuid());
            cPly.player = event.player;
            cPly.connected = true;

            idMapping.put(String.valueOf(event.player.id), event.player.uuid()); // For bans

            int adminRank = 0;

            HashMap<String, Object> minEntries = db.loadRow("mindustry_data", "uuid", event.player.uuid());
            adminRank = (int) minEntries.get("adminRank");
            if (adminRank != 0) {
                event.player.admin = true;
                Call.sendMessage("[royal]" + Strings.stripColors(event.player.name) + "[accent] has joined the game");
            }

            // Save name to database

            db.saveRow("mindustry_data", "uuid", event.player.uuid(), "latestName", event.player.name);

            cPly.playTime = (int) minEntries.get("playTime");

            // if (cPly.hudEnabled)
            // Call.infoPopup(event.player.con, "[accent]Play time: [scarlet]" +
            // cPly.playTime + "[accent] mins.",
            // 55, 10, 90, 0, 100, 0);
        });

        Events.on(EventType.PlayerLeave.class, event -> {
            uuidMapping.get(event.player.uuid()).connected = false;
            String s = "[scarlet]" + event.player.id + "[accent]: [white]" + event.player.name;
            recentlyDisconnect.add(s);
            Time.runTask(60 * 60 * 5, () -> {
                recentlyDisconnect.remove(s);
            });
        });

        Events.on(EventType.BlockBuildEndEvent.class, event -> {
            if (event.unit.getPlayer() == null) {
                return;
            }
            try {
                Seq<Tile> tiles = event.tile.getLinkedTiles(new Seq<>());
                for (Tile t : tiles) {
                    historyHandler.addEntry(t.x, t.y,
                            (event.breaking ? "[red] - " : "[green] + ") + event.unit.getPlayer().name + "[accent]:" +
                                    (event.breaking ? "[scarlet] broke [accent]this tile"
                                            : "[lime] placed [accent]" +
                                                    event.tile.block().name));
                }

                if (event.breaking) {
                    uuidMapping.get(event.unit.getPlayer().uuid()).addBroken();
                }
            } catch (NullPointerException ignored) {
            }

        });
        Events.on(EventType.ConfigEvent.class, event -> {
            if (event.player == null || event.tile == null)
                return;
            Seq<Tile> tiles = event.tile.tile.getLinkedTiles(new Seq<>());
            if (event.tile.block() instanceof PowerNode) {
                for (Tile t : tiles) {
                    try {
                        historyHandler.addEntry(t.x, t.y,
                                "[orange] ~ [accent]" + event.player.name + "[accent]:"
                                        + (!(event.value instanceof Point2)
                                                && !event.tile.power.links.contains((int) event.value)
                                                        ? "[scarlet] disconnected"
                                                        : "[lime] connected" + "[accent] this tile"));
                    } catch (ClassCastException e) {
                        historyHandler.addEntry(t.x, t.y,
                                "[orange] ~ [accent]" + event.player.name
                                        + "[accent]:[purple] did strange things[accent] to this tile (pls tell me what caused this)");
                    }
                }
            } else {
                for (Tile t : tiles) {
                    historyHandler.addEntry(t.x, t.y,
                            "[orange] ~ [accent]" + event.player.name + "[accent]:" +
                                    " changed config"
                                    + (event.value == null ? " to default" : " to " + event.value));
                }
            }

        });

        Events.on(EventType.TapEvent.class, event -> {
            if (uuidMapping.get(event.player.uuid()).historyMode) {
                event.player.sendMessage(displayHistory(event.tile.x, event.tile.y));
            }
        });

        Events.on(EventType.UnitDestroyEvent.class, event -> {
            if (event.unit.getPlayer() != null) {
                Player player = event.unit.getPlayer();
                CustomPlayer cPly = uuidMapping.get(player.uuid());
                if (player.admin()) {
                    Call.effectReliable(Fx.airBubble, player.x, player.y, 0, Color.white);
                } else if (cPly.playTime > 1000) {
                    Call.effectReliable(Fx.heal, player.x, player.y, 0, Color.white);
                }
            }
        });
        Events.on(Endgame.class, event -> {
            if (Groups.player.isEmpty()) {
                Log.info("No players - restarting server");
                System.exit(2);
            }
            historyHandler.clear();
            uuidMapping.keySet().removeIf(uuid -> !uuidMapping.get(uuid).connected);
            if (endNext) {
                endNext = false;
                Call.infoMessage("[accent]Server is restarting! Join back after a few seconds...");
                netServer.kickAll(Packets.KickReason.serverRestarting);
                Log.info("Game ended successfully.");
                Time.runTask(serverCloseTime, () -> System.exit(2));
            }

        });
    }

    // register commands that run on the server
    public void register(CommandHandler handler) {
        handler.register("setadmin", "<uuid> <rank>", "Set the admin rank of a player", args -> {
            int newRank;
            try {
                newRank = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Log.info("Invalid rank input '" + args[1] + "'");
                return;
            }
            if (!db.hasRow("mindustry_data", "uuid", args[0])) {
                Log.info("Invalid uuid: " + args[0]);
                return;
            }

            db.saveRow("mindustry_data", "uuid", args[0], "adminRank", newRank);

            Log.info("Set uuid " + args[0] + " to have adminRank of " + args[1]);

        });

        handler.register("setplaytime", "<uuid> <playtime>", "Set the play time of a player", args -> {
            int newTime;
            try {
                newTime = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                Log.info("Invalid playtime input '" + args[1] + "'");
                return;
            }
            if (!db.hasRow("mindustry_data", "uuid", args[0])) {
                Log.info("Invalid uuid: " + args[0]);
                return;
            }

            CustomPlayer cPly = uuidMapping.get(args[0]);

            db.saveRow("mindustry_data", "uuid", args[0], "playTime", newTime);

            if (!(cPly.player == null)) {
                cPly.playTime = newTime;
                Call.setHudTextReliable(cPly.player.con,
                        "[accent]Play time: [scarlet]" + cPly.playTime + "[accent] mins.");
            }
            Log.info("Set uuid " + args[0] + " to have play time of " + args[1] + " minutes");

        });

        handler.register("endnextgame", "Ends the game after this round is over", args -> {
            Call.sendMessage("[scarlet]Server [accent]has called for, [scarlet]AFTER THIS GAME[accent], the server" +
                    " to restart! You can reconnect once it has finished restarting");
            Log.info("kill after next game.");
            endNext = true;
        });

        handler.removeCommand("status");
        handler.register("status", "Server status", _arg -> {
            Log.info("@ TPS / @ MB / @ PLAYERS", Core.graphics.getFramesPerSecond(),
                    Core.app.getJavaHeap() / 1024 / 1024, Groups.player.size());
        });

        handler.removeCommand("maps");
        handler.register("maps", "Lists maps with index(0: name)", _args -> {
            StringBuilder s = new StringBuilder();
            int i = 0;
            for (mindustry.maps.Map map : maps.customMaps()) {
                s.append(i + ":" + map.name() + "\n");
                i += 1;
            }
            Log.info(s.toString());
        });

        handler.removeCommand("players");
        handler.register("players", "List all players currently in game.", arg -> {
            if (Groups.player.size() == 0) {
                Log.info("No players are currently in the server.");
            } else {
                StringBuilder s = new StringBuilder();
                for (Player user : Groups.player) {
                    PlayerInfo userInfo = user.getInfo();
                    s.append(userInfo.admin ? "[A]" : "[P]");
                    s.append(' ');
                    s.append(userInfo.plainLastName());
                    s.append('/');
                    s.append(userInfo.id);
                    s.append('/');
                    s.append(userInfo.lastIP);
                    s.append('\n');
                }
                Log.info(s.toString());
            }
        });
    }

    // register commands that player can invoke in-game
    public void player_register(CommandHandler handler) {

        handler.<Player>register("hub", "Connect to the AA hub server", (args, player) -> {
            net.pingHost("recessive.net", 6567, host -> {
                Call.connect(player.con, "recessive.net", 6567);
            }, e -> {
                player.sendMessage("[accent]Server offline");
            });

        });

        handler.<Player>register("plague", "Connect to the Plague server", (args, player) -> {
            net.pingHost("recessive.net", 6571, host -> {
                Call.connect(player.con, "recessive.net", 6571);
            }, e -> {
                player.sendMessage("[accent]Server offline");
            });
        });

        Function<String[], Consumer<Player>> historyCommand = args -> player -> {
            CustomPlayer cPly = uuidMapping.get(player.uuid());
            if (cPly.historyMode) {
                cPly.historyMode = false;
                player.sendMessage("[accent]History mode disabled");
            } else {
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
            for (String discon : recentlyDisconnect) {
                s += "[accent]ID: " + discon + '\n';
            }
            if (s.equals("")) {
                player.sendMessage("[accent]No recent disconnects!");
            } else {
                player.sendMessage(s);
            }
        };

        handler.<Player>register("recentdc", "Show a list of recent disconnects", (args, player) -> {
            rcdCommand.apply(args).accept(player);
        });

        handler.<Player>register("rdc", "Alias for recentdc", (args, player) -> {
            rcdCommand.apply(args).accept(player);
        });

        handler.<Player>register("vote", "<y/n>", "Vote on a current ban vote", (args, player) -> {
            player.sendMessage(voteBan.vote(player.uuid(), args[0].equals("y")));
        });

        handler.<Player>register("votekick", "[id/name] [minutes] [reason...]",
                "Start a vote ban for a player id, or immediately ban if admin", (args, player) -> {
                    CustomPlayer cPly = uuidMapping.get(player.uuid());
                    if (cPly.playTime < 30) {
                        player.sendMessage("You must have at least 30 minutes of playtime to start a votekick!");
                        return;
                    }

                    if (args.length == 0) {
                        player.sendMessage(voteBan.getSyntax());
                        return;
                    }

                    String reason = "None given";
                    Player found = find(args[0], player);

                    if (found == null) {
                        player.sendMessage("[accent]No player with name or id: [white]" + args[0] + "[accent] found!");
                        return;
                    }

                    // Try and get reason

                    if (args.length > 2) {
                        String[] newArray = Arrays.copyOfRange(args, 2, args.length);
                        reason = String.join(" ", newArray);
                    }

                    player.sendMessage(voteBan.startVoteBan(found.uuid(), args[1], reason, player.uuid()));

                });

        handler.<Player>register("historyhere", "Display history for the tile you're positioned over",
                (args, player) -> {
                    player.sendMessage(displayHistory(player.tileX(), player.tileY()));
                });

        handler.<Player>register("hh", "Alias for historyhere", (args, player) -> {
            player.sendMessage(displayHistory(player.tileX(), player.tileY()));
        });

        handler.<Player>register("discord", "Prints the discord link", (args, player) -> {
            player.sendMessage("[purple]https://discord.gg/GEnYcSv");
        });

        handler.<Player>register("website", "Prints website link", (args, player) -> {
            player.sendMessage("[gold]http://apricotalliance.org");
        });

        handler.<Player>register("uuid", "Prints your uuid", (args, player) -> {
            player.sendMessage("[scarlet]" + player.uuid());
        });

        handler.<Player>register("hud", "Toggle HUD (display/hide playtime and other info)", (args, player) -> {

            CustomPlayer cPly = uuidMapping.get(player.uuid());
            player.sendMessage(
                    "[accent]Hud " + (cPly.hudEnabled ? "[scarlet]disabled. [accent]Hud will clear in 1 minute"
                            : "[green]enabled. [accent]Playtime will show in one minute"));
            cPly.hudEnabled = !cPly.hudEnabled;

            Events.fire(new HudToggle(player.uuid(), cPly.hudEnabled));

            db.saveRow("mindustry_data", "uuid", player.uuid(), "hudOn", cPly.hudEnabled);
        });

        handler.<Player>register("kill", "[sky]Destroy yourself", (args, player) -> {
            player.unit().kill();
        });

        handler.<Player>register("tp", "[player/id]", "[sky]Teleport to player", (args, player) -> {
            if (args.length == 0) {
                String s = "[accent]Use [orange]/tp [player/id][accent] to teleport to a players location.\n";
                s += "You are able to tp to the following players:";
                for (Player ply : Groups.player) {
                    s += "\n[accent]Name: [white]" + ply.name + "[accent], ID: [white]" + ply.id;
                }
                player.sendMessage(s);
                return;
            }

            Player other = find(args[0], player);
            try {
                other = Groups.player.getByID(Integer.parseInt(args[0]));
            } catch (NumberFormatException e) {
                other = null;
            }

            if (other == null) {
                other = Groups.player.find(p -> p.name.equalsIgnoreCase(args[0]));
                if (other == null) {
                    String s = "[accent]No player by name [white]" + args[0] + "[accent] or id [white]" + args[0]
                            + "[accent].\n";
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
            if (!player.admin) {
                player.sendMessage("[accent]Admin only!");
                return;
            }
            int page;
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
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
                if (size == 0) {
                    player.sendMessage("[accent]No active bans!");
                    rs.close();
                    return;
                }
                if (page < 1 || page > size / 5 + 1) {
                    player.sendMessage(
                            "[accent]Page must be between [scarlet]1[accent] and [scarlet]" + (size / 5 + 1));
                    rs.close();
                    return;
                }
                s += (size / 5 + 1) + "[accent])";
                int i = -1;
                while (rs.next()) {
                    i++;
                    if (i / 5 + 1 == page) {
                        // First two should be ip then uuid, hence start at 3
                        s += "\n[gold] " + (i + 1) + "[white]: " + rs.getString(3) + "[accent], ban time: [scarlet]"
                                + (rs.getInt(4) - Instant.now().getEpochSecond()) / 60
                                + "[accent] minutes, reason: [sky]"
                                + rs.getString(5);
                    } // Inefficient, should end here once out of range (> page)
                }
                rs.close();
            } catch (SQLException e) {
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
            if (!player.admin) {
                player.sendMessage("[accent]Admin only!");
                return;
            }

            int id;
            try {
                id = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
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
                if (size == 0) {
                    player.sendMessage("[accent]No active bans!");
                    rs.close();
                    return;
                }
                if (id < 1 || id > size) {
                    player.sendMessage("id must be between [scarlet]1[accent] and [scarlet]" + size);
                    rs.close();
                    return;
                }

                int i = 0;
                while (rs.next()) {
                    i++;
                    if (i == id) {
                        String ip = rs.getString(1);
                        String uuid = rs.getString(2);

                        HashMap<String, Object> entries = db.loadRow("bans", new String[] { "ip", "uuid" },
                                new Object[] { ip, uuid });

                        db.saveRow("bans", new String[] { "ip", "uuid" }, new Object[] { ip, uuid },
                                new String[] { "banPeriod", "banReason" },
                                new Object[] { Instant.now().getEpochSecond(), "Unbanned by: " + player.name });

                        player.sendMessage("[accent]ID: [white]" + id + " [accent]([white]" +
                                entries.get("bannedName") + "[accent]) unbanned.");
                        rs.close();
                        return;
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                player.sendMessage("Invalid SQL");
                return;
            }

        };

        handler.<Player>register("unban", "<id>", "[scarlet]Unban an id. Check ids with /bans (admin only)",
                (args, player) -> {
                    unbanCommand.apply(args).accept(player);
                });

        handler.<Player>register("cr", "[scarlet]Code red, blocks all actions for 30 seconds", (args, player) -> {
            if (!player.admin) {
                player.sendMessage("[accent]Admin only!");
                return;
            }
            if (codeRed) {
                Call.sendMessage("[scarlet]Code red over");
                codeRed = false;
                return;
            }
            codeRed = true;
            Call.sendMessage(
                    player.name + " [scarlet]has called a code red! All actions are blocked for the next 30 seconds");
            Time.runTask(60 * 30, () -> {
                if (codeRed) {
                    codeRed = false;
                    Call.sendMessage("[scarlet]Code red over");
                }
            });

        });

        handler.<Player>register("js", "<script...>", "Run arbitrary javascript(admin only)", (arg, player) -> {
            if (!player.admin) {
                player.sendMessage("[scarlet]Not admin!");
                return;
            }
            String result = js(arg[0]);
            player.sendMessage(result);
            Log.info(result);
        });
    }

    String displayHistory(int x, int y) {
        List<Tuple<Long, String>> history = historyHandler.getHistory(x, y);
        if (history != null) {
            String s = "[accent]History for tile ([scarlet]" + x + "[accent],[scarlet]" + y + "[accent]):";
            for (Tuple<Long, String> entry : history) {
                Long time = (Instant.now().getEpochSecond() - (Long) entry.get(0));
                s += "\n" + entry.get(1) +
                        " [scarlet]" + time + "[accent] second" +
                        (time != 1 ? "s" : "") + " ago.";
            }
            return s;
        } else {
            return "[accent]No history for tile ([scarlet]" + x + "[accent],[scarlet]" + y + "[accent])";
        }
    }
}
