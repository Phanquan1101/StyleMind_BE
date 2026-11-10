package com.stylemind.stylist.entity.request;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "styling_request", schema = "public")
public class StylingRequestEntity {

    @Id
    @Column(name = "styling_request_id", nullable = false, updatable = false)
    private UUID stylingRequestId;

    @Column(name = "stylist_session_id", nullable = false, updatable = false)
    private UUID stylistSessionId;

    @Column(name = "source_message_id", updatable = false)
    private UUID sourceMessageId;

    @Column(name = "occasion", columnDefinition = "text", updatable = false)
    private String occasion;

    @Column(name = "intended_use", columnDefinition = "text", updatable = false)
    private String intendedUse;

    @Column(name = "style_context", columnDefinition = "text", updatable = false)
    private String styleContext;

    @Column(name = "general_weather_context", columnDefinition = "text", updatable = false)
    private String generalWeatherContext;

    @Column(name = "max_budget_vnd", updatable = false)
    private Long maxBudgetVnd;

    @Column(name = "budget_is_hard", nullable = false, updatable = false)
    private boolean budgetIsHard;

    @Column(name = "request_status", nullable = false, columnDefinition = "text")
    private String requestStatus;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    protected StylingRequestEntity() {
    }

    public StylingRequestEntity(UUID stylingRequestId, UUID stylistSessionId, UUID sourceMessageId,
                                String occasion, String intendedUse, String styleContext,
                                String generalWeatherContext, Long maxBudgetVnd, boolean budgetIsHard,
                                String requestStatus, Instant requestedAt) {
        this.stylingRequestId = stylingRequestId;
        this.stylistSessionId = stylistSessionId;
        this.sourceMessageId = sourceMessageId;
        this.occasion = occasion;
        this.intendedUse = intendedUse;
        this.styleContext = styleContext;
        this.generalWeatherContext = generalWeatherContext;
        this.maxBudgetVnd = maxBudgetVnd;
        this.budgetIsHard = budgetIsHard;
        this.requestStatus = requestStatus;
        this.requestedAt = requestedAt;
    }

    public UUID getStylingRequestId() { return stylingRequestId; }
    public UUID getStylistSessionId() { return stylistSessionId; }
    public UUID getSourceMessageId() { return sourceMessageId; }
    public String getOccasion() { return occasion; }
    public String getIntendedUse() { return intendedUse; }
    public String getStyleContext() { return styleContext; }
    public String getGeneralWeatherContext() { return generalWeatherContext; }
    public Long getMaxBudgetVnd() { return maxBudgetVnd; }
    public boolean isBudgetIsHard() { return budgetIsHard; }
    public String getRequestStatus() { return requestStatus; }
    public void setRequestStatus(String requestStatus) { this.requestStatus = requestStatus; }
    public Instant getRequestedAt() { return requestedAt; }
}
