package main;

import arc.util.Strings;
import arc.util.Time;
import mindustry.gen.Call;
import mindustry.gen.Groups;
import mindustry.gen.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static mindustry.Vars.netServer;

public class VoteBan {
    public boolean currentVoteBan = false;
    public int votes = 0;
    public int requiredVotes = 0;
    public String uuidTrial;
    public int minutesTrial;
    public String reasonTrial;
    public List<String> voted;

    private final HashMap<String, CustomPlayer> uuidMapping;
    private DBInterface db;

    private String votekickSyntax = "\n\n[accent]Syntax: [scarlet]/votekick [green][uuid/id] [blue][minutes] [orange][reason...]\n" +
            "[accent]EXAMPLE: [scarlet]/votekick [green]example_id [blue]60 [orange]They have been griefing";
    private String voteSyntax = "[accent]Type [orange]/vote <y/n>[accent] to vote.";

    public VoteBan(HashMap<String, CustomPlayer> uuidMapping, DBInterface db){
        this.uuidMapping = uuidMapping;
        this.db = db;

        netServer.admins.addActionFilter((action) -> {
            // Prevent votekick users from doing anything
            return !(currentVoteBan && action.player != null && action.player.uuid().equals(uuidTrial));
        });
    }

    public String getSyntax(){
        String s = "[accent]You can vote on the following players: ";
        for(Player ply : Groups.player){
            if(ply.admin){
                continue;
            }
            CustomPlayer cPly = uuidMapping.get(ply.uuid());
            if(cPly == null){
                Call.sendMessage("Tell recessive to fix this");
            }
            s += "\n[gold] - [accent]ID: [scarlet]" + ply.id + "[accent]: [white]" + Strings.stripColors(cPly.rawName);
        }
        s += "\n[accent]Check [scarlet]/recentdc [accent]as well";
        return s + votekickSyntax;
    }

    public String vote(String uuid, boolean votedYes){
        if(!currentVoteBan){
            return "[accent]There is no active vote!\n\n" + voteSyntax;
        }

        if(voted.contains(uuid)){
            return "[accent]You have already voted!";
        }

        voted.add(uuid);
        String s = uuidMapping.get(uuid).player.name + "[accent] voted ";
        if(votedYes){
            votes++;
        }else{
            votes--;
            s += "[scarlet] NOT [accent]";
        }
        s += "to ban [white]" + uuidMapping.get(uuidTrial).player.name +
        " [accent]([scarlet]" + votes + "[accent]/[scarlet]" + requiredVotes + "[accent])";
        Call.sendMessage(s);
        return "";

    }

    public void newBan(String uuid, String ip, int minutes, String reason, Player voterPly){
        int timeLength = (int) (minutes * 60 + Instant.now().getEpochSecond());

        if(!db.hasRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid})){
            db.addEmptyRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid});
        }
        String name = uuidMapping.get(uuid).player.name;

        String keys[] = new String[]{"bannedName", "banPeriod", "banReason"};
        Object vals[] = new Object[]{name, timeLength, reason};
        db.saveRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid}, keys, vals);

        String banMessage = " for [scarlet]" + minutes + "[accent] minutes\nReason: [white]" + reason;
        if(voterPly.admin){
            banMessage = voterPly.name + "[accent] has banned " + name + banMessage;
        }else{
            banMessage = "[accent]Vote passed. [white]" + name + "[accent] has been banned " + name + banMessage;
        }
        Call.sendMessage(banMessage);


        uuidMapping.get(uuid).player.con.kick("[accent]You are banned for another [scarlet]" +
                minutes + "[accent] minutes.\nReason: [white]" + reason);
        uuidMapping.remove(uuid);
    }

    public String startVoteBan(String uuid, int minutes, String reason, String voter){
        // Assumes:
        //  uuid is in uuidMapping
        //  minutes is above 0
        //
        //  voter is in uuidMapping

        Player voterPly = uuidMapping.get(voter).player;
        String ip = netServer.admins.getInfo(uuid).lastIP;

        if(currentVoteBan && !voterPly.admin()){
            return "[accent]There is already a vote in progress" + votekickSyntax;
        }

        if(uuid.equals(voter)){
            return "[accent]Cannot start a vote against yourself!";
        }

        if(!voterPly.admin() && Instant.now().getEpochSecond() - uuidMapping.get(voter).lastvoteBan < 60 * 3){
            return "[accent]You can only vote to ban someone every 3 minutes" + votekickSyntax;
        }

        // Check if minutes is valid time length
        if(minutes > 60 && voterPly.donatorLevel == 0 && !voterPly.admin){
            return "[accent]Max ban time for your rank is [scarlet]60 [accent]minutes" + votekickSyntax;
        }

        if(minutes > 60 * 5 && !voterPly.admin){
            return "[accent]Max ban time for your rank is [scarlet]300 [accent]minutes" + votekickSyntax;
        }

        // Check if uuid is already banned
        if(!voterPly.admin && db.hasRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid})){
            HashMap<String, Object> entries = db.loadRow("bans", new String[]{"ip", "uuid"}, new Object[]{ip, uuid});
            if((int) entries.get("banPeriod") > Instant.now().getEpochSecond()){
                return "[accent]Player is already banned!" + votekickSyntax;
            }
        }


        if(voterPly.admin){
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
                uuidMapping.get(uuid).player.name + "[accent] to ban for [scarlet]" + minutes + "[accent] minutes " +
                "[accent]([scarlet]0[accent]/[scarlet]" + requiredVotes + "[accent])" +
                "\n[accent]Reason:[white] " + reason +
                "\nType [orange]/vote <y/n>[accent] to vote.");


        Time.runTask(60 * 45, () -> {
            if(votes >= requiredVotes){
                newBan(uuid, ip, minutes, reason, voterPly);
            }else{
                Call.sendMessage("[accent]Vote failed. Not enough votes.");
            }
            currentVoteBan = false;
        });

        uuidMapping.get(voterPly.uuid()).lastvoteBan = (int) Instant.now().getEpochSecond();

        return "\n[green]Vote started successfully";

    }


}
