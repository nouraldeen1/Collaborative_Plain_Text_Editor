package model;

public class CRDTNode {
    private char value;          // The character stored in this node
    private String identifier;   // Unique identifier (e.g., userID:timestamp:position)
    private boolean isDeleted;   // Tombstone for soft deletion

    public CRDTNode(char value, String identifier) {
        this.value = value;
        this.identifier = identifier;
        this.isDeleted = false;
    }

    // Getters and setters
    public char getValue() {
        return value;
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        this.isDeleted = deleted;
    }

    @Override
    public String toString() {
        return "CRDTNode{" +
               "value=" + value +
               ", identifier='" + identifier + '\'' +
               ", isDeleted=" + isDeleted +
               '}';
    }
}