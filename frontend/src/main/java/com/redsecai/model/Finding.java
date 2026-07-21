package com.redsecai.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A single security finding, parsed from one entry of the backend's generic
 * {@code results.findings} list into a typed, render-ready shape.
 */
public class Finding {
    private final Severity severity;
    private final String title;
    private final String techniqueId;
    private final String tactic;
    private final String description;
    private final String remediation;
    private final List<String> locations;

    public Finding(Severity severity, String title, String techniqueId, String tactic,
                   String description, String remediation, List<String> locations) {
        this.severity = severity;
        this.title = title;
        this.techniqueId = techniqueId;
        this.tactic = tactic;
        this.description = description;
        this.remediation = remediation;
        this.locations = locations;
    }

    public Severity severity() {
        return severity;
    }

    public String title() {
        return title;
    }

    public String techniqueId() {
        return techniqueId;
    }

    public String tactic() {
        return tactic;
    }

    public String description() {
        return description;
    }

    public String remediation() {
        return remediation;
    }

    public List<String> locations() {
        return locations;
    }

    /** Build a {@code Finding} from one map in the backend's findings list. */
    @SuppressWarnings("unchecked")
    public static Finding fromMap(Map<String, Object> map) {
        Severity severity = Severity.from(str(map.get("severity")));
        String title = orDefault(str(map.get("title")), "Untitled finding");
        String technique = str(map.get("technique_id"));
        String tactic = str(map.get("tactic"));
        String description = str(map.get("description"));
        String remediation = str(map.get("remediation"));

        List<String> locations = new ArrayList<>();
        Object evidence = map.get("evidence");
        if (evidence instanceof Map) {
            Object locs = ((Map<String, Object>) evidence).get("locations");
            if (locs instanceof List) {
                for (Object loc : (List<Object>) locs) {
                    String value = str(loc);
                    if (!value.isEmpty()) {
                        locations.add(value);
                    }
                }
            }
        }
        return new Finding(severity, title, technique, tactic, description, remediation, locations);
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String orDefault(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }
}
