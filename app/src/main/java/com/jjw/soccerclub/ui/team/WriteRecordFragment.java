package com.jjw.soccerclub.ui.team;

import android.os.Bundle;
import android.text.TextUtils;
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
    private TextView tvScoreLockNotice;  // ★ 스코어 잠금 안내 (신청팀용)

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String myTeamId, eventId;
    private String myTeamName = "", myTeamLogoUrl = "";
    private String opponentName = "", opponentLogoUrl = "";
    private String opponentTeamId = "";
    private String ownerTeamId = "";
    private boolean isOwner = true;
    private boolean scoreLocked = false;  // ★ 신청팀이면 true → 스코어 편집 불가

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

    private static class GoalEventData {
        String scorerId, scorerNickname;
        @Nullable String assistId, assistNickname;
        GoalEventData(String sId, String sNick, String aId, String aNick) {
            this.scorerId = sId; this.scorerNickname = sNick;
            this.assistId = aId; this.assistNickname = aNick;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ★ newInstance — scoreLocked, ownerTeamId 파라미터 추가
    // ═══════════════════════════════════════════════════════════════════════
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
    private void loadEventAndTeam() {
        db.collection("schedules").document(myTeamId)
                .collection("events").document(eventId)
                .get()
                .addOnSuccessListener(ev -> {
                    if (!isUiReady()) return;

                    opponentName    = AppUtils.safe(ev.getString("opponentTeamName"));
                    opponentLogoUrl = AppUtils.safe(ev.getString("opponentLogoUrl"));
                    opponentTeamId  = AppUtils.safe(ev.getString("opponentTeamId"));

                    // ownerTeamId 결정
                    String evOwner = ev.getString("ownerTeamId");
                    if (!AppUtils.isEmpty(evOwner)) ownerTeamId = evOwner;
                    isOwner = AppUtils.isEmpty(ownerTeamId) || myTeamId.equals(ownerTeamId);

                    // ★ 신청팀이면 스코어 잠금
                    if (scoreLocked || !isOwner) {
                        scoreLocked = true;
                        lockScoreFields();
                    }

                    if (tvAwayTeam != null) tvAwayTeam.setText(
                            AppUtils.isEmpty(opponentName) ? "상대팀" : opponentName);

                    // 상대팀 로고
                    loadAwayLogo(ev);

                    // 내 팀 정보
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

                                // ★ 신청팀이면 주최팀 스코어를 가져와서 뒤집어 표시
                                if (scoreLocked) loadOwnerScore();
                            });
                });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ★ 신청팀 전용: 주최팀 기록에서 스코어 로딩 (뒤집어서 표시)
    // ═══════════════════════════════════════════════════════════════════════
    private void loadOwnerScore() {
        if (AppUtils.isEmpty(ownerTeamId)) return;
        String ownerMatchDocId = eventId + "_" + ownerTeamId;
        db.collection("matches").document(ownerMatchDocId).get()
                .addOnSuccessListener(doc -> {
                    if (!isUiReady() || doc == null || !doc.exists()) return;
                    Long ownerFor     = doc.getLong("scoreFor");
                    Long ownerAgainst = doc.getLong("scoreAgainst");
                    if (ownerFor != null && ownerAgainst != null) {
                        // ★ 뒤집기: 주최팀의 득점 = 신청팀의 실점
                        int myFor     = ownerAgainst.intValue();
                        int myAgainst = ownerFor.intValue();

                        if (editScoreFor != null) editScoreFor.setText(String.valueOf(myFor));
                        if (editScoreAgainst != null) editScoreAgainst.setText(String.valueOf(myAgainst));
                    }
                });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ★ 스코어 필드 잠금 (신청팀)
    // ═══════════════════════════════════════════════════════════════════════
    private void lockScoreFields() {
        if (editScoreFor != null) {
            editScoreFor.setEnabled(false);
            editScoreFor.setAlpha(0.5f);
        }
        if (editScoreAgainst != null) {
            editScoreAgainst.setEnabled(false);
            editScoreAgainst.setAlpha(0.5f);
        }

        // 잠금 안내 텍스트 동적 추가
        if (isUiReady() && getView() != null) {
            View content = getView().findViewById(R.id.state);
            if (content instanceof ViewGroup) {
                // fragment_write_record.xml 의 NestedScrollView > LinearLayout 에 추가
                LinearLayout parent = getView().findViewById(R.id.goalEventListContainer);
                if (parent != null && parent.getParent() instanceof LinearLayout) {
                    LinearLayout container = (LinearLayout) parent.getParent();
                    TextView notice = new TextView(getContext());
                    notice.setText("📌 스코어는 주최팀이 입력한 결과입니다. 수정할 수 없습니다.\n우리 팀의 득점/도움만 기록해 주세요.");
                    notice.setTextSize(13f);
                    notice.setTextColor(0xFF1565C0);
                    notice.setPadding(0, 16, 0, 16);
                    notice.setGravity(android.view.Gravity.CENTER);
                    // goalEventListContainer 바로 위에 삽입
                    int idx = container.indexOfChild(parent);
                    if (idx >= 0) container.addView(notice, idx);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 팀 멤버 로딩
    // ═══════════════════════════════════════════════════════════════════════
    private void loadTeamMembers() {
        db.collection("teams").document(myTeamId).get()
                .addOnSuccessListener(teamDoc -> {
                    List<String> members = (List<String>) teamDoc.get("members");
                    if (members == null || members.isEmpty()) {
                        membersFullyLoaded = true;
                        maybeShowContent();
                        return;
                    }
                    attendeeUids.addAll(members);
                    playerNames.clear();
                    playerUids.clear();

                    final int[] pending = {members.size()};
                    for (String uid : members) {
                        db.collection("profiles").document(uid).get()
                                .addOnSuccessListener(p -> {
                                    String nick = AppUtils.safe(p.getString("nickname"));
                                    if (nick.isEmpty()) nick = uid.substring(0, Math.min(6, uid.length()));
                                    playerNames.add(nick);
                                    playerUids.add(uid);
                                    pending[0]--;
                                    if (pending[0] <= 0) {
                                        membersFullyLoaded = true;
                                        maybeApplyExistingRecord();
                                        maybeShowContent();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    playerNames.add(uid.substring(0, Math.min(6, uid.length())));
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
            if (!attendeeUids.contains(ge.scorerId)) continue;
            addGoalEventRow(ge.scorerId, ge.assistId);
        }
        // ★ 신청팀이 아닌 경우에만 기존 스코어 복원
        if (!scoreLocked) {
            if (existingScoreFor >= 0 && editScoreFor != null)
                editScoreFor.setText(String.valueOf(existingScoreFor));
            if (existingScoreAgainst >= 0 && editScoreAgainst != null)
                editScoreAgainst.setText(String.valueOf(existingScoreAgainst));
        }
        existingApplied = true;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 득점 이벤트 행 추가
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
        if (btnRemove != null) {
            btnRemove.setOnClickListener(v -> goalEventListContainer.removeView(row));
        }

        goalEventListContainer.addView(row);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ★★★ saveRecord — 주최팀/신청팀 분기
    // ═══════════════════════════════════════════════════════════════════════
    private void saveRecord() {
        if (!isUiReady()) return;

        // 1) goalEvents 수집
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
            if (assistId != null) {
                ev.put("assistId", assistId);
                ev.put("assistNickname", assistNick);
            }
            goalEvents.add(ev);

            newGoalsByUid.put(scorerId, newGoalsByUid.getOrDefault(scorerId, 0) + 1);
            if (assistId != null)
                newAssistsByUid.put(assistId, newAssistsByUid.getOrDefault(assistId, 0) + 1);
        }

        int scoreFor     = parseInt(editScoreFor);
        int scoreAgainst = parseInt(editScoreAgainst);

        // ★ 신청팀: 득점 수 검증은 신청팀의 scoreFor 기준
        if (scoreFor != goalEvents.size()) {
            CustomToast.error(getContext(),
                    "득점(" + scoreFor + ")과 득점자 수(" + goalEvents.size() + ")가 일치하지 않아요.");
            return;
        }

        // 2) 승/무/패 계산
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

        // 3) WriteBatch
        WriteBatch batch = db.batch();

        // (A) 내 팀 matches 문서
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

        // (B) 내 팀 event 상태
        Map<String, Object> eventUpd = new HashMap<>();
        eventUpd.put("status",       "finished");
        eventUpd.put("matchId",      getMatchDocId());
        eventUpd.put("scoreFor",     scoreFor);
        eventUpd.put("scoreAgainst", scoreAgainst);
        eventUpd.put("ownerTeamId",  ownerTeamId);
        batch.set(db.collection("schedules").document(myTeamId)
                .collection("events").document(eventId), eventUpd, SetOptions.merge());

        // (C) 내 팀 teamStats 증감
        Map<String, Object> statsInc = new HashMap<>();
        if (dGames  != 0) statsInc.put("games",        FieldValue.increment(dGames));
        if (dWins   != 0) statsInc.put("wins",         FieldValue.increment(dWins));
        if (dDraws  != 0) statsInc.put("draws",        FieldValue.increment(dDraws));
        if (dLosses != 0) statsInc.put("losses",       FieldValue.increment(dLosses));
        if (dGF     != 0) statsInc.put("goalsFor",     FieldValue.increment(dGF));
        if (dGA     != 0) statsInc.put("goalsAgainst", FieldValue.increment(dGA));
        if (!statsInc.isEmpty())
            batch.set(db.collection("teamStats").document(myTeamId), statsInc, SetOptions.merge());

        // (D) 내 팀 scorers 서브컬렉션
        Set<String> unionGoals = new HashSet<>();
        unionGoals.addAll(newGoalsByUid.keySet());
        unionGoals.addAll(oldGoalsByUid.keySet());
        for (String uid : unionGoals) {
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

        // ═══════════════════════════════════════════════════════════════════
        // ★★★ (E) 주최팀이 저장할 때 → 상대팀 일정에 스코어 반영 (뒤집어서)
        // ═══════════════════════════════════════════════════════════════════
        if (isOwner && !AppUtils.isEmpty(opponentTeamId)) {
            // 상대팀 event 문서에 스코어 기록 (뒤집기: 우리 득점=상대 실점)
            Map<String, Object> oppEventUpd = new HashMap<>();
            oppEventUpd.put("status",       "finished");
            oppEventUpd.put("scoreFor",     scoreAgainst);  // ★ 뒤집기
            oppEventUpd.put("scoreAgainst", scoreFor);      // ★ 뒤집기
            oppEventUpd.put("ownerTeamId",  ownerTeamId);
            batch.set(db.collection("schedules").document(opponentTeamId)
                    .collection("events").document(eventId), oppEventUpd, SetOptions.merge());

            // 상대팀의 teamStats도 증감 (뒤집은 기준)
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

        // 커밋
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
    // userStats 개인 누적
    // ═══════════════════════════════════════════════════════════════════════
    private void updateUserStats(boolean isFirstSave,
                                 Map<String, Integer> oldGoals, Map<String, Integer> newGoals,
                                 Map<String, Integer> oldAssists, Map<String, Integer> newAssists) {
        db.collection("teams").document(myTeamId).get()
                .addOnSuccessListener(teamDoc -> {
                    List<String> members = (List<String>) teamDoc.get("members");
                    if (members == null) members = new ArrayList<>();
                    Set<String> teamUids = new HashSet<>(members);

                    WriteBatch wb = db.batch();

                    if (isFirstSave) {
                        for (String uid : teamUids) {
                            Map<String, Object> inc = new HashMap<>();
                            inc.put("teamGames", FieldValue.increment(1));
                            wb.set(db.collection("userStats").document(uid), inc, SetOptions.merge());
                        }
                    }

                    Set<String> unionG = new HashSet<>();
                    unionG.addAll(oldGoals.keySet());
                    unionG.addAll(newGoals.keySet());
                    for (String uid : unionG) {
                        int delta = newGoals.getOrDefault(uid, 0) - oldGoals.getOrDefault(uid, 0);
                        if (delta != 0 && teamUids.contains(uid)) {
                            Map<String, Object> inc = new HashMap<>();
                            inc.put("teamGoals", FieldValue.increment(delta));
                            wb.set(db.collection("userStats").document(uid), inc, SetOptions.merge());
                        }
                    }

                    Set<String> unionA = new HashSet<>();
                    unionA.addAll(oldAssists.keySet());
                    unionA.addAll(newAssists.keySet());
                    for (String uid : unionA) {
                        int delta = newAssists.getOrDefault(uid, 0) - oldAssists.getOrDefault(uid, 0);
                        if (delta != 0 && teamUids.contains(uid)) {
                            Map<String, Object> inc = new HashMap<>();
                            inc.put("teamAssists", FieldValue.increment(delta));
                            wb.set(db.collection("userStats").document(uid), inc, SetOptions.merge());
                        }
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