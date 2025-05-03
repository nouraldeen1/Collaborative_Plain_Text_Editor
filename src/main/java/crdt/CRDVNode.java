package crdt;

public class CRDVNode {
    private char character;
    private String identifier;
    private boolean deleted;

    public CRDVNode(char character, String identifier) {
        this.character = character;
        this.identifier = identifier;
        this.deleted = false;
    }

    public char getCharacter() {
        return character;
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
}