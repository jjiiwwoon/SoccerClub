package com.example.soccerclub.ui.recruit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.adapter.RecruitAdapter;
import com.example.soccerclub.model.RecruitFilters;
import com.example.soccerclub.util.AppUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecruitListFragment extends Fragment {

    private static final int PAGE_SIZE = 20;

    private RecyclerView recyclerRecruit;
    private TextView emptyView;
    private View loadingFooter;
    private RecruitAdapter adapter;

    private RecruitFilters currentFilters = null;
    private final List<RecruitAdapter.RecruitItem> allItems = new ArrayList<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // 페이지네이션 상태
    private DocumentSnapshot lastDoc       = null;
    private boolean           isLoading    = false;
    private boolean           hasMore      = true;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_recruit_list, container, false);

        recyclerRecruit = v.findViewById(R.id.recyclerRecruit);
        emptyView       = v.findViewById(R.id.emptyView);
        loadingFooter   = v.findViewById(R.id.loadingFooter);

        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        recyclerRecruit.setLayoutManager(lm);
        adapter = new RecruitAdapter();
        recyclerRecruit.setAdapter(adapter);

        // 스크롤 끝 감지 → 다음 페이지 로드
        recyclerRecruit.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                int total       = lm.getItemCount();
                // 마지막에서 3개 전에 도달하면 다음 페이지 요청
                if (!isLoading && hasMore && lastVisible >= total - 3) {
                    loadNextPage();
                }
            }
        });

        // 첫 페이지 로드
        resetAndLoad();
        return v;
    }

    // 상위 Fragment에서 필터 변경 시 호출
    public void applyExternalFilters(@Nullable RecruitFilters filters) {
        this.currentFilters = filters;
        // 필터 변경 시 처음부터 다시 로드
        if (adapter != null) resetAndLoad();
    }

    // 상태 초기화 후 첫 페이지 로드
    private void resetAndLoad() {
        allItems.clear();
        lastDoc   = null;
        hasMore   = true;
        isLoading = false;
        if (adapter != null) adapter.submit(new ArrayList<>());
        if (emptyView     != null) emptyView.setVisibility(View.GONE);
        if (loadingFooter != null) loadingFooter.setVisibility(View.GONE);
        loadNextPage();
    }

    private void loadNextPage() {
        if (isLoading || !hasMore || !isAdded()) return;
        isLoading = true;
        if (loadingFooter != null) loadingFooter.setVisibility(View.VISIBLE);

        // 기본 쿼리
        Query q = db.collection("recruitPosts")
                .whereEqualTo("status", "open")
                .orderBy("createdAtMs", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);

        // 두 번째 페이지부터 커서 적용
        if (lastDoc != null) q = q.startAfter(lastDoc);

        q.get().addOnSuccessListener(snap -> {
                    if (!isAdded()) return;
                    if (loadingFooter != null) loadingFooter.setVisibility(View.GONE);
                    isLoading = false;

                    if (snap == null || snap.isEmpty()) {
                        hasMore = false;
                        if (allItems.isEmpty()) {
                            if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
                        }
                        return;
                    }

                    // 페이지 크기보다 적게 왔으면 마지막 페이지
                    if (snap.size() < PAGE_SIZE) hasMore = false;

                    // 커서 업데이트
                    lastDoc = snap.getDocuments().get(snap.size() - 1);

                    // 문서 → 아이템 변환 (RecruitAdapter.RecruitItem 실제 필드 기준)
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        RecruitAdapter.RecruitItem it = new RecruitAdapter.RecruitItem();
                        it.id           = d.getId();
                        it.teamName     = d.getString("teamName");
                        it.teamLogoUrl  = AppUtils.firstNonEmpty(
                                d.getString("teamLogoUrl"), d.getString("logoUrl"));
                        it.date         = d.getString("date");
                        it.time         = d.getString("time");
                        it.weekday      = d.getString("weekday");
                        it.stadiumName  = AppUtils.firstNonEmpty(
                                d.getString("stadiumName"), d.getString("stadium"));
                        it.stadiumAddress = AppUtils.firstNonEmpty(
                                d.getString("address"), d.getString("stadiumAddress"));
                        it.recruitType  = d.getString("recruitType");
                        it.relativeTime = d.getString("relativeTime");

                        Long skillMinL = d.getLong("skillMin");
                        Long skillMaxL = d.getLong("skillMax");
                        it.skillMin = skillMinL != null ? skillMinL.intValue() : null;
                        it.skillMax = skillMaxL != null ? skillMaxL.intValue() : null;

                        List<String> pos = (List<String>) d.get("positions");
                        it.positions = pos != null ? pos : new ArrayList<>();

                        Long createdAtMs = d.getLong("createdAtMs");
                        Timestamp createdAtTs = d.getTimestamp("createdAt");
                        Long postTs  = d.getLong("postTs");
                        Long matchTs = d.getLong("matchTs");

                        it.createdAtMs = createdAtMs != null ? createdAtMs : 0L;
                        it.createdAt   = createdAtTs != null ? createdAtTs.toDate().getTime() : 0L;
                        it.postTs      = postTs  != null ? postTs  : 0L;
                        it.matchTs     = matchTs != null ? matchTs : 0L;

                        allItems.add(it);
                    }

                    // 정렬 후 필터 적용
                    Collections.sort(allItems, (a, b) ->
                            Long.compare(sortKey(b), sortKey(a)));
                    applyFilters();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    isLoading = false;
                    if (loadingFooter != null) loadingFooter.setVisibility(View.GONE);
                });
    }

    private long sortKey(RecruitAdapter.RecruitItem it) {
        if (it.createdAtMs > 0) return it.createdAtMs;
        if (it.createdAt   > 0) return it.createdAt;
        if (it.postTs      > 0) return it.postTs;
        return it.matchTs;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // get() 방식은 리스너 없으므로 해제 불필요
    }

    private void applyFilters() {
        if (adapter == null) return;
        List<RecruitAdapter.RecruitItem> out = new ArrayList<>();
        for (RecruitAdapter.RecruitItem it : allItems) {
            if (passFilters(it, currentFilters)) out.add(it);
        }
        adapter.submit(out);
        if (emptyView != null)
            emptyView.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean passFilters(RecruitAdapter.RecruitItem it, @Nullable RecruitFilters f) {
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

        // positions는 List<String>
        if (!isAll(f.position) && it.positions != null && !it.positions.isEmpty()) {
            boolean matched = false;
            for (String p : it.positions) {
                if (p != null && p.equalsIgnoreCase(f.position)) { matched = true; break; }
            }
            if (!matched) return false;
        }

        if (!isAll(f.recruitType) && !AppUtils.safe(it.recruitType).equalsIgnoreCase(f.recruitType)) return false;

        return true;
    }

    private boolean isAll(String v) {
        return v == null || v.isEmpty() || v.equals("전체");
    }
}
