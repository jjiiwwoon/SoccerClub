package com.example.soccerclub.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

    private static void bindCommonPost(@NonNull View root, @NonNull Item it,
                                       OnItemClickListener cb) {
        TextView tvTeamName  = root.findViewById(R.id.textTeamName);
        TextView tvDate      = root.findViewById(R.id.textDate);
        TextView tvTime      = root.findViewById(R.id.textTime);
        TextView tvStadium   = root.findViewById(R.id.textStadium);
        ImageView ivLogo     = root.findViewById(R.id.imageTeamLogo);
        TextView tvTimestamp = root.findViewById(R.id.textTimestamp);

        if (tvTeamName != null)  tvTeamName.setText(AppUtils.safe(it.teamName));
        if (tvDate != null)      tvDate.setText(DateUtils.appendWeekday(it.date));
        if (tvTime != null)      tvTime.setText(AppUtils.safe(it.time));
        if (tvStadium != null)   tvStadium.setText("주소 | " + AppUtils.safe(it.stadium));
        if (ivLogo != null)      GlideHelper.loadTeamLogo(ivLogo.getContext(), it.teamLogoUrl, ivLogo);

        if (tvTimestamp != null) {
            long ts = it.timestamp > 0 ? it.timestamp
                    : it.matchTs > 0 ? it.matchTs
                    : DateUtils.computeStartMillis(it.date, it.time);
            tvTimestamp.setText(DateUtils.formatRelativeTime(ts));
        }

        root.setOnClickListener(v -> { if (cb != null) cb.onPostClicked(it); });
    }

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
            if (txtNew != null)   txtNew.setVisibility(it.hasSessionNew ? View.VISIBLE : View.GONE);

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

                TextView tvNew     = row.findViewById(R.id.textApplicantRowNew);
                TextView tvName    = row.findViewById(R.id.textApplicantName);
                TextView tvSkill   = row.findViewById(R.id.textApplicantSkill);
                ImageView ivLogo   = row.findViewById(R.id.imageApplicantLogo);
                Button btnAccept   = row.findViewById(R.id.btnAccept);
                Button btnReject   = row.findViewById(R.id.btnReject);

                boolean isNew = sessionNewKeys.contains(rowKey);
                if (tvNew != null) tvNew.setVisibility(isNew ? View.VISIBLE : View.GONE);

                String displayName = AppUtils.firstNonEmpty(a.nickname, a.teamName, "(알 수 없음)");
                if (tvName  != null) tvName.setText(displayName);
                if (tvSkill != null) tvSkill.setText("실력: " + (a.skill < 0 ? "-" : a.skill));
                if (ivLogo  != null) GlideHelper.loadTeamLogo(ivLogo.getContext(), a.logoUrl, ivLogo);

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

                if (ApplicationsAdapter.this.flashRows.remove(rowKey)) flash(row);

                container.addView(row);
            }
        }
    }

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
}