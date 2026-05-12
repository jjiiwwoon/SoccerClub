package com.example.soccerclub.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.util.AppUtils;
import com.example.soccerclub.util.DateUtils;
import com.example.soccerclub.util.GlideHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ApplicationsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_MINE    = 1;
    public static final int TYPE_APPLIED = 2;

    private static final int VT_MINE_MATCH      = 10;
    private static final int VT_MINE_RECRUIT    = 11;
    private static final int VT_APPLIED_MATCH   = 12;
    private static final int VT_APPLIED_RECRUIT = 13;

    private Set<String> sessionNewApplicantKeys = Collections.emptySet();
    private final Set<String> flashRows = new HashSet<>();

    // ── 데이터 모델 ───────────────────────────────────────────────────────────────

    public static class Item {
        public String postId;
        public String postType;
        public String teamLogoUrl, teamName, date, time, stadium;
        public int    skill = -1;
        public long   timestamp = 0L;
        public long   matchTs   = 0L;
        public String status;
        public List<Applicant> applicants = new ArrayList<>();
        public String recruitType;
        public Integer skillMin;
        public Integer skillMax;
        public List<String> positions;
        public boolean hasSessionNew = false;
    }

    public static class Applicant {
        public String applicantDocId;
        public String teamId;
        public String logoUrl;
        public String teamName;
        public String nickname;
        public int    skill = -1;
        public String applicantUserId;
        public String status;
        public String profileImageUrl;
        public String position;
        public long   timestamp = 0L;
    }

    private final List<Item> items = new ArrayList<>();
    private int listMode = TYPE_MINE;

    public interface OnItemClickListener {
        default void onPostClicked(@NonNull Item item) {}
        default void onApplicantAccept(@NonNull Item post, @NonNull Applicant applicant) {}
        default void onApplicantReject(@NonNull Item post, @NonNull Applicant applicant) {}
        default void onApplicantChat(@NonNull Item post, @NonNull Applicant applicant) {}
    }
    private OnItemClickListener listener;
    public void setOnItemClickListener(OnItemClickListener cb) { this.listener = cb; }

    public void setItems(@NonNull List<Item> newItems, int mode) {
        items.clear();
        items.addAll(newItems);
        listMode = mode;
        notifyDataSetChanged();
    }

    public void setSessionNewApplicantKeys(@NonNull Set<String> keys) {
        this.sessionNewApplicantKeys = new HashSet<>(keys);
        notifyDataSetChanged();
    }

    public void updateApplicantStatus(@NonNull String postId,
                                      @NonNull String applicantDocId,
                                      @NonNull String newStatus) {
        for (Item it : items) {
            if (!postId.equals(it.postId) || it.applicants == null) continue;
            for (Applicant a : it.applicants) {
                if (applicantDocId.equals(a.applicantDocId)) {
                    a.status = newStatus.toLowerCase(Locale.ROOT);
                    markFlash(it.postType, it.postId, a.applicantDocId);
                    notifyDataSetChanged();
                    return;
                }
            }
        }
    }

    private void markFlash(String postType, String postId, String applicantDocId) {
        flashRows.add(AppUtils.safe(postType).toLowerCase(Locale.ROOT)
                + ":" + AppUtils.safe(postId)
                + ":" + AppUtils.safe(applicantDocId));
    }

    private static void flash(View v) {
        if (v == null) return;
        v.animate().cancel();
        v.setAlpha(0.35f);
        v.animate().alpha(1f).setDuration(140).start();
    }

    // ── RecyclerView ──────────────────────────────────────────────────────────────

    @Override public int getItemCount() { return items.size(); }

    @Override
    public int getItemViewType(int position) {
        Item it = items.get(position);
        boolean isRecruit = "recruit".equalsIgnoreCase(it.postType);
        if (listMode == TYPE_MINE) return isRecruit ? VT_MINE_RECRUIT : VT_MINE_MATCH;
        else                       return isRecruit ? VT_APPLIED_RECRUIT : VT_APPLIED_MATCH;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        switch (vt) {
            case VT_MINE_MATCH:
                return new MineVH(inf.inflate(R.layout.applications_item_mine, parent, false));
            case VT_MINE_RECRUIT:
                return new MineVH(inf.inflate(R.layout.applications_item_mine_recruit, parent, false));
            case VT_APPLIED_MATCH:
                return new AppliedVH(inf.inflate(R.layout.applications_item_applied, parent, false));
            default:
                return new AppliedVH(inf.inflate(R.layout.applications_item_applied_recruit, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
        Item it = items.get(pos);
        if (h instanceof MineVH) {
            ((MineVH) h).setSessionNewKeys(sessionNewApplicantKeys);
            ((MineVH) h).bind(it, listener);
        } else if (h instanceof AppliedVH) {
            ((AppliedVH) h).bind(it, listener);
        }
    }

    // ── 공통 포스트 바인딩 ────────────────────────────────────────────────────────

    private static void bindCommonPost(@NonNull View root, @NonNull Item it,
                                       OnItemClickListener cb) {
        TextView tvTeamName  = root.findViewById(R.id.textTeamName);
        TextView tvDate      = root.findViewById(R.id.textDate);
        TextView tvTime      = root.findViewById(R.id.textTime);
        TextView tvStadium   = root.findViewById(R.id.textStadium);
        ImageView ivLogo     = root.findViewById(R.id.imageTeamLogo);
        TextView tvTimestamp = root.findViewById(R.id.textTimestamp);

        if (tvTeamName != null)  tvTeamName.setText(AppUtils.safe(it.teamName));
        if (tvDate     != null)  tvDate.setText(DateUtils.appendWeekday(it.date));
        if (tvTime     != null)  tvTime.setText(AppUtils.safe(it.time));
        if (tvStadium  != null)  tvStadium.setText("주소 | " + AppUtils.safe(it.stadium));
        if (ivLogo     != null)  GlideHelper.loadTeamLogo(ivLogo.getContext(), it.teamLogoUrl, ivLogo);

        if (tvTimestamp != null) {
            long ts = it.timestamp > 0 ? it.timestamp
                    : it.matchTs > 0 ? it.matchTs
                    : DateUtils.computeStartMillis(it.date, it.time);
            tvTimestamp.setText(DateUtils.formatRelativeTime(ts));
        }

        root.setOnClickListener(v -> { if (cb != null) cb.onPostClicked(it); });
    }

    // ── MineVH (내 모집글 관리) ───────────────────────────────────────────────────

    public class MineVH extends RecyclerView.ViewHolder {
        final View includePost, toggleHeader;
        final LinearLayout applicantsContainer;
        final ImageView iconArrow;
        TextView txtCount, txtNew;
        boolean expanded = false;
        private Set<String> sessionNewKeys = Collections.emptySet();

        public MineVH(@NonNull View v) {
            super(v);
            includePost         = v.findViewById(R.id.includePost);
            toggleHeader        = v.findViewById(R.id.includeToggleHeader);
            applicantsContainer = v.findViewById(R.id.applicantsContainer);
            iconArrow = toggleHeader != null ? toggleHeader.findViewById(R.id.iconToggleArrow) : null;
            if (toggleHeader != null) {
                txtCount = toggleHeader.findViewById(R.id.textApplicantCount);
                txtNew   = toggleHeader.findViewById(R.id.textApplicantNew);
            }
        }

        void setSessionNewKeys(Set<String> keys) {
            this.sessionNewKeys = keys == null ? Collections.emptySet() : keys;
        }

        void bind(@NonNull Item it, OnItemClickListener cb) {
            if (includePost != null) bindCommonPost(includePost, it, cb);

            int total = it.applicants == null ? 0 : it.applicants.size();
            if (txtCount != null) txtCount.setText(String.valueOf(total));
            if (txtNew   != null) txtNew.setVisibility(it.hasSessionNew ? View.VISIBLE : View.GONE);

            if (toggleHeader != null) {
                toggleHeader.setOnClickListener(v -> {
                    expanded = !expanded;
                    if (iconArrow != null) iconArrow.setRotation(expanded ? 180f : 0f);
                    if (applicantsContainer != null) {
                        applicantsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
                        if (expanded) rebuildChildren(applicantsContainer, it, cb);
                        else          applicantsContainer.removeAllViews();
                    }
                });
            }

            if (applicantsContainer != null) {
                applicantsContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
                if (expanded) rebuildChildren(applicantsContainer, it, cb);
            }
        }

        private void rebuildChildren(LinearLayout container, Item it, OnItemClickListener cb) {
            container.removeAllViews();
            if (it.applicants == null) return;

            for (Applicant a : it.applicants) {
                String rowKey = AppUtils.safe(it.postType).toLowerCase(Locale.ROOT)
                        + ":" + AppUtils.safe(it.postId)
                        + ":" + AppUtils.safe(a.applicantDocId);

                View row = LayoutInflater.from(container.getContext())
                        .inflate(R.layout.item_applicant_row, container, false);

                TextView tvNew   = row.findViewById(R.id.textApplicantRowNew);
                TextView tvName  = row.findViewById(R.id.textApplicantName);
                TextView tvSkill = row.findViewById(R.id.textApplicantSkill);
                ImageView ivLogo = row.findViewById(R.id.imageApplicantLogo);
                Button btnAccept = row.findViewById(R.id.btnAccept);
                Button btnReject = row.findViewById(R.id.btnReject);

                boolean isNew = sessionNewKeys.contains(rowKey);
                if (tvNew != null) tvNew.setVisibility(isNew ? View.VISIBLE : View.GONE);

                String displayName = AppUtils.firstNonEmpty(a.nickname, a.teamName, "(알 수 없음)");
                if (tvName  != null) tvName.setText(displayName);
                if (tvSkill != null) tvSkill.setText("실력: " + (a.skill < 0 ? "-" : a.skill));
                if (ivLogo  != null) GlideHelper.loadTeamLogo(ivLogo.getContext(), a.logoUrl, ivLogo);

                // ✅ 신청자 이름 클릭 → 프로필 팝업
                if (tvName != null && !AppUtils.isEmpty(a.applicantUserId)) {
                    tvName.setOnClickListener(v -> showApplicantProfileDialog(container.getContext(), a));
                }

                // ✅ 이미 수락/거절된 경우 버튼 숨기기
                String apStatus = AppUtils.safe(a.status).toLowerCase(Locale.ROOT);
                boolean alreadyHandled = apStatus.equals("accepted") || apStatus.equals("rejected");

                if (btnAccept != null) {
                    if (alreadyHandled) {
                        btnAccept.setVisibility(View.GONE);
                    } else {
                        btnAccept.setVisibility(View.VISIBLE);
                        btnAccept.setOnClickListener(v -> {
                            btnAccept.setVisibility(View.GONE);
                            if (btnReject != null) btnReject.setVisibility(View.GONE);
                            a.status = "accepted"; // 로컬 상태 즉시 업데이트
                            flash(v);
                            if (listener != null) listener.onApplicantAccept(it, a);
                        });
                    }
                }
                if (btnReject != null) {
                    if (alreadyHandled) {
                        btnReject.setVisibility(View.GONE);
                    } else {
                        btnReject.setVisibility(View.VISIBLE);
                        btnReject.setOnClickListener(v -> {
                            if (btnAccept != null) btnAccept.setVisibility(View.GONE);
                            btnReject.setVisibility(View.GONE);
                            a.status = "rejected"; // 로컬 상태 즉시 업데이트
                            flash(v);
                            if (listener != null) listener.onApplicantReject(it, a);
                        });
                    }
                }

                if (ApplicationsAdapter.this.flashRows.remove(rowKey)) flash(row);

                container.addView(row);
            }
        }
    }

    // ── AppliedVH (내가 신청한 글) ────────────────────────────────────────────────

    public static class AppliedVH extends RecyclerView.ViewHolder {
        final View includePost;
        final TextView textStatus;

        public AppliedVH(@NonNull View v) {
            super(v);
            includePost = v.findViewById(R.id.includePost);
            textStatus  = v.findViewById(R.id.textApplicationStatus);
        }

        void bind(@NonNull Item it, OnItemClickListener cb) {
            if (includePost != null) bindCommonPost(includePost, it, cb);
            if (textStatus != null) {
                String st = AppUtils.safe(it.status).toLowerCase(Locale.ROOT);
                switch (st) {
                    case "accepted": textStatus.setText("수락됨"); break;
                    case "rejected": textStatus.setText("거절됨"); break;
                    case "pending":  textStatus.setText("대기중"); break;
                    default:         textStatus.setText(AppUtils.isEmpty(st) ? "대기중" : st);
                }
            }
        }
    }

    // ── ✅ 신청자 프로필 팝업 ─────────────────────────────────────────────────────

    private void showApplicantProfileDialog(Context ctx, Applicant a) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 20);
        layout.setPadding(pad, pad, pad, pad / 2);

        // 프로필 사진
        ImageView ivProfile = new ImageView(ctx);
        int size = dp(ctx, 72);
        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(size, size);
        imgParams.gravity = Gravity.CENTER_HORIZONTAL;
        imgParams.bottomMargin = dp(ctx, 12);
        ivProfile.setLayoutParams(imgParams);
        ivProfile.setScaleType(ImageView.ScaleType.CENTER_CROP);
        ivProfile.setClipToOutline(true);
        GlideHelper.loadProfile(ctx, a.profileImageUrl, ivProfile);
        layout.addView(ivProfile);

        // 닉네임
        addText(ctx, layout, AppUtils.firstNonEmpty(a.nickname, "(알 수 없음)"), 17f, true, 0xFF111111);

        // 실력
        addText(ctx, layout, "실력  " + (a.skill < 0 ? "-" : String.valueOf(a.skill)), 14f, false, 0xFF555555);

        // 포지션
        if (!AppUtils.isEmpty(a.position))
            addText(ctx, layout, "포지션  " + a.position, 14f, false, 0xFF555555);

        // 팀명
        if (!AppUtils.isEmpty(a.teamName))
            addText(ctx, layout, "팀  " + a.teamName, 14f, false, 0xFF555555);

        new AlertDialog.Builder(ctx)
                .setView(layout)
                .setPositiveButton("닫기", null)
                .show();
    }

    private void addText(Context ctx, LinearLayout parent,
                         String text, float sizeSp, boolean bold, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_HORIZONTAL);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        tv.setPadding(0, dp(ctx, 3), 0, dp(ctx, 3));
        parent.addView(tv);
    }

    private int dp(Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}