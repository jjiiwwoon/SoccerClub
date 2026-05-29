package com.jjw.soccerclub.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.jjw.soccerclub.model.Team;
import com.jjw.soccerclub.repository.ProfileRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * 내 프로필 화면 상태 관리.
 *
 * [변경 전] loadProfile() 에서 익명 MutableLiveData 서브클래스를 생성해
 *   setValue() 를 오버라이드하는 방식으로 콜백을 구현.
 *   → MutableLiveData 의 의도된 사용법이 아님, 코드 스멜.
 *
 * [변경 후] ProfileRepository.ProfileCallback 인터페이스를 사용.
 *   repository.fetchProfile(uid, doc -> { ... }) 람다로 간결하게 처리.
 *   ViewModel 코드가 훨씬 읽기 쉬워짐.
 */
public class ProfileViewModel extends ViewModel {

    private final ProfileRepository repository = new ProfileRepository();
    private ListenerRegistration statsReg;

    // ── Fragment 가 observe 하는 LiveData ────────────────────────────────────────

    private final MutableLiveData<DocumentSnapshot> _profile = new MutableLiveData<>();
    public final LiveData<DocumentSnapshot> profile = _profile;

    private final MutableLiveData<Team> _teamInfo = new MutableLiveData<>();
    public final LiveData<Team> teamInfo = _teamInfo;

    private final MutableLiveData<DocumentSnapshot> _teamStats = new MutableLiveData<>();
    public final LiveData<DocumentSnapshot> teamStats = _teamStats;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(true);
    public final LiveData<Boolean> isLoading = _isLoading;

    // ── 내부 상태 ────────────────────────────────────────────────────────────────

    private boolean initialLoadDone = false;

    // ── 외부에서 호출하는 메서드 ─────────────────────────────────────────────────

    /** Fragment 의 onViewCreated 에서 호출. 화면 회전 시 재요청 없음 */
    public void loadIfNeeded(String uid) {
        if (initialLoadDone) return;
        initialLoadDone = true;
        _isLoading.setValue(true);
        loadProfile(uid);
    }

    /** 프로필 편집 후 돌아왔을 때 강제 새로고침 */
    public void reload(String uid) {
        initialLoadDone = true;
        _isLoading.setValue(true);
        if (statsReg != null) { statsReg.remove(); statsReg = null; }
        loadProfile(uid);
    }

    // ── 내부 로직 ────────────────────────────────────────────────────────────────

    /**
     * 1단계: 프로필 조회.
     *
     * [변경 전]
     *   repository.fetchProfile(uid, new MutableLiveData<DocumentSnapshot>() {
     *       @Override public void setValue(DocumentSnapshot value) { ... }
     *   });
     *   → MutableLiveData 를 콜백 목적으로 오버라이드하는 방식. 코드 스멜.
     *
     * [변경 후]
     *   ProfileCallback 인터페이스(람다)로 결과를 받는다.
     *   훨씬 명확하고 의도가 분명하다.
     */
    private void loadProfile(String uid) {
        repository.fetchProfile(uid, doc -> {
            _isLoading.setValue(false);
            _profile.setValue(doc);

            if (doc != null && doc.exists()) {
                String teamId = doc.getString("myTeam");
                if (teamId != null && !teamId.isEmpty()) {
                    loadTeamInfo(teamId);
                    startStatsListener(teamId);
                }
            }
        });
    }

    /** 2단계: 팀 정보 조회 */
    private void loadTeamInfo(String teamId) {
        repository.fetchTeam(teamId, _teamInfo);
    }

    /** 3단계: 팀 전적 통계 실시간 구독 */
    private void startStatsListener(String teamId) {
        if (statsReg != null) statsReg.remove();
        statsReg = repository.listenTeamStats(teamId, _teamStats);
    }

    // ── 생명주기 ──────────────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        super.onCleared();
        if (statsReg != null) { statsReg.remove(); statsReg = null; }
    }
}