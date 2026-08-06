package com.branchteller.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One posted leg of a double-entry journal line, joined with its GL account for display
 *  in the Journal (chronological) and Ledger (per-account, running balance) views. */
public class GlEntryLine {
    private int entryId;
    private LocalDateTime postedAt;
    private String code;
    private String accountName;
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;
    private String description;
    private Integer txnId;
    /** Only populated by the Ledger view (running balance for one account, in its normal-balance direction). */
    private BigDecimal runningBalance;
    /** Only populated by the Cash Flow view: the "other side" of this posting (found by matching
     *  description + timestamp), used to classify the cash movement as Operating/Investing/Financing. */
    private String contraCode;
    private String contraName;
    private String contraClass;

    public int getEntryId() { return entryId; }
    public void setEntryId(int entryId) { this.entryId = entryId; }
    public LocalDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(LocalDateTime postedAt) { this.postedAt = postedAt; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public BigDecimal getDebit() { return debit; }
    public void setDebit(BigDecimal debit) { this.debit = debit; }
    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal credit) { this.credit = credit; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getTxnId() { return txnId; }
    public void setTxnId(Integer txnId) { this.txnId = txnId; }
    public BigDecimal getRunningBalance() { return runningBalance; }
    public void setRunningBalance(BigDecimal runningBalance) { this.runningBalance = runningBalance; }
    public String getContraCode() { return contraCode; }
    public void setContraCode(String contraCode) { this.contraCode = contraCode; }
    public String getContraName() { return contraName; }
    public void setContraName(String contraName) { this.contraName = contraName; }
    public String getContraClass() { return contraClass; }
    public void setContraClass(String contraClass) { this.contraClass = contraClass; }
}
