package com.jjw.soccerclub.model;

import java.util.List;

public class MatchPost {

    private String matchId;
    private String uid;
    private String teamId;
    private String teamName;
    private String logoUrl;
    private String date;
    private String time;
    private String stadium;
    private String address;
    private int skill;
    private long timestamp;
    private String description;
    private long matchTs;
    private String status;
    private String teamLogoUrl;
    private String stadiumName;
    private String stadiumAddress;
    private List<ApplicantInfo> applicants;

    public MatchPost() {}

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public String getStadium() { return stadium; }
    public void setStadium(String stadium) { this.stadium = stadium; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public int getSkill() { return skill; }
    public void setSkill(int skill) { this.skill = skill; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public long getMatchTs() { return matchTs; }
    public void setMatchTs(long matchTs) { this.matchTs = matchTs; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTeamLogoUrl() { return teamLogoUrl; }
    public void setTeamLogoUrl(String teamLogoUrl) { this.teamLogoUrl = teamLogoUrl; }

    public String getStadiumName() { return stadiumName; }
    public void setStadiumName(String stadiumName) { this.stadiumName = stadiumName; }

    public String getStadiumAddress() { return stadiumAddress; }
    public void setStadiumAddress(String stadiumAddress) { this.stadiumAddress = stadiumAddress; }

    public List<ApplicantInfo> getApplicants() { return applicants; }
    public void setApplicants(List<ApplicantInfo> applicants) { this.applicants = applicants; }
}