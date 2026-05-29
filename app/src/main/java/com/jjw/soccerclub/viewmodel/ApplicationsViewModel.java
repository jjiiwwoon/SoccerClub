package com.jjw.soccerclub.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.jjw.soccerclub.adapter.ApplicationsAdapter;
import com.jjw.soccerclub.repository.ApplicationsRepository;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 신청 목록 화면 상태 관리.
 *
 * [변경 전] ApplicationsListActivity 가 직접 하던 일
 *   - profileLoaded 플래그 직접 관리
 *   - loadData() → loadMine() / loadApplied() 직접 호출
 *   - sessionMaxTs 맵 직접 관리
 *   - handleAccept/handleReject 직접 처리
 *
 * [변경 후] ViewModel 이 담당
 *   - 상태(탭, 필터, myTeamId) 보관
 *   - LiveData 로 Activity 에 결과 전달
 *   - Activity 는 observe + 버튼 클릭 위임만 담당
 */
public class ApplicationsViewModel extends ViewModel {

    private final ApplicationsRepository repository = new ApplicationsRepository();

    // ── Activity 가 observe 하는 LiveData ─────────────────────────────────────────

    /** 현재 탭의 목록 */
    private final MutableLiveData<List<ApplicationsAdapter.Item>> _items
            = new MutableLiveData<>(Collections.emptyList());
    public final LiveData<List<ApplicationsAdapter.Item>> items = _items;

    /** 로딩 여부 */
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoading = _isLoading;

    /** 수락/거절 결과 메시지 (성공: 메시지, 실패: null) */
    private final MutableLiveData<String> _actionResult = new MutableLiveData<>();
    public final LiveData<String> actionResult = _actionResult;

    // ── 내부 상태 ────────────────────────────────────────────────────────────────

    private String  currentUid  = "";
    private String  myTeamId    = "";
    private boolean profileReady = false;

    /** 세션 NEW 뱃지 계산용 — 화면 회전해도 유지 */
    private final Map<String, Long> sessionMaxTs = new LinkedHashMap<>();

    // ── 초기화 ───────────────────────────────────────────────────────────────────

    /**
     * Activity 의 onCreate 에서 호출.
     * profiles 에서 myTeamId 를 가져온 뒤 loadData() 를 실행한다.
     *
     * [변경 전] Activity 가 직접 db.collection("profiles") 호출
     * [변경 후] ViewModel 이 담당 → 화면 회전 시 재조회 없음
     */
    public void init(String uid, String mineOrApplied, String typeFilter) {
        if (profileReady) {
            // 화면 회전 — myTeamId 이미 있으므로 재조회 없이 바로 로드
            load(mineOrApplied, typeFilter);
            return;
        }
        currentUid = uid;
        _isLoading.setValue(true);

        FirebaseFirestore.getInstance()
                .collection("profiles").document(uid).get()
                .addOnSuccessListener(doc -> {
                    myTeamId     = doc.exists() ? com.jjw.soccerclub.util.AppUtils.safe(doc.getString("myTeam")) : "";
                    profileReady = true;
                    load(mineOrApplied, typeFilter);
                })
                .addOnFailureListener(e -> {
                    profileReady = true;
                    load(mineOrApplied, typeFilter);
                });
    }

    // ── 데이터 로드 ───────────────────────────────────────────────────────────────

    /**
     * 탭/필터 변경 시 Activity 에서 호출.
     */
    public void load(String mineOrApplied, String typeFilter) {
        if (!profileReady) return;
        _isLoading.setValue(true);

        if ("mine".equals(mineOrApplied)) {
            repository.fetchMine(currentUid, myTeamId, typeFilter, result -> {
                _isLoading.setValue(false);
                applySessionBadges(result);
                _items.setValue(result);
            });
        } else {
            repository.fetchApplied(currentUid, myTeamId, typeFilter, result -> {
                _isLoading.setValue(false);
                _items.setValue(result);
            });
        }
    }

    // ── 수락 / 거절 ───────────────────────────────────────────────────────────────

    /**
     * 수락 버튼 클릭 → Activity 에서 호출.
     */
    public void accept(ApplicationsAdapter.Item post, ApplicationsAdapter.Applicant applicant) {
        repository.accept(currentUid, myTeamId, post, applicant,
                () -> _actionResult.setValue(
                        "match".equals(post.postType)
                                ? applicant.teamName + "의 시합신청을 수락했어요!"
                                : applicant.nickname + "님이 팀에 합류했어요!"),
                () -> _actionResult.setValue(null));
    }

    /**
     * 거절 버튼 클릭 → Activity 에서 호출.
     */
    public void reject(ApplicationsAdapter.Item post, ApplicationsAdapter.Applicant applicant) {
        repository.reject(currentUid, post, applicant,
                () -> _actionResult.setValue(
                        "match".equals(post.postType)
                                ? "시합 신청을 거절했어요."
                                : "모집 신청을 거절했어요."),
                () -> _actionResult.setValue(null));
    }

    // ── 세션 뱃지 ────────────────────────────────────────────────────────────────

    private void applySessionBadges(List<ApplicationsAdapter.Item> items) {
        for (ApplicationsAdapter.Item it : items) {
            if (it.applicants == null) continue;
            long saved = sessionMaxTs.getOrDefault(it.postId, 0L);
            long maxTs = 0L;
            for (ApplicationsAdapter.Applicant a : it.applicants) {
                if (a.timestamp > maxTs) maxTs = a.timestamp;
            }
            it.hasSessionNew = maxTs > saved;
            if (maxTs > 0) sessionMaxTs.put(it.postId, maxTs);
        }
    }
}