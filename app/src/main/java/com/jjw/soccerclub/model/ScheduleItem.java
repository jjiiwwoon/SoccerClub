package com.jjw.soccerclub.model;

public class ScheduleItem {

    public String eventId;
    public String teamId;
    public String date;
    public String status;
    public String matchId;
    public String title;
    public String time;
    public String opponentName;
    public String stadiumName;
    public String address;
    public String opponentLogoUrl;

    public ScheduleItem(String eventId, String teamId, String date, String status, String matchId,
                        String title, String time, String opponentName, String stadiumName, String address) {
        this.eventId = safe(eventId);
        this.teamId = safe(teamId);
        this.date = safe(date);
        this.status = safe(status);
        this.matchId = safe(matchId);
        this.title = safe(title);
        this.time = safe(time);
        this.opponentName = safe(opponentName);
        this.stadiumName = safe(stadiumName);
        this.address = safe(address);
        this.opponentLogoUrl = "";
    }

    private String safe(String s) {
        return (s == null) ? "" : s;
    }
}