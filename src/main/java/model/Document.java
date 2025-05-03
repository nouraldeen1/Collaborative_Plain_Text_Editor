package model;

import crdt.CRDVNode;

import java.util.HashMap;
import java.util.Map;

public class Document {
    private String text;
    private String editorCode;
    private String viewerCode;
    private Map<String, CRDVNode> content;  // Added for CRDT

    public Document() {
        this.text = "";
        this.content = new HashMap<>();
    }

    public Document(String text, String editorCode, String viewerCode) {
        this.text = text;
        this.editorCode = editorCode;
        this.viewerCode = viewerCode;
        this.content = new HashMap<>();
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getEditorCode() {
        return editorCode;
    }

    public void setEditorCode(String editorCode) {
        this.editorCode = editorCode;
    }

    public String getViewerCode() {
        return viewerCode;
    }

    public void setViewerCode(String viewerCode) {
        this.viewerCode = viewerCode;
    }

    public Map<String, CRDVNode> getContent() {
        return content;
    }

    public void addNode(CRDVNode node) {
        content.put(node.getIdentifier(), node);
        updateText();
    }

    public void deleteNode(String identifier) {
        CRDVNode node = content.get(identifier);
        if (node != null) {
            node.setDeleted(true);
            updateText();
        }
    }

    public void applyOperation(Operation operation) {
        if ("insert".equals(operation.getType())) {
            text = text.substring(0, operation.getPosition()) + operation.getContent() + text.substring(operation.getPosition());
        } else if ("delete".equals(operation.getType())) {
            int end = operation.getPosition() + operation.getContent().length();
            if (end <= text.length()) {
                text = text.substring(0, operation.getPosition()) + text.substring(end);
            }
        }
    }

    public void updateText() {
        StringBuilder sb = new StringBuilder();
        // Build text from non-deleted nodes
        // This is a simplified version - you would need to sort nodes by position
        for (CRDVNode node : content.values()) {
            if (!node.isDeleted()) {
                sb.append(node.getCharacter());
            }
        }
        text = sb.toString();
    }
}