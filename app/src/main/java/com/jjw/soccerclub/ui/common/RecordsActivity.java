package com.jjw.soccerclub.ui.common;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.StateLayout;
import com.jjw.soccerclub.util.AppUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.android.gms.tasks.Tasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 팀 전적 상세 화면.
 *
 * Capstone Records.java 의 기능을 SoccerClub 패키지 구조에 맞게 이식.
 *
 * 포함 기능:
 *   - 팀 전적 요약 (경기/승/무/패/승률/득점/실점/득실차)
 *   - 득점 TOP3 포디움 (왕관 + 프로필 사진)
 *   - 도움 TOP3 포디움 (왕관 + 프로필 사진)
 *   - 개인 기록 테이블 (경기/득점/도움 정렬)
 */
public class RecordsActivity extends AppCompatActivity {

    // ── 뷰 ────────────────────────────────────────────────────────────────────────
    private StateLayout state;

    // 팀 전적 요약
    private TextView tvGames, tvWins, tvDraws, tvLosses, tvWinRate;
    private TextView tvTotalGoalsFor, tvTotalGoalsAgainst, tvGoalDiff;

    // 득점 TOP3
    private ImageView imageTop1, imageTop2, imageTop3;
    private TextView  tvTop1Name, tvTop2Name, tvTop3Name;
    private TextView  tvTop1Goals, tvTop2Goals, tvTop3Goals;
    private View      layoutTop1Crown, layoutTop2Crown, layoutTop3Crown;

    // 도움 TOP3
    private ImageView imageAssistTop1, imageAssistTop2, imageAssistTop3;
    private TextView  tvAssistTop1Name, tvAssistTop2Name, tvAssistTop3Name;
    private TextView  tvAssistTop1Count, tvAssistTop2Count, tvAssistTop3Count;
    private View      layoutAssist1Crown, layoutAssist2Crown, layoutAssist3Crown;

    // 개인 기록 테이블
    private RecyclerView rvPersonal;
    private PersonalStatsAdapter personalAdapter;

    // 정렬 헤더 버튼
    private LinearLayout hGames, hGoals, hAssists;

    // ── Firebase ──────────────────────────────────────────────────────────────────
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration statsReg;
    private String myTeamId = "";

    // ── 데이터 ────────────────────────────────────────────────────────────────────

    /** 팀원 UID → {nickname, photoUrl} */
    private final Map<String, MemberInfo> members = new HashMap<>();

    /** 개인 기록 목록 */
    private final List<PlayerStat> personalData = new ArrayList<>();

    private SortKey currentKey = SortKey.GOALS;
    private SortDir currentDir = SortDir.DESC;

    // ── 생명주기 ──────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        bindViews();
        if (state != null) state.showLoading();

        myTeamId = getIntent().getStringExtra("teamId");
        if (AppUtils.isEmpty(myTeamId))
            myTeamId = getIntent().getStringExtra("myTeamId");

        if (AppUtils.isEmpty(myTeamId)) {
            String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                    ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
            if (!TextUtils.isEmpty(uid)) {
                db.collection("profiles").document(uid).get()
                        .addOnSuccessListener(doc -> {
                            myTeamId = AppUtils.safe(doc.getString("myTeam"));
                            if (!AppUtils.isEmpty(myTeamId)) startLoad();
                            else showEmpty("소속 팀이 없어요.");
                        });
            } else showEmpty("로그인이 필요해요.");
        } else {
            startLoad();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (statsReg != null) statsReg.remove();
    }

    // ── 뷰 바인딩 ────────────────────────────────────────────────────────────────

