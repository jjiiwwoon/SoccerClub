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
import com.jjw.soccerclub.model.MatchPost;
import com.jjw.soccerclub.ui.match.MatchDetailActivity;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.jjw.soccerclub.util.GlideHelper;

import java.util.ArrayList;
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
        this.context           = context;
        this.postList          = postList != null ? postList : new ArrayList<>();
        this.useRecommendLayout = useRecommendLayout;
    }

    public void setReasonsMap(Map<String, List<String>> reasonsMap) {
        this.reasonsMap = reasonsMap;
        notifyDataSetChanged();
    }

    /** 기존 메서드 — 하위 호환 유지 */
    public void setMatchPostList(List<MatchPost> filteredList) {
        this.postList = filteredList != null ? filteredList : new ArrayList<>();
        notifyDataSetChanged();
    }

    // ✅ MatchViewModel 에서 새 List 를 전달할 때 호출
    public void updateItems(List<MatchPost> newItems) {
        this.postList = newItems != null ? newItems : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MatchPostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = useRecommendLayout
                ? R.layout.recommend_match_item
                : R.layout.match_post_item;
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

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(v.getContext(), MatchDetailActivity.class);
            intent.putExtra("matchId", post.getMatchId());
            v.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return postList == null ? 0 : postList.size();
    }

    private void bindReasons(LinearLayout container, String matchId) {
        container.removeAllViews();
        if (reasonsMap == null || matchId == null) return;
        List<String> reasons = reasonsMap.get(matchId);
        if (reasons == null) return;
        int max = Math.min(reasons.size(), 3);
        for (int i = 0; i < max; i++) {
            TextView badge = new TextView(container.getContext());
            badge.setText(reasons.get(i));
            badge.setTextSize(11f);
            badge.setPadding(12, 4, 12, 4);
            container.addView(badge);
        }
    }

    public static class MatchPostViewHolder extends RecyclerView.ViewHolder {
        ImageView     imageTeamLogo;
        TextView      textTeamName, textDate, textTime, textStadium, textSkill, textTimestamp;
        LinearLayout  recoReasonsContainer;

        public MatchPostViewHolder(@NonNull View itemView) {
            super(itemView);
            imageTeamLogo        = itemView.findViewById(R.id.imageTeamLogo);
            textTeamName         = itemView.findViewById(R.id.textTeamName);
            textDate             = itemView.findViewById(R.id.textDate);
            textTime             = itemView.findViewById(R.id.textTime);
            textStadium          = itemView.findViewById(R.id.textStadium);
            textSkill            = itemView.findViewById(R.id.textSkill);
            textTimestamp        = itemView.findViewById(R.id.textTimestamp);
            recoReasonsContainer = itemView.findViewById(R.id.recoReasonsContainer);
        }
    }
}