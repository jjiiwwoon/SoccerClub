package com.jjw.soccerclub.util;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.Calendar;

/**
 * DateUtils 단위 테스트.
 *
 * 날짜/시간 유틸은 매치글·모집글·캘린더·어댑터 등 8개 이상의 파일이 의존한다.
 * 특히 computeStartMillis / computeEndMillis 는 Firestore 에 저장되는 matchTs / endTs 를
 * 결정하므로, 계산 오류가 발생하면 목록 정렬과 D-day 표시가 동시에 깨진다.
 */
public class DateUtilsTest {

    // ── getKoreanWeekday ─────────────────────────────────────────────────────────

    @Test
    public void getKoreanWeekday_monday_returns월() {
        // 2025-06-09 은 월요일
        assertEquals("월", DateUtils.getKoreanWeekday("2025-06-09"));
    }

    @Test
    public void getKoreanWeekday_saturday_returns토() {
        // 2025-06-14 는 토요일
        assertEquals("토", DateUtils.getKoreanWeekday("2025-06-14"));
    }

    @Test
    public void getKoreanWeekday_sunday_returns일() {
        // 2025-06-15 는 일요일
        assertEquals("일", DateUtils.getKoreanWeekday("2025-06-15"));
    }

    @Test
    public void getKoreanWeekday_null_returnsEmpty() {
        assertEquals("", DateUtils.getKoreanWeekday(null));
    }

    @Test
    public void getKoreanWeekday_empty_returnsEmpty() {
        assertEquals("", DateUtils.getKoreanWeekday(""));
    }

    @Test
    public void getKoreanWeekday_invalidFormat_returnsEmpty() {
        assertEquals("", DateUtils.getKoreanWeekday("not-a-date"));
    }

    // ── appendWeekday ────────────────────────────────────────────────────────────

    @Test
    public void appendWeekday_validDate_appendsParenthesized() {
        // 2025-06-09 = 월요일
        assertEquals("2025-06-09(월)", DateUtils.appendWeekday("2025-06-09"));
    }

    @Test
    public void appendWeekday_null_returnsEmpty() {
        assertEquals("", DateUtils.appendWeekday(null));
    }

    @Test
    public void appendWeekday_empty_returnsEmpty() {
        assertEquals("", DateUtils.appendWeekday(""));
    }

    // ── computeStartMillis ───────────────────────────────────────────────────────

    @Test
    public void computeStartMillis_validDateTime_returnsCorrectMs() {
        long result = DateUtils.computeStartMillis("2025-01-01", "12:00");
        // 2025-01-01 12:00 KST — 정확한 ms 를 직접 비교하기보다
        // 같은 날짜/시간으로 역변환되는지 확인
        String formatted = DateUtils.format(result, "yyyy-MM-dd HH:mm");
        assertEquals("2025-01-01 12:00", formatted);
    }

    @Test
    public void computeStartMillis_timeRange_usesStartOnly() {
        long result = DateUtils.computeStartMillis("2025-06-01", "19:00 ~ 21:00");
        String formatted = DateUtils.format(result, "HH:mm");
        assertEquals("19:00", formatted);
    }

    @Test
    public void computeStartMillis_emptyTime_usesDateOnly() {
        // 시간이 빈 문자열이면 날짜만으로 파싱 (00:00)
        long result = DateUtils.computeStartMillis("2025-06-01", "");
        String formatted = DateUtils.format(result, "yyyy-MM-dd");
        assertEquals("2025-06-01", formatted);
    }

    // ── computeEndMillis ─────────────────────────────────────────────────────────

    @Test
    public void computeEndMillis_range_usesEndTime() {
        long result = DateUtils.computeEndMillis("2025-06-01", "19:00 ~ 21:00");
        String formatted = DateUtils.format(result, "HH:mm");
        assertEquals("21:00", formatted);
    }

    @Test
    public void computeEndMillis_singleTime_addsDefaultTwoHours() {
        long start = DateUtils.computeStartMillis("2025-06-01", "19:00");
        long end   = DateUtils.computeEndMillis("2025-06-01", "19:00");
        long twoHoursMs = 2L * 60L * 60L * 1000L;
        assertEquals(twoHoursMs, end - start);
    }

    @Test
    public void computeEndMillis_midnightCrossing_addsOneDay() {
        // 23:00 ~ 01:00 → 종료가 시작보다 작으면 +24시간
        long start = DateUtils.computeStartMillis("2025-06-01", "23:00");
        long end   = DateUtils.computeEndMillis("2025-06-01", "23:00 ~ 01:00");
        assertTrue("종료 시각이 시작 시각보다 뒤여야 함", end > start);
        // 차이가 2시간 (23:00 → 다음날 01:00)
        long diff = end - start;
        assertEquals(2L * 60L * 60L * 1000L, diff);
    }

