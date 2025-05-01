package model;

public class Comment {
    private String userID;     // User who added the comment
    private String text;       // Comment content
    private String startID;    // CRDT node ID for the start of the commented text
    private String endID;      // CRDT node ID for the end of the commented text

    public Comment(String userID, String text, String startID, String endID) {
        this.userID = userID;
        this.text = text;
        this.startID = startID;
        this.endID = endID;
    }

    // Getters
    public String getUserID() {
        return userID;
    }

    public String getText() {
        return text;
    }

    public String getStartID() {
        return startID;
    }

    public String getEndID() {
        return endID;
    }

    @Override
    public String toString() {
        return "Comment{" +
               "userID='" + userID + '\'' +
               ", text='" + text + '\'' +
               ", startID='" + startID + '\'' +
               ", endID='" + endID + '\'' +
               '}';
    }
}