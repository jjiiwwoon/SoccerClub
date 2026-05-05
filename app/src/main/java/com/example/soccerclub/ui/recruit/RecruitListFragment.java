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
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class RecruitListFragment extends Fragment {

    private RecyclerView recyclerRecruit;
    private TextView emptyView;
    private RecruitAdapter adapter;

    private RecruitFilters currentFilters = null;
    private final List<RecruitAdapter.RecruitItem> allItems = new ArrayList<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration reg;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_recruit_list, container, false);

        recyclerRecruit = v.findViewById(R.id.recyclerRecruit);
        emptyView       = v.findViewById(R.id.emptyView);

        recyclerRecruit.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecruitAdapter();
        recyclerRecruit.setAdapter(adapter);

        attachRealtime();
        return v;
    }

    public void applyExternalFilters(@Nullable RecruitFilters filters) {
        this.currentFilters = filters;
        applyFilters();
    }

    private void attachRealtime() {
        detachRealtime();
        reg = db.collection("recruitPosts")
                .whereEqualTo("status", "open")
                .limit(100)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || e != null || snap == null) return;
                    allItems.clear();

                    for (DocumentSnapshot ds : snap.getDocuments()) {
                        RecruitAdapter.RecruitItem it = new RecruitAdapter.RecruitItem();
                        it.id           = ds.getId();
                        it.teamName     = ds.getString("teamName");
                        it.teamLogoUrl  = AppUtils.firstNonEmpty(
                                ds.getString("teamLogoUrl"), ds.getString("logoUrl"));
                        it.date         = ds.getString("date");
                        it.time         = ds.getString("time");
                        it.weekday      = ds.getString("weekday");
                        it.stadiumName  = AppUtils.firstNonEmpty(
                                ds.getString("stadiumName"), ds.getString("stadium"));
                        it.stadiumAddress = AppUtils.firstNonEmpty(
                                ds.getString("address"), ds.getString("stadiumAddress"));
                        it.recruitType  = ds.getString("recruitType");
                        it.relativeTime = ds.getString("relativeTime");

                        Long skillMinL = ds.getLong("skillMin");
                        Long skillMaxL = ds.getLong("skillMax");
                        it.skillMin = skillMinL != null ? skillMinL.intValue() : null;
                        it.skillMax = skillMaxL != null ? skillMaxL.intValue() : null;

                        List<String> pos = (List<String>) ds.get("positions");
                        it.positions = pos != null ? pos : new ArrayList<>();

                        Long createdAtMs = ds.getLong("createdAtMs");
                        Timestamp createdAtTs = ds.getTimestamp("createdAt");
                        Long postTs  = ds.getLong("postTs");
                        Long matchTs = ds.getLong("matchTs");

                        it.createdAtMs = createdAtMs != null ? createdAtMs : 0L;
                        it.createdAt   = createdAtTs != null ? createdAtTs.toDate().getTime() : 0L;
                        it.postTs      = postTs  != null ? postTs  : 0L;
                        it.matchTs     = matchTs != null ? matchTs : 0L;

                        allItems.add(it);
                    }

                    Collections.sort(allItems, (a, b) ->
                            Long.compare(sortKey(b), sortKey(a)));

                    applyFilters();
                });
    }

    private long sortKey(RecruitAdapter.RecruitItem it) {
        if (it.createdAtMs > 0) return it.createdAtMs;
        if (it.createdAt   > 0) return it.createdAt;
        if (it.postTs      > 0) return it.postTs;
        return it.matchTs;
    }

    private void detachRealtime() {
        if (reg != null) { reg.remove(); reg = null; }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        detachRealtime();
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
            int postMin = it.skillMin != null ? it.skillMin : Integer.MIN_VALUE;
            if (postMin > f.skillMax) return false;
        }

        if (!isAll(f.position)) {
            List<String> wanted = splitSelections(f.position);
            boolean ok = false;
            if (it.positions != null) {
                for (String p : it.positions) {
                    if (wanted.contains(AppUtils.safe(p))) { ok = true; break; }
                }
            }
            if (!ok) return false;
        }

        if (!isAll(f.recruitType)) {
            String postType = AppUtils.normalizeRecruitType(it.recruitType);
            String wantType = AppUtils.normalizeRecruitType(f.recruitType);
            if (!wantType.equals(postType)) return false;
        }

        if (!isAll(f.dateFrom) || !isAll(f.dateTo)) {
            String postDate = AppUtils.safe(it.date);
            if (!postDate.isEmpty()) {
                if (!isAll(f.dateFrom) && postDate.compareTo(f.dateFrom) < 0) return false;
                if (!isAll(f.dateTo)   && postDate.compareTo(f.dateTo)   > 0) return false;
            }
        }

        if (!isAll(f.timeFrom) || !isAll(f.timeTo)) {
            String postTime = AppUtils.safe(it.time);
            if (!postTime.isEmpty()) {
                if (!isAll(f.timeFrom) && postTime.compareTo(f.timeFrom) < 0) return false;
                if (!isAll(f.timeTo)   && postTime.compareTo(f.timeTo)   > 0) return false;
            }
        }

        if (!isAll(f.weekday)) {
            List<String> wanted = splitSelections(f.weekday);
            if (!wanted.contains(AppUtils.safe(it.weekday))) return false;
        }

        return true;
    }

    private boolean isAll(String v) {
        return v == null || v.trim().isEmpty() || "전체".equals(v.trim());
    }

    private List<String> splitSelections(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String p : raw.split(",")) {
            String v = p.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }
}