    @Test
    public void computeEndMillis_emptyTime_returnsMaxValue() {
        assertEquals(Long.MAX_VALUE, DateUtils.computeEndMillis("2025-06-01", ""));
    }

    @Test
    public void computeEndMillis_nullTime_returnsMaxValue() {
        assertEquals(Long.MAX_VALUE, DateUtils.computeEndMillis("2025-06-01", null));
    }

    // ── extractStartTime ─────────────────────────────────────────────────────────

    @Test
    public void extractStartTime_rangeFormat_returnsStart() {
        assertEquals("19:00", DateUtils.extractStartTime("19:00 ~ 21:00"));
    }

    @Test
    public void extractStartTime_singleTime_returnsIt() {
        assertEquals("14:30", DateUtils.extractStartTime("14:30"));
    }

    @Test
    public void extractStartTime_null_returnsEmpty() {
        assertEquals("", DateUtils.extractStartTime(null));
    }

    @Test
    public void extractStartTime_empty_returnsEmpty() {
        assertEquals("", DateUtils.extractStartTime(""));
    }

    @Test
    public void extractStartTime_koreanAmPm_converts() {
        // "오전 9:10" → "09:10"
        String result = DateUtils.extractStartTime("오전 9:10 ~ 11:00");
        assertEquals("09:10", result);
    }

    // ── addHours ─────────────────────────────────────────────────────────────────

    @Test
    public void addHours_normal_adds() {
        assertEquals("21:00", DateUtils.addHours("19:00", 2));
    }

    @Test
    public void addHours_midnight_wraps() {
        assertEquals("01:00", DateUtils.addHours("23:00", 2));
    }

    @Test
    public void addHours_zero_unchanged() {
        assertEquals("15:30", DateUtils.addHours("15:30", 0));
    }

    // ── joinWithDivider ──────────────────────────────────────────────────────────

    @Test
    public void joinWithDivider_bothPresent_joins() {
        assertEquals("A | B", DateUtils.joinWithDivider("A", "B", " | "));
    }

    @Test
    public void joinWithDivider_firstEmpty_returnsSecond() {
        assertEquals("B", DateUtils.joinWithDivider("", "B", " | "));
    }

    @Test
    public void joinWithDivider_secondEmpty_returnsFirst() {
        assertEquals("A", DateUtils.joinWithDivider("A", "", " | "));
    }

    @Test
    public void joinWithDivider_bothEmpty_returnsEmpty() {
        assertEquals("", DateUtils.joinWithDivider("", "", " | "));
    }

    // ── buildDDayText ────────────────────────────────────────────────────────────

    @Test
    public void buildDDayText_today_returns오늘() {
        // 오늘 정오의 ms
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 12);
        c.set(Calendar.MINUTE, 0);
        assertEquals("오늘", DateUtils.buildDDayText(c.getTimeInMillis()));
    }

    @Test
    public void buildDDayText_tomorrow_returnsDMinus1() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, 1);
        assertEquals("D-1", DateUtils.buildDDayText(c.getTimeInMillis()));
    }

    @Test
    public void buildDDayText_yesterday_returns종료() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -1);
        assertEquals("종료", DateUtils.buildDDayText(c.getTimeInMillis()));
    }

    // ── zeroTime ─────────────────────────────────────────────────────────────────

    @Test
    public void zeroTime_clearsHourMinuteSecondMs() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 15);
        c.set(Calendar.MINUTE, 30);
        c.set(Calendar.SECOND, 45);
        c.set(Calendar.MILLISECOND, 123);

        DateUtils.zeroTime(c);

        assertEquals(0, c.get(Calendar.HOUR_OF_DAY));
        assertEquals(0, c.get(Calendar.MINUTE));
        assertEquals(0, c.get(Calendar.SECOND));
        assertEquals(0, c.get(Calendar.MILLISECOND));
    }

    // ── format ───────────────────────────────────────────────────────────────────

    @Test
    public void format_datePattern_formatsCorrectly() {
        // 2025-01-01 00:00:00 KST 의 ms 를 직접 계산하기보다
        // computeStartMillis 와 교차 검증
        long ms = DateUtils.computeStartMillis("2025-06-15", "09:30");
        assertEquals("2025-06-15", DateUtils.format(ms, "yyyy-MM-dd"));
        assertEquals("09:30", DateUtils.format(ms, "HH:mm"));
    }
}