    private void bindViews() {
        state               = findViewById(R.id.stateLayout);

        tvGames             = findViewById(R.id.tvGames);
        tvWins              = findViewById(R.id.tvWins);
        tvDraws             = findViewById(R.id.tvDraws);
        tvLosses            = findViewById(R.id.tvLosses);
        tvWinRate           = findViewById(R.id.tvWinRate);
        tvTotalGoalsFor     = findViewById(R.id.tvTotalGoalsFor);
        tvTotalGoalsAgainst = findViewById(R.id.tvTotalGoalsAgainst);
        tvGoalDiff          = findViewById(R.id.tvGoalDiff);

        imageTop1           = findViewById(R.id.imageTop1);
        imageTop2           = findViewById(R.id.imageTop2);
        imageTop3           = findViewById(R.id.imageTop3);
        tvTop1Name          = findViewById(R.id.tvTop1Name);
        tvTop2Name          = findViewById(R.id.tvTop2Name);
        tvTop3Name          = findViewById(R.id.tvTop3Name);
        tvTop1Goals         = findViewById(R.id.tvTop1Goals);
        tvTop2Goals         = findViewById(R.id.tvTop2Goals);
        tvTop3Goals         = findViewById(R.id.tvTop3Goals);

        imageAssistTop1     = findViewById(R.id.imageAssistTop1);
        imageAssistTop2     = findViewById(R.id.imageAssistTop2);
        imageAssistTop3     = findViewById(R.id.imageAssistTop3);
        tvAssistTop1Name    = findViewById(R.id.tvAssistTop1Name);
        tvAssistTop2Name    = findViewById(R.id.tvAssistTop2Name);
        tvAssistTop3Name    = findViewById(R.id.tvAssistTop3Name);
        tvAssistTop1Count   = findViewById(R.id.tvAssistTop1Count);
        tvAssistTop2Count   = findViewById(R.id.tvAssistTop2Count);
        tvAssistTop3Count   = findViewById(R.id.tvAssistTop3Count);

        rvPersonal          = findViewById(R.id.rvPersonal);
        hGames              = findViewById(R.id.hGames);
        hGoals              = findViewById(R.id.hGoals);
        hAssists            = findViewById(R.id.hAssists);

        if (rvPersonal != null) {
            rvPersonal.setLayoutManager(new LinearLayoutManager(this));
            personalAdapter = new PersonalStatsAdapter();
            rvPersonal.setAdapter(personalAdapter);
        }

        setupSortHeaders();
    }

    // ── 데이터 로드 ───────────────────────────────────────────────────────────────

    private void startLoad() {
        loadTeamStats();
        loadTeamMembers();
    }

    /** 팀 전적 실시간 리스너 */
    private void loadTeamStats() {
        statsReg = db.collection("teamStats").document(myTeamId)
                .addSnapshotListener((doc, e) -> {
                    if (doc == null || !doc.exists()) return;

                    int games  = safeInt(doc.getLong("games"));
                    int wins   = safeInt(doc.getLong("wins"));
                    int draws  = safeInt(doc.getLong("draws"));
                    int losses = safeInt(doc.getLong("losses"));
                    int gf     = safeInt(doc.getLong("goalsFor"));
                    int ga     = safeInt(doc.getLong("goalsAgainst"));

                    if (tvGames  != null) tvGames.setText(String.valueOf(games));
                    if (tvWins   != null) tvWins.setText(String.valueOf(wins));
                    if (tvDraws  != null) tvDraws.setText(String.valueOf(draws));
                    if (tvLosses != null) tvLosses.setText(String.valueOf(losses));
                    if (tvWinRate != null)
                        tvWinRate.setText(games > 0
                                ? Math.round((wins * 100f) / games) + "%" : "-");
                    if (tvTotalGoalsFor     != null)
                        tvTotalGoalsFor.setText(String.valueOf(gf));
                    if (tvTotalGoalsAgainst != null)
                        tvTotalGoalsAgainst.setText(String.valueOf(ga));
                    if (tvGoalDiff != null) {
                        int diff = gf - ga;
                        tvGoalDiff.setText((diff >= 0 ? "+" : "") + diff);
                        tvGoalDiff.setTextColor(diff > 0 ? 0xFF1565C0 : diff < 0 ? 0xFFC62828 : 0xFF424242);
                    }
                });
    }

