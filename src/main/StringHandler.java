package main;

public class StringHandler {

    public static final String[] badNames = {"apricot", "aprico t", "shit", "fuck", "asshole", "cunt", "nigger", "nigga", "niga", "faggot", "dick", "bitch", "admin", "mod", "mindustry", "server", "owner", "<", ">", "\\]", "\\[", "nazi"};

    public static final String[] badWords = {"apricot", "aprico t", "cunt", "nigger", "nigga", "niga", "faggot"};

    public final String donatorMessagePrefix(int dLevel){
        return dLevel > 0 ? "[purple]{[yellow]Donator[purple]}[white] " : "";
    }

}
