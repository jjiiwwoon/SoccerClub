
package com.jjw.soccerclub.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.adapter.EventPickAdapter;
import com.jjw.soccerclub.util.AppUtils;

import java.util.List;

public class SchedulePickerDialog extends BottomSheetDialogFragment {

    @FunctionalInterface
    public interface OnScheduleSelected {
        void onSelected(DocumentSnapshot doc);
    }

    private static final String ARG_TEAM_ID      = "teamId";
    private static final String ARG_MY_TEAM_ID   = "myTeamId";
    private static final String ARG_MY_TEAM_NAME = "myTeamName";

    private OnScheduleSelected listener;

    public static SchedulePickerDialog newInstance(String teamId, String myTeamId, String myTeamName) {
        SchedulePickerDialog f = new SchedulePickerDialog();
        Bundle b = new Bundle();
        b.putString(ARG_TEAM_ID, teamId);
        b.putString(ARG_MY_TEAM_ID, myTeamId);
        b.putString(ARG_MY_TEAM_NAME, myTeamName);
        f.setArguments(b);
        return f;
    }

    public void setOnScheduleSelected(OnScheduleSelected l) { this.listener = l; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inf.inflate(R.layout.dialog_schedule_picker, container, false);

        RecyclerView rv = v.findViewById(R.id.recycler);
        ProgressBar pb  = v.findViewById(R.id.progress);
        TextView empty  = v.findViewById(R.id.empty);

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        pb.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        String teamId     = AppUtils.safe(getArguments() != null ? getArguments().getString(ARG_TEAM_ID) : "");
        String myTeamId   = AppUtils.safe(getArguments() != null ? getArguments().getString(ARG_MY_TEAM_ID) : "");
        String myTeamName = AppUtils.safe(getArguments() != null ? getArguments().getString(ARG_MY_TEAM_NAME) : "");

        long nowMs = System.currentTimeMillis();

        FirebaseFirestore.getInstance()
                .collection("schedules").document(teamId).collection("events")
                .whereGreaterThanOrEqualTo("matchTs", nowMs)
                .orderBy("matchTs", Query.Direction.ASCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(qs -> {
                    pb.setVisibility(View.GONE);
                    List<DocumentSnapshot> docs = qs.getDocuments();
                    if (docs.isEmpty()) {
                        empty.setVisibility(View.VISIBLE);
                        empty.setText("불러올 일정이 없습니다.");
                        return;
                    }
                    rv.setAdapter(new EventPickAdapter(docs, myTeamId, myTeamName, d -> {
                        if (listener != null) listener.onSelected(d);
                        dismiss();
                    }));
                })
                .addOnFailureListener(e -> {
                    pb.setVisibility(View.GONE);
                    empty.setVisibility(View.VISIBLE);
                    empty.setText("불러오기 실패: " + e.getMessage());
                });

        return v;
    }
}
