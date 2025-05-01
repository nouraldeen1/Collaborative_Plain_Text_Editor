package client;

public class ClientMessage {
    public String type;
    public String code;
    public String userID;
    public String data;
    public boolean isEditor;

    public ClientMessage(String type, String code, String userID, String data, boolean isEditor) {
        this.type = type;
        this.code = code;
        this.userID = userID;
        this.data = data;
        this.isEditor = isEditor;
    }
}