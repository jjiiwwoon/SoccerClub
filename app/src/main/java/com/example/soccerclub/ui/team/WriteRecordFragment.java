package com.example.soccerclub.ui.team;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.soccerclub.R;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.common.StateLayout;
import com.example.soccerclub.util.AppUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WriteRecordFragment extends Fragment {

    private StateLayout state;
    private EditText editScoreFor, editScoreAgainst;
    private LinearLayout goalEventListContainer;
    private Button btnAddGoalEvent, btnSaveRecord;
    private TextView tvHomeTeam, tvAwayTeam;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String myTeamId, eventId;
    private boolean isOwner = false;
    private boolean existingLoaded = false;
    private boolean existingRecorded = false;
    private boolean membersFullyLoaded = false;
    private boolean headerLoaded = false;
    private boolean existingApplied = false;
    private boolean ownerScoreLoaded = false;
    private int existingScoreFor = -1, existingScoreAgainst = -1;

    private final List<String> playerNames = new ArrayList<>();
    private final List<String> playerUids  = new ArrayList<>();
    private final Set<String> attendeeUids = new HashSet<>();

    private static class GoalEventData {
        String scorerId, scorerNickname;
        @Nullable String assistId, assistNickname;
        GoalEventData(String sId, String sNick, String aId, String aNick) {
            this.scorerId = sId; this.scorerNickname = sNick;
            this.assistId = aId; this.assistNickname = aNick;
        }
    }
    private final List<GoalEventData> existingGoalEvents = new ArrayList<>();

    public static WriteRecordFragment newInstance(String myTeamId, String eventId) {
        WriteRecordFragment f = new WriteRecordFragment();
        Bundle b = new Bundle();
        b.putString("myTeamId", myTeamId);
        b.putString("eventId", eventId);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_write_record, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        Bundle args = getArguments();
        myTeamId = args != null ? args.getString("myTeamId") : "";
        eventId  = args != null ? args.getString("eventId") : "";

        state                  = v.findViewById(R.id.state);
        editScoreFor           = v.findViewById(R.id.editScoreFor);
        editScoreAgainst       = v.findViewById(R.id.editScoreAgainst);
        goalEventListContainer = v.findViewById(R.id.goalEventListContainer);
        btnAddGoalEvent        = v.findViewById(R.id.btnAddGoalEvent);
        btnSaveRecord          = v.findViewById(R.id.btnSaveRecord);
        tvHomeTeam             = v.findViewById(R.id.tvHomeTeam);
        tvAwayTeam             = v.findViewById(R.id.tvAwayTeam);

        if (state != null) state.showLoading();

        btnAddGoalEvent.setOnClickListener(btn -> addGoalEventRow(null, null));
        btnSaveRecord.setOnClickListener(btn -> saveRecord());

        loadEventAndTeam();
    }

    private String getMatchDocId() { return eventId + "_" + myTeamId; }

    private void loadEventAndTeam() {
        db.collection("schedules").document(myTeamId)
                .collection("events").document(eventId)
                .get()
                .addOnSuccessListener(ev -> {
                    if (!isUiReady()) return;

                    String homeTeamId   = ev.getString("homeTeamId");
                    String homeTeamName = ev.getString("homeTeamName");
                    String awayTeamName = ev.getString("awayTeamName");
                    isOwner = myTeamId.equals(homeTeamId);

                    if (tvHomeTeam != null) tvHomeTeam.setText(AppUtils.safe(homeTeamName));
                    if (tvAwayTeam != null) tvAwayTeam.setText(AppUtils.safe(awayTeamName));

                    headerLoaded = true;
                    loadTeamMembers();
                    loadExistingRecord();
                })
                .addOnFailureListener(e -> {
                    if (isUiReady()) CustomToast.error(getContext(), "경기 정보를 불러오지 못했어요.");
                });
    }

    private void loadTeamMembers() {
        db.collection("teams").document(myTeamId).get()
                .addOnSuccessListener(teamDoc -> {
                    List<String> members = (List<String>) teamDoc.get("members");
                    if (members == null || members.isEmpty()) {
                        membersFullyLoaded = true;
                        maybeShowContent();
                        return;
                    }

                    List<List<String>> chunks = new ArrayList<>();
                    for (int i = 0; i < members.size(); i += 10) {
                        chunks.add(members.subList(i, Math.min(i + 10, members.size())));
                    }

                    int[] remaining = {chunks.size()};
                    for (List<String> chunk : chunks) {
                        db.collection("profiles").whereIn("__name__", chunk).get()
                                .addOnSuccessListener(snap -> {
                                    for (DocumentSnapshot p : snap.getDocuments()) {
                                        String uid      = p.getId();
                                        String nickname = AppUtils.nz(p.getString("nickname"), uid);
                                        playerUids.add(uid);
                                        playerNames.add(nickname);
                                        attendeeUids.add(uid);
                                    }
                                    remaining[0]--;
                                    if (remaining[0] <= 0) {
                                        membersFullyLoaded = true;
                                        maybeApplyExistingRecord();
                                        maybeShowContent();
                                    }
                                });
                    }
                });
    }

    private void loadExistingRecord() {
        db.collection("matches").document(getMatchDocId()).get()
                .addOnSuccessListener(doc -> {
                    if (!isUiReady()) return;
                    if (doc != null && doc.exists()) {
                        String status    = doc.getString("status");
                        boolean hasScore = doc.contains("scoreFor") || doc.contains("scoreAgainst");
                        existingRecorded = "finished".equals(status) || hasScore;

                        Long sf = getNumber(doc.get("scoreFor"));
                        Long sa = getNumber(doc.get("scoreAgainst"));
                        existingScoreFor     = sf != null ? sf.intValue() : -1;
                        existingScoreAgainst = sa != null ? sa.intValue() : -1;

                        List<Map<String, Object>> evs = (List<Map<String, Object>>) doc.get("goalEvents");
                        if (evs != null) {
                            for (Map<String, Object> m : evs) {
                                existingGoalEvents.add(new GoalEventData(
                                        s(m.get("scorerId")), s(m.get("scorerNickname")),
                                        s(m.get("assistId")),  s(m.get("assistNickname"))));
                            }
                        }
                    }
                    existingLoaded = true;
                    ownerScoreLoaded = true;
                    maybeApplyExistingRecord();
                    maybeShowContent();
                });
    }

    private void maybeApplyExistingRecord() {
        if (!isUiReady() || existingApplied || !existingLoaded || !membersFullyLoaded) return;
        goalEventListContainer.removeAllViews();
        for (GoalEventData ge : existingGoalEvents) {
            if (!attendeeUids.contains(ge.scorerId)) continue;
            addGoalEventRow(ge.scorerId, ge.assistId);
        }
        if (existingScoreFor >= 0 && editScoreFor != null)
            editScoreFor.setText(String.valueOf(existingScoreFor));
        if (existingScoreAgainst >= 0 && editScoreAgainst != null)
            editScoreAgainst.setText(String.valueOf(existingScoreAgainst));
        existingApplied = true;
    }

    private void addGoalEventRow(@Nullable String scorerId, @Nullable String assistId) {
        if (!isUiReady() || playerUids.isEmpty()) return;
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_goal_event_row, goalEventListContainer, false);

        Spinner spScorer = row.findViewWithTag("scorer");
        Spinner spAssist = row.findViewWithTag("assist");

        List<String> namesWithNone = new ArrayList<>();
        namesWithNone.add("도움 없음");
        namesWithNone.addAll(playerNames);

        ArrayAdapter<String> scoreAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, playerNames);
        ArrayAdapter<String> assistAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, namesWithNone);
        scoreAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        assistAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        if (spScorer != null) {
            spScorer.setAdapter(scoreAdapter);
            if (scorerId != null) {
                int idx = playerUids.indexOf(scorerId);
                if (idx >= 0) spScorer.setSelection(idx);
            }
        }
        if (spAssist != null) {
            spAssist.setAdapter(assistAdapter);
            if (assistId != null) {
                int idx = playerUids.indexOf(assistId);
                if (idx >= 0) spAssist.setSelection(idx + 1);
            }
        }

        Button btnRemove = row.findViewById(R.id.btnRemoveRow);
        if (btnRemove != null) btnRemove.setOnClickListener(v -> goalEventListContainer.removeView(row));

        goalEventListContainer.addView(row);
    }

    private void saveRecord() {
        if (!isUiReady()) return;

        List<Map<String, Object>> goalEvents = new ArrayList<>();
        for (int i = 0; i < goalEventListContainer.getChildCount(); i++) {
            View row = goalEventListContainer.getChildAt(i);
            Spinner spSc = row.findViewWithTag("scorer");
            Spinner spAs = row.findViewWithTag("assist");
            if (spSc == null) continue;

            int posSc = spSc.getSelectedItemPosition();
            if (posSc < 0 || posSc >= playerUids.size()) continue;

            String scorerId   = playerUids.get(posSc);
            String scorerNick = playerNames.get(posSc);

            String assistId = null, assistNick = null;
            if (spAs != null && spAs.getSelectedItemPosition() > 0) {
                int posAs = spAs.getSelectedItemPosition() - 1;
                if (posAs < playerUids.size()) {
                    assistId   = playerUids.get(posAs);
                    assistNick = playerNames.get(posAs);
                }
            }

            Map<String, Object> ev = new HashMap<>();
            ev.put("scorerId", scorerId);
            ev.put("scorerNickname", scorerNick);
            if (assistId != null) {
                ev.put("assistId", assistId);
                ev.put("assistNickname", assistNick);
            }
            goalEvents.add(ev);
        }

        int scoreFor     = parseInt(editScoreFor);
        int scoreAgainst = parseInt(editScoreAgainst);

        if (scoreFor != goalEvents.size()) {
            CustomToast.error(getContext(),
                    "득점(" + scoreFor + ")과 득점자 수(" + goalEvents.size() + ")가 일치하지 않아요.");
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("scoreFor",     scoreFor);
        data.put("scoreAgainst", scoreAgainst);
        data.put("goalEvents",   goalEvents);
        data.put("status",       "finished");
        data.put("teamId",       myTeamId);

        db.collection("matches").document(getMatchDocId())
                .set(data)
                .addOnSuccessListener(v -> {
                    updateUserStats(goalEvents);
                    CustomToast.success(getContext(), "기록이 저장됐어요!");
                    if (getActivity() != null) getActivity().onBackPressed();
                })
                .addOnFailureListener(e ->
                        CustomToast.error(getContext(), "저장 실패: " + e.getMessage()));
    }

    private void updateUserStats(List<Map<String, Object>> goalEvents) {
        for (Map<String, Object> ev : goalEvents) {
            String scorerId = (String) ev.get("scorerId");
            String assistId = (String) ev.get("assistId");
            if (!AppUtils.isEmpty(scorerId)) {
                Map<String, Object> u = new HashMap<>();
                u.put("teamGoals",  FieldValue.increment(1));
                u.put("teamGames",  FieldValue.increment(1));
                db.collection("userStats").document(scorerId).set(u,
                        com.google.firebase.firestore.SetOptions.merge());
            }
            if (!AppUtils.isEmpty(assistId)) {
                Map<String, Object> u = new HashMap<>();
                u.put("teamAssists", FieldValue.increment(1));
                db.collection("userStats").document(assistId).set(u,
                        com.google.firebase.firestore.SetOptions.merge());
            }
        }
    }

    private void maybeShowContent() {
        if (state == null) return;
        if (headerLoaded && membersFullyLoaded) state.showContent();
    }

    private boolean isUiReady() {
        return isAdded() && getView() != null && getContext() != null;
    }

    private String s(Object o) { return o == null ? "" : o.toString().trim(); }
    private Long getNumber(Object v) {
        if (v instanceof Number) return ((Number) v).longValue();
        return null;
    }
    private int parseInt(EditText et) {
        if (et == null || et.getText() == null) return 0;
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (Exception e) { return 0; }
    }
}