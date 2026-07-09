package com.jjw.soccerclub.util;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * AppUtils 단위 테스트.
 *
 * 순수 Java 로직만 사용하므로 Android 의존성 없이 JUnit 만으로 실행 가능.
 * 프로젝트 전역에서 10개 이상의 파일이 이 유틸에 의존하기 때문에,
 * 여기서 regression 을 잡으면 전체 앱의 안정성이 올라간다.
 */
public class AppUtilsTest {

    // ── isEmpty ──────────────────────────────────────────────────────────────────

    @Test
    public void isEmpty_null_returnsTrue() {
        assertTrue(AppUtils.isEmpty(null));
    }

    @Test
    public void isEmpty_emptyString_returnsTrue() {
        assertTrue(AppUtils.isEmpty(""));
    }

    @Test
    public void isEmpty_blankSpaces_returnsTrue() {
        assertTrue(AppUtils.isEmpty("   "));
    }

    @Test
    public void isEmpty_tabAndNewline_returnsTrue() {
        assertTrue(AppUtils.isEmpty(" \t\n "));
    }

    @Test
    public void isEmpty_validString_returnsFalse() {
        assertFalse(AppUtils.isEmpty("hello"));
    }

    @Test
    public void isEmpty_stringWithSpaces_returnsFalse() {
        assertFalse(AppUtils.isEmpty("  hello  "));
    }

    // ── safe ─────────────────────────────────────────────────────────────────────

    @Test
    public void safe_null_returnsEmpty() {
        assertEquals("", AppUtils.safe(null));
    }

    @Test
    public void safe_normal_returnsTrimmed() {
        assertEquals("hello", AppUtils.safe("  hello  "));
    }

    @Test
    public void safe_alreadyTrimmed_returnsSame() {
        assertEquals("abc", AppUtils.safe("abc"));
    }

    // ── nz (null → default) ─────────────────────────────────────────────────────

    @Test
    public void nz_null_returnsDefault() {
        assertEquals("기본값", AppUtils.nz(null, "기본값"));
    }

    @Test
    public void nz_empty_returnsDefault() {
        assertEquals("기본값", AppUtils.nz("", "기본값"));
    }

    @Test
    public void nz_blank_returnsDefault() {
        assertEquals("기본값", AppUtils.nz("   ", "기본값"));
    }

    @Test
    public void nz_valid_returnsTrimmedValue() {
        assertEquals("값", AppUtils.nz("  값  ", "기본값"));
    }

    // ── firstNonEmpty ────────────────────────────────────────────────────────────

    @Test
    public void firstNonEmpty_allNull_returnsEmpty() {
        assertEquals("", AppUtils.firstNonEmpty(null, null, null));
    }

    @Test
    public void firstNonEmpty_nullArray_returnsEmpty() {
        assertEquals("", AppUtils.firstNonEmpty((String[]) null));
    }

    @Test
    public void firstNonEmpty_firstValid_returnsFirst() {
        assertEquals("a", AppUtils.firstNonEmpty("a", "b", "c"));
    }

    @Test
    public void firstNonEmpty_secondValid_returnsSecond() {
        assertEquals("b", AppUtils.firstNonEmpty(null, "b", "c"));
    }

    @Test
    public void firstNonEmpty_skipsBlank_returnsThird() {
        assertEquals("c", AppUtils.firstNonEmpty("", "  ", "c"));
    }

    @Test
    public void firstNonEmpty_trimsResult() {
        assertEquals("hello", AppUtils.firstNonEmpty(null, "  hello  "));
    }

    // ── safeEquals ───────────────────────────────────────────────────────────────

    @Test
    public void safeEquals_bothNull_returnsFalse() {
        assertFalse(AppUtils.safeEquals(null, null));
    }

    @Test
    public void safeEquals_oneNull_returnsFalse() {
        assertFalse(AppUtils.safeEquals("a", null));
        assertFalse(AppUtils.safeEquals(null, "a"));
    }

    @Test
    public void safeEquals_same_returnsTrue() {
        assertTrue(AppUtils.safeEquals("hello", "hello"));
    }

