package base;

import arc.math.Mathf;
import arc.util.Log;
import arc.util.Time;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static mindustry.Vars.netServer;

public class VoteBan {
    public boolean currentVoteBan = false;
    public int votes = 0;
    public int requiredVotes = 0;
    public String uuidTrial;
    public long minutesTrial;
    public String reasonTrial;
    public List<String> voted;

    private static final int decade = 5259600;
    private final HashMap<String, CustomPlayer> uuidMapping;
    private DBInterface db;

    private String votekickSyntax = "\n\n[accent]Syntax: [scarlet]/votekick [green][uuid/id] [blue][minutes] [orange][reason...]\n"
            +
            "[accent]EXAMPLE: [scarlet]/votekick [green]example_id [blue]60 [orange]They have been griefing";
    private String voteSyntax = "[accent]Type [orange]/vote <y/n>[accent] to vote.";

    public VoteBan(HashMap<String, CustomPlayer> uuidMapping, DBInterface db) {
        this.uuidMapping = uuidMapping;
        this.db = db;

        netServer.admins.addActionFilter((action) -> {
            // Prevent votekick users from doing anything
            return !(currentVoteBan && action.player != null && action.player.uuid().equals(uuidTrial));
        });
    }

    private static final Pattern periodPattern = Pattern.compile("([0-9]+)([mhd])");

    static int parseTime(String time) throws NumberFormatException {
        try {
            time = time.toLowerCase(Locale.ENGLISH);
            var matcher = periodPattern.matcher(time);
            Duration then = Duration.ZERO;
            while (matcher.find()) {
                int num = Integer.parseInt(matcher.group(1));
                String typ = matcher.group(2);
                switch (typ) {
                    case "m":
                        then = then.plus(Duration.ofMinutes(num));
                        break;
                    case "h":
                        then = then.plus(Duration.ofHours(num));
                        break;
                    case "d":
                        then = then.plus(Duration.ofDays(num));
                        break;
                }
            }
            return Mathf.clamp((int) then.toMinutes(), 0, decade);
        } catch (DateTimeParseException _e) {
            return Mathf.clamp(Integer.parseInt(time), 0, decade);
        }
    }

    public String getSyntax() {
        String s = "[accent]You can vote on the following players: ";
        for (Player ply : Groups.player) {
            if (ply.admin) {
                continue;
            }
            s += "\n[gold] - [accent]ID: [scarlet]" + ply.id + "[accent]: [white]" + ply.plainName();
        }
        s += "\n[accent]Check [scarlet]/recentdc [accent]as well";
        return s + votekickSyntax;
    }

    public String vote(String uuid, boolean votedYes) {
        if (!currentVoteBan) {
            return "[accent]There is no active vote!\n\n" + voteSyntax;
        }

        if (voted.contains(uuid)) {
            return "[accent]You have already voted!";
        }

        voted.add(uuid);
        String s = uuidMapping.get(uuid).player.name + "[accent] voted ";
        if (votedYes) {
            votes++;
        } else {
            votes--;
            s += "[scarlet] NOT [accent]";
        }
        s += "to ban [white]" + uuidMapping.get(uuidTrial).player.name +
                " [accent]([scarlet]" + votes + "[accent]/[scarlet]" + requiredVotes + "[accent])";
        Call.sendMessage(s);
        return "";

    }

