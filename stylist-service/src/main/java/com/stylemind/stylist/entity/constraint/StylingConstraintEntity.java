package com.stylemind.stylist.entity.constraint;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "styling_constraint", schema = "public")
public class StylingConstraintEntity {

    @Id
    @Column(name = "constraint_id", nullable = false, updatable = false)
    private UUID constraintId;

    @Column(name = "styling_request_id", nullable = false, updatable = false)
    private UUID stylingRequestId;

    @Column(name = "constraint_kind", nullable = false, columnDefinition = "text", updatable = false)
    private String constraintKind;

    @Column(name = "catalog_entity_id", updatable = false)
    private UUID catalogEntityId;

    @Column(name = "text_value", columnDefinition = "text", updatable = false)
    private String textValue;

    @Column(name = "is_hard", nullable = false, updatable = false)
    private boolean hard;

    protected StylingConstraintEntity() {
    }

    public StylingConstraintEntity(UUID constraintId, UUID stylingRequestId, String constraintKind,
                                   UUID catalogEntityId, String textValue, boolean hard) {
        this.constraintId = constraintId;
        this.stylingRequestId = stylingRequestId;
        this.constraintKind = constraintKind;
        this.catalogEntityId = catalogEntityId;
        this.textValue = textValue;
        this.hard = hard;
    }

    public UUID getConstraintId() { return constraintId; }
    public UUID getStylingRequestId() { return stylingRequestId; }
    public String getConstraintKind() { return constraintKind; }
    public UUID getCatalogEntityId() { return catalogEntityId; }
    public String getTextValue() { return textValue; }
    public boolean isHard() { return hard; }
}
