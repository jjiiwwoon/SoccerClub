package com.jjw.soccerclub.ui.recruit;

import android.app.DatePickerDialog;
import android.os.Bundle;
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
        restorePositionChip(nullToAll(current.position));
        selectWeekdayChip(nullToAll(current.weekday));
    }

    private void setupListeners() {
        String[] cities = requireContext().getResources().getStringArray(R.array.city_array);
        String[] skillItems = new String[12];
        skillItems[0] = ALL;
        for (int i = 1; i <= 10; i++) skillItems[i] = String.valueOf(i);
        skillItems[11] = "10";

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

    private void selectRecruitTypeChip(String type) {
        current.recruitType = ALL.equals(type) ? "전체" : type;
        setChipSelected(chipRecruitTypeAll,     ALL.equals(type));
        setChipSelected(chipRecruitTypeRegular, "regular".equals(type));
        setChipSelected(chipRecruitTypeMerc,    "mercenary".equals(type));
    }

    private void selectPositionChip(String pos) {
        current.position = ALL.equals(pos) ? "전체" : pos;
        setChipSelected(chipPosAll, ALL.equals(pos));
        setChipSelected(chipPosGK,  "GK".equals(pos));
        setChipSelected(chipPosDF,  "DF".equals(pos));
        setChipSelected(chipPosMF,  "MF".equals(pos));
        setChipSelected(chipPosFW,  "FW".equals(pos));
    }

    private void restorePositionChip(String pos) {
        selectPositionChip(pos == null || pos.isEmpty() || ALL.equals(pos) ? ALL : pos);
    }

    private void selectWeekdayChip(String day) {
        current.weekday = ALL.equals(day) ? "전체" : day;
        setChipSelected(chipWeekAll, ALL.equals(day));
        setChipSelected(chipWeekMon, "월".equals(day));
        setChipSelected(chipWeekTue, "화".equals(day));
        setChipSelected(chipWeekWed, "수".equals(day));
        setChipSelected(chipWeekThu, "목".equals(day));
        setChipSelected(chipWeekFri, "금".equals(day));
        setChipSelected(chipWeekSat, "토".equals(day));
        setChipSelected(chipWeekSun, "일".equals(day));
    }

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
        districtMap.put("서울", Arrays.asList(ALL,"강남구","강동구","강북구","강서구","관악구","광진구","구로구","금천구","노원구","도봉구","동대문구","동작구","마포구","서대문구","서초구","성동구","성북구","송파구","양천구","영등포구","용산구","은평구","종로구","중구","중랑구"));
        districtMap.put("부산", Arrays.asList(ALL,"강서구","금정구","기장군","남구","동구","동래구","부산진구","북구","사상구","사하구","서구","수영구","연제구","영도구","중구","해운대구"));
        districtMap.put("경기도", Arrays.asList(ALL,"가평군","고양시","과천시","광명시","광주시","구리시","군포시","김포시","남양주시","동두천시","부천시","성남시","수원시","시흥시","안산시","안성시","안양시","양주시","양평군","여주시","연천군","오산시","용인시","의왕시","의정부시","이천시","파주시","평택시","포천시","하남시","화성시"));
    }
}