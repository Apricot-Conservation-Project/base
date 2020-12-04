package main;

public class StringHandler {

    public static final String[] badNames = {"shit", "fuck", "asshole", "cunt", "nigger", "nigga", "niga", "faggot", "dick", "bitch", "admin", "mod", "mindustry", "server", "owner", "<", ">", "recessive", "nazi"};


    public final String donatorMessagePrefix(int donatorLevel){
        if(donatorLevel == 1){
            return "[#4d004d]{[sky]Donator[#4d004d]}[white]";
        }else if(donatorLevel == 2){
            return "[#4d004d]{[sky]Donator[gold]+[#4d004d]}[white]";
        }
        return "";
    }

}
