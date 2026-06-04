package com.jjw.soccerclub.ui.common;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.StateLayout;
import com.jjw.soccerclub.repository.RecordsRepository.PlayerStat;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.viewmodel.RecordsViewModel;
import com.jjw.soccerclub.viewmodel.RecordsViewModel.SortKey;
import com.jjw.soccerclub.viewmodel.RecordsViewModel.TeamStats;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class RecordsActivity extends BaseActivity {

    // ── 뷰 ────────────────────────────────────────────────────────────────────────
    private StateLayout state;

    // 팀 전적 요약
    private TextView tvGames, tvWins, tvDraws, tvLosses, tvWinRate;
    private TextView tvTotalGoalsFor, tvTotalGoalsAgainst, tvGoalDiff;

    // 득점 TOP3
    private ImageView imageTop1, imageTop2, imageTop3;
    private TextView  tvTop1Name, tvTop2Name, tvTop3Name;
    private TextView  tvTop1Goals, tvTop2Goals, tvTop3Goals;

    // 도움 TOP3
    private ImageView imageAssistTop1, imageAssistTop2, imageAssistTop3;
    private TextView  tvAssistTop1Name, tvAssistTop2Name, tvAssistTop3Name;
    private TextView  tvAssistTop1Count, tvAssistTop2Count, tvAssistTop3Count;

    // 개인 기록 테이블
    private RecyclerView rvPersonal;
    private PersonalStatsAdapter personalAdapter;

    // 정렬 헤더 버튼
    private LinearLayout hGames, hGoals, hAssists;

    // ── ViewModel ────────────────────────────────────────────────────────────────
    private RecordsViewModel viewModel;

    // ── 생명주기 ──────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        bindViews();

        viewModel = new ViewModelProvider(this).get(RecordsViewModel.class);
        observeViewModel();

        String teamId = getIntent().getStringExtra("teamId");
        if (AppUtils.isEmpty(teamId))
            teamId = getIntent().getStringExtra("myTeamId");

        if (!AppUtils.isEmpty(teamId)) {
            viewModel.loadIfNeeded(teamId);
        } else {
            resolveTeamIdFromProfile();
        }
    }

    // ── ViewModel 관찰 ───────────────────────────────────────────────────────────

    private void observeViewModel() {

        viewModel.isLoading.observe(this, loading -> {
            if (state == null) return;
            if (Boolean.TRUE.equals(loading)) state.showLoading();
            else state.showContent();
        });

        viewModel.emptyMsg.observe(this, msg -> {
            if (msg != null) showEmpty(msg);
        });

        viewModel.teamStats.observe(this, this::bindTeamStats);

        viewModel.personalStats.observe(this, stats -> {
            if (personalAdapter != null) personalAdapter.submitList(stats);
            updateTop3(stats);
        });
    }

    // ── 팀 전적 요약 바인딩 ──────────────────────────────────────────────────────

    private void bindTeamStats(TeamStats s) {
        if (s == null) return;
        if (tvGames   != null) tvGames.setText(String.valueOf(s.games));
        if (tvWins    != null) tvWins.setText(String.valueOf(s.wins));
        if (tvDraws   != null) tvDraws.setText(String.valueOf(s.draws));
        if (tvLosses  != null) tvLosses.setText(String.valueOf(s.losses));
        if (tvWinRate != null) tvWinRate.setText(s.winRate());
        if (tvTotalGoalsFor     != null) tvTotalGoalsFor.setText(String.valueOf(s.gf));
        if (tvTotalGoalsAgainst != null) tvTotalGoalsAgainst.setText(String.valueOf(s.ga));
        if (tvGoalDiff != null) {
            int diff = s.goalDiff();
            tvGoalDiff.setText((diff >= 0 ? "+" : "") + diff);
            tvGoalDiff.setTextColor(diff > 0 ? 0xFF1565C0 : diff < 0 ? 0xFFC62828 : 0xFF424242);
        }
    }

    // ── TOP3 렌더링 ───────────────────────────────────────────────────────────────

    private void updateTop3(List<PlayerStat> stats) {
        List<PlayerStat> byGoals = new ArrayList<>(stats);
        byGoals.sort((a, b) -> Integer.compare(b.goals, a.goals));
        bindTop3(byGoals,
                imageTop1, tvTop1Name, tvTop1Goals,
                imageTop2, tvTop2Name, tvTop2Goals,
                imageTop3, tvTop3Name, tvTop3Goals, true);

        List<PlayerStat> byAssists = new ArrayList<>(stats);
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
        bindPlayer(sorted.size() > 0 ? sorted.get(0) : null, img1, name1, val1, isGoals);
        bindPlayer(sorted.size() > 1 ? sorted.get(1) : null, img2, name2, val2, isGoals);
        bindPlayer(sorted.size() > 2 ? sorted.get(2) : null, img3, name3, val3, isGoals);
    }

    private void bindPlayer(PlayerStat p,
                            ImageView img, TextView name, TextView val, boolean isGoals) {
        if (p == null) return;
        if (name != null) name.setText(AppUtils.safe(p.nickname));
        if (val  != null) val.setText((isGoals ? p.goals : p.assists) + (isGoals ? "골" : "도움"));
        if (img  != null && !TextUtils.isEmpty(p.photoUrl)) {
            Glide.with(this).load(p.photoUrl)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .circleCrop().into(img);
        }
    }

    // ── 정렬 헤더 ────────────────────────────────────────────────────────────────

    private void setupSortHeaders() {
        if (hGoals   != null) hGoals.setOnClickListener(v   -> viewModel.sort(SortKey.GOALS));
        if (hAssists != null) hAssists.setOnClickListener(v -> viewModel.sort(SortKey.ASSISTS));
        if (hGames   != null) hGames.setOnClickListener(v   -> viewModel.sort(SortKey.GAMES));
    }

    // ── teamId 없을 때 profiles 에서 조회 ────────────────────────────────────────

    private void resolveTeamIdFromProfile() {
        if (state != null) state.showLoading();
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (TextUtils.isEmpty(uid)) { showEmpty("로그인이 필요해요."); return; }

        FirebaseFirestore.getInstance().collection("profiles").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String teamId = AppUtils.safe(doc.getString("myTeam"));
                    if (!AppUtils.isEmpty(teamId)) viewModel.loadIfNeeded(teamId);
                    else showEmpty("소속 팀이 없어요.");
                });
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

    // ── 내부 어댑터 ──────────────────────────────────────────────────────────────

    private class PersonalStatsAdapter
            extends RecyclerView.Adapter<PersonalStatsAdapter.VH> {

        private List<PlayerStat> list = new ArrayList<>();

        void submitList(List<PlayerStat> newList) {
            this.list = newList != null ? newList : new ArrayList<>();
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

        class VH extends RecyclerView.ViewHolder {
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

    private void showEmpty(String msg) {
        if (state != null) { state.setEmptyMessage(msg); state.showEmpty(); }
    }
}