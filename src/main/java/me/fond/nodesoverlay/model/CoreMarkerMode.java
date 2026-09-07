package me.fond.nodesoverlay.model;

public enum CoreMarkerMode {
    ALWAYS("Always"),
    ZOOMED_IN("Zoomed in"),
    ATTACKED_ONLY("Attacked only"),
    DISABLED("Disabled");

    private final String label;

    CoreMarkerMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
