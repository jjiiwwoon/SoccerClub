package com.jjw.soccerclub.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.util.GlideHelper;

import java.util.ArrayList;
import java.util.List;

public class TeamMemberAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_PLAYER = 1;

    private List<MemberItem> itemList = new ArrayList<>();
    private String captainUid;
    private String viceCaptainUid;
    private String currentUid;
    private OnPlayerLongClickListener longClickListener;

    public interface OnPlayerLongClickListener {
        void onLongClick(String nickname, String uid);
    }

    public TeamMemberAdapter(String captainUid, String viceCaptainUid,
                             String currentUid, OnPlayerLongClickListener listener) {
        this.captainUid        = captainUid;
        this.viceCaptainUid    = viceCaptainUid;
        this.currentUid        = currentUid;
        this.longClickListener = listener;
    }

    public void setItems(List<MemberItem> items) {
        this.itemList = items;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return itemList.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            TextView headerView = new TextView(parent.getContext());
            headerView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            int dp16 = dp(parent.getContext(), 16);
            int dp8  = dp(parent.getContext(), 8);
            headerView.setPadding(dp16, dp8, dp16, dp8);
            headerView.setTextSize(13f);
            headerView.setTextColor(0xFF888888);
            return new HeaderViewHolder(headerView);
        }
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_team_member_card, parent, false);
        return new PlayerViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MemberItem item = itemList.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).header.setText(item.header);
        } else if (holder instanceof PlayerViewHolder) {
            bindPlayer((PlayerViewHolder) holder, item);
        }
    }

    private void bindPlayer(PlayerViewHolder h, MemberItem item) {
        h.tvNickname.setText(TextUtils.isEmpty(item.nickname) ? "이름 없음" : item.nickname);
        h.tvPosition.setText(TextUtils.isEmpty(item.position) ? "-" : item.position);

        boolean isCaptain = item.uid != null && item.uid.equals(captainUid);
        boolean isVice    = item.uid != null && item.uid.equals(viceCaptainUid);
        boolean isCurrent = item.uid != null && item.uid.equals(currentUid);

        // 역할 배지 표시
        h.tvRole.setVisibility(View.GONE);
        if (isCaptain) {
            h.tvRole.setVisibility(View.VISIBLE);
            h.tvRole.setText("주장");
        } else if (isVice) {
            h.tvRole.setVisibility(View.VISIBLE);
            h.tvRole.setText("부주장");
        }

        GlideHelper.loadProfile(h.ivPhoto.getContext(), item.photoUrl, h.ivPhoto);

        // ✅ 현재 유저의 등급
        boolean currentIsCaptain = currentUid != null && currentUid.equals(captainUid);
        boolean currentIsVice    = currentUid != null && currentUid.equals(viceCaptainUid);

        // ✅ 롱클릭 가능 조건
        // - 주장: 본인 제외 모든 멤버 롱클릭 가능
        // - 부주장: 일반 멤버만 롱클릭 가능 (주장·부주장에게는 불가)
        // - 일반 멤버: 롱클릭 불가
        boolean canLongClick = false;
        if (!isCurrent) {
            if (currentIsCaptain) {
                // 주장은 본인 제외 모두 가능
                canLongClick = true;
            } else if (currentIsVice) {
                // 부주장은 일반 멤버(주장·부주장 아닌 사람)만 가능
                canLongClick = !isCaptain && !isVice;
            }
        }

        final boolean finalCanLongClick = canLongClick;
        h.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null && finalCanLongClick) {
                longClickListener.onLongClick(item.nickname, item.uid);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() { return itemList.size(); }

    private static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    public static class MemberItem {
        public static final int TYPE_HEADER = 0;
        public static final int TYPE_PLAYER = 1;
        public int    type;
        public String header;
        public String uid;
        public String nickname;
        public String position;
        public String photoUrl;
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView header;
        HeaderViewHolder(TextView v) { super(v); header = v; }
    }

    static class PlayerViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPhoto;
        TextView  tvNickname, tvPosition, tvRole;
        PlayerViewHolder(@NonNull View v) {
            super(v);
            ivPhoto    = v.findViewById(R.id.ivPhoto);
            tvNickname = v.findViewById(R.id.tvNickname);
            tvPosition = v.findViewById(R.id.tvPosition);
            tvRole     = v.findViewById(R.id.tvRole);
        }
    }
}