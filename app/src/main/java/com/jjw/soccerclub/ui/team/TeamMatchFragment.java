package com.jjw.soccerclub.ui.team;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.model.Team;
import com.jjw.soccerclub.model.TeamMatchCondition;
import com.jjw.soccerclub.util.AppUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeamMatchFragment extends Fragment
        implements TeamMatchFindDialog.OnTeamMatchFilterSelected {

    private RecyclerView recyclerView;
    private TextView textInfo;
    private TeamMatchAdapter adapter;
    private final List<Team> teamList = new ArrayList<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    private String myTeamId;
    private TeamMatchCondition myCondition = new TeamMatchCondition();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_team_match, container, false);

        recyclerView = v.findViewById(R.id.recyclerTeams);
        textInfo     = v.findViewById(R.id.textInfo);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TeamMatchAdapter(requireContext(), teamList, this::onProposeMatchClicked);
        recyclerView.setAdapter(adapter);
        textInfo.setVisibility(View.GONE);

        loadMyTeamAndPref();
        return v;
    }

    public void openFilterDialog() {
        TeamMatchFindDialog dialog = TeamMatchFindDialog.newInstance(myCondition);
        dialog.show(getChildFragmentManager(), "TeamMatchFindDialog");
    }

    private void loadMyTeamAndPref() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            textInfo.setVisibility(View.VISIBLE);
            textInfo.setText("로그인이 필요합니다.");
            return;
        }

        db.collection("profiles").document(user.getUid()).get()
                .addOnSuccessListener(p -> {
                    String teamId = p.getString("myTeam");
                    if (AppUtils.isEmpty(teamId)) {
                        textInfo.setVisibility(View.VISIBLE);
                        textInfo.setText("팀이 없습니다.\n팀을 먼저 생성하거나 가입해 주세요.");
                        return;
                    }
                    myTeamId = teamId;
                    db.collection("teams").document(myTeamId).get()
                            .addOnSuccessListener(doc -> {
                                myCondition = readConditionFromDoc(doc);
                                runTeamQuery();
                            });
                });
    }

    private TeamMatchCondition readConditionFromDoc(DocumentSnapshot d) {
        TeamMatchCondition c = new TeamMatchCondition();
        c.regionCity     = getOrNull(d, "matchPref_regionCity");
        c.regionDistrict = getOrNull(d, "matchPref_regionDistrict");
        Long s1 = d.getLong("matchPref_skillMin");
        Long s2 = d.getLong("matchPref_skillMax");
        if (s1 != null) c.skillMin = s1.intValue();
        if (s2 != null) c.skillMax = s2.intValue();
        c.weekday  = getOrNull(d, "matchPref_weekday");
        c.dateFrom = getOrNull(d, "matchPref_dateFrom");
        c.dateTo   = getOrNull(d, "matchPref_dateTo");
        c.timeFrom = getOrNull(d, "matchPref_timeFrom");
        c.timeTo   = getOrNull(d, "matchPref_timeTo");
        return c;
    }

    private String getOrNull(DocumentSnapshot d, String key) {
        String v = d.getString(key);
        if (v == null) return null;
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    private void runTeamQuery() {
        textInfo.setVisibility(View.VISIBLE);
        textInfo.setText("조건에 맞는 팀을 찾는 중...");
        teamList.clear();
        adapter.notifyDataSetChanged();

        Query q = db.collection("teams");

        if (!AppUtils.isEmpty(myCondition.regionCity))
            q = q.whereEqualTo("matchPref_regionCity", myCondition.regionCity);
        if (!AppUtils.isEmpty(myCondition.regionDistrict))
            q = q.whereEqualTo("matchPref_regionDistrict", myCondition.regionDistrict);
        if (myCondition.skillMin != null)
            q = q.whereEqualTo("matchPref_skillMin", myCondition.skillMin);
        if (myCondition.skillMax != null)
            q = q.whereEqualTo("matchPref_skillMax", myCondition.skillMax);
        if (!AppUtils.isEmpty(myCondition.weekday) && !"전체".equals(myCondition.weekday))
            q = q.whereEqualTo("matchPref_weekday", myCondition.weekday);
        if (!AppUtils.isEmpty(myCondition.dateFrom))
            q = q.whereEqualTo("matchPref_dateFrom", myCondition.dateFrom);
        if (!AppUtils.isEmpty(myCondition.dateTo))
            q = q.whereEqualTo("matchPref_dateTo", myCondition.dateTo);
        if (!AppUtils.isEmpty(myCondition.timeFrom))
            q = q.whereEqualTo("matchPref_timeFrom", myCondition.timeFrom);
        if (!AppUtils.isEmpty(myCondition.timeTo))
            q = q.whereEqualTo("matchPref_timeTo", myCondition.timeTo);

        q.get().addOnSuccessListener(qs -> {
            teamList.clear();
            for (DocumentSnapshot d : qs) {
                if (d.getId().equals(myTeamId)) continue;
                Team t = d.toObject(Team.class);
                if (t != null) teamList.add(t);
            }
            adapter.notifyDataSetChanged();
            if (teamList.isEmpty()) {
                textInfo.setVisibility(View.VISIBLE);
                textInfo.setText("조건에 맞는 팀이 없습니다.");
            } else {
                textInfo.setVisibility(View.GONE);
            }
        }).addOnFailureListener(e -> {
            textInfo.setVisibility(View.VISIBLE);
            textInfo.setText("팀 목록을 불러오는 중 오류가 발생했습니다.");
        });
    }

    @Override
    public void onTeamMatchFilterSelected(@Nullable TeamMatchCondition condition) {
        if (condition != null) myCondition = condition;
        saveConditionToTeam();
        runTeamQuery();
    }

    private void saveConditionToTeam() {
        if (AppUtils.isEmpty(myTeamId)) return;
        Map<String, Object> m = new HashMap<>();
        m.put("matchPref_regionCity",     norm(myCondition.regionCity));
        m.put("matchPref_regionDistrict", norm(myCondition.regionDistrict));
        m.put("matchPref_skillMin",       myCondition.skillMin);
        m.put("matchPref_skillMax",       myCondition.skillMax);
        m.put("matchPref_weekday",
                (AppUtils.isEmpty(myCondition.weekday) || "전체".equals(myCondition.weekday))
                        ? null : myCondition.weekday);
        m.put("matchPref_dateFrom", norm(myCondition.dateFrom));
        m.put("matchPref_dateTo",   norm(myCondition.dateTo));
        m.put("matchPref_timeFrom", norm(myCondition.timeFrom));
        m.put("matchPref_timeTo",   norm(myCondition.timeTo));

        db.collection("teams").document(myTeamId).update(m)
                .addOnFailureListener(e ->
                        CustomToast.error(requireContext(), "조건 저장에 실패했습니다."));
    }

    private String norm(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private void onProposeMatchClicked(@NonNull Team team) {
        String name = AppUtils.nz(team.getTeamName(), "상대팀");
        CustomToast.info(requireContext(), name + " 팀에 시합 제안을 보냅니다.");
    }

    // ===== 내부 어댑터 =====
    public static class TeamMatchAdapter extends RecyclerView.Adapter<TeamMatchAdapter.VH> {

        public interface OnTeamActionListener {
            void onProposeMatch(Team team);
        }

        private final Context context;
        private final List<Team> items;
        private final OnTeamActionListener listener;

        public TeamMatchAdapter(Context context, List<Team> items, OnTeamActionListener listener) {
            this.context  = context;
            this.items    = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.team_item, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Team item = items.get(position);

            h.teamName.setText(AppUtils.safe(item.getTeamName()));
            h.teamRegion.setText(AppUtils.safe(item.getRegion()));

            Integer avg   = item.getSkillAverage();
            String ageRange = AppUtils.nz(item.getAgeRange(), "미정");
            String skillStr = (avg != null && avg > 0) ? String.valueOf(avg) : "-";
            h.teamSkillAge.setText("실력: " + skillStr + "   나이: " + ageRange);

            String logoUrl = item.getLogoUrl();
            if (!AppUtils.isEmpty(logoUrl)) {
                Glide.with(context).load(logoUrl)
                        .placeholder(R.drawable.ic_shield_gray).into(h.teamLogo);
            } else {
                h.teamLogo.setImageResource(R.drawable.ic_shield_gray);
            }

            h.btnProposeMatch.setVisibility(View.VISIBLE);
            h.btnProposeMatch.setOnClickListener(v -> {
                if (listener != null) listener.onProposeMatch(item);
            });
        }

        @Override
        public int getItemCount() { return items == null ? 0 : items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            ImageView teamLogo;
            TextView teamName, teamRegion, teamSkillAge;
            Button btnProposeMatch;

            VH(@NonNull View v) {
                super(v);
                teamLogo      = v.findViewById(R.id.teamLogo);
                teamName      = v.findViewById(R.id.teamName);
                teamRegion    = v.findViewById(R.id.teamRegion);
                teamSkillAge  = v.findViewById(R.id.teamSkillAge);
                btnProposeMatch = v.findViewById(R.id.btnProposeMatch);
            }
        }
    }
}