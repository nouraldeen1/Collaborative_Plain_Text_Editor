package model;

public class Operation {
    private String type;       // "insert" or "delete"
    private char character;    // Character for insert operations
    private String identifier; // CRDT node identifier
    private int position;      // Position in the document (for UI rendering)
    private String userID;     // User who performed the operation

    public Operation(String type, char character, String identifier, int position, String userID) {
        this.type = type;
        this.character = character;
        this.identifier = identifier;
        this.position = position;
        this.userID = userID;
    }

    // Getters
    public String getType() {
        return type;
    }

    public char getCharacter() {
        return character;
    }

    public String getIdentifier() {
        return identifier;
    }

    public int getPosition() {
        return position;
    }

    public String getUserID() {
        return userID;
    }

    @Override
    public String toString() {
        return "Operation{" +
               "type='" + type + '\'' +
               ", character=" + character +
               ", identifier='" + identifier + '\'' +
               ", position=" + position +
               ", userID='" + userID + '\'' +
               '}';
    }
}