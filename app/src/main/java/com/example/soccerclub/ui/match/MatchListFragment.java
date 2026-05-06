package com.example.soccerclub.ui.match;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.adapter.MatchPostAdapter;
import com.example.soccerclub.model.MatchFilters;
import com.example.soccerclub.model.MatchPost;
import com.example.soccerclub.util.AppUtils;
import com.example.soccerclub.util.DateUtils;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MatchListFragment extends Fragment {

    private static final int PAGE_SIZE = 20;

    private RecyclerView recyclerView;
    private ProgressBar  progress;
    private TextView     emptyView;
    private View         loadingFooter;

    private MatchPostAdapter adapter;
    private final List<MatchPost>                items  = new ArrayList<>();
    private final LinkedHashMap<String, MatchPost> merged = new LinkedHashMap<>();

    @Nullable private MatchFilters currentFilters = null;

    // 페이지네이션 상태 (신규 스키마)
    private DocumentSnapshot lastDocNew     = null;
    private boolean          hasMoreNew     = true;
    // 레거시 스키마
    private DocumentSnapshot lastDocLegacy  = null;
    private boolean          hasMoreLegacy  = true;

    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_match_list, container, false);

        recyclerView  = v.findViewById(R.id.recyclerMatch);
        progress      = v.findViewById(R.id.progress);
        emptyView     = v.findViewById(R.id.emptyView);
        loadingFooter = v.findViewById(R.id.loadingFooter);

        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(lm);
        adapter = new MatchPostAdapter(requireContext(), items);
        recyclerView.setAdapter(adapter);

        // 스크롤 끝 감지 → 다음 페이지
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                int total       = lm.getItemCount();
                if (!isLoading && (hasMoreNew || hasMoreLegacy) && lastVisible >= total - 3) {
                    loadNextPage();
                }
            }
        });

        resetAndLoad();
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // get() 방식 — 리스너 없으므로 해제 불필요
    }

    public void applyExternalFilters(@Nullable MatchFilters filters) {
        this.currentFilters = filters;
        if (adapter != null) {
            // 필터 변경 시 처음부터 다시 로드
            resetAndLoad();
        }
    }

    // ── 초기화 후 첫 페이지 로드 ─────────────────────────────────────────────────

    private void resetAndLoad() {
        merged.clear();
        items.clear();
        lastDocNew    = null;
        lastDocLegacy = null;
        hasMoreNew    = true;
        hasMoreLegacy = true;
        isLoading     = false;
        if (adapter   != null) adapter.notifyDataSetChanged();
        if (emptyView != null) emptyView.setVisibility(View.GONE);
        if (progress  != null) progress.setVisibility(View.VISIBLE);
        loadNextPage();
    }

    // ── 다음 페이지 로드 (신규 + 레거시 병렬) ────────────────────────────────────

    private void loadNextPage() {
        if (isLoading || !isAdded()) return;
        if (!hasMoreNew && !hasMoreLegacy) return;
        isLoading = true;
        if (progress      != null) progress.setVisibility(View.VISIBLE);
        if (loadingFooter != null) loadingFooter.setVisibility(View.VISIBLE);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        AtomicBoolean newDone    = new AtomicBoolean(false);
        AtomicBoolean legacyDone = new AtomicBoolean(false);

        // ── 신규 스키마 쿼리 ───────────────────────────────────────────────────────
        if (hasMoreNew) {
            Query qNew = db.collection("matches")
                    .whereEqualTo("status", "OPEN")
                    .orderBy("matchTs", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE);
            if (lastDocNew != null) qNew = qNew.startAfter(lastDocNew);

            qNew.get().addOnCompleteListener(task -> {
                if (!isAdded()) return;
                if (task.isSuccessful() && task.getResult() != null) {
                    List<DocumentSnapshot> docs = task.getResult().getDocuments();
                    if (docs.size() < PAGE_SIZE) hasMoreNew = false;
                    if (!docs.isEmpty()) lastDocNew = docs.get(docs.size() - 1);
                    for (DocumentSnapshot d : docs) {
                        MatchPost p = d.toObject(MatchPost.class);
                        if (p == null) continue;
                        p.setMatchId(d.getId());
                        fillFallbacks(p, d);
                        merged.put(d.getId(), p);
                    }
                } else {
                    hasMoreNew = false;
                }
                newDone.set(true);
                if (legacyDone.get() || !hasMoreLegacy) onBothDone();
            });
        } else {
            newDone.set(true);
        }

        // ── 레거시 스키마 쿼리 ────────────────────────────────────────────────────
        if (hasMoreLegacy) {
            Query qLegacy = db.collection("matches")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(PAGE_SIZE);
            if (lastDocLegacy != null) qLegacy = qLegacy.startAfter(lastDocLegacy);

            qLegacy.get().addOnCompleteListener(task -> {
                if (!isAdded()) return;
                if (task.isSuccessful() && task.getResult() != null) {
                    List<DocumentSnapshot> docs = task.getResult().getDocuments();
                    if (docs.size() < PAGE_SIZE) hasMoreLegacy = false;
                    if (!docs.isEmpty()) lastDocLegacy = docs.get(docs.size() - 1);
                    for (DocumentSnapshot d : docs) {
                        if (merged.containsKey(d.getId())) continue; // 신규에서 이미 처리됨
                        MatchPost p = d.toObject(MatchPost.class);
                        if (p == null) continue;
                        p.setMatchId(d.getId());
                        fillFallbacks(p, d);
                        merged.put(d.getId(), p);
                    }
                } else {
                    hasMoreLegacy = false;
                }
                legacyDone.set(true);
                if (newDone.get() || !hasMoreNew) onBothDone();
            });
        } else {
            legacyDone.set(true);
            if (newDone.get()) onBothDone();
        }
    }

    // 신규 + 레거시 둘 다 완료됐을 때 UI 업데이트
    private void onBothDone() {
        if (!isAdded()) return;
        isLoading = false;
        if (progress      != null) progress.setVisibility(View.GONE);
        if (loadingFooter != null) loadingFooter.setVisibility(View.GONE);
        refreshList(currentFilters);
    }

    // ── 리스트 렌더링 ─────────────────────────────────────────────────────────────

    private void refreshList(@Nullable MatchFilters filters) {
        if (adapter == null) return;
        items.clear();
        for (MatchPost post : merged.values()) {
            if (passClientFilters(post, filters)) items.add(post);
        }
        items.sort((a, b) -> Long.compare(bestTs(b), bestTs(a)));
        adapter.notifyDataSetChanged();
        if (emptyView != null)
            emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── 클라이언트 필터 ───────────────────────────────────────────────────────────

    private boolean passClientFilters(@NonNull MatchPost post, @Nullable MatchFilters f) {
        // status 필터 (레거시는 status 없을 수 있음 — 있을 때만 체크)
        String st = post.getStatus();
        if (st != null && !st.trim().isEmpty()) {
            if (!st.trim().toUpperCase(Locale.getDefault()).equals("OPEN")) return false;
        }
        if (f == null) return true;

        String address = AppUtils.firstNonEmpty(
                post.getStadiumAddress(), post.getAddress(),
                post.getStadium(), post.getStadiumName());

        if (!isAll(f.common.city)     && !address.contains(f.common.city))     return false;
        if (!isAll(f.common.district) && !address.contains(f.common.district)) return false;

        int skill = post.getSkill();
        if (f.skillMin != null && skill != 0 && skill < f.skillMin) return false;
        if (f.skillMax != null && skill != 0 && skill > f.skillMax) return false;

        String postDate = AppUtils.safe(post.getDate());
        if (!isAll(f.dateFrom) && !postDate.isEmpty() && postDate.compareTo(f.dateFrom) < 0) return false;
        if (!isAll(f.dateTo)   && !postDate.isEmpty() && postDate.compareTo(f.dateTo)   > 0) return false;

        if (!isAll(f.weekday)) {
            String weekday = DateUtils.getKoreanWeekday(postDate);
            if (!f.weekday.contains(weekday)) return false;
        }

        return true;
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private void fillFallbacks(MatchPost p, DocumentSnapshot d) {
        if (AppUtils.isEmpty(p.getTeamName())) {
            String tn = d.getString("homeTeamName");
            if (!AppUtils.isEmpty(tn)) p.setTeamName(tn);
        }
        if (AppUtils.isEmpty(p.getLogoUrl())) {
            String lu = AppUtils.firstNonEmpty(
                    d.getString("teamLogoUrl"), d.getString("homeTeamLogoUrl"));
            if (!AppUtils.isEmpty(lu)) p.setLogoUrl(lu);
        }
        if (AppUtils.isEmpty(p.getAddress())) {
            String addr = AppUtils.firstNonEmpty(
                    d.getString("stadiumAddress"), d.getString("address"));
            if (!AppUtils.isEmpty(addr)) p.setAddress(addr);
        }
        if (p.getTimestamp() == 0) {
            Long ts = d.getLong("timestamp");
            if (ts != null) p.setTimestamp(ts);
        }
        if (p.getMatchTs() == 0) {
            long calc = DateUtils.computeStartMillis(p.getDate(), p.getTime());
            p.setMatchTs(calc);
        }
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
