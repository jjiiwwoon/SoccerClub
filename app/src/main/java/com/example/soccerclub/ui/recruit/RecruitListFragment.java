package com.example.soccerclub.ui.recruit;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.adapter.RecruitAdapter;
import com.example.soccerclub.model.RecruitFilters;
import com.example.soccerclub.viewmodel.RecruitViewModel;

public class RecruitListFragment extends Fragment {

    private RecyclerView   recyclerRecruit;
    private TextView       emptyView;
    private View           loadingFooter;
    private RecruitAdapter adapter;

    private RecruitViewModel viewModel;
    private RecruitFilters   pendingFilters = null;

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

        recyclerRecruit.addOnScrollListener(new RecyclerView.OnScrollListener() {
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

        viewModel = new ViewModelProvider(this).get(RecruitViewModel.class);

        viewModel.displayItems.observe(getViewLifecycleOwner(), items ->
                adapter.submit(items));

        viewModel.isLoading.observe(getViewLifecycleOwner(), loading -> {
            if (loadingFooter != null)
                loadingFooter.setVisibility(loading ? View.VISIBLE : View.GONE);
        });

        viewModel.isEmpty.observe(getViewLifecycleOwner(), empty -> {
            if (emptyView != null)
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        });

        viewModel.loadIfNeeded(pendingFilters);
    }

    public void applyExternalFilters(@Nullable RecruitFilters filters) {
        pendingFilters = filters;
        if (viewModel != null) {
            viewModel.applyFilters(filters != null ? filters : new RecruitFilters());
        }
    }
}