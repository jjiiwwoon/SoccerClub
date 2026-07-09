package com.jjw.soccerclub.model;

import java.io.Serializable;

public class TeamMatchCondition implements Serializable {

    public String regionCity;
    public String regionDistrict;
    public Integer skillMin;
    public Integer skillMax;
    public String weekday;
    public String dateFrom;
    public String dateTo;
    public String timeFrom;
    public String timeTo;
}