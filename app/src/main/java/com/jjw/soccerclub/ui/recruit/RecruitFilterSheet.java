package com.jjw.soccerclub.ui.recruit;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.model.RecruitFilters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RecruitFilterSheet extends BottomSheetDialogFragment {

    private static final String ARG_FILTERS = "arg_filters";
    private static final String ALL = "전체";

    public interface OnRecruitFilterApplied {
        void onRecruitFilterApplied(@NonNull RecruitFilters filters);
    }

    private TextView chipRecruitTypeAll, chipRecruitTypeRegular, chipRecruitTypeMerc;
    private LinearLayout btnCity, btnDistrict, btnSkillMin, btnSkillMax;
    private LinearLayout btnDateFrom, btnDateTo, btnTimeFrom, btnTimeTo;
    private TextView textCity, textDistrict, textSkillMin, textSkillMax;
    private TextView textDateFrom, textDateTo, textTimeFrom, textTimeTo;
    private TextView chipPosAll, chipPosGK, chipPosDF, chipPosMF, chipPosFW;
    private TextView chipWeekAll, chipWeekMon, chipWeekTue, chipWeekWed;
    private TextView chipWeekThu, chipWeekFri, chipWeekSat, chipWeekSun;
    private TextView btnReset, btnApply, btnClose;

    private RecruitFilters current;
    private Map<String, List<String>> districtMap;

    public static RecruitFilterSheet newInstance(@Nullable RecruitFilters filters) {
        RecruitFilterSheet f = new RecruitFilterSheet();
        Bundle b = new Bundle();
        if (filters != null) b.putSerializable(ARG_FILTERS, filters);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_filter_recruit, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        if (getArguments() != null) {
            Serializable s = getArguments().getSerializable(ARG_FILTERS);
            if (s instanceof RecruitFilters) current = (RecruitFilters) s;
        }
        if (current == null) current = new RecruitFilters();

        bindViews(root);
        initDistrictMap();
        restoreFromCurrent();
        setupListeners();
    }

    private void bindViews(View root) {
        chipRecruitTypeAll     = root.findViewById(R.id.chipRecruitTypeAll);
        chipRecruitTypeRegular = root.findViewById(R.id.chipRecruitTypeRegular);
        chipRecruitTypeMerc    = root.findViewById(R.id.chipRecruitTypeMerc);

        btnCity      = root.findViewById(R.id.btnCity);
        btnDistrict  = root.findViewById(R.id.btnDistrict);
        textCity     = root.findViewById(R.id.textCity);
        textDistrict = root.findViewById(R.id.textDistrict);

        btnSkillMin  = root.findViewById(R.id.btnSkillMin);
        btnSkillMax  = root.findViewById(R.id.btnSkillMax);
        textSkillMin = root.findViewById(R.id.textSkillMin);
        textSkillMax = root.findViewById(R.id.textSkillMax);

        chipPosAll = root.findViewById(R.id.chipPosAll);
        chipPosGK  = root.findViewById(R.id.chipPosGK);
        chipPosDF  = root.findViewById(R.id.chipPosDF);
        chipPosMF  = root.findViewById(R.id.chipPosMF);
        chipPosFW  = root.findViewById(R.id.chipPosFW);

        btnDateFrom  = root.findViewById(R.id.btnDateFrom);
        btnDateTo    = root.findViewById(R.id.btnDateTo);
        textDateFrom = root.findViewById(R.id.textDateFrom);
        textDateTo   = root.findViewById(R.id.textDateTo);

        btnTimeFrom  = root.findViewById(R.id.btnTimeFrom);
        btnTimeTo    = root.findViewById(R.id.btnTimeTo);
        textTimeFrom = root.findViewById(R.id.textTimeFrom);
        textTimeTo   = root.findViewById(R.id.textTimeTo);

        chipWeekAll = root.findViewById(R.id.chipWeekAll);
        chipWeekMon = root.findViewById(R.id.chipWeekMon);
        chipWeekTue = root.findViewById(R.id.chipWeekTue);
        chipWeekWed = root.findViewById(R.id.chipWeekWed);
        chipWeekThu = root.findViewById(R.id.chipWeekThu);
        chipWeekFri = root.findViewById(R.id.chipWeekFri);
        chipWeekSat = root.findViewById(R.id.chipWeekSat);
        chipWeekSun = root.findViewById(R.id.chipWeekSun);

        btnReset = root.findViewById(R.id.btnReset);
        btnApply = root.findViewById(R.id.btnApply);
        btnClose = root.findViewById(R.id.btnClose);
    }

    private void restoreFromCurrent() {
        setText(textCity,     nullToAll(current.common != null ? current.common.city : null));
        setText(textDistrict, nullToAll(current.common != null ? current.common.district : null));
        setText(textSkillMin, current.skillMin != null ? String.valueOf(current.skillMin) : ALL);
        setText(textSkillMax, current.skillMax != null ? String.valueOf(current.skillMax) : ALL);
        setText(textDateFrom, nullToAll(current.dateFrom));
        setText(textDateTo,   nullToAll(current.dateTo));
        setText(textTimeFrom, nullToAll(current.timeFrom));
        setText(textTimeTo,   nullToAll(current.timeTo));
        selectRecruitTypeChip(nullToAll(current.recruitType));
        restorePositionChipsFromCurrent();
        restoreWeekdayChipsFromCurrent();
    }

    private void setupListeners() {
        String[] cities = requireContext().getResources().getStringArray(R.array.city_array);
        String[] skillItems = new String[11];
        skillItems[0] = ALL;
        for (int i = 1; i <= 10; i++) skillItems[i] = String.valueOf(i);

        btnCity.setOnClickListener(v -> showPopup(btnCity, cities, sel -> {
            setText(textCity, sel);
            setText(textDistrict, ALL);
            if (current.common != null) { current.common.city = sel; current.common.district = ALL; }
            List<String> dists = districtMap.getOrDefault(sel, Arrays.asList(ALL));
            setupDistrictPopup(dists.toArray(new String[0]));
        }));

        btnSkillMin.setOnClickListener(v -> showPopup(btnSkillMin,
                skillItems, sel -> {
                    setText(textSkillMin, sel);
                    current.skillMin = ALL.equals(sel) ? null : Integer.parseInt(sel);
                }));

        btnSkillMax.setOnClickListener(v -> showPopup(btnSkillMax,
                skillItems, sel -> {
                    setText(textSkillMax, sel);
                    current.skillMax = ALL.equals(sel) ? null : Integer.parseInt(sel);
                }));

        chipRecruitTypeAll.setOnClickListener(v -> selectRecruitTypeChip(ALL));
        chipRecruitTypeRegular.setOnClickListener(v -> selectRecruitTypeChip("regular"));
        chipRecruitTypeMerc.setOnClickListener(v -> selectRecruitTypeChip("mercenary"));

        chipPosAll.setOnClickListener(v -> selectPositionChip(ALL));
        chipPosGK.setOnClickListener(v -> selectPositionChip("GK"));
        chipPosDF.setOnClickListener(v -> selectPositionChip("DF"));
        chipPosMF.setOnClickListener(v -> selectPositionChip("MF"));
        chipPosFW.setOnClickListener(v -> selectPositionChip("FW"));

        btnDateFrom.setOnClickListener(v -> pickDate(true));
        btnDateTo.setOnClickListener(v -> pickDate(false));

        btnTimeFrom.setOnClickListener(v -> showTimePicker(true));
        btnTimeTo.setOnClickListener(v -> showTimePicker(false));

        chipWeekAll.setOnClickListener(v -> selectWeekdayChip(ALL));
        chipWeekMon.setOnClickListener(v -> selectWeekdayChip("월"));
        chipWeekTue.setOnClickListener(v -> selectWeekdayChip("화"));
        chipWeekWed.setOnClickListener(v -> selectWeekdayChip("수"));
        chipWeekThu.setOnClickListener(v -> selectWeekdayChip("목"));
        chipWeekFri.setOnClickListener(v -> selectWeekdayChip("금"));
        chipWeekSat.setOnClickListener(v -> selectWeekdayChip("토"));
        chipWeekSun.setOnClickListener(v -> selectWeekdayChip("일"));

        btnClose.setOnClickListener(v -> dismiss());

        btnReset.setOnClickListener(v -> {
            current = new RecruitFilters();
            restoreFromCurrent();
        });

        btnApply.setOnClickListener(v -> {
            if (current.common != null) {
                current.common.city     = textOf(textCity);
                current.common.district = textOf(textDistrict);
            }
            current.skillMin = parseSkill(textOf(textSkillMin));
            current.skillMax = parseSkill(textOf(textSkillMax));
            current.dateFrom = parseFilter(textOf(textDateFrom));
            current.dateTo   = parseFilter(textOf(textDateTo));
            current.timeFrom = parseFilter(textOf(textTimeFrom));
            current.timeTo   = parseFilter(textOf(textTimeTo));

            Fragment parent = getParentFragment();
            if (parent instanceof OnRecruitFilterApplied)
                ((OnRecruitFilterApplied) parent).onRecruitFilterApplied(current);
            dismiss();
        });
    }

    private void setupDistrictPopup(String[] items) {
        if (btnDistrict == null) return;
        btnDistrict.setOnClickListener(v -> showPopup(btnDistrict, items, sel -> {
            setText(textDistrict, sel);
            if (current.common != null) current.common.district = sel;
        }));
    }

    // ── 모집유형 (단일 선택) ──────────────────────────────────────────────────

    private void selectRecruitTypeChip(String type) {
        current.recruitType = ALL.equals(type) ? "전체" : type;
        setChipSelected(chipRecruitTypeAll,     ALL.equals(type));
        setChipSelected(chipRecruitTypeRegular, "regular".equals(type));
        setChipSelected(chipRecruitTypeMerc,    "mercenary".equals(type));
    }

    // ── 포지션 (다중 선택 토글) ──────────────────────────────────────────────

    private void selectPositionChip(String pos) {
        if (ALL.equals(pos)) {
            current.position = "전체";
        } else {
            String raw = current.position == null ? "" : current.position.trim();
            List<String> selected = new ArrayList<>();
            if (!raw.isEmpty() && !"전체".equals(raw)) {
                selected.addAll(Arrays.asList(raw.split(",")));
            }
            if (selected.contains(pos)) selected.remove(pos);
            else selected.add(pos);

            current.position = selected.isEmpty() ? "전체" : TextUtils.join(",", selected);
        }
        restorePositionChipsFromCurrent();
    }

    private void restorePositionChipsFromCurrent() {
        String raw = current.position;
        boolean isAll = raw == null || raw.trim().isEmpty() || "전체".equals(raw.trim());
        setChipSelected(chipPosAll, isAll);
        if (isAll) {
            setChipSelected(chipPosGK, false);
            setChipSelected(chipPosDF, false);
            setChipSelected(chipPosMF, false);
            setChipSelected(chipPosFW, false);
        } else {
            setChipSelected(chipPosGK, raw.contains("GK"));
            setChipSelected(chipPosDF, raw.contains("DF"));
            setChipSelected(chipPosMF, raw.contains("MF"));
            setChipSelected(chipPosFW, raw.contains("FW"));
        }
    }

    // ── 요일 (다중 선택 토글) ────────────────────────────────────────────────

    private void selectWeekdayChip(String day) {
        if (ALL.equals(day)) {
            current.weekday = "전체";
        } else {
            String raw = current.weekday == null ? "" : current.weekday.trim();
            List<String> selected = new ArrayList<>();
            if (!raw.isEmpty() && !"전체".equals(raw)) {
                selected.addAll(Arrays.asList(raw.split(",")));
            }
            if (selected.contains(day)) selected.remove(day);
            else selected.add(day);

            current.weekday = selected.isEmpty() ? "전체" : TextUtils.join(",", selected);
        }
        restoreWeekdayChipsFromCurrent();
    }

    private void restoreWeekdayChipsFromCurrent() {
        String raw = current.weekday;
        boolean isAll = raw == null || raw.trim().isEmpty() || "전체".equals(raw.trim());
        setChipSelected(chipWeekAll, isAll);
        if (isAll) {
            setChipSelected(chipWeekMon, false); setChipSelected(chipWeekTue, false);
            setChipSelected(chipWeekWed, false); setChipSelected(chipWeekThu, false);
            setChipSelected(chipWeekFri, false); setChipSelected(chipWeekSat, false);
            setChipSelected(chipWeekSun, false);
        } else {
            setChipSelected(chipWeekMon, raw.contains("월")); setChipSelected(chipWeekTue, raw.contains("화"));
            setChipSelected(chipWeekWed, raw.contains("수")); setChipSelected(chipWeekThu, raw.contains("목"));
            setChipSelected(chipWeekFri, raw.contains("금")); setChipSelected(chipWeekSat, raw.contains("토"));
            setChipSelected(chipWeekSun, raw.contains("일"));
        }
    }

    // ── 공통 UI 헬퍼 ─────────────────────────────────────────────────────────

    private void setChipSelected(TextView chip, boolean selected) {
        if (chip == null) return;
        chip.setSelected(selected);
        chip.setBackgroundResource(selected
                ? R.drawable.bg_badge_blue : R.drawable.bg_chip);
        chip.setTextColor(selected ? 0xFFFFFFFF : 0xFF263238);
    }

    private void pickDate(boolean isFrom) {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(requireContext(), (view, y, m, d) -> {
            String date = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
            if (isFrom) { setText(textDateFrom, date); current.dateFrom = date; }
            else        { setText(textDateTo,   date); current.dateTo   = date; }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimePicker(boolean isFrom) {
        View v = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_time_picker, null);
        NumberPicker npHour = v.findViewById(R.id.npHour);
        NumberPicker npMin  = v.findViewById(R.id.npMin);
        npHour.setMinValue(0); npHour.setMaxValue(23);
        String[] mins = {"00","10","20","30","40","50"};
        npMin.setMinValue(0); npMin.setMaxValue(5);
        npMin.setDisplayedValues(mins);

        new AlertDialog.Builder(requireContext())
                .setTitle(isFrom ? "시작 시간" : "종료 시간")
                .setView(v)
                .setPositiveButton("확인", (d, w) -> {
                    String t = String.format(Locale.getDefault(), "%02d:%s",
                            npHour.getValue(), mins[npMin.getValue()]);
                    if (isFrom) { setText(textTimeFrom, t); current.timeFrom = t; }
                    else        { setText(textTimeTo,   t); current.timeTo   = t; }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void showPopup(View anchor, String[] items, OnItemSelected listener) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        for (String item : items) popup.getMenu().add(item);
        popup.setOnMenuItemClickListener(mi -> {
            listener.onSelected(mi.getTitle().toString());
            return true;
        });
        popup.show();
    }

    private void setText(TextView tv, String text) { if (tv != null) tv.setText(text); }
    private String textOf(TextView tv) { return tv == null ? ALL : tv.getText().toString().trim(); }
    private String nullToAll(String s) { return (s == null || s.trim().isEmpty()) ? ALL : s.trim(); }
    private String parseFilter(String s) { return ALL.equals(s) ? null : s; }
    private Integer parseSkill(String s) {
        if (s == null || ALL.equals(s)) return null;
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    interface OnItemSelected { void onSelected(String item); }

    private void initDistrictMap() {
        districtMap = new HashMap<>();
        districtMap.put("서울", Arrays.asList("전체","강남구","강동구","강북구","강서구","관악구","광진구","구로구","금천구","노원구","도봉구","동대문구","동작구","마포구","서대문구","서초구","성동구","성북구","송파구","양천구","영등포구","용산구","은평구","종로구","중구","중랑구"));
        districtMap.put("부산", Arrays.asList("전체","강서구","금정구","기장군","남구","동구","동래구","부산진구","북구","사상구","사하구","서구","수영구","연제구","영도구","중구","해운대구"));
        districtMap.put("대구", Arrays.asList("전체","남구","달서구","달성군","동구","북구","서구","수성구","중구"));
        districtMap.put("인천", Arrays.asList("전체","강화군","계양구","남동구","동구","미추홀구","부평구","서구","연수구","옹진군","중구"));
        districtMap.put("광주", Arrays.asList("전체","광산구","남구","동구","북구","서구"));
        districtMap.put("대전", Arrays.asList("전체","대덕구","동구","서구","유성구","중구"));
        districtMap.put("울산", Arrays.asList("전체","남구","동구","북구","중구","울주군"));
        districtMap.put("세종", Arrays.asList("전체","세종시"));
        districtMap.put("경기도", Arrays.asList("전체","가평군","고양시","과천시","광명시","광주시","구리시","군포시","김포시","남양주시","동두천시","부천시","성남시","수원시","시흥시","안산시","안성시","안양시","양주시","양평군","여주시","연천군","오산시","용인시","의왕시","의정부시","이천시","파주시","평택시","포천시","하남시","화성시"));
        districtMap.put("강원도", Arrays.asList("전체","강릉시","고성군","동해시","삼척시","속초시","양구군","양양군","영월군","원주시","인제군","정선군","철원군","춘천시","태백시","평창군","홍천군","화천군","횡성군"));
        districtMap.put("충북", Arrays.asList("전체","괴산군","단양군","보은군","영동군","옥천군","음성군","제천시","증평군","진천군","청주시","충주시"));
        districtMap.put("충남", Arrays.asList("전체","계룡시","공주시","금산군","논산시","당진시","보령시","부여군","서산시","서천군","아산시","예산군","천안시","청양군","태안군","홍성군"));
        districtMap.put("전북", Arrays.asList("전체","고창군","군산시","김제시","남원시","무주군","부안군","순창군","완주군","익산시","임실군","장수군","전주시","정읍시","진안군"));
        districtMap.put("전남", Arrays.asList("전체","강진군","고흥군","곡성군","광양시","구례군","나주시","담양군","목포시","무안군","보성군","순천시","신안군","여수시","영광군","영암군","완도군","장성군","장흥군","진도군","함평군","해남군","화순군"));
        districtMap.put("경북", Arrays.asList("전체","경산시","경주시","고령군","구미시","군위군","김천시","문경시","봉화군","상주시","성주군","안동시","영덕군","영양군","영주시","영천시","예천군","울릉군","울진군","의성군","청도군","청송군","칠곡군","포항시"));
        districtMap.put("경남", Arrays.asList("전체","거제시","거창군","고성군","김해시","남해군","밀양시","사천시","산청군","양산시","의령군","진주시","창녕군","창원시","통영시","하동군","함안군","함양군","합천군"));
        districtMap.put("제주도", Arrays.asList("전체","서귀포시","제주시"));
    }
}