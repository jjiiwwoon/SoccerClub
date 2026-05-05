package com.example.soccerclub.model;

import java.io.Serializable;

public class MatchFilters implements Serializable {

    public Common common = new Common();
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