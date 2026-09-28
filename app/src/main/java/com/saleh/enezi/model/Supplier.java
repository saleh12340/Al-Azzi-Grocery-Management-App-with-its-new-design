package com.saleh.enezi.model;

public class Supplier {
    private long id;
    private String name;
    private String phone;
    private String details;
    private double balance;

    public Supplier() {
    }

    public Supplier(long id, String name, String phone, String details, double balance) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.details = details;
        this.balance = balance;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }
}
