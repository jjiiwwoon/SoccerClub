package com.jjw.soccerclub.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomCalendarView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class CalendarAdapter extends RecyclerView.Adapter<CalendarAdapter.DayViewHolder> {

    private List<CustomCalendarView.CalendarDate> days = new ArrayList<>();

    private int year = -1, month = -1, selectedDay = -1;

    private final Calendar today = Calendar.getInstance();
    private final int todayYear  = today.get(Calendar.YEAR);
    private final int todayMonth = today.get(Calendar.MONTH);
    private final int todayDay   = today.get(Calendar.DAY_OF_MONTH);

    public interface OnItemClickListener {
        void onItemClick(int position, CustomCalendarView.CalendarDate date);
    }
    private OnItemClickListener listener;
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setDays(List<CustomCalendarView.CalendarDate> newDays) {
        this.days = newDays;
        notifyDataSetChanged();
    }

    public void setYearMonth(int year, int month) {
        this.year = year;
        this.month = month;
        notifyDataSetChanged();
    }

    public void setSelectedDay(int year, int month, int day) {
        this.selectedDay = (this.year == year && this.month == month) ? day : -1;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() { return days.size(); }

    @NonNull
    @Override
    public DayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.calendar_day_item, parent, false);

        int totalWidth = parent.getMeasuredWidth();
        if (totalWidth == 0) totalWidth = parent.getResources().getDisplayMetrics().widthPixels;

        int margin    = (int) (parent.getResources().getDisplayMetrics().density * 2);
        int itemWidth = (totalWidth - margin * 8) / 7;
        int itemHeight = itemWidth + (int) (parent.getResources().getDisplayMetrics().density * 20);

        view.setLayoutParams(new ViewGroup.LayoutParams(itemWidth, itemHeight));
        return new DayViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DayViewHolder holder, int position) {
        CustomCalendarView.CalendarDate item = days.get(position);

        holder.tvDay.setText(String.valueOf(item.day));
        holder.tvDay.setAlpha(item.inThisMonth ? 1f : 0.35f);
        holder.tvDay.setTextColor(Color.BLACK);

        holder.dayContainer.setBackgroundResource(R.drawable.bg_rect_default);

        boolean isToday = item.inThisMonth
                && year == todayYear && month == todayMonth && item.day == todayDay;
        if (isToday) holder.dayContainer.setBackgroundResource(R.drawable.bg_rect_today);

        boolean isSelected = item.inThisMonth && item.day == selectedDay;
        if (isSelected) holder.dayContainer.setBackgroundResource(R.drawable.bg_rect_selected);

        boolean showMatch = item.inThisMonth && item.hasMatch;
        holder.matchIndicator.setVisibility(showMatch ? View.VISIBLE : View.GONE);

        if (showMatch) {
            holder.textMatch.setTextColor(item.isPastMatch ? 0xFF9E9E9E : 0xFFFF5722);
            holder.iconMatch.setColorFilter(item.isPastMatch ? 0xFF9E9E9E : 0xFFFF5722);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onItemClick(position, item);
        });
    }

    public static class DayViewHolder extends RecyclerView.ViewHolder {
        View dayContainer;
        TextView tvDay, textMatch;
        ImageView iconMatch;
        View matchIndicator;

        public DayViewHolder(@NonNull View itemView) {
            super(itemView);
            dayContainer   = itemView.findViewById(R.id.dayContainer);
            tvDay          = itemView.findViewById(R.id.tvDay);
            matchIndicator = itemView.findViewById(R.id.matchIndicator);
            textMatch      = itemView.findViewById(R.id.textMatch);
            iconMatch      = itemView.findViewById(R.id.iconMatch);
        }
    }
}