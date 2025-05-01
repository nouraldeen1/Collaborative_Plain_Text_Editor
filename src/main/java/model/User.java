package model;

import java.awt.Color;

public class User {
    private String userID;         // Unique user identifier
    private int cursorPosition;    // Current cursor position in the document
    private Color cursorColor;     // Color for cursor display
    private boolean isEditor;      // True for editors, false for viewers

    public User(String userID, Color cursorColor, boolean isEditor) {
        this.userID = userID;
        this.cursorPosition = 0; // Start at beginning of document
        this.cursorColor = cursorColor;
        this.isEditor = isEditor;
    }

    // Getters and setters
    public String getUserID() {
        return userID;
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    public void setCursorPosition(int cursorPosition) {
        this.cursorPosition = cursorPosition;
    }

    public Color getCursorColor() {
        return cursorColor;
    }

    public boolean isEditor() {
        return isEditor;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return userID.equals(user.userID);
    }

    @Override
    public int hashCode() {
        return userID.hashCode();
    }
}