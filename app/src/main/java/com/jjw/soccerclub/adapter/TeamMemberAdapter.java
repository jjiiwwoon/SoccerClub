package com.jjw.soccerclub.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.util.AppUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 팀 멤버 목록 어댑터.
 *
 * [변경 전] 큰 카드 형태 → 멤버 많을 때 스크롤이 너무 길어짐
 * [변경 후] 가로 2열 리스트 + 포지션 헤더에 컬러 바
 *   - FW : 빨간색  #E53935
 *   - MF : 파란색  #1E88E5
 *   - DF : 초록색  #43A047
 *   - GK : 주황색  #FB8C00
 *   - 헤더는 span 2, 멤버 아이템은 span 1 (GridLayoutManager 필요)
 */
public class TeamMemberAdapter
        extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_PLAYER = 1;

    private final String captainUid;
    private final String viceCaptainUid;
    private final String currentUid;
    private final OnMemberClickListener listener;

    private List<MemberItem> items = new ArrayList<>();

    public interface OnMemberClickListener {
        void onClick(String nickname, String uid);
    }

    public TeamMemberAdapter(String captainUid, String viceCaptainUid,
                             String currentUid, OnMemberClickListener listener) {
        this.captainUid     = captainUid     != null ? captainUid     : "";
        this.viceCaptainUid = viceCaptainUid != null ? viceCaptainUid : "";
        this.currentUid     = currentUid     != null ? currentUid     : "";
        this.listener       = listener;
    }

    public void setItems(List<MemberItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    // ── SpanSizeLookup 헬퍼 ──────────────────────────────────────────────────────

    /** GridLayoutManager 에 연결할 SpanSizeLookup 반환 */
    public GridLayoutManager.SpanSizeLookup getSpanSizeLookup() {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getItemViewType(position) == TYPE_HEADER ? 2 : 1;
            }
        };
    }

    // ── RecyclerView.Adapter ─────────────────────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View v = inf.inflate(R.layout.item_team_member_header, parent, false);
            return new HeaderVH(v);
        } else {
            View v = inf.inflate(R.layout.item_team_member_row, parent, false);
            return new PlayerVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderVH) {
            bindHeader((HeaderVH) holder, items.get(position));
        } else {
            bindPlayer((PlayerVH) holder, items.get(position));
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── 헤더 바인딩 ──────────────────────────────────────────────────────────────

    private void bindHeader(HeaderVH h, MemberItem item) {
        h.tvHeader.setText(item.header);

        // 포지션별 컬러 바
        int color = positionColor(item.header);
        if (h.positionBar != null) h.positionBar.setBackgroundColor(color);
    }

    /** 포지션 헤더 텍스트에서 컬러 결정 */
    private int positionColor(String header) {
        if (header == null) return 0xFF9E9E9E;
        String h = header.toUpperCase();
        if (h.startsWith("FW")) return 0xFFE53935; // 빨강
        if (h.startsWith("MF")) return 0xFF1E88E5; // 파랑
        if (h.startsWith("DF")) return 0xFF43A047; // 초록
        if (h.startsWith("GK")) return 0xFFFB8C00; // 주황
        return 0xFF9E9E9E;
    }

    // ── 플레이어 바인딩 ───────────────────────────────────────────────────────────

    private void bindPlayer(PlayerVH h, MemberItem item) {
        // 이름
        h.tvName.setText(AppUtils.safe(item.nickname));

        // 포지션
        if (h.tvPosition != null) h.tvPosition.setText(AppUtils.safe(item.position));

        // 주장 / 부주장 뱃지
        if (h.tvBadge != null) {
            boolean isCap  = item.uid != null && item.uid.equals(captainUid);
            boolean isVice = item.uid != null && item.uid.equals(viceCaptainUid);

            if (isCap) {
                h.tvBadge.setText("주장");
                h.tvBadge.setTextColor(0xFF1565C0);  // ← 파란색으로 변경
                h.tvBadge.setBackgroundResource(R.drawable.bg_badge_captain);
                h.tvBadge.setVisibility(View.VISIBLE);
            } else if (isVice) {
                h.tvBadge.setText("부주장");
                h.tvBadge.setTextColor(0xFF1565C0);
                h.tvBadge.setBackgroundResource(R.drawable.bg_badge_vice_captain);
                h.tvBadge.setVisibility(View.VISIBLE);
            } else {
                h.tvBadge.setVisibility(View.GONE);
            }
        }

        // 프로필 사진
        if (h.imgProfile != null) {
            if (!AppUtils.isEmpty(item.photoUrl)) {
                Glide.with(h.imgProfile.getContext())
                        .load(item.photoUrl)
                        .placeholder(R.drawable.ic_person_placeholder)
                        .into(h.imgProfile);
            } else {
                h.imgProfile.setImageResource(R.drawable.ic_person_placeholder);
            }
        }

        // 클릭 (주장만 옵션 다이얼로그 열기)
        if (listener != null && currentUid.equals(captainUid)) {
            h.itemView.setOnClickListener(v ->
                    listener.onClick(item.nickname, item.uid));
        }
    }

    // ── ViewHolder ───────────────────────────────────────────────────────────────

    static class HeaderVH extends RecyclerView.ViewHolder {
        View     positionBar;
        TextView tvHeader;
        HeaderVH(View v) {
            super(v);
            positionBar = v.findViewById(R.id.positionBar);
            tvHeader    = v.findViewById(R.id.tvPositionHeader);
        }
    }

    static class PlayerVH extends RecyclerView.ViewHolder {
        ShapeableImageView imgProfile;
        TextView           tvName, tvBadge, tvPosition;
        PlayerVH(View v) {
            super(v);
            imgProfile = v.findViewById(R.id.imgMember);
            tvName     = v.findViewById(R.id.tvMemberName);
            tvBadge    = v.findViewById(R.id.tvMemberBadge);
            tvPosition = v.findViewById(R.id.tvMemberPosition);
        }
    }

    // ── 데이터 모델 ───────────────────────────────────────────────────────────────

    public static class MemberItem {
        public int    type     = TYPE_PLAYER;
        public String uid      = "";
        public String nickname = "";
        public String photoUrl = "";
        public String position = "";
        public String header   = "";  // type == TYPE_HEADER 일 때만 사용
    }
}