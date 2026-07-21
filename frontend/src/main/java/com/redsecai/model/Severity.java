package com.redsecai.model;

/**
 * Finding severity levels, ordered most-to-least severe.
 *
 * <p>Each level carries the CSS style-class used to colour its badge and stat
 * tile, so the rendering layer never hard-codes severity strings.
 */
public enum Severity {
    CRITICAL("Critical", "sev-critical", 0),
    HIGH("High", "sev-high", 1),
    MEDIUM("Medium", "sev-medium", 2),
    LOW("Low", "sev-low", 3),
    INFO("Info", "sev-info", 4),
    UNKNOWN("Unknown", "sev-unknown", 5);

    private final String label;
    private final String styleClass;
    private final int rank;

    Severity(String label, String styleClass, int rank) {
        this.label = label;
        this.styleClass = styleClass;
        this.rank = rank;
    }

    public String label() {
        return label;
    }

    public String styleClass() {
        return styleClass;
    }

    /** Sort key: 0 = most severe. */
    public int rank() {
        return rank;
    }

    /** Map an arbitrary backend severity string onto a known level. */
    public static Severity from(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        switch (raw.trim().toLowerCase()) {
            case "critical":
                return CRITICAL;
            case "high":
                return HIGH;
            case "medium":
                return MEDIUM;
            case "low":
                return LOW;
            case "info":
            case "informational":
            case "none":
                return INFO;
            default:
                return UNKNOWN;
        }
    }
}
