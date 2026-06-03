package com.jjw.soccerclub.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.jjw.soccerclub.adapter.ApplicationsAdapter;
import com.jjw.soccerclub.repository.ApplicationsRepository;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Collections;
import java.util.List;

public class ApplicationsViewModel extends ViewModel {

    private final ApplicationsRepository repository = new ApplicationsRepository();

    // ── Activity 가 observe 하는 LiveData ─────────────────────────────────────────

    private final MutableLiveData<List<ApplicationsAdapter.Item>> _items
            = new MutableLiveData<>(Collections.emptyList());
    public final LiveData<List<ApplicationsAdapter.Item>> items = _items;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<String> _actionResult = new MutableLiveData<>();
    public final LiveData<String> actionResult = _actionResult;

    // ── 내부 상태 ────────────────────────────────────────────────────────────────

    private String  currentUid  = "";
    private String  myTeamId    = "";
    private boolean profileReady = false;

    // ── 초기화 ───────────────────────────────────────────────────────────────────

    public void init(String uid, String mineOrApplied, String typeFilter) {
        if (profileReady) {
            load(mineOrApplied, typeFilter);
            return;
        }
        currentUid = uid;
        _isLoading.setValue(true);

        FirebaseFirestore.getInstance()
                .collection("profiles").document(uid).get()
                .addOnSuccessListener(doc -> {
                    myTeamId     = doc.exists()
                            ? com.jjw.soccerclub.util.AppUtils.safe(doc.getString("myTeam"))
                            : "";
                    profileReady = true;
                    load(mineOrApplied, typeFilter);
                })
                .addOnFailureListener(e -> {
                    profileReady = true;
                    load(mineOrApplied, typeFilter);
                });
    }

    // ── 데이터 로드 ───────────────────────────────────────────────────────────────

    public void load(String mineOrApplied, String typeFilter) {
        if (!profileReady) return;
        _isLoading.setValue(true);

        if ("mine".equals(mineOrApplied)) {
            repository.fetchMine(currentUid, myTeamId, typeFilter, result -> {
                _isLoading.setValue(false);
                // ★ 뱃지 계산은 Activity의 computeSessionBadgesAndApply()에서 처리
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

    public void accept(ApplicationsAdapter.Item post, ApplicationsAdapter.Applicant applicant) {
        repository.accept(currentUid, myTeamId, post, applicant,
                () -> _actionResult.setValue(
                        "match".equals(post.postType)
                                ? applicant.teamName + "의 시합신청을 수락했어요!"
                                : applicant.nickname + "님이 팀에 합류했어요!"),
                () -> _actionResult.setValue(null));
    }

    public void reject(ApplicationsAdapter.Item post, ApplicationsAdapter.Applicant applicant) {
        repository.reject(currentUid, post, applicant,
                () -> _actionResult.setValue(
                        "match".equals(post.postType)
                                ? "시합 신청을 거절했어요."
                                : "모집 신청을 거절했어요."),
                () -> _actionResult.setValue(null));
    }
}