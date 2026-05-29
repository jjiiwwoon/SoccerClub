package com.jjw.soccerclub.ui.team;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.model.TeamMatchCondition;
import com.jjw.soccerclub.util.AppUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeamMatchFindDialog extends DialogFragment {

    private static final String ARG_CONDITION = "condition";

    public interface OnTeamMatchFilterSelected {
        void onTeamMatchFilterSelected(@Nullable TeamMatchCondition condition);
    }

    private TeamMatchCondition current;
    private TextView textCity, textDistrict, textSkillMin, textSkillMax;
    private TextView textDateFrom, textDateTo, textTimeFrom, textTimeTo;
    private TextView chipWeekAll, chipWeekMon, chipWeekTue, chipWeekWed;
    private TextView chipWeekThu, chipWeekFri, chipWeekSat, chipWeekSun;
    private TextView btnReset, btnApply, btnClose;
    private Map<String, List<String>> districtMap;

    public static TeamMatchFindDialog newInstance(@Nullable TeamMatchCondition cond) {
        TeamMatchFindDialog f = new TeamMatchFindDialog();
        Bundle b = new Bundle();
        if (cond != null) b.putSerializable(ARG_CONDITION, cond);
        f.setArguments(b);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View root = LayoutInflater.from(requireContext())
                .inflate(R.layout.sheet_filter_match, null, false);

        if (getArguments() != null) {
            java.io.Serializable s = getArguments().getSerializable(ARG_CONDITION);
            if (s instanceof TeamMatchCondition) current = (TeamMatchCondition) s;
        }
        if (current == null) current = new TeamMatchCondition();

        bindViews(root);
        initDistrictMap();
        restoreFromCurrent();
        setupListeners();

        return new AlertDialog.Builder(requireContext())
                .setView(root).create();
    }

    private void bindViews(View root) {
        TextView tvTitle = root.findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setText("팀 찾기");

        textCity     = root.findViewById(R.id.textCity);
        textDistrict = root.findViewById(R.id.textDistrict);
        textSkillMin = root.findViewById(R.id.textSkillMin);
        textSkillMax = root.findViewById(R.id.textSkillMax);
        textDateFrom = root.findViewById(R.id.textDateFrom);
        textDateTo   = root.findViewById(R.id.textDateTo);
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
        setText(textCity,     AppUtils.nz(current.regionCity, "전체"));
        setText(textDistrict, AppUtils.nz(current.regionDistrict, "전체"));
        setText(textSkillMin, current.skillMin != null ? String.valueOf(current.skillMin) : "전체");
        setText(textSkillMax, current.skillMax != null ? String.valueOf(current.skillMax) : "전체");
        setText(textDateFrom, AppUtils.nz(current.dateFrom, "전체"));
        setText(textDateTo,   AppUtils.nz(current.dateTo,   "전체"));
        setText(textTimeFrom, AppUtils.nz(current.timeFrom, "전체"));
        setText(textTimeTo,   AppUtils.nz(current.timeTo,   "전체"));
        selectWeekdayChip(AppUtils.nz(current.weekday, "전체"));
    }

    private void setupListeners() {
        String[] cities  = getResources().getStringArray(R.array.city_array);
        String[] skills  = getResources().getStringArray(R.array.skill_filter_array);
        String[] times   = getResources().getStringArray(R.array.time_array);

        setupPopup(textCity, cities, sel -> {
            setText(textCity, sel);
            setText(textDistrict, "전체");
            List<String> dists = districtMap.getOrDefault(sel, Arrays.asList("전체"));
            setupPopup(textDistrict, dists.toArray(new String[0]),
                    d -> setText(textDistrict, d));
        });

        setupPopup(textSkillMin, skills, sel -> setText(textSkillMin, sel));
        setupPopup(textSkillMax, skills, sel -> setText(textSkillMax, sel));
        setupPopup(textTimeFrom, times,  sel -> setText(textTimeFrom, sel));
        setupPopup(textTimeTo,   times,  sel -> setText(textTimeTo,   sel));

        chipWeekAll.setOnClickListener(v -> selectWeekdayChip("전체"));
        chipWeekMon.setOnClickListener(v -> selectWeekdayChip("월"));
        chipWeekTue.setOnClickListener(v -> selectWeekdayChip("화"));
        chipWeekWed.setOnClickListener(v -> selectWeekdayChip("수"));
        chipWeekThu.setOnClickListener(v -> selectWeekdayChip("목"));
        chipWeekFri.setOnClickListener(v -> selectWeekdayChip("금"));
        chipWeekSat.setOnClickListener(v -> selectWeekdayChip("토"));
        chipWeekSun.setOnClickListener(v -> selectWeekdayChip("일"));

        if (btnReset != null) btnReset.setOnClickListener(v -> {
            current = new TeamMatchCondition();
            restoreFromCurrent();
        });

        if (btnClose != null) btnClose.setOnClickListener(v -> dismiss());

        if (btnApply != null) btnApply.setOnClickListener(v -> {
            current.regionCity     = parseFilter(textCity);
            current.regionDistrict = parseFilter(textDistrict);
            current.skillMin       = parseSkill(textSkillMin);
            current.skillMax       = parseSkill(textSkillMax);
            current.dateFrom       = parseFilter(textDateFrom);
            current.dateTo         = parseFilter(textDateTo);
            current.timeFrom       = parseFilter(textTimeFrom);
            current.timeTo         = parseFilter(textTimeTo);

            androidx.fragment.app.Fragment parent = getParentFragment();
            if (parent instanceof OnTeamMatchFilterSelected) {
                ((OnTeamMatchFilterSelected) parent).onTeamMatchFilterSelected(current);
            }
            dismiss();
        });
    }

    private void selectWeekdayChip(String day) {
        current.weekday = "전체".equals(day) ? null : day;
        setChipSelected(chipWeekAll, "전체".equals(day));
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
        chip.setBackgroundResource(selected ? R.drawable.bg_badge_blue : R.drawable.bg_chip);
        chip.setTextColor(selected ? 0xFFFFFFFF : 0xFF263238);
    }

    private void setupPopup(TextView tv, String[] items, OnItemSelected listener) {
        if (tv == null) return;
        tv.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(requireContext(), tv);
            for (String item : items) popup.getMenu().add(item);
            popup.setOnMenuItemClickListener(mi -> {
                listener.onSelected(mi.getTitle().toString());
                return true;
            });
            popup.show();
        });
    }

    private void setText(TextView tv, String text) {
        if (tv != null) tv.setText(text);
    }

    private String parseFilter(TextView tv) {
        if (tv == null) return null;
        String s = tv.getText().toString().trim();
        return (s.isEmpty() || s.equals("전체")) ? null : s;
    }

    private Integer parseSkill(TextView tv) {
        if (tv == null) return null;
        String s = tv.getText().toString().trim();
        if (s.isEmpty() || s.equals("전체")) return null;
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    private void initDistrictMap() {
        districtMap = new HashMap<>();
        districtMap.put("서울", Arrays.asList("전체","강남구","강동구","강북구","강서구","관악구","광진구","구로구","금천구","노원구","도봉구","동대문구","동작구","마포구","서대문구","서초구","성동구","성북구","송파구","양천구","영등포구","용산구","은평구","종로구","중구","중랑구"));
        districtMap.put("부산", Arrays.asList("전체","강서구","금정구","기장군","남구","동구","동래구","부산진구","북구","사상구","사하구","서구","수영구","연제구","영도구","중구","해운대구"));
        districtMap.put("경기도", Arrays.asList("전체","가평군","고양시","과천시","광명시","광주시","구리시","군포시","김포시","남양주시","동두천시","부천시","성남시","수원시","시흥시","안산시","안성시","안양시","양주시","양평군","여주시","연천군","오산시","용인시","의왕시","의정부시","이천시","파주시","평택시","포천시","하남시","화성시"));
    }

    interface OnItemSelected { void onSelected(String item); }
}