package com.branchteller.model;

import java.time.LocalDateTime;

public class RegulatoryReport {
    private int id;
    private String reportType; // SAR, CTR
    private String referenceNo;
    private Integer relatedAccountId;
    private String relatedAccountNumber;
    private Integer relatedFlagId;
    private int filedBy;
    private String filedByName;
    private LocalDateTime filedAt;
    private String narrative;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getReportType() { return reportType; }
    public void setReportType(String reportType) { this.reportType = reportType; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public Integer getRelatedAccountId() { return relatedAccountId; }
    public void setRelatedAccountId(Integer relatedAccountId) { this.relatedAccountId = relatedAccountId; }
    public String getRelatedAccountNumber() { return relatedAccountNumber; }
    public void setRelatedAccountNumber(String relatedAccountNumber) { this.relatedAccountNumber = relatedAccountNumber; }
    public Integer getRelatedFlagId() { return relatedFlagId; }
    public void setRelatedFlagId(Integer relatedFlagId) { this.relatedFlagId = relatedFlagId; }
    public int getFiledBy() { return filedBy; }
    public void setFiledBy(int filedBy) { this.filedBy = filedBy; }
    public String getFiledByName() { return filedByName; }
    public void setFiledByName(String filedByName) { this.filedByName = filedByName; }
    public LocalDateTime getFiledAt() { return filedAt; }
    public void setFiledAt(LocalDateTime filedAt) { this.filedAt = filedAt; }
    public String getNarrative() { return narrative; }
    public void setNarrative(String narrative) { this.narrative = narrative; }
}
