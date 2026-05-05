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
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class MatchListFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progress;
    private TextView emptyView;

    private MatchPostAdapter adapter;
    private final List<MatchPost> items = new ArrayList<>();
    private final LinkedHashMap<String, MatchPost> merged = new LinkedHashMap<>();

    private ListenerRegistration regNew, regLegacy;

    @Nullable
    private MatchFilters currentFilters = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_match_list, container, false);

        recyclerView = v.findViewById(R.id.recyclerMatch);
        progress     = v.findViewById(R.id.progress);
        emptyView    = v.findViewById(R.id.emptyView);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MatchPostAdapter(requireContext(), items);
        recyclerView.setAdapter(adapter);

        attachListeners(currentFilters);
        return v;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        detachListeners();
    }

    public void applyExternalFilters(@Nullable MatchFilters filters) {
        this.currentFilters = filters;
        if (adapter != null) refreshList(filters);
    }

    private void attachListeners(@Nullable MatchFilters filters) {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        detachListeners();
        merged.clear();
        items.clear();
        if (adapter != null) adapter.notifyDataSetChanged();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        regNew = db.collection("matches")
                .whereEqualTo("status", "OPEN")
                .orderBy("matchTs", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || e != null || snap == null) return;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        MatchPost p = d.toObject(MatchPost.class);
                        if (p == null) continue;
                        p.setMatchId(d.getId());
                        fillFallbacks(p, d);
                        merged.put(d.getId(), p);
                    }
                    refreshList(filters);
                });

        regLegacy = db.collection("matches")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded() || e != null || snap == null) return;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        MatchPost p = d.toObject(MatchPost.class);
                        if (p == null) continue;
                        p.setMatchId(d.getId());
                        fillFallbacks(p, d);
                        merged.put(d.getId(), p);
                    }
                    refreshList(filters);
                });
    }

    private void detachListeners() {
        if (regNew    != null) { regNew.remove();    regNew    = null; }
        if (regLegacy != null) { regLegacy.remove(); regLegacy = null; }
    }

    private void refreshList(@Nullable MatchFilters filters) {
        if (adapter == null) return;
        items.clear();
        for (MatchPost post : merged.values()) {
            if (passClientFilters(post, filters)) items.add(post);
        }
        items.sort((a, b) -> Long.compare(bestTs(b), bestTs(a)));
        adapter.notifyDataSetChanged();
        if (progress  != null) progress.setVisibility(View.GONE);
        if (emptyView != null) emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean passClientFilters(@NonNull MatchPost post, @Nullable MatchFilters f) {
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

    private void fillFallbacks(MatchPost p, DocumentSnapshot d) {
        if (AppUtils.isEmpty(p.getTeamName())) {
            String tn = d.getString("homeTeamName");
            if (!AppUtils.isEmpty(tn)) p.setTeamName(tn);
        }
        if (AppUtils.isEmpty(p.getLogoUrl())) {
            String lu = AppUtils.firstNonEmpty(d.getString("teamLogoUrl"), d.getString("homeTeamLogoUrl"));
            if (!AppUtils.isEmpty(lu)) p.setLogoUrl(lu);
        }
        if (AppUtils.isEmpty(p.getAddress())) {
            String addr = AppUtils.firstNonEmpty(d.getString("stadiumAddress"), d.getString("address"));
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
        if (p.getMatchTs() > 0) return p.getMatchTs();
        if (p.getTimestamp() > 0) return p.getTimestamp();
        return 0;
    }

    private boolean isAll(String v) {
        return v == null || v.trim().isEmpty() || "전체".equals(v.trim());
    }
}