package com.jjw.soccerclub.viewmodel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.jjw.soccerclub.model.MatchFilters;
import com.jjw.soccerclub.model.MatchPost;
import com.jjw.soccerclub.repository.MatchRepository;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 매치 리스트 UI 상태 관리.
 *
 * 신규/레거시 두 쿼리를 병렬로 처리하고 중복을 제거한 뒤
 * 필터를 적용해 displayItems 로 노출한다.
 *
 * Fragment 는 Firestore 코드 없이 LiveData 만 observe 한다.
 */
public class MatchViewModel extends ViewModel {

    private final MatchRepository repository = new MatchRepository();

    // ── Fragment 가 observe 하는 LiveData ────────────────────────────────────────

    /** 필터 적용된 최종 목록 */
    private final MutableLiveData<List<MatchPost>> _displayItems
            = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<MatchPost>> displayItems = _displayItems;

    /** 로딩 중 여부 */
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoading = _isLoading;

    /** 결과 없음 여부 */
    private final MutableLiveData<Boolean> _isEmpty = new MutableLiveData<>(false);
    public final LiveData<Boolean> isEmpty = _isEmpty;

    // ── 내부 상태 (화면 회전해도 유지) ──────────────────────────────────────────

    /**
     * 신규 + 레거시를 병합한 전체 맵. (key = matchId)
     * LinkedHashMap 으로 삽입 순서 유지 + 중복 제거.
     */
    private final LinkedHashMap<String, MatchPost> merged = new LinkedHashMap<>();

    private MatchFilters currentFilters = null;

    // 신규 스키마 페이지네이션
    private DocumentSnapshot lastDocNew  = null;
    private boolean hasMoreNew           = true;

    // 레거시 스키마 페이지네이션
    private DocumentSnapshot lastDocLegacy = null;
    private boolean hasMoreLegacy          = true;

    private boolean isLoadingFlag  = false;
    private boolean initialLoadDone = false;

    // ── 외부에서 호출하는 메서드 ─────────────────────────────────────────────────

    /** Fragment 의 onViewCreated 에서 호출 — 이미 데이터 있으면 무시 */
    public void loadIfNeeded(@Nullable MatchFilters filters) {
        this.currentFilters = filters;
        if (!initialLoadDone) {
            initialLoadDone = true;
            resetAndLoad();
        } else {
            publishFiltered();
        }
    }

    /** 상위 Fragment 에서 필터 변경 시 호출 */
    public void applyFilters(@NonNull MatchFilters filters) {
        this.currentFilters = filters;
        resetAndLoad();
    }

    /** 스크롤 끝 도달 시 Fragment 가 호출 */
    public void loadNextPage() {
        if (isLoadingFlag) return;
        if (!hasMoreNew && !hasMoreLegacy) return;
        loadPage();
    }

    // ── 내부 로직 ────────────────────────────────────────────────────────────────

    private void resetAndLoad() {
        merged.clear();
        lastDocNew     = null;
        lastDocLegacy  = null;
        hasMoreNew     = true;
        hasMoreLegacy  = true;
        isLoadingFlag  = false;
        _displayItems.setValue(new ArrayList<>());
        _isEmpty.setValue(false);
        loadPage();
    }