    /** 팀원 목록 로드 → 개인 기록 집계 */
    private void loadTeamMembers() {
        db.collection("profiles")
                .whereEqualTo("myTeam", myTeamId)
                .get()
                .addOnSuccessListener(snap -> {
                    members.clear();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        MemberInfo m = new MemberInfo();
                        m.uid      = d.getId();
                        m.nickname = AppUtils.safe(d.getString("nickname"));
                        m.photoUrl = AppUtils.safe(d.getString("profileImageUrl"));
                        members.put(m.uid, m);
                    }
                    loadPersonalStats();
                });
    }

    /** 개인 기록 집계 — matches 컬렉션의 goalEvents 기반 */
    @SuppressWarnings("unchecked")
    private void loadPersonalStats() {
        db.collection("matches")
                .whereEqualTo("teamId", myTeamId)
                .whereEqualTo("status", "finished")
                .orderBy("matchTs", Query.Direction.DESCENDING)
                .limit(300)
                .get()
                .addOnSuccessListener(snap -> {
                    Set<String> memberUids = members.keySet();
                    Map<String, PlayerStat> map = new HashMap<>();

                    // 팀원 전원 초기화 (0기록도 표시)
                    for (MemberInfo m : members.values()) {
                        PlayerStat ps = new PlayerStat();
                        ps.uid      = m.uid;
                        ps.nickname = m.nickname;
                        ps.photoUrl = m.photoUrl;
                        map.put(m.uid, ps);
                    }

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String matchId = d.getId();

                        // 경기 참여 카운트 (팀원 전원)
                        for (String uid : memberUids) {
                            if (map.containsKey(uid)) map.get(uid).games++;
                        }

                        // goalEvents 기반 득점/도움
                        List<Map<String, Object>> events =
                                (List<Map<String, Object>>) d.get("goalEvents");
                        if (events != null) {
                            for (Map<String, Object> ev : events) {
                                String scorerId  = safe(ev, "scorerId");
                                String assistId  = safe(ev, "assistId");
                                String scorerNick = safe(ev, "scorerNickname");
                                String assistNick = safe(ev, "assistNickname");

                                if (!TextUtils.isEmpty(scorerId) && memberUids.contains(scorerId)) {
                                    PlayerStat ps = map.get(scorerId);
                                    if (ps != null) ps.goals++;
                                }
                                if (!TextUtils.isEmpty(assistId) && memberUids.contains(assistId)) {
                                    PlayerStat ps = map.get(assistId);
                                    if (ps != null) ps.assists++;
                                }
                            }
                        }
                    }

                    personalData.clear();
                    personalData.addAll(map.values());
                    sortAndRender();

                    if (state != null) state.showContent();
                })
                .addOnFailureListener(e -> {
                    if (state != null) state.showContent();
                });
    }

    // ── 정렬 + 렌더링 ────────────────────────────────────────────────────────────

    private void sortAndRender() {
        personalData.sort((a, b) -> {
            int va, vb;
            switch (currentKey) {
                case GAMES:   va = a.games;   vb = b.games;   break;
                case ASSISTS: va = a.assists; vb = b.assists; break;
                default:      va = a.goals;   vb = b.goals;   break;
            }
            int c = Integer.compare(va, vb);
            if (currentDir == SortDir.DESC) c = -c;
            return c != 0 ? c : a.nickname.compareTo(b.nickname);
        });

        if (personalAdapter != null) personalAdapter.submitList(new ArrayList<>(personalData));
        updateTop3();
    }

    /** 득점 TOP3 + 도움 TOP3 계산 및 렌더링 */
    private void updateTop3() {
        // 득점 TOP3
        List<PlayerStat> byGoals = new ArrayList<>(personalData);
        byGoals.sort((a, b) -> Integer.compare(b.goals, a.goals));
        bindTop3(byGoals,
                imageTop1, tvTop1Name, tvTop1Goals,
                imageTop2, tvTop2Name, tvTop2Goals,
                imageTop3, tvTop3Name, tvTop3Goals, true);

        // 도움 TOP3
        List<PlayerStat> byAssists = new ArrayList<>(personalData);
        byAssists.sort((a, b) -> Integer.compare(b.assists, a.assists));
        bindTop3(byAssists,
                imageAssistTop1, tvAssistTop1Name, tvAssistTop1Count,
                imageAssistTop2, tvAssistTop2Name, tvAssistTop2Count,
                imageAssistTop3, tvAssistTop3Name, tvAssistTop3Count, false);
    }

    private void bindTop3(List<PlayerStat> sorted,
                          ImageView img1, TextView name1, TextView val1,
                          ImageView img2, TextView name2, TextView val2,
                          ImageView img3, TextView name3, TextView val3,
                          boolean isGoals) {
        PlayerStat p1 = sorted.size() > 0 ? sorted.get(0) : null;
        PlayerStat p2 = sorted.size() > 1 ? sorted.get(1) : null;
        PlayerStat p3 = sorted.size() > 2 ? sorted.get(2) : null;
        String unit = isGoals ? "골" : "도움";

        bindPlayer(p1, img1, name1, val1, unit);
        bindPlayer(p2, img2, name2, val2, unit);
        bindPlayer(p3, img3, name3, val3, unit);
    }

    private void bindPlayer(PlayerStat p,
                            ImageView img, TextView name, TextView val, String unit) {
        if (p == null) return;
        if (name != null) name.setText(AppUtils.safe(p.nickname));
        if (val  != null) val.setText((unit.equals("골") ? p.goals : p.assists) + unit);
        if (img  != null && !TextUtils.isEmpty(p.photoUrl)) {
            Glide.with(this).load(p.photoUrl)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .circleCrop().into(img);
        }
    }

    // ── 정렬 헤더 설정 ────────────────────────────────────────────────────────────

    private void setupSortHeaders() {
        if (hGoals != null) hGoals.setOnClickListener(v -> {
            if (currentKey == SortKey.GOALS)
                currentDir = currentDir == SortDir.DESC ? SortDir.ASC : SortDir.DESC;
            else { currentKey = SortKey.GOALS; currentDir = SortDir.DESC; }
            sortAndRender();
        });
        if (hAssists != null) hAssists.setOnClickListener(v -> {
            if (currentKey == SortKey.ASSISTS)
                currentDir = currentDir == SortDir.DESC ? SortDir.ASC : SortDir.DESC;
            else { currentKey = SortKey.ASSISTS; currentDir = SortDir.DESC; }
            sortAndRender();
        });
        if (hGames != null) hGames.setOnClickListener(v -> {
            if (currentKey == SortKey.GAMES)
                currentDir = currentDir == SortDir.DESC ? SortDir.ASC : SortDir.DESC;
            else { currentKey = SortKey.GAMES; currentDir = SortDir.DESC; }
            sortAndRender();
        });
    }

    // ── 내부 데이터 모델 ──────────────────────────────────────────────────────────

    private enum SortKey { GAMES, GOALS, ASSISTS }
    private enum SortDir { ASC, DESC }

    public static class MemberInfo {
        public String uid, nickname, photoUrl;
    }

    public static class PlayerStat {
        public String uid = "", nickname = "", photoUrl = "";
        public int games = 0, goals = 0, assists = 0;
    }

    // ── PersonalStatsAdapter (내부 클래스) ────────────────────────────────────────

    private class PersonalStatsAdapter
            extends androidx.recyclerview.widget.RecyclerView.Adapter<PersonalStatsAdapter.VH> {

        private List<PlayerStat> list = new ArrayList<>();

        void submitList(List<PlayerStat> newList) {
            this.list = newList;
            notifyDataSetChanged();
        }

        @androidx.annotation.NonNull
        @Override
        public VH onCreateViewHolder(@androidx.annotation.NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_player_stat, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@androidx.annotation.NonNull VH h, int pos) {
            PlayerStat p = list.get(pos);
            h.tvRank.setText(String.valueOf(pos + 1));
            h.tvNickname.setText(AppUtils.safe(p.nickname));
            h.tvGames.setText(String.valueOf(p.games));
            h.tvGoals.setText(String.valueOf(p.goals));
            h.tvAssists.setText(String.valueOf(p.assists));
            if (!TextUtils.isEmpty(p.photoUrl)) {
                Glide.with(RecordsActivity.this)
                        .load(p.photoUrl)
                        .placeholder(R.drawable.ic_person_placeholder)
                        .circleCrop()
                        .into(h.imgProfile);
            } else {
                h.imgProfile.setImageResource(R.drawable.ic_person_placeholder);
            }
        }

        @Override
        public int getItemCount() { return list.size(); }

        class VH extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            ImageView imgProfile;
            TextView tvRank, tvNickname, tvGames, tvGoals, tvAssists;
            VH(android.view.View v) {
                super(v);
                imgProfile  = v.findViewById(R.id.imgProfile);
                tvRank      = v.findViewById(R.id.tvRank);
                tvNickname  = v.findViewById(R.id.tvNickname);
                tvGames     = v.findViewById(R.id.tvGames);
                tvGoals     = v.findViewById(R.id.tvGoals);
                tvAssists   = v.findViewById(R.id.tvAssists);
            }
        }
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private int safeInt(Long v) { return v != null ? v.intValue() : 0; }

    private String safe(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof String ? (String) v : "";
    }

    private void showEmpty(String msg) {
        if (state != null) { state.setEmptyMessage(msg); state.showEmpty(); }
    }
}