package com.example.soccerclub.model;

import java.io.Serializable;

public class RecruitFilters implements Serializable {

    public Common common = new Common();
    public String recruitType = "전체";
    public String position = "전체";
    public Integer skillMin = null;
    public Integer skillMax = null;
    public String dateFrom = null;
    public String dateTo = null;
    public String timeFrom = null;
    public String timeTo = null;
    public String weekday = "전체";

    public static class Common implements Serializable {
        public String city = "전체";
        public String district = "전체";
    }
}