package com.example.soccerclub.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.example.soccerclub.R;
import com.example.soccerclub.ui.recruit.RecruitDetailActivity;
import com.example.soccerclub.util.AppUtils;
import com.example.soccerclub.util.DateUtils;
import com.example.soccerclub.util.GlideHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecruitAdapter extends RecyclerView.Adapter<RecruitAdapter.VH> {

    private final List<RecruitItem> items = new ArrayList<>();

    public void submit(List<RecruitItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recruit_post_item, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Context ctx = h.itemView.getContext();
        RecruitItem it = items.get(position);

        h.textTeamName.setText(AppUtils.safe(it.teamName));
        GlideHelper.loadTeamLogo(ctx, it.teamLogoUrl, h.imageTeamLogo);

        h.textDate.setText(DateUtils.appendWeekday(it.date));
        h.textTime.setText(AppUtils.nz(it.time, "-"));

        String addressLine = AppUtils.firstNonEmpty(
                buildAddressLine(it.stadiumName, it.stadiumAddress), "-"
        );
        h.textStadium.setText(addressLine);

        String skillLeft  = it.skillMin == null ? "-" : String.valueOf(it.skillMin);
        String skillRight = it.skillMax == null ? "-" : String.valueOf(it.skillMax);
        h.textSkill.setText(String.format(Locale.getDefault(), "실력 : %s ~ %s", skillLeft, skillRight));

        boolean isMercenary = "mercenary".equalsIgnoreCase(AppUtils.normalizeRecruitType(it.recruitType));
        h.textRecruitType.setText(isMercenary ? "용병" : "회원");
        h.textRecruitType.setBackgroundResource(
                isMercenary ? R.drawable.bg_badge_red_circle : R.drawable.bg_badge_blue
        );

        long ts = it.createdAtMs > 0 ? it.createdAtMs
                : it.createdAt > 0 ? it.createdAt
                : it.postTs > 0 ? it.postTs : it.matchTs;
        h.textTimestamp.setText(ts > 0 ? DateUtils.formatRelativeTime(ts) : "");

        h.chipGroupPositions.removeAllViews();
        if (it.positions != null) {
            for (String pos : it.positions) {
                if (AppUtils.isEmpty(pos)) continue;
                Chip chip = new Chip(ctx);
                chip.setText(pos);
                chip.setClickable(false);
                h.chipGroupPositions.addView(chip);
            }
        }

        h.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(ctx, RecruitDetailActivity.class);
            intent.putExtra("recruitId", it.id);
            ctx.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    private String buildAddressLine(String name, String addr) {
        String n = AppUtils.safe(name);
        String a = AppUtils.safe(addr);
        if (!n.isEmpty() && !a.isEmpty()) return n + " | " + a;
        if (!n.isEmpty()) return n;
        if (!a.isEmpty()) return a;
        return "";
    }

    public static class VH extends RecyclerView.ViewHolder {
        ImageView imageTeamLogo;
        TextView textTeamName, textDate, textTime, textStadium,
                textSkill, textRecruitType, textTimestamp;
        ChipGroup chipGroupPositions;

        public VH(@NonNull View v) {
            super(v);
            imageTeamLogo     = v.findViewById(R.id.imageTeamLogo);
            textTeamName      = v.findViewById(R.id.textTeamName);
            textDate          = v.findViewById(R.id.textDate);
            textTime          = v.findViewById(R.id.textTime);
            textStadium       = v.findViewById(R.id.textStadium);
            textSkill         = v.findViewById(R.id.textSkill);
            textRecruitType   = v.findViewById(R.id.textRecruitType);
            textTimestamp     = v.findViewById(R.id.textTimestamp);
            chipGroupPositions = v.findViewById(R.id.chipGroupPositions);
        }
    }

    public static class RecruitItem {
        public String id;
        public String teamLogoUrl;
        public String teamName;
        public String date;
        public String time;
        public String stadiumName;
        public String stadiumAddress;
        public Integer skillMin;
        public Integer skillMax;
        public List<String> positions;
        public String recruitType;
        public String relativeTime;
        public long matchTs;
        public long postTs;
        public long createdAtMs;
        public long createdAt;
        public String weekday;
    }
}