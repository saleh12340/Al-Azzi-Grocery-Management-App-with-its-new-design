package com.saleh.enezi.model;

public class Invoice {
    private long id;
    private String invoiceNo;
    private long customerId;
    private String customerName;
    private double total;
    private double paid;
    private String date;
    private String saleType;
    private String details;

    public Invoice() {
    }

    public Invoice(long id, String invoiceNo, long customerId, String customerName, double total, double paid, String date, String saleType, String details) {
        this.id = id;
        this.invoiceNo = invoiceNo;
        this.customerId = customerId;
        this.customerName = customerName;
        this.total = total;
        this.paid = paid;
        this.date = date;
        this.saleType = saleType;
        this.details = details;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }

    public long getCustomerId() { return customerId; }
    public void setCustomerId(long customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = total; }

    public double getPaid() { return paid; }
    public void setPaid(double paid) { this.paid = paid; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getSaleType() { return saleType; }
    public void setSaleType(String saleType) { this.saleType = saleType; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
