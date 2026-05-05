package com.example.soccerclub.adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.model.MatchPost;
import com.example.soccerclub.ui.match.MatchDetailActivity;
import com.example.soccerclub.util.AppUtils;
import com.example.soccerclub.util.DateUtils;
import com.example.soccerclub.util.GlideHelper;

import java.util.List;
import java.util.Map;

public class MatchPostAdapter extends RecyclerView.Adapter<MatchPostAdapter.MatchPostViewHolder> {

    private List<MatchPost> postList;
    private final Context context;
    private final boolean useRecommendLayout;
    private Map<String, List<String>> reasonsMap;

    public MatchPostAdapter(Context context, List<MatchPost> postList) {
        this(context, postList, false);
    }

    public MatchPostAdapter(Context context, List<MatchPost> postList, boolean useRecommendLayout) {
        this.context = context;
        this.postList = postList;
        this.useRecommendLayout = useRecommendLayout;
    }

    public void setReasonsMap(Map<String, List<String>> reasonsMap) {
        this.reasonsMap = reasonsMap;
        notifyDataSetChanged();
    }

    public void setMatchPostList(List<MatchPost> filteredList) {
        this.postList = filteredList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MatchPostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = useRecommendLayout ? R.layout.recommend_match_item : R.layout.match_post_item;
        View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
        return new MatchPostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MatchPostViewHolder holder, int position) {
        MatchPost post = postList.get(position);

        holder.textTeamName.setText(AppUtils.safe(post.getTeamName()));
        holder.textDate.setText(DateUtils.appendWeekday(post.getDate()));
        holder.textTime.setText(AppUtils.safe(post.getTime()));

        String stadium = AppUtils.firstNonEmpty(post.getStadium(), post.getStadiumName());
        String address = AppUtils.firstNonEmpty(post.getAddress(), post.getStadiumAddress());
        holder.textStadium.setText(AppUtils.safe(stadium) + " | " + AppUtils.safe(address));
        holder.textSkill.setText("실력 : " + post.getSkill());

        long ts = post.getTimestamp() > 0
                ? post.getTimestamp()
                : DateUtils.computeStartMillis(post.getDate(), post.getTime());
        holder.textTimestamp.setText(DateUtils.formatRelativeTime(ts));

        String logo = AppUtils.firstNonEmpty(post.getLogoUrl(), post.getTeamLogoUrl());
        GlideHelper.loadTeamLogo(context, logo, holder.imageTeamLogo);

        if (holder.recoReasonsContainer != null) {
            bindReasons(holder.recoReasonsContainer, post.getMatchId());
        }

        holder.itemRoot.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), MatchDetailActivity.class);
            intent.putExtra("matchId", post.getMatchId());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return postList == null ? 0 : postList.size(); }

    private void bindReasons(LinearLayout container, String matchId) {
        if (container == null) return;
        if (reasonsMap == null || matchId == null || !reasonsMap.containsKey(matchId)) {
            container.setVisibility(View.GONE);
            container.removeAllViews();
            return;
        }
        List<String> reasons = reasonsMap.get(matchId);
        if (reasons == null || reasons.isEmpty()) {
            container.setVisibility(View.GONE);
            container.removeAllViews();
            return;
        }
        container.setVisibility(View.VISIBLE);
        container.removeAllViews();
        int shown = 0;
        for (String r : reasons) {
            if (AppUtils.isEmpty(r)) continue;
            container.addView(makeChip(r.trim()));
            if (++shown == 3) break;
        }
        if (shown == 0) container.setVisibility(View.GONE);
    }

    private View makeChip(String text) {
        TextView tv = new TextView(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(28));
        lp.setMarginEnd(dp(6));
        lp.gravity = android.view.Gravity.CENTER_VERTICAL;
        tv.setLayoutParams(lp);
        tv.setText(text);
        tv.setIncludeFontPadding(false);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setTextColor(Color.parseColor("#111111"));
        tv.setPadding(dp(10), 0, dp(10), 0);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setBackgroundResource(R.drawable.bg_filter_chip_selector);
        return tv;
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }

    public static class MatchPostViewHolder extends RecyclerView.ViewHolder {
        View itemRoot;
        TextView textTeamName, textDate, textTime, textStadium, textSkill, textTimestamp;
        ImageView imageTeamLogo;
        LinearLayout recoReasonsContainer;

        public MatchPostViewHolder(@NonNull View itemView) {
            super(itemView);
            itemRoot              = itemView.findViewById(R.id.itemRoot);
            imageTeamLogo         = itemView.findViewById(R.id.imageTeamLogo);
            textTeamName          = itemView.findViewById(R.id.textTeamName);
            textDate              = itemView.findViewById(R.id.textDate);
            textTime              = itemView.findViewById(R.id.textTime);
            textStadium           = itemView.findViewById(R.id.textStadium);
            textSkill             = itemView.findViewById(R.id.textSkill);
            textTimestamp         = itemView.findViewById(R.id.textTimestamp);
            recoReasonsContainer  = itemView.findViewById(R.id.recoReasonsContainer);
        }
    }
}