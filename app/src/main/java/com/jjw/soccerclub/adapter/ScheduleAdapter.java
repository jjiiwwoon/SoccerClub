package com.jjw.soccerclub.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.model.ScheduleItem;
import com.jjw.soccerclub.util.AppUtils;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class ScheduleAdapter extends RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder> {

    public interface OnScheduleActionListener {
        void onWriteRecord(ScheduleItem item);
    }

    private final List<ScheduleItem> scheduleList;
    private final OnScheduleActionListener listener;

    public ScheduleAdapter(List<ScheduleItem> scheduleList) {
        this(scheduleList, null);
    }

    public ScheduleAdapter(List<ScheduleItem> scheduleList, OnScheduleActionListener listener) {
        this.scheduleList = scheduleList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ScheduleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.schedule_item, parent, false);
        return new ScheduleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScheduleViewHolder holder, int position) {
        ScheduleItem item = scheduleList.get(position);

        holder.tvTitle.setText(AppUtils.safe(item.title));
        holder.tvTime.setText("시간: " + AppUtils.safe(item.time));
        holder.tvOpponent.setText("상대 팀: " + AppUtils.safe(item.opponentName));
        holder.tvStadium.setText("장소: " + AppUtils.safe(item.stadiumName));
        holder.tvAddress.setText(AppUtils.safe(item.address));

        holder.btnWriteRecord.setVisibility(View.VISIBLE); // 테스트 모드

        holder.btnWriteRecord.setOnClickListener(v -> {
            if (listener != null) listener.onWriteRecord(item);
        });
    }

    @Override
    public int getItemCount() { return scheduleList.size(); }

    public static class ScheduleViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTime, tvOpponent, tvStadium, tvAddress;
        MaterialButton btnWriteRecord;

        public ScheduleViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle       = itemView.findViewById(R.id.tvTitle);
            tvTime        = itemView.findViewById(R.id.tvTime);
            tvOpponent    = itemView.findViewById(R.id.tvOpponent);
            tvStadium     = itemView.findViewById(R.id.tvStadium);
            tvAddress     = itemView.findViewById(R.id.tvAddress);
            btnWriteRecord = itemView.findViewById(R.id.btnWriteRecord);
        }
    }
}