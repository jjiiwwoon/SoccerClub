package com.jjw.soccerclub.ui.recruit;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.gms.tasks.Tasks;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.common.StateLayout;
import com.jjw.soccerclub.ui.common.BaseActivity;
import com.jjw.soccerclub.ui.team.TeamDetailActivity;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.jjw.soccerclub.util.GlideHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecruitDetailActivity extends BaseActivity {

    private static final String TAG = "RecruitDetailActivity";

    private static final int APPLY_ALLOWED          = 0;
    private static final int APPLY_ALREADY          = 1;
    private static final int BLOCK_SELF_AUTHOR      = 2;
    private static final int BLOCK_MY_TEAM          = 3;
    private static final int BLOCK_REGULAR_HAS_TEAM = 4;
    private static final int APPLY_REAPPLY          = 5;

    private int applyState = APPLY_ALLOWED;
    private int eligibilityVersion = 0;
    private boolean contentShown = false;

    private StateLayout state;
    private ImageView imgTeamLogo;
    private TextView tvTeamName, tvSkillRange, tvDate, tvTime, tvStadium;
    private TextView tvTimeLabel, tvStadiumLabel;
    private TextView tvRecruitType, tvRecruitSkill, tvIntro;
    private ChipGroup chipGroupPositions;
    private Button btnApply;

    // ✅ 작성자 전용 버튼
    private LinearLayout layoutWriterActions;
    private Button btnCloseRecruit, btnDeleteRecruit;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String recruitId     = "";
    private String currentUid    = "";
    private String myTeamId      = "";
    private String myTeamName    = "";
    private String myTeamLogo    = "";
    private String myNickname    = "";
    private int    mySkill       = -1;
    private String recruitTypeRaw = "";
    private String postTeamId    = "";
    private String postAuthorUid = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recruit_detail);

        state              = findViewById(R.id.stateLayout);
        imgTeamLogo        = findViewById(R.id.imgTeamLogo);
        tvTeamName         = findViewById(R.id.tvTeamName);
        tvSkillRange       = findViewById(R.id.tvSkillRange);
        tvDate             = findViewById(R.id.tvDate);
        tvTime             = findViewById(R.id.tvTime);
        tvStadium          = findViewById(R.id.tvStadium);
        tvTimeLabel        = findViewById(R.id.tvTimeLabel);
        tvStadiumLabel     = findViewById(R.id.tvStadiumLabel);
        tvRecruitType      = findViewById(R.id.tvRecruitType);
        tvRecruitSkill     = findViewById(R.id.tvRecruitSkill);
        tvIntro            = findViewById(R.id.tvIntro);
        chipGroupPositions = findViewById(R.id.chipGroupPositions);
        btnApply           = findViewById(R.id.btnApply);

        // ✅ 작성자 전용 버튼 바인딩
        layoutWriterActions = findViewById(R.id.layoutWriterActions);
        btnCloseRecruit     = findViewById(R.id.btnCloseRecruit);
        btnDeleteRecruit    = findViewById(R.id.btnDeleteRecruit);

        if (state != null) state.showLoading();

        View teamInfoBox = findViewById(R.id.teamInfoBox);
        if (teamInfoBox != null) teamInfoBox.setOnClickListener(v -> openTeamDetail());

        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        btnApply.setText("신청하기");
        btnApply.setEnabled(false);

        btnApply.setOnClickListener(v -> {
            switch (applyState) {
                case APPLY_ALLOWED:
                    showApplyConfirmDialog(false, this::applyOrReapply); break;
                case APPLY_REAPPLY:
                    showApplyConfirmDialog(true, this::applyOrReapply); break;
                case APPLY_ALREADY:
                    CustomToast.info(this, "이미 신청한 글입니다."); break;
                case BLOCK_SELF_AUTHOR:
                    CustomToast.warning(this, "본인이 올린 글에는 신청할 수 없어요."); break;
                case BLOCK_MY_TEAM:
                    CustomToast.warning(this, "내 팀이 올린 글에는 신청할 수 없어요."); break;
                case BLOCK_REGULAR_HAS_TEAM:
                    CustomToast.info(this, "팀이 없는 사용자만 신청할 수 있어요."); break;
            }
        });

        recruitId = getIntent().getStringExtra("recruitId");
        if (AppUtils.isEmpty(recruitId)) {
            CustomToast.warning(this, "잘못된 접근입니다.");
            if (state != null) { state.setEmptyMessage("잘못된 접근입니다."); state.showEmpty(); }
            return;
        }

        loadRecruitAndMyInfo();
    }

    private void openTeamDetail() {
        if (AppUtils.isEmpty(postTeamId)) {
            CustomToast.info(this, "팀 정보를 불러오는 중입니다.");
            return;
        }
        Intent intent = new Intent(this, TeamDetailActivity.class);
        intent.putExtra("teamId", postTeamId);
        startActivity(intent);
    }

    private void loadRecruitAndMyInfo() {
        Tasks.whenAllSuccess(
                db.collection("recruitPosts").document(recruitId).get(),
                db.collection("profiles").document(currentUid).get()
        ).addOnSuccessListener(list -> {
            DocumentSnapshot dsRecruit = (DocumentSnapshot) list.get(0);
            DocumentSnapshot dsProfile = (DocumentSnapshot) list.get(1);

            if (dsRecruit != null && dsRecruit.exists()) {
                bindRecruitDoc(dsRecruit);
            } else {
                db.collection("recruits").document(recruitId).get()
                        .addOnSuccessListener(old -> {
                            if (old != null && old.exists()) bindRecruitDoc(old);
                            else {
                                CustomToast.info(this, "삭제된 글입니다.");
                                if (state != null) { state.setEmptyMessage("삭제된 글입니다."); state.showEmpty(); }
                            }
                        });
                return;
            }

            if (dsProfile != null && dsProfile.exists()) {
                myTeamId   = AppUtils.safe(dsProfile.getString("myTeam"));
                myTeamName = AppUtils.safe(dsProfile.getString("teamName"));
                myTeamLogo = AppUtils.safe(dsProfile.getString("teamLogoUrl"));
                myNickname = AppUtils.safe(dsProfile.getString("nickname"));
                Long sk = dsProfile.getLong("skill");
                mySkill = sk != null ? sk.intValue() : -1;
            }

            checkEligibility();
        }).addOnFailureListener(e -> {
            CustomToast.error(this, "불러오기 실패: " + e.getMessage());
            if (state != null) { state.setEmptyMessage("불러오기 실패"); state.showEmpty(); }
        });
    }

    private void bindRecruitDoc(DocumentSnapshot ds) {
        postTeamId    = AppUtils.safe(ds.getString("teamId"));
        postAuthorUid = AppUtils.firstNonEmpty(
                ds.getString("authorUid"), ds.getString("writerUid"), ds.getString("uid"));
        recruitTypeRaw = AppUtils.safe(ds.getString("recruitType"));

        String teamName    = ds.getString("teamName");
        String logoUrl     = AppUtils.firstNonEmpty(ds.getString("teamLogoUrl"), ds.getString("logoUrl"));
        String date        = ds.getString("date");
        String time        = ds.getString("time");
        String stadiumName = AppUtils.firstNonEmpty(ds.getString("stadiumName"), ds.getString("stadium"));
        String address     = AppUtils.firstNonEmpty(ds.getString("address"), ds.getString("stadiumAddress"));
        String intro       = ds.getString("details");
        Long skillMinL     = ds.getLong("skillMin");
        Long skillMaxL     = ds.getLong("skillMax");
        List<String> positions = (List<String>) ds.get("positions");

        tvTeamName.setText(AppUtils.safe(teamName));
        GlideHelper.loadTeamLogo(this, logoUrl, imgTeamLogo);

        String norm = AppUtils.normalizeRecruitType(recruitTypeRaw);
        boolean isMercenary = "mercenary".equals(norm);

        if (tvRecruitType  != null) {
            tvRecruitType.setText(isMercenary ? "용병" : "회원");
            tvRecruitType.setBackgroundResource(isMercenary
                    ? R.drawable.bg_badge_recruit_merc
                    : R.drawable.bg_badge_recruit);
            tvRecruitType.setTextColor(isMercenary
                    ? 0xFFE65100    // 주황
                    : 0xFF1565C0);  // 파랑
        }
        if (tvTimeLabel    != null) tvTimeLabel.setText(isMercenary ? "시합 시간" : "활동 시간");
        if (tvStadiumLabel != null) tvStadiumLabel.setText(isMercenary ? "시합 장소" : "주 활동 장소");

        tvDate.setText(DateUtils.appendWeekday(date));
        tvTime.setText(AppUtils.nz(time, "-"));
        tvStadium.setText(AppUtils.firstNonEmpty(stadiumName, address, "-"));

        String skillLeft  = skillMinL != null ? String.valueOf(skillMinL) : "-";
        String skillRight = skillMaxL != null ? String.valueOf(skillMaxL) : "-";
        String skillRange = skillLeft + " ~ " + skillRight;
        if (tvSkillRange  != null) tvSkillRange.setText(skillRange);
        if (tvRecruitSkill != null) tvRecruitSkill.setText(skillRange);

        tvIntro.setText(AppUtils.nz(intro, "소개 없음"));

        chipGroupPositions.removeAllViews();
        if (positions != null) {
            for (String pos : positions) {
                if (AppUtils.isEmpty(pos)) continue;
                Chip chip = new Chip(this);
                chip.setText(pos);
                chip.setClickable(false);
                chipGroupPositions.addView(chip);
            }
        }

        db.collection("teams").document(postTeamId).get()
                .addOnSuccessListener(teamDoc -> {
                    if (teamDoc.exists()) {
                        Long skillAvg = teamDoc.getLong("skillAverage");
                        if (skillAvg != null && tvSkillRange != null) {
                            tvSkillRange.setText("평균 실력 " + skillAvg);
                        }
                    }
                });

        // ✅ 작성자 전용 마감/삭제 버튼 설정
        setupWriterActions(ds);
    }

    // ✅ 작성자 본인에게만 마감/삭제 버튼 표시
    private void setupWriterActions(DocumentSnapshot ds) {
        String status = AppUtils.safe(ds.getString("status"));
        boolean isAuthor = !AppUtils.isEmpty(postAuthorUid) && postAuthorUid.equals(currentUid);
        boolean isOpen   = !"closed".equalsIgnoreCase(status) && !"deleted".equalsIgnoreCase(status);

        if (!isAuthor || !isOpen) return;

        if (layoutWriterActions != null)
            layoutWriterActions.setVisibility(View.VISIBLE);

        // 마감 버튼 — 신청을 더 받지 않지만 글은 유지
        if (btnCloseRecruit != null) {
            btnCloseRecruit.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("모집 마감")
                            .setMessage("모집을 마감하시겠어요?\n마감 후에는 새 신청을 받지 않습니다.")
                            .setPositiveButton("마감", (d, i) ->
                                    db.collection("recruitPosts").document(recruitId)
                                            .update("status", "closed")
                                            .addOnSuccessListener(v2 -> {
                                                CustomToast.success(this, "모집이 마감됐어요.");
                                                if (btnApply != null) {
                                                    btnApply.setText("모집 마감");
                                                    btnApply.setEnabled(false);
                                                }
                                                if (layoutWriterActions != null)
                                                    layoutWriterActions.setVisibility(View.GONE);
                                            })
                                            .addOnFailureListener(e ->
                                                    CustomToast.error(this, "마감 처리에 실패했어요.")))
                            .setNegativeButton("취소", null)
                            .show());
        }

        // 삭제 버튼 — status를 deleted로 변경 후 화면 닫기
        if (btnDeleteRecruit != null) {
            btnDeleteRecruit.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("글 삭제")
                            .setMessage("정말 이 모집 글을 삭제하시겠어요?\n삭제된 글은 복구되지 않습니다.")
                            .setPositiveButton("삭제", (d, i) ->
                                    db.collection("recruitPosts").document(recruitId)
                                            .update("status", "deleted")
                                            .addOnSuccessListener(v2 -> {
                                                CustomToast.success(this, "글이 삭제됐어요.");
                                                finish();
                                            })
                                            .addOnFailureListener(e ->
                                                    CustomToast.error(this, "삭제에 실패했어요.")))
                            .setNegativeButton("취소", null)
                            .show());
        }
    }

    private void checkEligibility() {
        int ver = ++eligibilityVersion;

        if (!AppUtils.isEmpty(postAuthorUid) && postAuthorUid.equals(currentUid)) {
            updateEligibilityAndShow(BLOCK_SELF_AUTHOR, "신청 불가"); return;
        }
        if (!AppUtils.isEmpty(myTeamId) && myTeamId.equals(postTeamId)) {
            updateEligibilityAndShow(BLOCK_MY_TEAM, "신청 불가"); return;
        }

        db.collection("recruitPosts").document(recruitId)
                .collection("applicants").document(currentUid).get()
                .addOnSuccessListener(ap -> {
                    if (ver != eligibilityVersion) return;
                    if (ap.exists()) {
                        String st = AppUtils.safe(ap.getString("status")).toLowerCase();
                        if (st.startsWith("rej")) updateEligibilityAndShow(APPLY_REAPPLY, "다시 신청");
                        else updateEligibilityAndShow(APPLY_ALREADY, "신청 완료");
                    } else {
                        String norm = AppUtils.normalizeRecruitType(recruitTypeRaw);
                        if ("regular".equals(norm) && !AppUtils.isEmpty(myTeamId)) {
                            updateEligibilityAndShow(BLOCK_REGULAR_HAS_TEAM, "신청 불가");
                        } else {
                            updateEligibilityAndShow(APPLY_ALLOWED, "신청하기");
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (ver != eligibilityVersion) return;
                    String norm = AppUtils.normalizeRecruitType(recruitTypeRaw);
                    if ("regular".equals(norm) && !AppUtils.isEmpty(myTeamId)) {
                        updateEligibilityAndShow(BLOCK_REGULAR_HAS_TEAM, "신청 불가");
                    } else {
                        updateEligibilityAndShow(APPLY_ALLOWED, "신청하기");
                    }
                });
    }

    private void updateEligibilityAndShow(int stateCode, String label) {
        this.applyState = stateCode;
        btnApply.setText(label);
        // 신청 불가 → 빨간 배경, 그 외 → 파란 배경
        if ("신청 불가".equals(label)) {
            btnApply.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFEF4444));
        } else {
            btnApply.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            getResources().getColor(R.color.primary)));
        }
        finishLoadingAndShow();
    }

    private void finishLoadingAndShow() {
        if (contentShown) return;
        contentShown = true;
        if (state != null) state.showContent();
        btnApply.setEnabled(true);
    }

    private void showApplyConfirmDialog(boolean isReapply, Runnable onConfirm) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView msg = new TextView(this);
        msg.setText(isReapply ? "다시 신청하시겠습니까?" : "이 글에 신청하시겠습니까?");
        msg.setTextSize(16f);
        root.addView(msg);

        if (isReapply) {
            TextView warn = new TextView(this);
            warn.setText("상대방이 이미 거절한 글입니다. 재신청하겠습니까?");
            warn.setTextSize(13f);
            warn.setTextColor(0xFFF44336);
            warn.setPadding(0, dp(8), 0, 0);
            root.addView(warn);
        }

        new AlertDialog.Builder(this)
                .setTitle(isReapply ? "재신청 확인" : "신청 확인")
                .setView(root)
                .setNegativeButton("취소", null)
                .setPositiveButton("신청", (d, w) -> onConfirm.run())
                .show();
    }

    private void applyOrReapply() {
        if (AppUtils.isEmpty(recruitId)) { CustomToast.warning(this, "잘못된 글입니다."); return; }
        btnApply.setEnabled(false);

        db.runTransaction(tr -> {
            DocumentReference postRef = db.collection("recruitPosts").document(recruitId);
            DocumentSnapshot postSnap = tr.get(postRef);
            if (!postSnap.exists()) {
                postRef = db.collection("recruits").document(recruitId);
                postSnap = tr.get(postRef);
                if (!postSnap.exists()) throw new IllegalStateException("삭제된 글입니다.");
            }

            DocumentSnapshot profSnap = tr.get(db.collection("profiles").document(currentUid));
            String myTeamIdTx   = AppUtils.safe(profSnap.getString("myTeam"));
            String postTeamIdTx = AppUtils.safe(postSnap.getString("teamId"));
            String typeTx       = AppUtils.normalizeRecruitType(postSnap.getString("recruitType"));
            String authorTx     = AppUtils.firstNonEmpty(
                    postSnap.getString("authorUid"),
                    postSnap.getString("writerUid"),
                    postSnap.getString("uid"));

            if (!AppUtils.isEmpty(authorTx) && authorTx.equals(currentUid))
                throw new IllegalStateException("본인이 올린 글에는 신청할 수 없습니다.");
            if (!AppUtils.isEmpty(myTeamIdTx) && myTeamIdTx.equals(postTeamIdTx))
                throw new IllegalStateException("내 팀이 올린 글에는 신청할 수 없습니다.");
            if ("regular".equals(typeTx) && !AppUtils.isEmpty(myTeamIdTx))
                throw new IllegalStateException("현재 소속된 팀이 있습니다.");

            DocumentReference apRef  = postRef.collection("applicants").document(currentUid);
            DocumentSnapshot  apSnap = tr.get(apRef);

            long now = System.currentTimeMillis();
            if (apSnap.exists()) {
                String st = AppUtils.safe(apSnap.getString("status")).toLowerCase();
                if (!st.startsWith("rej")) throw new IllegalStateException("이미 신청한 글입니다.");
            }

            Map<String, Object> apData = new HashMap<>();
            apData.put("status",          "pending");
            apData.put("timestamp",       now);
            apData.put("teamId",          myTeamId);
            apData.put("teamName",        myTeamName);
            apData.put("nickname",        myNickname);
            apData.put("skill",           mySkill);
            apData.put("applicantUserId", currentUid);
            tr.set(apRef, apData, SetOptions.merge());

            // ✅ profiles/{uid}/applications 에도 저장 → 인덱스 없이 내 신청 조회 가능
            DocumentReference myAppRef = db.collection("profiles")
                    .document(currentUid)
                    .collection("applications")
                    .document(recruitId);
            Map<String, Object> myApp = new HashMap<>();
            myApp.put("postId",    recruitId);
            myApp.put("postType",  "recruit");
            myApp.put("status",    "pending");
            myApp.put("timestamp", now);
            tr.set(myAppRef, myApp, SetOptions.merge());

            return null;

        }).addOnSuccessListener(v -> {
            CustomToast.success(this, "신청 완료!");
            applyState = APPLY_ALREADY;
            btnApply.setText("신청 완료");
            btnApply.setEnabled(true);
        }).addOnFailureListener(e -> {
            String msg = AppUtils.safe(e.getMessage());
            CustomToast.error(this, msg.isEmpty() ? "신청 실패" : msg);
            applyState = APPLY_ALLOWED;
            btnApply.setText("신청하기");
            btnApply.setEnabled(true);
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}