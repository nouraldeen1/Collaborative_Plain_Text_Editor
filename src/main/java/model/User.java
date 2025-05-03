package model;

import java.awt.Color;

public class User {
    private String userId;  // Changed from userID to userId for consistency
    private Color color;
    private boolean isEditor;
    private int cursorPosition;

    public User() {
    }

    public User(String userId, Color color, boolean isEditor) {
        this.userId = userId;
        this.color = color;
        this.isEditor = isEditor;
        this.cursorPosition = 0;
    }

    public String getUserId() {  // Changed from getUserID to getUserId
        return userId;
    }
 
    public void setUserId(String userId) {  // Changed from setUserID to setUserId
        this.userId = userId;
    }

    public Color getColor() {
        return color;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public boolean isEditor() {
        return isEditor;
    }

    public void setEditor(boolean editor) {
        isEditor = editor;
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    public void setCursorPosition(int cursorPosition) {
        this.cursorPosition = cursorPosition;
    }
}