package com.theskysid.echobackend.messaging.entity;

public enum RetentionPolicy {
    SIX_HOURS(6),
    ONE_DAY(24),
    SEVEN_DAYS(168);

    private final int hours;

    RetentionPolicy(int hours) {
        this.hours = hours;
    }

    public int getHours() {
        return hours;
    }
}
