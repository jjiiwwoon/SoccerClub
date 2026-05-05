package com.example.soccerclub.util;

/**
 * 앱 전역 공통 유틸
 * - 기존 프로젝트에서 10개 이상의 파일에 복붙되어 있던
 *   isEmpty / safe / firstNonEmpty / safeEquals / nz / normalizeRecruitType 통합
 */
public class AppUtils {

    private AppUtils() {}

    /** null 또는 공백이면 true */
    public static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** null이면 빈 문자열, 아니면 trim */
    public static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /** null이면 defaultValue 반환 */
    public static String nz(String s, String defaultValue) {
        return isEmpty(s) ? defaultValue : s.trim();
    }

    /** 여러 값 중 첫 번째로 비어있지 않은 값 반환 */
    public static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (!isEmpty(v)) return v.trim();
        }
        return "";
    }

    /** null-safe 문자열 동등 비교 (trim 적용) */
    public static boolean safeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equals(b.trim());
    }

    /** Long → int 안전 변환 */
    public static int safeInt(Long l, int defaultValue) {
        return l == null ? defaultValue : l.intValue();
    }

    /** Long → long 안전 변환 */
    public static long safeLong(Long l, long defaultValue) {
        return l == null ? defaultValue : l;
    }

    /**
     * 모집 유형 정규화
     * "용병" / "mercenary" / "일일" → "mercenary"
     * "회원" / "regular" / "정식"  → "regular"
     */
    public static String normalizeRecruitType(String raw) {
        if (isEmpty(raw)) return "";
        String s = raw.trim().toLowerCase();
        if (s.contains("regular") || s.contains("정식") || s.contains("회원")) return "regular";
        if (s.contains("mercenary") || s.contains("용병") || s.contains("일일")) return "mercenary";
        return s;
    }

    /** 필터값이 "전체" 또는 비어있으면 true */
    public static boolean isFilterAll(String value) {
        if (value == null) return true;
        String t = value.trim();
        return t.isEmpty() || t.equals("전체");
    }

    /**
     * 초/밀리초 혼재 방지
     * 10^12 미만이면 초 단위로 판단 → ms로 변환
     */
    public static long normalizeToMillis(long ts) {
        if (ts <= 0L) return 0L;
        return (ts < 1_000_000_000_000L) ? ts * 1000L : ts;
    }
}