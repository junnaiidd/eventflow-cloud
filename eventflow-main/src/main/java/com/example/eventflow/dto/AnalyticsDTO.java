package com.example.eventflow.dto;

import java.util.Map;

/**
 * Data Transfer Object for Analytics
 * Contains aggregated statistics and metrics
 */
public class AnalyticsDTO {

    private Double totalBudget;
    private Integer totalAttendees;
    private Long totalEvents;
    private Long upcomingEvents;
    private Long completedEvents;
    private Long inProgressEvents;
    private Long cancelledEvents;
    private Double averageBudget;
    private Double averageAttendees;
    private Map<String, Long> eventsByType;
    private Map<String, Double> budgetByType;
    private Map<String, Long> eventsByMonth;
    private Map<String, Double> budgetByMonth;
    private Double successRate;

    // No-args constructor
    public AnalyticsDTO() {
    }

    // Getters and Setters
    public Double getTotalBudget() {
        return totalBudget;
    }

    public void setTotalBudget(Double totalBudget) {
        this.totalBudget = totalBudget;
    }

    public Integer getTotalAttendees() {
        return totalAttendees;
    }

    public void setTotalAttendees(Integer totalAttendees) {
        this.totalAttendees = totalAttendees;
    }

    public Long getTotalEvents() {
        return totalEvents;
    }

    public void setTotalEvents(Long totalEvents) {
        this.totalEvents = totalEvents;
    }

    public Long getUpcomingEvents() {
        return upcomingEvents;
    }

    public void setUpcomingEvents(Long upcomingEvents) {
        this.upcomingEvents = upcomingEvents;
    }

    public Long getCompletedEvents() {
        return completedEvents;
    }

    public void setCompletedEvents(Long completedEvents) {
        this.completedEvents = completedEvents;
    }

    public Long getInProgressEvents() {
        return inProgressEvents;
    }

    public void setInProgressEvents(Long inProgressEvents) {
        this.inProgressEvents = inProgressEvents;
    }

    public Long getCancelledEvents() {
        return cancelledEvents;
    }

    public void setCancelledEvents(Long cancelledEvents) {
        this.cancelledEvents = cancelledEvents;
    }

    public Double getAverageBudget() {
        return averageBudget;
    }

    public void setAverageBudget(Double averageBudget) {
        this.averageBudget = averageBudget;
    }

    public Double getAverageAttendees() {
        return averageAttendees;
    }

    public void setAverageAttendees(Double averageAttendees) {
        this.averageAttendees = averageAttendees;
    }

    public Map<String, Long> getEventsByType() {
        return eventsByType;
    }

    public void setEventsByType(Map<String, Long> eventsByType) {
        this.eventsByType = eventsByType;
    }

    public Map<String, Double> getBudgetByType() {
        return budgetByType;
    }

    public void setBudgetByType(Map<String, Double> budgetByType) {
        this.budgetByType = budgetByType;
    }

    public Map<String, Long> getEventsByMonth() {
        return eventsByMonth;
    }

    public void setEventsByMonth(Map<String, Long> eventsByMonth) {
        this.eventsByMonth = eventsByMonth;
    }

    public Map<String, Double> getBudgetByMonth() {
        return budgetByMonth;
    }

    public void setBudgetByMonth(Map<String, Double> budgetByMonth) {
        this.budgetByMonth = budgetByMonth;
    }

    public Double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(Double successRate) {
        this.successRate = successRate;
    }

    // Builder
    public static AnalyticsDTOBuilder builder() {
        return new AnalyticsDTOBuilder();
    }

    public static class AnalyticsDTOBuilder {
        private Double totalBudget;
        private Integer totalAttendees;
        private Long totalEvents;
        private Long upcomingEvents;
        private Long completedEvents;
        private Long inProgressEvents;
        private Long cancelledEvents;
        private Double averageBudget;
        private Double averageAttendees;
        private Map<String, Long> eventsByType;
        private Map<String, Double> budgetByType;
        private Map<String, Long> eventsByMonth;
        private Map<String, Double> budgetByMonth;
        private Double successRate;

        public AnalyticsDTOBuilder totalBudget(Double totalBudget) {
            this.totalBudget = totalBudget;
            return this;
        }

        public AnalyticsDTOBuilder totalAttendees(Integer totalAttendees) {
            this.totalAttendees = totalAttendees;
            return this;
        }

        public AnalyticsDTOBuilder totalEvents(Long totalEvents) {
            this.totalEvents = totalEvents;
            return this;
        }

        public AnalyticsDTOBuilder upcomingEvents(Long upcomingEvents) {
            this.upcomingEvents = upcomingEvents;
            return this;
        }

        public AnalyticsDTOBuilder completedEvents(Long completedEvents) {
            this.completedEvents = completedEvents;
            return this;
        }

        public AnalyticsDTOBuilder inProgressEvents(Long inProgressEvents) {
            this.inProgressEvents = inProgressEvents;
            return this;
        }

        public AnalyticsDTOBuilder cancelledEvents(Long cancelledEvents) {
            this.cancelledEvents = cancelledEvents;
            return this;
        }

        public AnalyticsDTOBuilder averageBudget(Double averageBudget) {
            this.averageBudget = averageBudget;
            return this;
        }

        public AnalyticsDTOBuilder averageAttendees(Double averageAttendees) {
            this.averageAttendees = averageAttendees;
            return this;
        }

        public AnalyticsDTOBuilder eventsByType(Map<String, Long> eventsByType) {
            this.eventsByType = eventsByType;
            return this;
        }

        public AnalyticsDTOBuilder budgetByType(Map<String, Double> budgetByType) {
            this.budgetByType = budgetByType;
            return this;
        }

        public AnalyticsDTOBuilder eventsByMonth(Map<String, Long> eventsByMonth) {
            this.eventsByMonth = eventsByMonth;
            return this;
        }

        public AnalyticsDTOBuilder budgetByMonth(Map<String, Double> budgetByMonth) {
            this.budgetByMonth = budgetByMonth;
            return this;
        }

        public AnalyticsDTOBuilder successRate(Double successRate) {
            this.successRate = successRate;
            return this;
        }

        public AnalyticsDTO build() {
            AnalyticsDTO dto = new AnalyticsDTO();
            dto.totalBudget = this.totalBudget;
            dto.totalAttendees = this.totalAttendees;
            dto.totalEvents = this.totalEvents;
            dto.upcomingEvents = this.upcomingEvents;
            dto.completedEvents = this.completedEvents;
            dto.inProgressEvents = this.inProgressEvents;
            dto.cancelledEvents = this.cancelledEvents;
            dto.averageBudget = this.averageBudget;
            dto.averageAttendees = this.averageAttendees;
            dto.eventsByType = this.eventsByType;
            dto.budgetByType = this.budgetByType;
            dto.eventsByMonth = this.eventsByMonth;
            dto.budgetByMonth = this.budgetByMonth;
            dto.successRate = this.successRate;
            return dto;
        }
    }
}