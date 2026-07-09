package com.jjw.soccerclub.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;
import java.util.ArrayList;
import java.util.List;

public class Team {

    private String teamName;
    private String region;
    private String ageRange;
    private String intro;
    private String logoUrl;
    private String teamPhotoUrl;   // ← 추가: Firestore 의 teamPhotoUrl 필드 매핑
    private String stadium;

    private String captainUID;
    private String viceCaptainUID;
    private List<String> members;

    private String activityDay;
    private String timeStart;
    private String timeEnd;

    private Long skillSum;
    private Long memberCount;
    private Integer skillAverage;

    private Timestamp updateAt;
    private String logoStatus;
    private Timestamp logoUpdatedAt;

    @Exclude
    private String teamId;

    public Team() {}

    public Team(String teamName, String region, String ageRange, String intro,
                String logoUrl, String stadium, String captainUID, List<String> members,
                String viceCaptainUID, String activityDay, String timeStart, String timeEnd,
                Long skillSum, Long memberCount, Integer skillAverage,
                Timestamp updateAt, String logoStatus, Timestamp logoUpdatedAt) {
        this.teamName = teamName;
        this.region = region;
        this.ageRange = ageRange;
        this.intro = intro;
        this.logoUrl = logoUrl;
        this.stadium = stadium;
        this.captainUID = captainUID;
        this.members = (members != null) ? members : new ArrayList<>();
        this.viceCaptainUID = (viceCaptainUID != null) ? viceCaptainUID : "";
        this.activityDay = activityDay;
        this.timeStart = timeStart;
        this.timeEnd = timeEnd;
        this.skillSum = (skillSum != null) ? skillSum : 0L;
        this.memberCount = (memberCount != null) ? memberCount : 0L;
        this.skillAverage = (skillAverage != null) ? skillAverage : 0;
        this.updateAt = updateAt;
        this.logoStatus = logoStatus;
        this.logoUpdatedAt = logoUpdatedAt;
    }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getAgeRange() { return ageRange; }
    public void setAgeRange(String ageRange) { this.ageRange = ageRange; }

    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }

    public String getLogoUrl() { return logoUrl; }
    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    // ← 추가: 팀 사진 URL
    public String getTeamPhotoUrl() { return teamPhotoUrl; }
    public void setTeamPhotoUrl(String teamPhotoUrl) { this.teamPhotoUrl = teamPhotoUrl; }

    public String getStadium() { return stadium; }
    public void setStadium(String stadium) { this.stadium = stadium; }

    public String getCaptainUID() { return captainUID; }
    public void setCaptainUID(String captainUID) { this.captainUID = captainUID; }

    public String getViceCaptainUID() { return viceCaptainUID; }
    public void setViceCaptainUID(String v) { this.viceCaptainUID = v; }

    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }

    public String getActivityDay() { return activityDay; }
    public void setActivityDay(String activityDay) { this.activityDay = activityDay; }

    public String getTimeStart() { return timeStart; }
    public void setTimeStart(String timeStart) { this.timeStart = timeStart; }

    public String getTimeEnd() { return timeEnd; }
    public void setTimeEnd(String timeEnd) { this.timeEnd = timeEnd; }

    public Long getSkillSum() { return skillSum; }
    public void setSkillSum(Long skillSum) { this.skillSum = skillSum; }

    public Long getMemberCount() { return memberCount; }
    public void setMemberCount(Long memberCount) { this.memberCount = memberCount; }

    public Integer getSkillAverage() { return skillAverage; }
    public void setSkillAverage(Integer skillAverage) { this.skillAverage = skillAverage; }

    public Timestamp getUpdateAt() { return updateAt; }
    public void setUpdateAt(Timestamp updateAt) { this.updateAt = updateAt; }

    public String getLogoStatus() { return logoStatus; }
    public void setLogoStatus(String logoStatus) { this.logoStatus = logoStatus; }

    public Timestamp getLogoUpdatedAt() { return logoUpdatedAt; }
    public void setLogoUpdatedAt(Timestamp logoUpdatedAt) { this.logoUpdatedAt = logoUpdatedAt; }

    @Exclude
    public String getTeamId() { return teamId; }
    @Exclude
    public void setTeamId(String teamId) { this.teamId = teamId; }
}