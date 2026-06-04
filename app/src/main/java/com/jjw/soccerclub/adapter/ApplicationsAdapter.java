package com.jjw.soccerclub.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.jjw.soccerclub.util.GlideHelper;

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
    private static final int VT_MINE_RECRUIT     = 11;
    private static final int VT_APPLIED_MATCH   = 12;
    private static final int VT_APPLIED_RECRUIT = 13;

    private Set<String> sessionNewApplicantKeys = Collections.emptySet();
    private final Set<String> flashRows = new HashSet<>();

    // ── ★ 선택 모드 상태 ──────────────────────────────────────────────────────────
    private boolean selectionMode = false;
    private final Set<String> selectedPostIds = new HashSet<>();
    private OnSelectionListener selectionListener;

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

    // ── 어댑터 상태 ───────────────────────────────────────────────────────────────

    private final List<Item> items = new ArrayList<>();
    private int listMode = TYPE_MINE;

    // ── 콜백 인터페이스 ───────────────────────────────────────────────────────────

    public interface OnItemClickListener {
        default void onPostClicked(@NonNull Item item) {}
        default void onApplicantAccept(@NonNull Item post, @NonNull Applicant applicant) {}
        default void onApplicantReject(@NonNull Item post, @NonNull Applicant applicant) {}
        default void onApplicantChat(@NonNull Item post, @NonNull Applicant applicant) {}
    }

    // ★ 선택 모드 콜백
    public interface OnSelectionListener {
        void onSelectionModeChanged(boolean active);
        void onSelectionCountChanged(int count);
    }

    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener cb) { this.listener = cb; }
    public void setOnSelectionListener(OnSelectionListener cb) { this.selectionListener = cb; }

    // ── 데이터 조작 ───────────────────────────────────────────────────────────────

    public void setItems(@NonNull List<Item> newItems, int mode) {
        items.clear();
        items.addAll(newItems);
        listMode = mode;
        if (selectionMode) exitSelectionMode();
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

    // ── ★ 선택 모드 메서드 ────────────────────────────────────────────────────────

    public boolean isSelectionMode() { return selectionMode; }

    public void enterSelectionMode(int initialPosition) {
        if (selectionMode) return;
        selectionMode = true;
        selectedPostIds.clear();
        if (initialPosition >= 0 && initialPosition < items.size()) {
            selectedPostIds.add(items.get(initialPosition).postId);
        }
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionModeChanged(true);
            selectionListener.onSelectionCountChanged(selectedPostIds.size());
        }
    }

    public void exitSelectionMode() {
        if (!selectionMode) return;
        selectionMode = false;
        selectedPostIds.clear();
        notifyDataSetChanged();
        if (selectionListener != null) {
            selectionListener.onSelectionModeChanged(false);
            selectionListener.onSelectionCountChanged(0);
        }
    }

    public void toggleSelection(int position) {
        if (position < 0 || position >= items.size()) return;
        String id = items.get(position).postId;
        if (id == null) return;
        if (selectedPostIds.contains(id)) selectedPostIds.remove(id);
        else selectedPostIds.add(id);
        notifyItemChanged(position);
        if (selectionListener != null)
            selectionListener.onSelectionCountChanged(selectedPostIds.size());
    }

    public List<Item> getSelectedItems() {
        List<Item> result = new ArrayList<>();
        for (Item it : items) {
            if (it.postId != null && selectedPostIds.contains(it.postId))
                result.add(it);
        }
        return result;
    }

    public int getSelectedCount() { return selectedPostIds.size(); }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────────────

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
            ((MineVH) h).bind(it, listener, pos);
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
        final CheckBox cbSelect;          // ★ XML에서 바인딩
        TextView txtCount, txtNew;
        boolean expanded = false;
        private Set<String> sessionNewKeys = Collections.emptySet();

        public MineVH(@NonNull View v) {
            super(v);
            includePost         = v.findViewById(R.id.includePost);
            toggleHeader        = v.findViewById(R.id.includeToggleHeader);
            applicantsContainer = v.findViewById(R.id.applicantsContainer);
            cbSelect            = v.findViewById(R.id.cbSelect);   // ★ XML에서 가져옴
            iconArrow = toggleHeader != null ? toggleHeader.findViewById(R.id.iconToggleArrow) : null;
            if (toggleHeader != null) {
                txtCount = toggleHeader.findViewById(R.id.textApplicantCount);
                txtNew   = toggleHeader.findViewById(R.id.textApplicantNew);
            }
        }

        void setSessionNewKeys(Set<String> keys) {
            this.sessionNewKeys = keys == null ? Collections.emptySet() : keys;
        }

        void bind(@NonNull Item it, OnItemClickListener cb, int position) {
            if (includePost != null) bindCommonPost(includePost, it, cb);

            int total = it.applicants == null ? 0 : it.applicants.size();
            if (txtCount != null) txtCount.setText(String.valueOf(total));
            if (txtNew   != null) txtNew.setVisibility(it.hasSessionNew ? View.VISIBLE : View.GONE);

            // ★ 체크박스 & 선택 모드 처리
            boolean selected = it.postId != null && selectedPostIds.contains(it.postId);
            if (cbSelect != null) {
                cbSelect.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
                cbSelect.setChecked(selected);
            }

            if (selectionMode) {
                // 선택 모드: 아이템 전체 클릭 → 선택 토글
                itemView.setOnClickListener(v -> toggleSelection(position));
                itemView.setOnLongClickListener(null);
                if (includePost != null) includePost.setOnClickListener(v -> toggleSelection(position));
                // 신청자 토글 비활성 + 접기
                if (toggleHeader != null) toggleHeader.setOnClickListener(v -> toggleSelection(position));
                if (applicantsContainer != null) {
                    applicantsContainer.setVisibility(View.GONE);
                    applicantsContainer.removeAllViews();
                    expanded = false;
                }
            }  else {
            // 일반 모드: 롱클릭 → 선택 모드 진입
            itemView.setOnLongClickListener(v -> {
                enterSelectionMode(position);
                return true;
            });
            itemView.setOnClickListener(null);

            // ★ 포스트 영역에도 롱클릭 추가 (사용자가 시합 내용 부분을 눌러도 선택 모드 진입)
            if (includePost != null) {
                includePost.setOnClickListener(v -> { if (cb != null) cb.onPostClicked(it); });
                includePost.setOnLongClickListener(v -> {
                    enterSelectionMode(position);
                    return true;
                });
            }

                // 신청자 토글 원래 동작
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
                if (tvSkill != null) tvSkill.setText("실력: " + (a.skill < 0 ? "-" : String.valueOf(a.skill)));
                if (ivLogo  != null) GlideHelper.loadTeamLogo(ivLogo.getContext(), a.logoUrl, ivLogo);

                String st = AppUtils.safe(a.status).toLowerCase(Locale.ROOT);
                boolean pending = !"accepted".equals(st) && !"rejected".equals(st);
                if (btnAccept != null) btnAccept.setVisibility(pending ? View.VISIBLE : View.GONE);
                if (btnReject != null) btnReject.setVisibility(pending ? View.VISIBLE : View.GONE);

                if (btnAccept != null) {
                    btnAccept.setOnClickListener(v -> {
                        v.setEnabled(false);
                        flash(v);
                        if (listener != null) listener.onApplicantAccept(it, a);
                    });
                }
                if (btnReject != null) {
                    btnReject.setOnClickListener(v -> {
                        v.setEnabled(false);
                        flash(v);
                        if (listener != null) listener.onApplicantReject(it, a);
                    });
                }

                row.setOnClickListener(v -> showApplicantProfileDialog(v.getContext(), a));

                if (flashRows.remove(rowKey)) flash(row);

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

    // ── 프로필 팝업 ───────────────────────────────────────────────────────────────

    private void showApplicantProfileDialog(Context ctx, Applicant a) {
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(ctx, 20);
        layout.setPadding(pad, pad, pad, pad / 2);

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

        addText(ctx, layout, AppUtils.firstNonEmpty(a.nickname, "(알 수 없음)"), 17f, true, 0xFF111111);
        addText(ctx, layout, "실력  " + (a.skill < 0 ? "-" : a.skill), 14f, false, 0xFF555555);
        if (!AppUtils.isEmpty(a.position))
            addText(ctx, layout, "포지션  " + a.position, 14f, false, 0xFF555555);

        new AlertDialog.Builder(ctx)
                .setView(layout)
                .setPositiveButton("닫기", null)
                .show();
    }

    private static void addText(Context ctx, LinearLayout parent,
                                String text, float sp, boolean bold, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(ctx, 4);
        parent.addView(tv, lp);
    }

    private static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}