    @Test
    public void safeEquals_withSpaces_trimmedMatch() {
        assertTrue(AppUtils.safeEquals("  hello  ", "hello"));
    }

    @Test
    public void safeEquals_different_returnsFalse() {
        assertFalse(AppUtils.safeEquals("hello", "world"));
    }

    // ── safeInt / safeLong ───────────────────────────────────────────────────────

    @Test
    public void safeInt_null_returnsDefault() {
        assertEquals(99, AppUtils.safeInt(null, 99));
    }

    @Test
    public void safeInt_valid_returnsIntValue() {
        assertEquals(42, AppUtils.safeInt(42L, 0));
    }

    @Test
    public void safeLong_null_returnsDefault() {
        assertEquals(100L, AppUtils.safeLong(null, 100L));
    }

    @Test
    public void safeLong_valid_returnsValue() {
        assertEquals(999L, AppUtils.safeLong(999L, 0L));
    }

    // ── normalizeRecruitType ─────────────────────────────────────────────────────

    @Test
    public void normalizeRecruitType_null_returnsEmpty() {
        assertEquals("", AppUtils.normalizeRecruitType(null));
    }

    @Test
    public void normalizeRecruitType_empty_returnsEmpty() {
        assertEquals("", AppUtils.normalizeRecruitType(""));
    }

    @Test
    public void normalizeRecruitType_regular_korean() {
        assertEquals("regular", AppUtils.normalizeRecruitType("정식선수"));
        assertEquals("regular", AppUtils.normalizeRecruitType("회원"));
    }

    @Test
    public void normalizeRecruitType_regular_english() {
        assertEquals("regular", AppUtils.normalizeRecruitType("regular"));
        assertEquals("regular", AppUtils.normalizeRecruitType("Regular"));
    }

    @Test
    public void normalizeRecruitType_mercenary_korean() {
        assertEquals("mercenary", AppUtils.normalizeRecruitType("용병"));
        assertEquals("mercenary", AppUtils.normalizeRecruitType("일일용병"));
    }

    @Test
    public void normalizeRecruitType_mercenary_english() {
        assertEquals("mercenary", AppUtils.normalizeRecruitType("mercenary"));
        assertEquals("mercenary", AppUtils.normalizeRecruitType("Mercenary"));
    }

    @Test
    public void normalizeRecruitType_unknown_returnsLowered() {
        assertEquals("unknown", AppUtils.normalizeRecruitType("unknown"));
    }

    // ── isFilterAll ──────────────────────────────────────────────────────────────

    @Test
    public void isFilterAll_null_returnsTrue() {
        assertTrue(AppUtils.isFilterAll(null));
    }

    @Test
    public void isFilterAll_empty_returnsTrue() {
        assertTrue(AppUtils.isFilterAll(""));
    }

    @Test
    public void isFilterAll_jeonche_returnsTrue() {
        assertTrue(AppUtils.isFilterAll("전체"));
        assertTrue(AppUtils.isFilterAll("  전체  "));
    }

    @Test
    public void isFilterAll_specific_returnsFalse() {
        assertFalse(AppUtils.isFilterAll("서울"));
    }

    // ── normalizeToMillis ────────────────────────────────────────────────────────

    @Test
    public void normalizeToMillis_zero_returnsZero() {
        assertEquals(0L, AppUtils.normalizeToMillis(0L));
    }

    @Test
    public void normalizeToMillis_negative_returnsZero() {
        assertEquals(0L, AppUtils.normalizeToMillis(-1L));
    }

    @Test
    public void normalizeToMillis_seconds_convertsToMs() {
        // 1_700_000_000 은 초 단위 (2023년 11월경)
        assertEquals(1_700_000_000_000L, AppUtils.normalizeToMillis(1_700_000_000L));
    }

    @Test
    public void normalizeToMillis_alreadyMs_returnsSame() {
        // 1_700_000_000_000 은 이미 밀리초
        assertEquals(1_700_000_000_000L, AppUtils.normalizeToMillis(1_700_000_000_000L));
    }
}