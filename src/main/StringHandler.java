package main;

public class StringHandler {

    public static final String[] badNames = {"apricot", "shit", "fuck", "asshole", "cunt", "nigger", "nigga", "niga", "faggot", "dick", "bitch", "admin", "mod", "mindustry", "server", "owner", "<", ">", "recessive", "nazi"};


    public final String donatorMessagePrefix(int dLevel){
        return dLevel > 0 ? "[yellow]Apric*t}[white]" : "";
    }

}
