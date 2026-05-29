package com.jjw.soccerclub.model;

public class ApplicantInfo {

    private String teamName;
    private String nickname;
    private int skill;
    private String teamId;
    private String userId;

    public ApplicantInfo() {}

    public ApplicantInfo(String teamName, String nickname, int skill, String teamId, String userId) {
        this.teamName = teamName;
        this.nickname = nickname;
        this.skill = skill;
        this.teamId = teamId;
        this.userId = userId;
    }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public int getSkill() { return skill; }
    public void setSkill(int skill) { this.skill = skill; }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
}