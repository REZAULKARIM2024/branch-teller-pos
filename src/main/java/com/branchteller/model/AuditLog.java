package com.branchteller.model;

import java.time.LocalDateTime;

public class AuditLog {
    private int id;
    private Integer actorId;
    private String action;
    private String entityType;
    private Integer entityId;
    private String beforeValue;
    private String afterValue;
    private LocalDateTime createdAt;

    // populated by joins
    private String actorName;

    public AuditLog() {}

    public AuditLog(Integer actorId, String action, String entityType, Integer entityId,
                     String beforeValue, String afterValue) {
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Integer getActorId() { return actorId; }
    public void setActorId(Integer actorId) { this.actorId = actorId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Integer getEntityId() { return entityId; }
    public void setEntityId(Integer entityId) { this.entityId = entityId; }

    public String getBeforeValue() { return beforeValue; }
    public void setBeforeValue(String beforeValue) { this.beforeValue = beforeValue; }

    public String getAfterValue() { return afterValue; }
    public void setAfterValue(String afterValue) { this.afterValue = afterValue; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getActorName() { return actorName; }
    public void setActorName(String actorName) { this.actorName = actorName; }
}