    public void newBan(String uuid, String ip, int minutes, String reason, Player voterPly) {
        Player p = uuidMapping.get(uuid).player;
        String name = p.plainName();

        if (minutes == 0) {
            String msg = " .\nReason: [white]"
                    + reason;
            if (voterPly.admin) {
                msg = voterPly.name + "[accent] has kicked " + name + msg;
            } else {
                msg = "[accent]Vote passed. [white]" + name + "[accent] has been kicked " + name + msg;
            }
            Call.sendMessage(msg);
            p.kick("[accent]Kicked. Reason: " + reason);

            return;
        }
        int timeLength = (int) (minutes * 60 + Instant.now().getEpochSecond());

        if (!db.hasRow("bans", new String[] { "ip", "uuid" }, new Object[] { ip, uuid })) {
            db.addEmptyRow("bans", new String[] { "ip", "uuid" }, new Object[] { ip, uuid });
        }

        String keys[] = new String[] { "bannedName", "banPeriod", "banReason" };
        Object vals[] = new Object[] { name, timeLength, reason };
        db.saveRow("bans", new String[] { "ip", "uuid" }, new Object[] { ip, uuid }, keys, vals);
        String msg = " for [scarlet]" + Base.formatTime(Duration.ofMinutes(minutes)) + "[accent]\nReason: [white]"
                + reason;
        if (voterPly.admin) {
            msg = voterPly.name + "[accent] has banned " + name + msg;
        } else {
            msg = "[accent]Vote passed. [white]" + name + "[accent] has been banned " + name + msg;
        }
        Call.sendMessage(msg);

        uuidMapping.get(uuid).player.con.kick("[accent]You are banned for another [scarlet]" +
                Base.formatTime(Duration.ofMinutes(minutes)) + "[accent].\nReason: [white]" + reason);
    }

    public String startVoteBan(String uuid, String length, String reason, String voter) {
        int min;
        try {
            min = parseTime(length);
        } catch (NumberFormatException e) {
            Log.err(e);
            min = 60;
        }
        return startVoteBan(uuid, min, reason, voter);
    }

    public String startVoteBan(String uuid, int minutes, String reason, String voter) {
        // Assumes:
        // uuid is in uuidMapping
        // voter is in uuidMapping
        CustomPlayer cPly = uuidMapping.get(voter);
        Player voterPly = cPly.player;
        String ip = netServer.admins.getInfo(uuid).lastIP;

        if (currentVoteBan && !voterPly.admin()) {
            return "[accent]There is already a vote in progress" + votekickSyntax;
        }

        if (uuid.equals(voter)) {
            return "[accent]Cannot start a vote against yourself!";
        }
        if (!voterPly.admin() && Instant.now().getEpochSecond() - uuidMapping.get(voter).lastvoteBan < 60) {
            return "[accent]You can only vote to ban someone every minute" + votekickSyntax;
        }

        // Check if minutes is valid time length
        if (!voterPly.admin) {
            int max = (int) (cPly.playTime * 0.25) + 60;
            if (minutes > max) {
                return String.format("[accent]Max ban time for your rank is %s[accent]",
                        Base.formatTime(Duration.ofMinutes(max)))
                        + votekickSyntax;
            }
        }

        // Check if uuid is admin
        if (!voterPly.admin && uuidMapping.get(uuid).player.admin) {
            return "[accent]Cannot ban an admin!";
        }

        if (voterPly.admin) {
            newBan(uuid, ip, minutes, reason, voterPly);
            return "\n[accent]Banned successfully.\n";
        }

        uuidTrial = uuid;
        minutesTrial = minutes;
        reasonTrial = reason;
        currentVoteBan = true;
        votes = 0;
        voted = new ArrayList<String>(Arrays.asList(uuid));
        requiredVotes = Math.max(Groups.player.size() / 5, 2);

        Call.sendMessage(uuidMapping.get(voter).player.name + "[accent] Has started a vote ban against [white]" +
                uuidMapping.get(uuid).player.name + "[accent] to ban for [scarlet]"
                + Base.formatTime(Duration.ofMinutes(minutes))
                + "[accent]" +
                "[accent]([scarlet]0[accent]/[scarlet]" + requiredVotes + "[accent])" +
                "\n[accent]Reason:[white] " + reason +
                "\nType [orange]/vote <y/n>[accent] to vote.");

        Time.runTask(60 * 45, () -> {
            if (votes >= requiredVotes) {
                newBan(uuid, ip, minutes, reason, voterPly);
            } else {
                Call.sendMessage("[accent]Vote failed. Not enough votes.");
            }
            currentVoteBan = false;
        });

        uuidMapping.get(voterPly.uuid()).lastvoteBan = (int) Instant.now().getEpochSecond();

        return "\n[green]Vote started successfully";

    }

}
