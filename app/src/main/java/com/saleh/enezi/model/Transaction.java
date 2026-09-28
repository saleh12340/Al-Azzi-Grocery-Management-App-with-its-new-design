package com.saleh.enezi.model;

public class Transaction {
    private long id;
    private long customerId;
    private long supplierId;
    private int type;
    private double amount;
    private String details;
    private String date;
    private String invoiceNo;

    public Transaction() {
    }

    public Transaction(long id, long customerId, long supplierId, int type, double amount, String details, String date, String invoiceNo) {
        this.id = id;
        this.customerId = customerId;
        this.supplierId = supplierId;
        this.type = type;
        this.amount = amount;
        this.details = details;
        this.date = date;
        this.invoiceNo = invoiceNo;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getCustomerId() { return customerId; }
    public void setCustomerId(long customerId) { this.customerId = customerId; }

    public long getSupplierId() { return supplierId; }
    public void setSupplierId(long supplierId) { this.supplierId = supplierId; }

    public int getType() { return type; }
    public void setType(int type) { this.type = type; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
}
