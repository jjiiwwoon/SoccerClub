package com.jjw.soccerclub.ui.team;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.common.StateLayout;
import com.jjw.soccerclub.util.AppUtils;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

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
    private ImageView imgHomeLogo, imgAwayLogo;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String myTeamId, eventId;
    private String myTeamName = "", myTeamLogoUrl = "";
    private String opponentName = "", opponentLogoUrl = "";
    private String opponentTeamId = "";
    private String ownerTeamId = "";
    private boolean isOwner = true;
    private boolean scoreLocked = false;

    private boolean existingLoaded = false;
    private boolean existingRecorded = false;
    private boolean membersFullyLoaded = false;
    private boolean headerLoaded = false;
    private boolean existingApplied = false;
    private int existingScoreFor = -1, existingScoreAgainst = -1;

    private final List<String> playerNames = new ArrayList<>();
    private final List<String> playerUids  = new ArrayList<>();
    private final Set<String> attendeeUids = new HashSet<>();
    private final List<GoalEventData> existingGoalEvents = new ArrayList<>();

    // ★ 용병
    private final Set<String> mercenaryUids = new HashSet<>();

    private static class GoalEventData {
        String scorerId, scorerNickname;
        @Nullable String assistId, assistNickname;
        GoalEventData(String sId, String sNick, String aId, String aNick) {
            this.scorerId = sId; this.scorerNickname = sNick;
            this.assistId = aId; this.assistNickname = aNick;
        }
    }

    public static WriteRecordFragment newInstance(String myTeamId, String eventId) {
        return newInstance(myTeamId, eventId, false, "");
    }

    public static WriteRecordFragment newInstance(String myTeamId, String eventId,
                                                  boolean scoreLocked, String ownerTeamId) {
        WriteRecordFragment f = new WriteRecordFragment();
        Bundle b = new Bundle();
        b.putString("myTeamId", myTeamId);
        b.putString("eventId", eventId);
        b.putBoolean("scoreLocked", scoreLocked);
        b.putString("ownerTeamId", ownerTeamId);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_write_record, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(v, savedInstanceState);

        Bundle args = getArguments();
        myTeamId    = args != null ? args.getString("myTeamId") : "";
        eventId     = args != null ? args.getString("eventId") : "";
        scoreLocked = args != null && args.getBoolean("scoreLocked", false);
        ownerTeamId = args != null ? args.getString("ownerTeamId", "") : "";

        state                  = v.findViewById(R.id.state);
        editScoreFor           = v.findViewById(R.id.editScoreFor);
        editScoreAgainst       = v.findViewById(R.id.editScoreAgainst);
        goalEventListContainer = v.findViewById(R.id.goalEventListContainer);
        btnAddGoalEvent        = v.findViewById(R.id.btnAddGoalEvent);
        btnSaveRecord          = v.findViewById(R.id.btnSaveRecord);
        tvHomeTeam             = v.findViewById(R.id.tvHomeTeam);
        tvAwayTeam             = v.findViewById(R.id.tvAwayTeam);
        imgHomeLogo            = v.findViewById(R.id.imgHomeLogo);
        imgAwayLogo            = v.findViewById(R.id.imgAwayLogo);

        if (state != null) state.showLoading();
        btnAddGoalEvent.setOnClickListener(btn -> addGoalEventRow(null, null));
        btnSaveRecord.setOnClickListener(btn -> saveRecord());
        loadEventAndTeam();
    }

    private String getMatchDocId() { return eventId + "_" + myTeamId; }

    // ═══════════════════════════════════════════════════════════════════════
    // loadEventAndTeam
    // ═══════════════════════════════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    private void loadEventAndTeam() {
        db.collection("schedules").document(myTeamId)
                .collection("events").document(eventId)
                .get()
                .addOnSuccessListener(ev -> {
                    if (!isUiReady()) return;

                    opponentName    = AppUtils.safe(ev.getString("opponentTeamName"));
                    opponentLogoUrl = AppUtils.safe(ev.getString("opponentLogoUrl"));
                    opponentTeamId  = AppUtils.safe(ev.getString("opponentTeamId"));

                    String evOwner = ev.getString("ownerTeamId");
                    if (!AppUtils.isEmpty(evOwner)) ownerTeamId = evOwner;
                    isOwner = AppUtils.isEmpty(ownerTeamId) || myTeamId.equals(ownerTeamId);

                    if (scoreLocked || !isOwner) {
                        scoreLocked = true;
                        lockScoreFields();
                    }

                    if (tvAwayTeam != null) tvAwayTeam.setText(
                            AppUtils.isEmpty(opponentName) ? "상대팀" : opponentName);

                    loadAwayLogo(ev);

                    // ★ 용병 목록 로드
                    List<String> mercIds = (List<String>) ev.get("mercenaryCandidateIds");
                    if (mercIds != null) mercenaryUids.addAll(mercIds);

                    db.collection("teams").document(myTeamId).get()
                            .addOnSuccessListener(team -> {
                                if (!isUiReady()) return;
                                myTeamName    = AppUtils.safe(team.getString("teamName"));
                                myTeamLogoUrl = AppUtils.safe(team.getString("logoUrl"));

                                if (tvHomeTeam != null && !AppUtils.isEmpty(myTeamName))
                                    tvHomeTeam.setText(myTeamName);
                                if (imgHomeLogo != null && !AppUtils.isEmpty(myTeamLogoUrl))
                                    Glide.with(this).load(myTeamLogoUrl)
                                            .placeholder(R.drawable.ic_shield_gray)
                                            .circleCrop().into(imgHomeLogo);

                                headerLoaded = true;
                                loadTeamMembers();
                                loadExistingRecord();
                                if (scoreLocked) loadOwnerScore();
                            });
                });
    }

    private void loadOwnerScore() {
        if (AppUtils.isEmpty(ownerTeamId)) return;
        String ownerMatchDocId = eventId + "_" + ownerTeamId;
        db.collection("matches").document(ownerMatchDocId).get()
                .addOnSuccessListener(doc -> {
                    if (!isUiReady() || doc == null || !doc.exists()) return;
                    Long ownerFor     = doc.getLong("scoreFor");
                    Long ownerAgainst = doc.getLong("scoreAgainst");
                    if (ownerFor != null && ownerAgainst != null) {
                        int myFor     = ownerAgainst.intValue();
                        int myAgainst = ownerFor.intValue();
                        if (editScoreFor != null) editScoreFor.setText(String.valueOf(myFor));
                        if (editScoreAgainst != null) editScoreAgainst.setText(String.valueOf(myAgainst));
                    }
                });
    }

    private void lockScoreFields() {
        if (editScoreFor != null) { editScoreFor.setEnabled(false); editScoreFor.setAlpha(0.5f); }
        if (editScoreAgainst != null) { editScoreAgainst.setEnabled(false); editScoreAgainst.setAlpha(0.5f); }

        if (isUiReady() && getView() != null) {
            LinearLayout parent = getView().findViewById(R.id.goalEventListContainer);
            if (parent != null && parent.getParent() instanceof LinearLayout) {
                LinearLayout container = (LinearLayout) parent.getParent();
                TextView notice = new TextView(getContext());
                notice.setText("📌 스코어는 주최팀이 입력한 결과입니다. 수정할 수 없습니다.\n우리 팀의 득점/도움만 기록해 주세요.");
                notice.setTextSize(13f);
                notice.setTextColor(0xFF1565C0);
                notice.setPadding(0, 16, 0, 16);
                notice.setGravity(android.view.Gravity.CENTER);
                int idx = container.indexOfChild(parent);
                if (idx >= 0) container.addView(notice, idx);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 팀 멤버 + 용병 로딩
    // ═══════════════════════════════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    private void loadTeamMembers() {
        db.collection("teams").document(myTeamId).get()
                .addOnSuccessListener(teamDoc -> {
                    List<String> members = (List<String>) teamDoc.get("members");
                    if (members == null) members = new ArrayList<>();
                    attendeeUids.addAll(members);
                    playerNames.clear();
                    playerUids.clear();

                    // 팀원 + 용병 합치기
                    List<String> allUids = new ArrayList<>(members);
                    for (String mercUid : mercenaryUids) {
                        if (!allUids.contains(mercUid)) allUids.add(mercUid);
                    }

                    if (allUids.isEmpty()) {
                        membersFullyLoaded = true;
                        maybeShowContent();
                        return;
                    }

                    final int[] pending = {allUids.size()};
                    for (String uid : allUids) {
                        final boolean isMerc = mercenaryUids.contains(uid);
                        db.collection("profiles").document(uid).get()
                                .addOnSuccessListener(p -> {
                                    String nick = AppUtils.safe(p.getString("nickname"));
                                    if (nick.isEmpty()) nick = uid.substring(0, Math.min(6, uid.length()));
                                    // ★ 용병이면 이름 뒤에 표시
                                    playerNames.add(isMerc ? nick + " [용병]" : nick);
                                    playerUids.add(uid);
                                    pending[0]--;
                                    if (pending[0] <= 0) {
                                        membersFullyLoaded = true;
                                        maybeApplyExistingRecord();
                                        maybeShowContent();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    String fallback = uid.substring(0, Math.min(6, uid.length()));
                                    playerNames.add(isMerc ? fallback + " [용병]" : fallback);
                                    playerUids.add(uid);
                                    pending[0]--;
                                    if (pending[0] <= 0) {
                                        membersFullyLoaded = true;
                                        maybeApplyExistingRecord();
                                        maybeShowContent();
                                    }
                                });
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 기존 기록 로딩
    // ═══════════════════════════════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    private void loadExistingRecord() {
        db.collection("matches").document(getMatchDocId()).get()
                .addOnSuccessListener(doc -> {
                    existingGoalEvents.clear();
                    existingScoreFor = -1;
                    existingScoreAgainst = -1;
                    existingRecorded = false;

                    if (doc != null && doc.exists()) {
                        String status = doc.getString("status");
                        boolean hasScore  = doc.contains("scoreFor") || doc.contains("scoreAgainst");
                        boolean hasEvents = doc.contains("goalEvents");
                        existingRecorded = "finished".equals(status) || hasScore || hasEvents;

                        Long sf = getNumber(doc.get("scoreFor"));
                        Long sa = getNumber(doc.get("scoreAgainst"));
                        existingScoreFor     = sf != null ? sf.intValue() : -1;
                        existingScoreAgainst = sa != null ? sa.intValue() : -1;

                        List<Map<String, Object>> evs = (List<Map<String, Object>>) doc.get("goalEvents");
                        if (evs != null) {
                            for (Map<String, Object> m : evs) {
                                existingGoalEvents.add(new GoalEventData(
                                        s(m.get("scorerId")), s(m.get("scorerNickname")),
                                        s(m.get("assistId")), s(m.get("assistNickname"))));
                            }
                        }
                    }
                    existingLoaded = true;
                    maybeApplyExistingRecord();
                    maybeShowContent();
                });
    }

    private void maybeApplyExistingRecord() {
        if (!isUiReady() || existingApplied || !existingLoaded || !membersFullyLoaded) return;
        goalEventListContainer.removeAllViews();
        for (GoalEventData ge : existingGoalEvents) {
            addGoalEventRow(ge.scorerId, ge.assistId);
        }
        if (!scoreLocked) {
            if (existingScoreFor >= 0 && editScoreFor != null)
                editScoreFor.setText(String.valueOf(existingScoreFor));
            if (existingScoreAgainst >= 0 && editScoreAgainst != null)
                editScoreAgainst.setText(String.valueOf(existingScoreAgainst));
        }
        existingApplied = true;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 득점 이벤트 행
    // ═══════════════════════════════════════════════════════════════════════
    private void addGoalEventRow(@Nullable String scorerId, @Nullable String assistId) {
        if (!isUiReady() || playerUids.isEmpty()) return;

        View row = LayoutInflater.from(getContext())
                .inflate(R.layout.item_goal_event_row, goalEventListContainer, false);

        Spinner spScorer = row.findViewWithTag("scorer");
        Spinner spAssist = row.findViewWithTag("assist");

        if (spScorer == null) {
            spScorer = new Spinner(getContext());
            spScorer.setTag("scorer");
            spAssist = new Spinner(getContext());
            spAssist.setTag("assist");
        }

        ArrayAdapter<String> scorerAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, playerNames);
        scorerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spScorer.setAdapter(scorerAdapter);

        List<String> assistNames = new ArrayList<>();
        assistNames.add("도움 없음");
        assistNames.addAll(playerNames);
        ArrayAdapter<String> assistAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, assistNames);
        assistAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAssist.setAdapter(assistAdapter);

        if (scorerId != null) {
            int idx = playerUids.indexOf(scorerId);
            if (idx >= 0) spScorer.setSelection(idx);
        }
        if (assistId != null) {
            int idx = playerUids.indexOf(assistId);
            if (idx >= 0) spAssist.setSelection(idx + 1);
        }

        Button btnRemove = row.findViewById(R.id.btnRemoveRow);
        if (btnRemove != null)
            btnRemove.setOnClickListener(v -> goalEventListContainer.removeView(row));

        goalEventListContainer.addView(row);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // saveRecord
    // ═══════════════════════════════════════════════════════════════════════
    private void saveRecord() {
        if (!isUiReady()) return;

        List<Map<String, Object>> goalEvents = new ArrayList<>();
        Map<String, Integer> newGoalsByUid   = new HashMap<>();
        Map<String, Integer> newAssistsByUid = new HashMap<>();

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
                if (posAs >= 0 && posAs < playerUids.size()) {
                    assistId   = playerUids.get(posAs);
                    assistNick = playerNames.get(posAs);
                }
            }

            Map<String, Object> ev = new HashMap<>();
            ev.put("scorerId", scorerId);
            ev.put("scorerNickname", scorerNick);
            ev.put("isMercenary", mercenaryUids.contains(scorerId));  // ★ 용병 플래그
            if (assistId != null) {
                ev.put("assistId", assistId);
                ev.put("assistNickname", assistNick);
                ev.put("isAssistMercenary", mercenaryUids.contains(assistId));  // ★
            }
            goalEvents.add(ev);

            newGoalsByUid.put(scorerId, newGoalsByUid.getOrDefault(scorerId, 0) + 1);
            if (assistId != null)
                newAssistsByUid.put(assistId, newAssistsByUid.getOrDefault(assistId, 0) + 1);
        }

        int scoreFor     = parseInt(editScoreFor);
        int scoreAgainst = parseInt(editScoreAgainst);

        if (scoreFor != goalEvents.size()) {
            CustomToast.error(getContext(),
                    "득점(" + scoreFor + ")과 득점자 수(" + goalEvents.size() + ")가 일치하지 않아요.");
            return;
        }

        int dGames = existingRecorded ? 0 : 1;
        int newW = scoreFor > scoreAgainst ? 1 : 0;
        int newD = scoreFor == scoreAgainst ? 1 : 0;
        int newL = scoreFor < scoreAgainst ? 1 : 0;

        int oldW = 0, oldD = 0, oldL = 0;
        if (existingRecorded && existingScoreFor >= 0 && existingScoreAgainst >= 0) {
            oldW = existingScoreFor > existingScoreAgainst ? 1 : 0;
            oldD = existingScoreFor == existingScoreAgainst ? 1 : 0;
            oldL = existingScoreFor < existingScoreAgainst ? 1 : 0;
        }

        int dWins   = newW - oldW;
        int dDraws  = newD - oldD;
        int dLosses = newL - oldL;
        int dGF     = scoreFor     - (existingRecorded ? Math.max(existingScoreFor, 0) : 0);
        int dGA     = scoreAgainst - (existingRecorded ? Math.max(existingScoreAgainst, 0) : 0);

        Map<String, Integer> oldGoalsByUid   = new HashMap<>();
        Map<String, Integer> oldAssistsByUid = new HashMap<>();
        for (GoalEventData ge : existingGoalEvents) {
            if (ge.scorerId != null)
                oldGoalsByUid.put(ge.scorerId, oldGoalsByUid.getOrDefault(ge.scorerId, 0) + 1);
            if (ge.assistId != null)
                oldAssistsByUid.put(ge.assistId, oldAssistsByUid.getOrDefault(ge.assistId, 0) + 1);
        }

        WriteBatch batch = db.batch();

        // (A) matches 문서
        Map<String, Object> matchData = new HashMap<>();
        matchData.put("scoreFor",     scoreFor);
        matchData.put("scoreAgainst", scoreAgainst);
        matchData.put("goalEvents",   goalEvents);
        matchData.put("status",       "finished");
        matchData.put("teamId",       myTeamId);
        matchData.put("teamName",     myTeamName);
        matchData.put("teamLogoUrl",  myTeamLogoUrl);
        matchData.put("opponentName", opponentName);
        matchData.put("opponentLogoUrl", opponentLogoUrl);
        matchData.put("createdFromEventId", eventId);
        matchData.put("matchTs",      System.currentTimeMillis());
        batch.set(db.collection("matches").document(getMatchDocId()), matchData, SetOptions.merge());

        // (B) event 상태
        Map<String, Object> eventUpd = new HashMap<>();
        eventUpd.put("status",       "finished");
        eventUpd.put("matchId",      getMatchDocId());
        eventUpd.put("scoreFor",     scoreFor);
        eventUpd.put("scoreAgainst", scoreAgainst);
        eventUpd.put("ownerTeamId",  ownerTeamId);
        batch.set(db.collection("schedules").document(myTeamId)
                .collection("events").document(eventId), eventUpd, SetOptions.merge());

        // (C) teamStats
        Map<String, Object> statsInc = new HashMap<>();
        if (dGames  != 0) statsInc.put("games",        FieldValue.increment(dGames));
        if (dWins   != 0) statsInc.put("wins",         FieldValue.increment(dWins));
        if (dDraws  != 0) statsInc.put("draws",        FieldValue.increment(dDraws));
        if (dLosses != 0) statsInc.put("losses",       FieldValue.increment(dLosses));
        if (dGF     != 0) statsInc.put("goalsFor",     FieldValue.increment(dGF));
        if (dGA     != 0) statsInc.put("goalsAgainst", FieldValue.increment(dGA));
        if (!statsInc.isEmpty())
            batch.set(db.collection("teamStats").document(myTeamId), statsInc, SetOptions.merge());

        // (D) ★ scorers 서브컬렉션 — 용병 제외
        Set<String> unionGoals = new HashSet<>();
        unionGoals.addAll(newGoalsByUid.keySet());
        unionGoals.addAll(oldGoalsByUid.keySet());
        for (String uid : unionGoals) {
            if (mercenaryUids.contains(uid)) continue;  // ★ 용병은 개인 순위 미포함
            int delta = newGoalsByUid.getOrDefault(uid, 0) - oldGoalsByUid.getOrDefault(uid, 0);
            if (delta == 0) continue;
            Map<String, Object> sc = new HashMap<>();
            sc.put("goals", FieldValue.increment(delta));
            sc.put("playerId", uid);
            int idx = playerUids.indexOf(uid);
            sc.put("nickname", idx >= 0 ? playerNames.get(idx) : uid);
            batch.set(db.collection("teamStats").document(myTeamId)
                    .collection("scorers").document(uid), sc, SetOptions.merge());
        }

        // (E) 주최팀 → 상대팀 일정에 스코어 반영
        if (isOwner && !AppUtils.isEmpty(opponentTeamId)) {
            Map<String, Object> oppEventUpd = new HashMap<>();
            oppEventUpd.put("status",       "finished");
            oppEventUpd.put("scoreFor",     scoreAgainst);
            oppEventUpd.put("scoreAgainst", scoreFor);
            oppEventUpd.put("ownerTeamId",  ownerTeamId);
            batch.set(db.collection("schedules").document(opponentTeamId)
                    .collection("events").document(eventId), oppEventUpd, SetOptions.merge());

            int oppDGames = existingRecorded ? 0 : 1;
            int oppW = scoreAgainst > scoreFor ? 1 : 0;
            int oppD = scoreAgainst == scoreFor ? 1 : 0;
            int oppL = scoreAgainst < scoreFor ? 1 : 0;

            Map<String, Object> oppStatsInc = new HashMap<>();
            if (oppDGames != 0) oppStatsInc.put("games",        FieldValue.increment(oppDGames));
            if (oppW != 0)      oppStatsInc.put("wins",         FieldValue.increment(oppW));
            if (oppD != 0)      oppStatsInc.put("draws",        FieldValue.increment(oppD));
            if (oppL != 0)      oppStatsInc.put("losses",       FieldValue.increment(oppL));
            if (scoreAgainst != 0) oppStatsInc.put("goalsFor",     FieldValue.increment(scoreAgainst));
            if (scoreFor     != 0) oppStatsInc.put("goalsAgainst", FieldValue.increment(scoreFor));
            if (!oppStatsInc.isEmpty())
                batch.set(db.collection("teamStats").document(opponentTeamId), oppStatsInc, SetOptions.merge());
        }

        if (state != null) state.showLoading();
        if (btnSaveRecord != null) btnSaveRecord.setEnabled(false);

        batch.commit()
                .addOnSuccessListener(v -> {
                    updateUserStats(!existingRecorded, oldGoalsByUid, newGoalsByUid,
                            oldAssistsByUid, newAssistsByUid);
                    CustomToast.success(getContext(), "기록이 저장됐어요!");
                    if (getActivity() != null) getActivity().onBackPressed();
                })
                .addOnFailureListener(e -> {
                    if (btnSaveRecord != null) btnSaveRecord.setEnabled(true);
                    if (state != null) state.showContent();
                    CustomToast.error(getContext(), "저장 실패: " + e.getMessage());
                });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ★ updateUserStats — 팀원은 team 필드, 용병은 merc 필드
    // ═══════════════════════════════════════════════════════════════════════
    @SuppressWarnings("unchecked")
    private void updateUserStats(boolean isFirstSave,
                                 Map<String, Integer> oldGoals, Map<String, Integer> newGoals,
                                 Map<String, Integer> oldAssists, Map<String, Integer> newAssists) {
        db.collection("teams").document(myTeamId).get()
                .addOnSuccessListener(teamDoc -> {
                    List<String> members = (List<String>) teamDoc.get("members");
                    if (members == null) members = new ArrayList<>();
                    Set<String> teamUids = new HashSet<>(members);

                    Set<String> allParticipants = new HashSet<>(teamUids);
                    allParticipants.addAll(mercenaryUids);

                    WriteBatch wb = db.batch();

                    // 경기수
                    if (isFirstSave) {
                        for (String uid : allParticipants) {
                            Map<String, Object> inc = new HashMap<>();
                            if (mercenaryUids.contains(uid)) {
                                inc.put("mercGames", FieldValue.increment(1));
                            } else {
                                inc.put("teamGames", FieldValue.increment(1));
                            }
                            wb.set(db.collection("userStats").document(uid), inc, SetOptions.merge());
                        }
                    }

                    // 득점
                    Set<String> unionG = new HashSet<>();
                    unionG.addAll(oldGoals.keySet());
                    unionG.addAll(newGoals.keySet());
                    for (String uid : unionG) {
                        int delta = newGoals.getOrDefault(uid, 0) - oldGoals.getOrDefault(uid, 0);
                        if (delta == 0) continue;
                        Map<String, Object> inc = new HashMap<>();
                        if (mercenaryUids.contains(uid)) {
                            inc.put("mercGoals", FieldValue.increment(delta));
                        } else if (teamUids.contains(uid)) {
                            inc.put("teamGoals", FieldValue.increment(delta));
                        } else {
                            continue;
                        }
                        wb.set(db.collection("userStats").document(uid), inc, SetOptions.merge());
                    }

                    // 도움
                    Set<String> unionA = new HashSet<>();
                    unionA.addAll(oldAssists.keySet());
                    unionA.addAll(newAssists.keySet());
                    for (String uid : unionA) {
                        int delta = newAssists.getOrDefault(uid, 0) - oldAssists.getOrDefault(uid, 0);
                        if (delta == 0) continue;
                        Map<String, Object> inc = new HashMap<>();
                        if (mercenaryUids.contains(uid)) {
                            inc.put("mercAssists", FieldValue.increment(delta));
                        } else if (teamUids.contains(uid)) {
                            inc.put("teamAssists", FieldValue.increment(delta));
                        } else {
                            continue;
                        }
                        wb.set(db.collection("userStats").document(uid), inc, SetOptions.merge());
                    }

                    wb.commit();
                });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 유틸
    // ═══════════════════════════════════════════════════════════════════════
    private void loadAwayLogo(DocumentSnapshot ev) {
        if (imgAwayLogo == null) return;
        String awayLogo = AppUtils.safe(ev.getString("opponentLogoUrl"));
        if (!AppUtils.isEmpty(awayLogo)) {
            Glide.with(this).load(awayLogo)
                    .placeholder(R.drawable.ic_shield_gray).circleCrop().into(imgAwayLogo);
        } else {
            String oppId = ev.getString("opponentTeamId");
            if (!AppUtils.isEmpty(oppId)) {
                db.collection("teams").document(oppId).get()
                        .addOnSuccessListener(t -> {
                            if (!isUiReady()) return;
                            String logo = t.getString("logoUrl");
                            if (!AppUtils.isEmpty(logo))
                                Glide.with(this).load(logo)
                                        .placeholder(R.drawable.ic_shield_gray).circleCrop().into(imgAwayLogo);
                        });
            }
        }
    }

    private void maybeShowContent() {
        if (state == null) return;
        if (headerLoaded && membersFullyLoaded && existingLoaded) state.showContent();
    }

    private boolean isUiReady() { return isAdded() && getView() != null && getContext() != null; }
    private String s(Object o) { return o == null ? "" : o.toString().trim(); }
    private Long getNumber(Object v) { if (v instanceof Number) return ((Number) v).longValue(); return null; }
    private int parseInt(EditText et) {
        if (et == null || et.getText() == null) return 0;
        try { return Integer.parseInt(et.getText().toString().trim()); }
        catch (Exception e) { return 0; }
    }
}