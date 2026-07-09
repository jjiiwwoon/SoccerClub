package com.jjw.soccerclub.ui.match;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.adapter.MatchPostAdapter;
import com.jjw.soccerclub.model.MatchFilters;
import com.jjw.soccerclub.viewmodel.MatchViewModel;

import java.util.ArrayList;

public class MatchListFragment extends Fragment {

    private RecyclerView     recyclerView;
    private ProgressBar      progress;
    private TextView         emptyView;
    private View             loadingFooter;
    private MatchPostAdapter adapter;

    private MatchViewModel viewModel;
    private MatchFilters   pendingFilters = null;

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
        adapter = new MatchPostAdapter(requireContext(), new ArrayList<>());
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return;
                // ✅ 수정: viewModel null 체크 추가
                // onCreateView 시점에는 viewModel 이 아직 null 이므로 방어 필요
                if (viewModel == null) return;
                int lastVisible = lm.findLastVisibleItemPosition();
                int total       = lm.getItemCount();
                if (lastVisible >= total - 3) {
                    viewModel.loadNextPage();
                }
            }
        });

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(MatchViewModel.class);

        viewModel.displayItems.observe(getViewLifecycleOwner(), items ->
                adapter.updateItems(items));

        viewModel.isLoading.observe(getViewLifecycleOwner(), loading -> {
            if (progress != null) progress.setVisibility(
                    (loading && adapter.getItemCount() == 0) ? View.VISIBLE : View.GONE);
            if (loadingFooter != null) loadingFooter.setVisibility(
                    (loading && adapter.getItemCount() > 0) ? View.VISIBLE : View.GONE);
        });

        viewModel.isEmpty.observe(getViewLifecycleOwner(), empty -> {
            if (emptyView != null)
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        });

        viewModel.loadIfNeeded(pendingFilters);
    }

    public void applyExternalFilters(@Nullable MatchFilters filters) {
        pendingFilters = filters;
        if (viewModel != null) {
            viewModel.applyFilters(filters != null ? filters : new MatchFilters());
        }
    }
}