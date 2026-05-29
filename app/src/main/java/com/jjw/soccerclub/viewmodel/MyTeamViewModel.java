package com.jjw.soccerclub.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;

import com.jjw.soccerclub.model.Team;
import com.jjw.soccerclub.repository.ProfileRepository;
import com.jjw.soccerclub.repository.TeamRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MyTeamViewModel extends ViewModel {

    private final TeamRepository    teamRepo    = new TeamRepository();
    private final ProfileRepository profileRepo = new ProfileRepository();
    private final FirebaseFirestore db          = FirebaseFirestore.getInstance();

    // ── Fragment 가 observe 하는 LiveData ────────────────────────────────────────

    private final MutableLiveData<Team> _teamInfo = new MutableLiveData<>();
    public final LiveData<Team> teamInfo = _teamInfo;

    private final MutableLiveData<DocumentSnapshot> _teamStats = new MutableLiveData<>();
    public final LiveData<DocumentSnapshot> teamStats = _teamStats;

    private final MutableLiveData<DocumentSnapshot> _nextSchedule = new MutableLiveData<>();
    public final LiveData<DocumentSnapshot> nextSchedule = _nextSchedule;

    private final MutableLiveData<List<DocumentSnapshot>> _memberProfiles
            = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<DocumentSnapshot>> memberProfiles = _memberProfiles;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(true);
    public final LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<Boolean> _hasNoTeam = new MutableLiveData<>(false);
    public final LiveData<Boolean> hasNoTeam = _hasNoTeam;

    // ── 내부 상태 ────────────────────────────────────────────────────────────────

    private ListenerRegistration teamReg;
    private ListenerRegistration statsReg;

    /**
     * ✅ 수정: observeForever observer 를 필드로 보관.
     * reload() 호출 시 기존 observer 를 먼저 해제하고 새로 등록해야
     * 중복 실행(loadMemberProfiles 2회, 3회...)을 막을 수 있다.
     */
    private Observer<Team> teamInfoObserver;

    private boolean initialLoadDone = false;
    private String  cachedTeamId   = null;

    // ── 외부에서 호출하는 메서드 ─────────────────────────────────────────────────

    /** Fragment 의 onViewCreated 에서 호출. 화면 회전 시 재요청 없음 */
    public void loadIfNeeded(String uid) {
        if (initialLoadDone) return;
        initialLoadDone = true;
        resolveTeamId(uid);
    }

    /** 팀 정보 수정/강퇴 후 강제 새로고침 */
    public void reload(String uid) {
        stopListeners();
        initialLoadDone = true;
        cachedTeamId = null;
        resolveTeamId(uid);
    }

    /** 30초 자동 새로고침 — 다음 일정만 재조회 */
    public void refreshNextSchedule(String teamId) {
        if (teamId == null || teamId.isEmpty()) return;
        loadNextSchedule(teamId);
    }

    // ── 내부 로직 ────────────────────────────────────────────────────────────────

    private void resolveTeamId(String uid) {
        _isLoading.setValue(true);
        db.collection("profiles").document(uid).get()
                .addOnSuccessListener(profileDoc -> {
                    String teamId = profileDoc.getString("myTeam");
                    if (teamId == null || teamId.isEmpty()) {
                        _isLoading.setValue(false);
                        _hasNoTeam.setValue(true);
                        return;
                    }
                    cachedTeamId = teamId;
                    startTeamListener(teamId);
                    startStatsListener(teamId);
                    loadNextSchedule(teamId);
                })
                .addOnFailureListener(e -> _isLoading.setValue(false));
    }

    private void startTeamListener(String teamId) {
        // Firestore 리스너 교체
        if (teamReg != null) teamReg.remove();

        // ✅ 수정: 기존 observer 먼저 해제 → 새 observer 등록
        // reload() 가 반복 호출돼도 observer 가 쌓이지 않는다.
        if (teamInfoObserver != null) {
            _teamInfo.removeObserver(teamInfoObserver);
        }

        teamInfoObserver = team -> {
            _isLoading.setValue(false);
            if (team != null) loadMemberProfiles(team);
        };

        teamReg = teamRepo.listenTeam(teamId, _teamInfo);
        _teamInfo.observeForever(teamInfoObserver);
    }

    private void startStatsListener(String teamId) {
        if (statsReg != null) statsReg.remove();
        statsReg = profileRepo.listenTeamStats(teamId, _teamStats);
    }

    private void loadNextSchedule(String teamId) {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date());

        db.collection("schedules").document(teamId)
                .collection("events")
                .whereGreaterThanOrEqualTo("date", today)
                .orderBy("date", Query.Direction.ASCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener(snap ->
                        _nextSchedule.setValue(
                                snap.isEmpty() ? null : snap.getDocuments().get(0)))
                .addOnFailureListener(e -> _nextSchedule.setValue(null));
    }

    private void loadMemberProfiles(Team team) {
        List<String> memberUids = team.getMembers();
        if (memberUids == null || memberUids.isEmpty()) {
            _memberProfiles.setValue(new ArrayList<>());
            return;
        }

        List<com.google.android.gms.tasks.Task<com.google.firebase.firestore.QuerySnapshot>> tasks
                = new ArrayList<>();

        for (int i = 0; i < memberUids.size(); i += 10) {
            List<String> chunk = memberUids.subList(i, Math.min(i + 10, memberUids.size()));
            tasks.add(db.collection("profiles")
                    .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                    .get());
        }

        com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks)
                .addOnSuccessListener(results -> {
                    List<DocumentSnapshot> all = new ArrayList<>();
                    for (Object result : results) {
                        com.google.firebase.firestore.QuerySnapshot snap =
                                (com.google.firebase.firestore.QuerySnapshot) result;
                        all.addAll(snap.getDocuments());
                    }
                    _memberProfiles.setValue(all);
                });
    }

    private void stopListeners() {
        if (teamReg  != null) { teamReg.remove();  teamReg  = null; }
        if (statsReg != null) { statsReg.remove(); statsReg = null; }
    }

    // ── 생명주기 ──────────────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListeners();
        // ✅ 수정: ViewModel 소멸 시 observeForever observer 도 함께 해제
        if (teamInfoObserver != null) {
            _teamInfo.removeObserver(teamInfoObserver);
            teamInfoObserver = null;
        }
    }
}