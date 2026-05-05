package com.example.soccerclub.ui.common;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.adapter.TeamMemberListAdapter;
import com.example.soccerclub.common.StateLayout;
import com.example.soccerclub.util.AppUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordsActivity extends AppCompatActivity {

    private StateLayout state;
    private TextView tvGames, tvWins, tvDraws, tvLosses, tvGF, tvGA, tvWinRate;
    private RecyclerView rvMembers;
    private TeamMemberListAdapter memberAdapter;

    private String myTeamId = "";
    private ListenerRegistration statsReg;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        state      = findViewById(R.id.stateLayout);
        tvGames    = findViewById(R.id.tvGames);
        tvWins     = findViewById(R.id.tvWins);
        tvDraws    = findViewById(R.id.tvDraws);
        tvLosses   = findViewById(R.id.tvLosses);
        tvGF       = findViewById(R.id.tvGF);
        tvGA       = findViewById(R.id.tvGA);
        tvWinRate  = findViewById(R.id.tvWinRate);
        rvMembers  = findViewById(R.id.rvMembers);

        if (state != null) state.showLoading();

        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        memberAdapter = new TeamMemberListAdapter();
        rvMembers.setAdapter(memberAdapter);

        myTeamId = getIntent().getStringExtra("myTeamId");

        if (AppUtils.isEmpty(myTeamId) && FirebaseAuth.getInstance().getCurrentUser() != null) {
            db.collection("profiles")
                    .document(FirebaseAuth.getInstance().getCurrentUser().getUid())
                    .get()
                    .addOnSuccessListener(doc -> {
                        myTeamId = AppUtils.safe(doc.getString("myTeam"));
                        if (!AppUtils.isEmpty(myTeamId)) startStats();
                        else if (state != null) { state.setEmptyMessage("소속 팀이 없어요."); state.showEmpty(); }
                    });
        } else if (!AppUtils.isEmpty(myTeamId)) {
            startStats();
        } else {
            if (state != null) { state.setEmptyMessage("소속 팀이 없어요."); state.showEmpty(); }
        }
    }

    private void startStats() {
        statsReg = db.collection("teamStats").document(myTeamId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    long games  = AppUtils.safeLong(doc.getLong("games"),  0L);
                    long wins   = AppUtils.safeLong(doc.getLong("wins"),   0L);
                    long draws  = AppUtils.safeLong(doc.getLong("draws"),  0L);
                    long losses = AppUtils.safeLong(doc.getLong("losses"), 0L);
                    long gf     = AppUtils.safeLong(doc.getLong("goalsFor"), 0L);
                    long ga     = AppUtils.safeLong(doc.getLong("goalsAgainst"), 0L);

                    if (tvGames  != null) tvGames.setText(String.valueOf(games));
                    if (tvWins   != null) tvWins.setText(String.valueOf(wins));
                    if (tvDraws  != null) tvDraws.setText(String.valueOf(draws));
                    if (tvLosses != null) tvLosses.setText(String.valueOf(losses));
                    if (tvGF     != null) tvGF.setText(String.valueOf(gf));
                    if (tvGA     != null) tvGA.setText(String.valueOf(ga));
                    if (tvWinRate!= null) tvWinRate.setText(
                            games > 0 ? Math.round((wins * 100f) / games) + "%" : "-");
                });

        loadPlayerStats();
    }

    private void loadPlayerStats() {
        db.collection("matches")
                .whereEqualTo("teamId", myTeamId)
                .whereEqualTo("status", "finished")
                .get()
                .addOnSuccessListener(snap -> {
                    Map<String, int[]> stats = new HashMap<>();
                    Map<String, String> nickMap = new HashMap<>();
                    Map<String, String> imgMap  = new HashMap<>();

                    for (DocumentSnapshot d : snap.getDocuments()) {
                        List<Map<String, Object>> events =
                                (List<Map<String, Object>>) d.get("goalEvents");
                        if (events == null) continue;
                        for (Map<String, Object> ev : events) {
                            String scorerId = AppUtils.safe((String) ev.get("scorerId"));
                            String nick     = AppUtils.safe((String) ev.get("scorerNickname"));
                            if (!AppUtils.isEmpty(scorerId)) {
                                stats.computeIfAbsent(scorerId, k -> new int[3])[0]++;
                                if (!AppUtils.isEmpty(nick)) nickMap.put(scorerId, nick);
                            }
                            String assistId = AppUtils.safe((String) ev.get("assistId"));
                            String aNick    = AppUtils.safe((String) ev.get("assistNickname"));
                            if (!AppUtils.isEmpty(assistId)) {
                                stats.computeIfAbsent(assistId, k -> new int[3])[1]++;
                                if (!AppUtils.isEmpty(aNick)) nickMap.put(assistId, aNick);
                            }
                        }
                    }

                    db.collection("profiles").whereEqualTo("myTeam", myTeamId).get()
                            .addOnSuccessListener(profileSnap -> {
                                for (DocumentSnapshot p : profileSnap.getDocuments()) {
                                    String uid  = p.getId();
                                    String nick = p.getString("nickname");
                                    String img  = p.getString("profileImageUrl");
                                    stats.computeIfAbsent(uid, k -> new int[3]);
                                    if (!AppUtils.isEmpty(nick)) nickMap.put(uid, nick);
                                    if (!AppUtils.isEmpty(img))  imgMap.put(uid, img);
                                }

                                List<TeamMemberListAdapter.Item> items = new ArrayList<>();
                                for (Map.Entry<String, int[]> entry : stats.entrySet()) {
                                    String uid = entry.getKey();
                                    int[] s    = entry.getValue();
                                    items.add(new TeamMemberListAdapter.Item(
                                            uid,
                                            AppUtils.nz(nickMap.get(uid), uid),
                                            null, null,
                                            imgMap.get(uid),
                                            s[0]
                                    ));
                                }
                                items.sort((a, b) -> Integer.compare(b.goals, a.goals));
                                memberAdapter.submit(items);
                                if (state != null) state.showContent();
                            });
                })
                .addOnFailureListener(e -> {
                    if (state != null) state.showContent();
                });
    }

    @Override
    protected void onDestroy() {
        if (statsReg != null) statsReg.remove();
        super.onDestroy();
    }
}