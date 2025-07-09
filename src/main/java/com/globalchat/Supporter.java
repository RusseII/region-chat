package com.globalchat;

public class Supporter {
    public String name;
    public String tier;
    public int amount;
    public boolean isPublic;

    public Supporter(String name, String tier, int amount, boolean isPublic) {
        this.name = name;
        this.tier = tier;
        this.amount = amount;
        this.isPublic = isPublic;
    }

    public String getDisplayName() {
        return isPublic ? name : "Anonymous";
    }

    public String getTierDisplay() {
        return tier + " Supporter";
    }
}