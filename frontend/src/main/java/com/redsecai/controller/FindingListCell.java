package com.redsecai.controller;

import com.redsecai.model.Finding;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Renders a {@link Finding} as a severity-badged card: a coloured severity
 * chip on the left, then the title, a muted metadata line (technique, tactic,
 * locations), and the wrapped description.
 */
public class FindingListCell extends ListCell<Finding> {

    private final Label badge = new Label();
    private final Label title = new Label();
    private final Label meta = new Label();
    private final Label description = new Label();
    private final HBox card;

    public FindingListCell(ListView<Finding> listView) {
        badge.getStyleClass().add("sev-badge");
        title.getStyleClass().add("finding-title");
        title.setWrapText(true);
        meta.getStyleClass().add("finding-meta");
        meta.setWrapText(true);
        description.getStyleClass().add("finding-desc");
        description.setWrapText(true);

        VBox body = new VBox(4, title, meta, description);
        HBox.setHgrow(body, Priority.ALWAYS);

        card = new HBox(12, badge, body);
        card.getStyleClass().add("finding-card");
        // Wrap long text within the viewport rather than forcing a horizontal
        // scrollbar; leave room for the cell padding and scrollbar gutter.
        card.maxWidthProperty().bind(listView.widthProperty().subtract(36));
    }

    @Override
    protected void updateItem(Finding finding, boolean empty) {
        super.updateItem(finding, empty);
        if (empty || finding == null) {
            setGraphic(null);
            return;
        }

        badge.setText(finding.severity().label().toUpperCase());
        // Reset any severity class carried over from a recycled cell.
        badge.getStyleClass().removeIf(styleClass ->
            styleClass.startsWith("sev-") && !styleClass.equals("sev-badge"));
        badge.getStyleClass().add(finding.severity().styleClass());

        title.setText(finding.title());

        String metaText = buildMeta(finding);
        meta.setText(metaText);
        setShown(meta, !metaText.isEmpty());

        description.setText(finding.description());
        setShown(description, !finding.description().isEmpty());

        setGraphic(card);
    }

    private static String buildMeta(Finding finding) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, finding.techniqueId());
        appendPart(sb, finding.tactic());
        if (!finding.locations().isEmpty()) {
            appendPart(sb, String.join(", ", finding.locations()));
        }
        return sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part == null || part.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append("   •   ");
        }
        sb.append(part);
    }

    /** Collapse a label completely when it has nothing to show. */
    private static void setShown(Label label, boolean shown) {
        label.setVisible(shown);
        label.setManaged(shown);
    }
}