    /**
     * 신규 + 레거시 쿼리를 병렬로 실행.
     * 둘 다 완료되면 onBothDone() 에서 UI 업데이트.
     */
    private void loadPage() {
        if (isLoadingFlag) return;
        if (!hasMoreNew && !hasMoreLegacy) return;

        isLoadingFlag = true;
        _isLoading.setValue(true);

        AtomicBoolean newDone    = new AtomicBoolean(!hasMoreNew);
        AtomicBoolean legacyDone = new AtomicBoolean(!hasMoreLegacy);

        // ── 신규 스키마 ───────────────────────────────────────────────────────────
        if (hasMoreNew) {
            repository.loadNewPage(lastDocNew)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            List<DocumentSnapshot> docs = task.getResult().getDocuments();
                            if (docs.size() < MatchRepository.PAGE_SIZE) hasMoreNew = false;
                            if (!docs.isEmpty()) lastDocNew = docs.get(docs.size() - 1);
                            for (DocumentSnapshot d : docs) {
                                merged.put(d.getId(), repository.parse(d));
                            }
                        } else {
                            hasMoreNew = false;
                        }
                        newDone.set(true);
                        if (legacyDone.get()) onBothDone();
                    });
        }

        // ── 레거시 스키마 ─────────────────────────────────────────────────────────
        if (hasMoreLegacy) {
            repository.loadLegacyPage(lastDocLegacy)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            List<DocumentSnapshot> docs = task.getResult().getDocuments();
                            if (docs.size() < MatchRepository.PAGE_SIZE) hasMoreLegacy = false;
                            if (!docs.isEmpty()) lastDocLegacy = docs.get(docs.size() - 1);
                            for (DocumentSnapshot d : docs) {
                                // 신규에서 이미 처리된 ID 는 덮어쓰지 않음
                                if (!merged.containsKey(d.getId())) {
                                    merged.put(d.getId(), repository.parse(d));
                                }
                            }
                        } else {
                            hasMoreLegacy = false;
                        }
                        legacyDone.set(true);
                        if (newDone.get()) onBothDone();
                    });
        }
    }

    /** 신규 + 레거시 둘 다 완료됐을 때 */
    private void onBothDone() {
        isLoadingFlag = false;
        _isLoading.setValue(false);
        publishFiltered();
    }

    /** merged 에 필터를 적용해 displayItems 업데이트 */
    private void publishFiltered() {
        List<MatchPost> out = new ArrayList<>();
        for (MatchPost post : merged.values()) {
            if (passClientFilters(post, currentFilters)) out.add(post);
        }
        out.sort((a, b) -> Long.compare(bestTs(b), bestTs(a)));
        _isEmpty.setValue(out.isEmpty());
        _displayItems.setValue(out);
    }

    // ── 필터 로직 (기존 MatchListFragment.passClientFilters 이동) ────────────────

    private boolean passClientFilters(@NonNull MatchPost post, @Nullable MatchFilters f) {
        // status 필터 (레거시는 status 없을 수 있음)
        String st = post.getStatus();
        if (st != null && !st.trim().isEmpty()) {
            if (!st.trim().toUpperCase(Locale.getDefault()).equals("OPEN")) return false;
        }
        if (f == null) return true;

        String address = AppUtils.firstNonEmpty(
                post.getStadiumAddress(), post.getAddress(),
                post.getStadium(),        post.getStadiumName());

        if (!isAll(f.common.city)     && !address.contains(f.common.city))     return false;
        if (!isAll(f.common.district) && !address.contains(f.common.district)) return false;

        int skill = post.getSkill();
        if (f.skillMin != null && skill != 0 && skill < f.skillMin) return false;
        if (f.skillMax != null && skill != 0 && skill > f.skillMax) return false;

        String postDate = AppUtils.safe(post.getDate());
        if (!isAll(f.dateFrom) && !postDate.isEmpty()
                && postDate.compareTo(f.dateFrom) < 0) return false;
        if (!isAll(f.dateTo)   && !postDate.isEmpty()
                && postDate.compareTo(f.dateTo)   > 0) return false;

        if (!isAll(f.weekday)) {
            String weekday = DateUtils.getKoreanWeekday(postDate);
            if (!f.weekday.contains(weekday)) return false;
        }

        return true;
    }

    private long bestTs(MatchPost p) {
        if (p.getMatchTs()   > 0) return p.getMatchTs();
        if (p.getTimestamp() > 0) return p.getTimestamp();
        return 0;
    }

    private boolean isAll(String v) {
        return v == null || v.trim().isEmpty() || "전체".equals(v.trim());
    }
}