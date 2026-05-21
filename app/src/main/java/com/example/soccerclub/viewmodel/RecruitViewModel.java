package com.example.soccerclub.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.soccerclub.adapter.RecruitAdapter;
import com.example.soccerclub.model.RecruitFilters;
import com.example.soccerclub.repository.RecruitRepository;
import com.example.soccerclub.util.AppUtils;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 모집 리스트 UI 상태 관리.
 *
 * 화면 회전이 일어나도 이 ViewModel 은 살아있기 때문에
 * Firestore 를 다시 요청하지 않는다.
 *
 * Fragment 는 Firestore 코드 없이 LiveData 만 observe 한다.
 */
public class RecruitViewModel extends ViewModel {

    private final RecruitRepository repository = new RecruitRepository();

    // ── Fragment 가 observe 하는 LiveData ────────────────────────────────────────

    /** 필터 적용된 최종 목록 */
    private final MutableLiveData<List<RecruitAdapter.RecruitItem>> _displayItems
            = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<RecruitAdapter.RecruitItem>> displayItems = _displayItems;

    /** 로딩 중 여부 (하단 스피너 표시용) */
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoading = _isLoading;

    /** 결과 없음 여부 (emptyView 표시용) */
    private final MutableLiveData<Boolean> _isEmpty = new MutableLiveData<>(false);
    public final LiveData<Boolean> isEmpty = _isEmpty;

    // ── 내부 상태 (화면 회전해도 유지) ──────────────────────────────────────────

    /** Firestore 에서 받아온 전체 목록 (필터 적용 전) */
    private final List<RecruitAdapter.RecruitItem> allItems = new ArrayList<>();

    /** 현재 적용된 필터 */
    private RecruitFilters currentFilters = null;

    /** 페이지네이션 커서 */
    private DocumentSnapshot lastDoc = null;

    /** 더 가져올 데이터 있는지 여부 */
    private boolean hasMore = true;

    /** 로딩 중 중복 요청 방지 플래그 */
    private boolean isLoadingFlag = false;

    /** 최초 1회 로드 완료 여부 — 화면 회전 시 재요청 방지 */
    private boolean initialLoadDone = false;

    // ── 외부에서 호출하는 메서드 ─────────────────────────────────────────────────

    /**
     * Fragment 의 onViewCreated 에서 호출.
     * 이미 데이터가 있으면(화면 회전) 아무것도 하지 않는다.
     *
     * @param filters Fragment 생성 전에 상위에서 전달받은 필터 (없으면 null)
     */
    public void loadIfNeeded(@Nullable RecruitFilters filters) {
        // 항상 최신 필터를 반영
        this.currentFilters = filters;

        if (!initialLoadDone) {
            initialLoadDone = true;
            resetAndLoad();
        } else {
            // 화면 회전 후 재진입 — 기존 데이터로 필터만 다시 적용
            publishFiltered();
        }
    }

    /**
     * 상위 Fragment(RecruitMatchFragment)가 필터를 바꿀 때 호출.
     * 목록을 처음부터 다시 로드한다.
     */
    public void applyFilters(@NonNull RecruitFilters filters) {
        this.currentFilters = filters;
        resetAndLoad();
    }

    /**
     * 스크롤이 끝에 도달했을 때 Fragment 가 호출.
     * 로딩 중이거나 더 이상 데이터가 없으면 무시한다.
     */
    public void loadNextPage() {
        if (isLoadingFlag || !hasMore) return;
        loadPage();
    }

    // ── 내부 로직 ────────────────────────────────────────────────────────────────

    /** 목록 초기화 후 첫 페이지 로드 */
    private void resetAndLoad() {
        allItems.clear();
        lastDoc       = null;
        hasMore       = true;
        isLoadingFlag = false;
        _displayItems.setValue(new ArrayList<>());
        _isEmpty.setValue(false);
        loadPage();
    }

    /** Repository 에 한 페이지 요청 */
    private void loadPage() {
        if (isLoadingFlag || !hasMore) return;
        isLoadingFlag = true;
        _isLoading.setValue(true);

        repository.loadPage(lastDoc)
                .addOnSuccessListener(snap -> {
                    isLoadingFlag = false;
                    _isLoading.setValue(false);

                    if (snap == null || snap.isEmpty()) {
                        hasMore = false;
                        if (allItems.isEmpty()) _isEmpty.setValue(true);
                        return;
                    }

                    // 마지막 페이지 여부
                    if (snap.size() < RecruitRepository.PAGE_SIZE) hasMore = false;

                    // 커서 업데이트
                    lastDoc = snap.getDocuments().get(snap.size() - 1);

                    // 파싱 — Repository 에 위임
                    for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                        allItems.add(repository.parse(d));
                    }

                    // 최신순 정렬
                    Collections.sort(allItems,
                            (a, b) -> Long.compare(sortKey(b), sortKey(a)));

                    // 필터 적용 후 LiveData 업데이트
                    publishFiltered();
                })
                .addOnFailureListener(e -> {
                    isLoadingFlag = false;
                    _isLoading.setValue(false);
                    if (allItems.isEmpty()) _isEmpty.setValue(true);
                });
    }

    /** allItems 에 currentFilters 를 적용해 displayItems 를 업데이트 */
    private void publishFiltered() {
        List<RecruitAdapter.RecruitItem> out = new ArrayList<>();
        for (RecruitAdapter.RecruitItem it : allItems) {
            if (passFilters(it, currentFilters)) out.add(it);
        }
        _isEmpty.setValue(out.isEmpty());
        _displayItems.setValue(out);
    }

    // ── 필터 로직 (기존 RecruitListFragment.passFilters 이동) ───────────────────

    private boolean passFilters(RecruitAdapter.RecruitItem it,
                                @Nullable RecruitFilters f) {
        if (f == null) return true;

        String address = AppUtils.safe(it.stadiumAddress);
        if (!isAll(f.common.city)     && !address.contains(f.common.city))     return false;
        if (!isAll(f.common.district) && !address.contains(f.common.district)) return false;

        if (f.skillMin != null) {
            int postMax = it.skillMax != null ? it.skillMax : Integer.MAX_VALUE;
            if (postMax < f.skillMin) return false;
        }
        if (f.skillMax != null) {
            int postMin = it.skillMin != null ? it.skillMin : 0;
            if (postMin > f.skillMax) return false;
        }

        if (!isAll(f.position) && it.positions != null && !it.positions.isEmpty()) {
            boolean matched = false;
            for (String p : it.positions) {
                if (p != null && p.equalsIgnoreCase(f.position)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        if (!isAll(f.recruitType)
                && !AppUtils.safe(it.recruitType).equalsIgnoreCase(f.recruitType)) {
            return false;
        }

        return true;
    }

    private boolean isAll(String v) {
        return v == null || v.isEmpty() || v.equals("전체");
    }

    /** 정렬 기준 타임스탬프 — 여러 필드명 혼재 대응 */
    private long sortKey(RecruitAdapter.RecruitItem it) {
        if (it.createdAtMs > 0) return it.createdAtMs;
        if (it.createdAt   > 0) return it.createdAt;
        if (it.postTs      > 0) return it.postTs;
        return it.matchTs;
    }
}