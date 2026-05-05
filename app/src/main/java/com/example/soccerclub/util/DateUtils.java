package com.example.soccerclub.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 날짜/시간 관련 유틸
 * - 기존 프로젝트에서 MatchPostAdapter, ApplicationsAdapter, RecruitAdapter,
 *   EventPickAdapter, CreateMatch, CustomCalendarView 등에 중복된 날짜 로직 통합
 */
public class DateUtils {

    private DateUtils() {}

    /**
     * timestamp → "방금 전 / N분 전 / N시간 전 / yyyy.MM.dd"
     */
    public static String formatRelativeTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < 0) {
            return new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(new Date(timestamp));
        }

        long minutes = diff / (60 * 1000);
        long hours   = diff / (60 * 60 * 1000);

        if (minutes < 1)  return "방금 전";
        if (minutes < 60) return minutes + "분 전";
        if (hours < 24)   return hours + "시간 전";
        return new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(new Date(timestamp));
    }

    /**
     * date("yyyy-MM-dd") + time("HH:mm ~ HH:mm" 등) → 시작 시각 ms
     * 다양한 시간 포맷 대응 (오전/오후, AM/PM, HHmm 등)
     */
    public static long computeStartMillis(String date, String time) {
        try {
            String startTime = extractStartTime(time);
            String val       = AppUtils.isEmpty(startTime) ? date : (date + " " + startTime);
            String pattern   = AppUtils.isEmpty(startTime) ? "yyyy-MM-dd" : "yyyy-MM-dd HH:mm";
            SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
            Date d = sdf.parse(val);
            return (d != null) ? d.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /**
     * date + time → 종료 시각 ms
     * time이 범위("HH:mm ~ HH:mm")이면 끝시간, 아니면 시작+2시간
     */
    public static long computeEndMillis(String date, String time) {
        if (AppUtils.isEmpty(time)) return Long.MAX_VALUE;
        String t = time.trim();
        if (t.contains("~")) {
            String[] parts = t.split("~");
            if (parts.length < 2) return Long.MAX_VALUE;
            long start = computeStartMillis(date, parts[0].trim());
            long end   = computeStartMillis(date, parts[1].trim());
            if (end <= start) end += 24L * 60L * 60L * 1000L; // 자정 넘김 보정
            return end;
        }
        long start = computeStartMillis(date, t);
        return start + 2L * 60L * 60L * 1000L; // 기본 2시간
    }

    /**
     * 시간 문자열에서 시작 시간만 "HH:mm" 형식으로 추출
     * "19:00~21:00" / "오전 9:10 ~ 11:00" / "7:10 PM" 등 대응
     */
    public static String extractStartTime(String raw) {
        if (AppUtils.isEmpty(raw)) return "";
        String s = raw.trim();
        String firstPart = s.split("[~\\-–—]")[0].trim();

        // 오전/오후 처리
        if (firstPart.contains("오전") || firstPart.contains("오후")) {
            try {
                String normalized = firstPart.replaceAll("\\s+", " ")
                        .replace("오전", "AM")
                        .replace("오후", "PM");
                Date d = new SimpleDateFormat("a H:mm", Locale.getDefault()).parse(normalized);
                return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
            } catch (Exception ignored) {}
        }

        // AM/PM 처리
        String upper = firstPart.toUpperCase(Locale.ROOT);
        if (upper.contains("AM") || upper.contains("PM")) {
            try {
                Date d = new SimpleDateFormat("h:mm a", Locale.US).parse(upper);
                return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
            } catch (Exception ignored) {}
        }

        // HH:mm 패턴
        Matcher m = Pattern.compile("(\\d{1,2}:\\d{2})").matcher(firstPart);
        if (m.find()) {
            String hhmm = m.group(1);
            if (hhmm.length() == 4) hhmm = "0" + hhmm;
            return hhmm;
        }

        // 숫자 4자리 (1900 형식)
        m = Pattern.compile("\\b(\\d{3,4})\\b").matcher(firstPart);
        if (m.find()) {
            String digits = m.group(1);
            if (digits.length() == 3) digits = "0" + digits;
            return digits.substring(0, 2) + ":" + digits.substring(2);
        }

        return "";
    }

    /**
     * "yyyy-MM-dd" → 한국어 요일 ("월" / "화" ... "일")
     */
    public static String getKoreanWeekday(String ymd) {
        if (AppUtils.isEmpty(ymd)) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar c = Calendar.getInstance();
            c.setTime(sdf.parse(ymd));
            switch (c.get(Calendar.DAY_OF_WEEK)) {
                case Calendar.MONDAY:    return "월";
                case Calendar.TUESDAY:   return "화";
                case Calendar.WEDNESDAY: return "수";
                case Calendar.THURSDAY:  return "목";
                case Calendar.FRIDAY:    return "금";
                case Calendar.SATURDAY:  return "토";
                default:                 return "일";
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 날짜에 요일 괄호 추가
     * "2025-04-01" → "2025-04-01(화)"
     */
    public static String appendWeekday(String date) {
        String w = getKoreanWeekday(date);
        if (AppUtils.isEmpty(w)) return AppUtils.safe(date);
        return AppUtils.safe(date) + "(" + w + ")";
    }

    /**
     * D-day 텍스트 생성
     * 오늘이면 "오늘", 미래면 "D-N", 과거면 "종료"
     */
    public static String buildDDayText(long matchTs) {
        Calendar today = Calendar.getInstance();
        zeroTime(today);

        Calendar match = Calendar.getInstance();
        match.setTimeInMillis(matchTs);
        zeroTime(match);

        long diff = (match.getTimeInMillis() - today.getTimeInMillis()) / (24L * 60 * 60 * 1000);
        if (diff == 0) return "오늘";
        if (diff > 0)  return "D-" + diff;
        return "종료";
    }

    /**
     * "HH:mm" 시간에 hours 더하기
     */
    public static String addHours(String hhmm, int hours) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
            Calendar c = Calendar.getInstance();
            c.setTime(f.parse(hhmm));
            c.add(Calendar.HOUR_OF_DAY, hours);
            return f.format(c.getTime());
        } catch (Exception e) {
            return hhmm;
        }
    }

    /**
     * 두 문자열을 구분자로 합치기 (빈 값 처리)
     * joinWithDivider("19:00", "21:00", " ~ ") → "19:00 ~ 21:00"
     */
    public static String joinWithDivider(String a, String b, String divider) {
        if (AppUtils.isEmpty(a) && AppUtils.isEmpty(b)) return "";
        if (AppUtils.isEmpty(a)) return AppUtils.safe(b);
        if (AppUtils.isEmpty(b)) return AppUtils.safe(a);
        return AppUtils.safe(a) + divider + AppUtils.safe(b);
    }

    /** Calendar의 시/분/초/밀리초를 0으로 초기화 */
    public static void zeroTime(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    /** millis → 지정 패턴 포맷 */
    public static String format(long millis, String pattern) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(millis);
    }
}