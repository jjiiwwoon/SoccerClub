package com.jjw.soccerclub.adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.ui.recruit.RecruitDetailActivity;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.jjw.soccerclub.util.GlideHelper;

import java.util.ArrayList;
import java.util.List;

public class RecruitAdapter extends RecyclerView.Adapter<RecruitAdapter.ViewHolder> {

    private List<RecruitItem> items = new ArrayList<>();

    public void submit(List<RecruitItem> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recruit_post_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        RecruitItem it = items.get(position);
        Context ctx = h.itemView.getContext();

        // 팀 로고
        GlideHelper.loadTeamLogo(ctx, it.teamLogoUrl, h.imgLogo);

        // 팀명
        h.tvTeamName.setText(AppUtils.safe(it.teamName));

        // 날짜 + 요일
        h.tvDate.setText(DateUtils.appendWeekday(it.date));

        // 시간
        h.tvTime.setText(AppUtils.safe(it.time));

        // 장소 (구장명 | 주소)
        String stadium = AppUtils.safe(it.stadiumName);
        String addr = AppUtils.safe(it.stadiumAddress);
        String stadiumText;
        if (!AppUtils.isEmpty(stadium) && !AppUtils.isEmpty(addr)) {
            stadiumText = stadium + " | " + addr;
        } else {
            stadiumText = AppUtils.firstNonEmpty(stadium, addr, "-");
        }
        h.tvStadium.setText(stadiumText);

        // ── 모집 유형 뱃지 ────────────────────────────────────────────────────
        String type = AppUtils.safe(it.recruitType).toLowerCase();
        if (type.contains("mercenary") || type.contains("용병")) {
            h.tvRecruitType.setText("용병");
            h.tvRecruitType.setVisibility(View.VISIBLE);
            // 주황색 뱃지
            h.tvRecruitType.setBackgroundResource(R.drawable.bg_badge_recruit_merc);
            h.tvRecruitType.setTextColor(ctx.getColor(R.color.badge_merc_text));
        } else if (type.contains("regular") || type.contains("회원")) {
            h.tvRecruitType.setText("회원");
            h.tvRecruitType.setVisibility(View.VISIBLE);
            // 파란색 뱃지
            h.tvRecruitType.setBackgroundResource(R.drawable.bg_badge_recruit);
            h.tvRecruitType.setTextColor(ctx.getColor(R.color.badge_recruit_text));
        } else {
            h.tvRecruitType.setVisibility(View.GONE);
        }

        // ── 포지션 칩 동적 생성 (포지션별 색상) ─────────────────────────────
        h.positionChipsContainer.removeAllViews();
        if (it.positions != null && !it.positions.isEmpty()) {
            for (String pos : it.positions) {
                if (AppUtils.isEmpty(pos)) continue;
                TextView chip = (TextView) LayoutInflater.from(ctx)
                        .inflate(R.layout.item_position_chip, h.positionChipsContainer, false);
                String upper = pos.trim().toUpperCase();
                chip.setText(upper);
                stylePositionChip(chip, upper);
                h.positionChipsContainer.addView(chip);
            }
        }

        // 실력 뱃지 (칩 스타일)
        if (it.skillMin != null || it.skillMax != null) {
            String minStr = it.skillMin != null ? String.valueOf(it.skillMin) : "-";
            String maxStr = it.skillMax != null ? String.valueOf(it.skillMax) : "-";
            h.tvSkill.setText("실력: " + minStr + " ~ " + maxStr);
            h.tvSkill.setVisibility(View.VISIBLE);
        } else {
            h.tvSkill.setVisibility(View.GONE);
        }

        // ── 등록 시간 ─────────────────────────────────────────────────────────
        long ts = it.createdAtMs > 0 ? it.createdAtMs
                : it.createdAt > 0 ? it.createdAt
                : it.postTs > 0    ? it.postTs
                : it.matchTs;
        h.tvTimestamp.setText(ts > 0 ? DateUtils.formatRelativeTime(ts) : "");

        // 클릭
        h.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(ctx, RecruitDetailActivity.class);
            intent.putExtra("recruitId", it.id);
            ctx.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── ViewHolder ───────────────────────────────────────────────────────────

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView    imgLogo;
        TextView     tvTeamName, tvDate, tvTime, tvStadium;
        TextView     tvRecruitType, tvSkill, tvTimestamp;
        LinearLayout positionChipsContainer;

        public ViewHolder(@NonNull View v) {
            super(v);
            imgLogo               = v.findViewById(R.id.imageTeamLogo);
            tvTeamName            = v.findViewById(R.id.textTeamName);
            tvDate                = v.findViewById(R.id.textDate);
            tvTime                = v.findViewById(R.id.textTime);
            tvStadium             = v.findViewById(R.id.textStadium);
            tvRecruitType         = v.findViewById(R.id.textRecruitType);
            tvSkill               = v.findViewById(R.id.textSkill);
            tvTimestamp           = v.findViewById(R.id.textTimestamp);
            positionChipsContainer = v.findViewById(R.id.positionChipsContainer);
        }
    }

    // ── RecruitItem 모델 (기존 필드 유지) ────────────────────────────────────

    public static class RecruitItem {
        public String id, teamName, teamLogoUrl;
        public String date, time, weekday;
        public String stadiumName, stadiumAddress;
        public String recruitType, relativeTime;
        public Integer skillMin, skillMax;
        public List<String> positions;
        public long createdAtMs, createdAt, postTs, matchTs;
    }

    private static void stylePositionChip(TextView chip, String pos) {
        int textColor, bgColor;
        switch (pos) {
            case "FW":
                textColor = 0xFFD50000;
                bgColor   = 0x1AD50000;
                break;
            case "MF":
                textColor = 0xFF00C853;
                bgColor   = 0x1A00C853;
                break;
            case "DF":
                textColor = 0xFF2962FF;
                bgColor   = 0x1A2962FF;
                break;
            case "GK":
                textColor = 0xFFE65100;
                bgColor   = 0x1AE65100;
                break;
            default:
                textColor = 0xFF666666;
                bgColor   = 0x1A666666;
                break;
        }
        chip.setTextColor(textColor);
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(20f);
        gd.setColor(bgColor);
        gd.setStroke(2, textColor);
        chip.setBackground(gd);
    }
}