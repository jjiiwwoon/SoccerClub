package com.example.soccerclub.ui.team;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.adapter.TeamAdapter;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.viewmodel.AllTeamViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AllTeamFragment extends Fragment {

    private static final String ALL = "전체";

    private LinearLayout         btnCity, btnDistrict, btnSkill, btnAgeStart, btnAgeEnd;
    private TextView             textCity, textDistrict, textSkill, textAgeStart, textAgeEnd;
    private RecyclerView         recyclerViewTeams;
    private TeamAdapter          teamAdapter;
    private EditText             editTeamSearch;
    private FloatingActionButton btnCreateTeam;

    private AllTeamViewModel          viewModel;
    private Map<String, List<String>> districtMap;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_all_team, container, false);

        btnCity        = view.findViewById(R.id.btnCity);
        btnDistrict    = view.findViewById(R.id.btnDistrict);
        btnSkill       = view.findViewById(R.id.btnSkill);
        btnAgeStart    = view.findViewById(R.id.btnAgeStart);
        btnAgeEnd      = view.findViewById(R.id.btnAgeEnd);
        textCity       = view.findViewById(R.id.textCity);
        textDistrict   = view.findViewById(R.id.textDistrict);
        textSkill      = view.findViewById(R.id.textSkill);
        textAgeStart   = view.findViewById(R.id.textAgeStart);
        textAgeEnd     = view.findViewById(R.id.textAgeEnd);
        editTeamSearch = view.findViewById(R.id.editTeamSearch);
        btnCreateTeam  = view.findViewById(R.id.btnCreateTeam);
        recyclerViewTeams = view.findViewById(R.id.recyclerViewTeams);

        teamAdapter = new TeamAdapter(requireContext(), new ArrayList<>());
        teamAdapter.setOnItemClickListener(team -> {
            Intent intent = new Intent(requireContext(), TeamDetailActivity.class);
            intent.putExtra("teamId", team.getTeamId());
            startActivity(intent);
        });

        recyclerViewTeams.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewTeams.setAdapter(teamAdapter);

        if (btnCreateTeam != null) {
            btnCreateTeam.setOnClickListener(v -> {
                if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                    CustomToast.info(requireContext(), "로그인이 필요합니다.");
                    return;
                }
                startActivity(new Intent(requireContext(), CreateTeamActivity.class));
            });
        }

        initDistrictMap();
        setupFilterButtons();
        setupSearchBox();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AllTeamViewModel.class);

        viewModel.displayTeams.observe(getViewLifecycleOwner(), teams ->
                teamAdapter.updateList(teams != null ? teams : new ArrayList<>()));

        viewModel.startListeningIfNeeded();
    }

    // ── 검색 ─────────────────────────────────────────────────────────────────────

    private void setupSearchBox() {
        if (editTeamSearch == null) return;
        editTeamSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (viewModel != null) viewModel.setSearchQuery(s.toString());
            }
        });
    }

    // ── 필터 버튼 ─────────────────────────────────────────────────────────────────

    private void setupFilterButtons() {
        setupPopup(btnCity, textCity, getCities(), item -> {
            textCity.setText(item);
            textDistrict.setText(ALL);
            notifyFilterChanged();
        });

        setupPopup(btnDistrict, textDistrict,
                () -> getDistricts(safeText(textCity)), item -> {
                    textDistrict.setText(item);
                    notifyFilterChanged();
                });

        setupPopup(btnSkill, textSkill, getSkillOptions(), item -> {
            textSkill.setText(item);
            notifyFilterChanged();
        });

        setupPopup(btnAgeStart, textAgeStart, getAgeOptions(), item -> {
            textAgeStart.setText(item);
            notifyFilterChanged();
        });

        setupPopup(btnAgeEnd, textAgeEnd, getAgeOptions(), item -> {
            textAgeEnd.setText(item);
            notifyFilterChanged();
        });
    }

    private void notifyFilterChanged() {
        if (viewModel == null) return;
        viewModel.setFilter(
                safeText(textCity),
                safeText(textDistrict),
                safeText(textSkill),
                safeText(textAgeStart),
                safeText(textAgeEnd)
        );
    }

    // ── 팝업 헬퍼 ─────────────────────────────────────────────────────────────────

    interface ItemsProvider  { List<String> get(); }
    interface OnItemSelected { void onSelected(String item); }

    private void setupPopup(View anchor, TextView label,
                            List<String> items, OnItemSelected cb) {
        if (anchor == null) return;
        anchor.setOnClickListener(v -> showPopup(anchor, label, () -> items, cb));
    }

    private void setupPopup(View anchor, TextView label,
                            ItemsProvider provider, OnItemSelected cb) {
        if (anchor == null) return;
        anchor.setOnClickListener(v -> showPopup(anchor, label, provider, cb));
    }

    private void showPopup(View anchor, TextView label,
                           ItemsProvider provider, OnItemSelected cb) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        for (String item : provider.get()) popup.getMenu().add(item);
        popup.setOnMenuItemClickListener(menuItem -> {
            String selected = menuItem.getTitle().toString();
            if (label != null) label.setText(selected);
            cb.onSelected(selected);
            return true;
        });
        popup.show();
    }

    // ── 데이터 ───────────────────────────────────────────────────────────────────

    private void initDistrictMap() {
        districtMap = new HashMap<>();
        districtMap.put("서울", Arrays.asList("전체","강남구","강동구","강북구","강서구",
                "관악구","광진구","구로구","금천구","노원구","도봉구","동대문구",
                "동작구","마포구","서대문구","서초구","성동구","성북구","송파구",
                "양천구","영등포구","용산구","은평구","종로구","중구","중랑구"));
        districtMap.put("경기", Arrays.asList("전체","수원시","성남시","의정부시","안양시",
                "부천시","광명시","평택시","안산시","고양시","구리시","남양주시",
                "오산시","시흥시","군포시","의왕시","하남시","용인시","파주시",
                "이천시","안성시","김포시","화성시","광주시","양주시","포천시",
                "여주시","연천군","가평군","양평군"));
    }

    private List<String> getCities() {
        List<String> list = new ArrayList<>();
        list.add(ALL);
        list.addAll(districtMap.keySet());
        return list;
    }

    private List<String> getDistricts(String city) {
        if (city == null || city.equals(ALL) || !districtMap.containsKey(city)) {
            // ✅ 수정: List.of() → Arrays.asList()
            // List.of()는 API 30 미만에서 크래시 가능성 있음
            return new ArrayList<>(Arrays.asList(ALL));
        }
        return districtMap.get(city);
    }

    private List<String> getSkillOptions() {
        List<String> list = new ArrayList<>();
        list.add(ALL);
        for (int i = 1; i <= 5; i++) list.add(String.valueOf(i));
        return list;
    }

    private List<String> getAgeOptions() {
        List<String> list = new ArrayList<>();
        list.add(ALL);
        for (int i = 10; i <= 60; i += 5) list.add(i + "~");
        return list;
    }

    private String safeText(TextView tv) {
        return tv == null ? ALL : tv.getText().toString().trim();
    }
}