package com.jjw.soccerclub.ui.team;

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

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.adapter.TeamAdapter;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.viewmodel.AllTeamViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

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
                // 팀이 있는지 확인 후 진행
                String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                FirebaseFirestore.getInstance().collection("profiles").document(uid).get()
                        .addOnSuccessListener(doc -> {
                            if (!isAdded()) return;
                            String myTeam = doc.getString("myTeam");
                            if (!AppUtils.isEmpty(myTeam)) {
                                CustomToast.info(requireContext(), "이미 팀이 있습니다.");
                            } else {
                                startActivity(new Intent(requireContext(), CreateTeamActivity.class));
                            }
                        });
            });
        }

        initDistrictMap();
        setupFilterButtons();
        setupSearchBox();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
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

    private List<String> getCities() {
        List<String> list = new ArrayList<>();
        list.add(ALL);
        list.addAll(districtMap.keySet());
        return list;
    }

    private List<String> getDistricts(String city) {
        if (city == null || city.equals(ALL) || !districtMap.containsKey(city)) {
            return new ArrayList<>(Arrays.asList(ALL));
        }
        return districtMap.get(city);
    }

    private List<String> getSkillOptions() {
        List<String> list = new ArrayList<>();
        list.add(ALL);
        for (int i = 1; i <= 10; i++) list.add(String.valueOf(i));
        return list;
    }

    private List<String> getAgeOptions() {
        List<String> list = new ArrayList<>();
        list.add(ALL);
        for (int i = 10; i <= 60; i += 10) list.add(i + "~");
        return list;
    }

    private String safeText(TextView tv) {
        return tv == null ? ALL : tv.getText().toString().trim();
    }
}