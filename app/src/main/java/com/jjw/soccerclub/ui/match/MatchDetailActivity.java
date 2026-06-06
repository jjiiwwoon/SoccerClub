package com.jjw.soccerclub.ui.match;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.common.StateLayout;
import com.jjw.soccerclub.ui.common.BaseActivity;
import com.jjw.soccerclub.ui.team.TeamDetailActivity;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.jjw.soccerclub.util.GlideHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;

public class MatchDetailActivity extends BaseActivity {

    private static final int APPLY_ALLOWED       = 0;
    private static final int APPLY_ALREADY       = 1;
    private static final int BLOCK_SELF_AUTHOR   = 2;
    private static final int BLOCK_MY_TEAM       = 3;
    private static final int BLOCK_NO_PERMISSION = 4;
    private static final int BLOCK_NO_TEAM       = 5;
    private static final int APPLY_REAPPLY       = 6;

    private int applyState = APPLY_ALLOWED;
    private boolean matchLoaded = false, profileLoaded = false;
    private boolean myTeamLoaded = false, contentShown = false;

    private StateLayout state;
    private ImageView imgTeamLogo;
    private TextView txtTeamName, txtDateTime, txtAddress, txtStadium, txtDescription;
    private Button btnApply;
    private LinearLayout teamInfoBox;
    private TextView chipAge, chipRegion, chipSkill;
    private TextView tile1, tile2, tile3, tile4, tile5;
    private TextView tvRecentWin, tvRecentDraw, tvRecentLoss, tvRecentGF, tvRecentGA;

    private String matchId, matchTeamId = "", matchAuthorUid = "", teamName = "";
    private String currentUid = "", myNickname = "", myTeamId = "";
    private String myTeamName = "", myTeamLogoUrl = "";
    private String myTeamCaptainUid = "", myTeamViceCaptainUid = "";
    private int mySkill = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_match_detail);

        state          = findViewById(R.id.state);
        imgTeamLogo    = findViewById(R.id.imgTeamLogo);
        txtTeamName    = findViewById(R.id.txtTeamName);
        txtDateTime    = findViewById(R.id.txtDateTime);
        txtAddress     = findViewById(R.id.txtAddress);
        txtStadium     = findViewById(R.id.txtStadium);
        txtDescription = findViewById(R.id.txtDescription);
        btnApply       = findViewById(R.id.btnApply);
        teamInfoBox    = findViewById(R.id.teamInfoBox);
        chipAge        = findViewById(R.id.chipAge);
        chipRegion     = findViewById(R.id.chipRegion);
        chipSkill      = findViewById(R.id.chipSkill);
        tile1          = findViewById(R.id.tile1);
        tile2          = findViewById(R.id.tile2);
        tile3          = findViewById(R.id.tile3);
        tile4          = findViewById(R.id.tile4);
        tile5          = findViewById(R.id.tile5);
        tvRecentWin    = findViewById(R.id.tvRecentWin);
        tvRecentDraw   = findViewById(R.id.tvRecentDraw);
        tvRecentLoss   = findViewById(R.id.tvRecentLoss);
        tvRecentGF     = findViewById(R.id.tvRecentGF);
        tvRecentGA     = findViewById(R.id.tvRecentGA);

        if (state != null) state.showLoading();

        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        matchId    = getIntent().getStringExtra("matchId");

        if (AppUtils.isEmpty(matchId)) {
            if (state != null) { state.setEmptyMessage("잘못된 접근이에요."); state.showEmpty(); }
            finish(); return;
        }

        btnApply.setText("신청하기");
        btnApply.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFEF4444));
        btnApply.setEnabled(false);

        btnApply.setOnClickListener(v -> {
            switch (applyState) {
                case APPLY_ALLOWED:   showApplyConfirmDialog(false, this::submitApplication); break;
                case APPLY_REAPPLY:   showApplyConfirmDialog(true,  this::reapply); break;
                case APPLY_ALREADY:   CustomToast.info(this, "이미 신청한 글이에요."); break;
                case BLOCK_SELF_AUTHOR: CustomToast.warning(this, "본인이 올린 글에는 신청할 수 없어요."); break;
                case BLOCK_MY_TEAM:   CustomToast.warning(this, "내 팀이 올린 글에는 신청할 수 없어요."); break;
                case BLOCK_NO_PERMISSION: CustomToast.info(this, "주장/부주장만 신청할 수 있어요."); break;
                case BLOCK_NO_TEAM:   CustomToast.info(this, "팀이 없어요. 먼저 팀에 가입하거나 만들어주세요."); break;
            }
        });

        if (teamInfoBox != null) {
            teamInfoBox.setOnClickListener(v -> {
                if (!AppUtils.isEmpty(matchTeamId)) {
                    Intent intent = new Intent(this, TeamDetailActivity.class);
                    intent.putExtra("teamId", matchTeamId);
                    startActivity(intent);
                }
            });
        }

        loadMyProfile();
        loadMatchDetail();
    }

    private void loadMatchDetail() {
        FirebaseFirestore.getInstance().collection("matches").document(matchId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        if (state != null) { state.setEmptyMessage("삭제된 시합이에요."); state.showEmpty(); }
                        return;
                    }
                    bindMatch(doc);
                    matchLoaded = true;
                    evaluateApplyEligibility();
                })
                .addOnFailureListener(e -> {
                    if (state != null) { state.setEmptyMessage("불러오기 실패"); state.showEmpty(); }
                });
    }

    private void bindMatch(DocumentSnapshot doc) {
        teamName      = AppUtils.safe(doc.getString("teamName"));
        matchTeamId   = AppUtils.safe(doc.getString("teamId"));
        matchAuthorUid = AppUtils.firstNonEmpty(doc.getString("uid"), doc.getString("authorUid"));

        txtTeamName.setText(teamName);
        GlideHelper.loadTeamLogo(this,
                AppUtils.firstNonEmpty(doc.getString("logoUrl"), doc.getString("teamLogoUrl")),
                imgTeamLogo);

        String date = doc.getString("date");
        String time = doc.getString("time");
        txtDateTime.setText(DateUtils.appendWeekday(date) + (AppUtils.isEmpty(time) ? "" : " | " + time));

        String addr    = AppUtils.firstNonEmpty(doc.getString("address"), doc.getString("stadiumAddress"));
        String stadium = AppUtils.firstNonEmpty(doc.getString("stadiumName"), doc.getString("stadium"));
        txtAddress.setText(AppUtils.safe(addr));
        txtStadium.setText(AppUtils.safe(stadium));
        txtDescription.setText(AppUtils.safe(doc.getString("description")));

        Long sk = doc.getLong("skill");
        if (chipSkill  != null) chipSkill.setText("실력 " + (sk != null ? sk : "-"));

        if (!AppUtils.isEmpty(matchTeamId)) {
            FirebaseFirestore.getInstance().collection("teams").document(matchTeamId).get()
                    .addOnSuccessListener(teamDoc -> {
                        if (!teamDoc.exists()) return;
                        if (chipAge    != null) chipAge.setText(AppUtils.nz(teamDoc.getString("ageRange"), "-"));
                        if (chipRegion != null) chipRegion.setText(AppUtils.nz(teamDoc.getString("region"), "-"));
                    });
            loadRecentForm(matchTeamId);
        }
    }

    private void loadMyProfile() {
        FirebaseFirestore.getInstance().collection("profiles").document(currentUid).get()
                .addOnSuccessListener(doc -> {
                    myNickname = AppUtils.safe(doc.getString("nickname"));
                    Long sk = doc.getLong("skill");
                    mySkill = sk != null ? sk.intValue() : -1;

                    String teamIdFromProfile = doc.getString("myTeam");
                    if (!AppUtils.isEmpty(teamIdFromProfile)) {
                        myTeamId = teamIdFromProfile;
                        FirebaseFirestore.getInstance().collection("teams").document(myTeamId).get()
                                .addOnSuccessListener(teamDoc -> {
                                    if (teamDoc.exists()) {
                                        myTeamName         = AppUtils.safe(teamDoc.getString("teamName"));
                                        myTeamLogoUrl      = AppUtils.safe(teamDoc.getString("logoUrl"));
                                        myTeamCaptainUid   = AppUtils.safe(teamDoc.getString("captainUID"));
                                        myTeamViceCaptainUid = AppUtils.safe(teamDoc.getString("viceCaptainUID"));
                                    }
                                    myTeamLoaded = true;
                                    profileLoaded = true;
                                    evaluateApplyEligibility();
                                })
                                .addOnFailureListener(e -> {
                                    myTeamLoaded = true; profileLoaded = true;
                                    evaluateApplyEligibility();
                                });
                    } else {
                        myTeamId = ""; myTeamLoaded = true; profileLoaded = true;
                        evaluateApplyEligibility();
                    }
                })
                .addOnFailureListener(e -> {
                    myTeamLoaded = true; profileLoaded = true;
                    evaluateApplyEligibility();
                });
    }

    private void evaluateApplyEligibility() {
        if (!matchLoaded || !profileLoaded || !myTeamLoaded) return;

        if (!AppUtils.isEmpty(matchAuthorUid) && matchAuthorUid.equals(currentUid)) {
            setApplyState(BLOCK_SELF_AUTHOR, "신청 불가"); showContentOnce(); return;
        }
        if (!AppUtils.isEmpty(myTeamId) && myTeamId.equals(matchTeamId)) {
            setApplyState(BLOCK_MY_TEAM, "신청 불가"); showContentOnce(); return;
        }
        if (AppUtils.isEmpty(myTeamId)) {
            setApplyState(BLOCK_NO_TEAM, "신청 불가"); showContentOnce(); return;
        }
        boolean hasPermission = currentUid.equals(myTeamCaptainUid)
                || currentUid.equals(myTeamViceCaptainUid);
        if (!hasPermission) {
            setApplyState(BLOCK_NO_PERMISSION, "신청 불가"); showContentOnce(); return;
        }

        FirebaseFirestore.getInstance().collection("matches").document(matchId)
                .collection("applicants").document(currentUid).get()
                .addOnSuccessListener(ap -> {
                    if (ap.exists()) {
                        String st = AppUtils.safe(ap.getString("status")).toLowerCase();
                        if (st.startsWith("rej")) setApplyState(APPLY_REAPPLY, "다시 신청");
                        else                      setApplyState(APPLY_ALREADY, "신청 완료");
                    } else {
                        setApplyState(APPLY_ALLOWED, "신청하기");
                    }
                    showContentOnce();
                })
                .addOnFailureListener(e -> {
                    setApplyState(APPLY_ALLOWED, "신청하기");
                    showContentOnce();
                });
    }

    private void setApplyState(int state, String label) {
        this.applyState = state;
        btnApply.setText(label);
    }

    private void showContentOnce() {
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

    private void submitApplication() {
        long now = System.currentTimeMillis();
        Map<String, Object> data = new HashMap<>();
        data.put("userId",      currentUid);
        data.put("nickname",    myNickname);
        data.put("skill",       mySkill);
        data.put("timestamp",   now);
        data.put("teamName",    myTeamName);
        data.put("teamId",      myTeamId);
        data.put("teamLogoUrl", myTeamLogoUrl);
        data.put("status",      "pending");
        data.put("responded",   false);

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("matches").document(matchId)
                .collection("applicants").document(currentUid)
                .set(data)
                .addOnSuccessListener(v -> {
                    db.collection("matches").document(matchId)
                            .update("lastApplicantTs", now);

                    // ✅ teams/{teamId}/matchApplications/{matchId} 에 저장
                    // → 팀 관점에서 신청한 시합 목록 조회 가능 (인덱스 불필요)
                    Map<String, Object> teamApp = new HashMap<>();
                    teamApp.put("matchId",      matchId);
                    teamApp.put("postType",     "match");
                    teamApp.put("status",       "pending");
                    teamApp.put("timestamp",    now);
                    teamApp.put("applicantUid", currentUid); // 신청한 주장 uid
                    db.collection("teams").document(myTeamId)
                            .collection("matchApplications").document(matchId)
                            .set(teamApp);

                    CustomToast.success(this, "신청 완료! 상대 팀의 확인을 기다려 주세요.");
                    applyState = APPLY_ALREADY;
                    btnApply.setText("신청 완료");
                })
                .addOnFailureListener(e ->
                        CustomToast.error(this, "신청 실패: " + e.getMessage()));
    }

    private void reapply() {
        long now = System.currentTimeMillis();
        Map<String, Object> up = new HashMap<>();
        up.put("status",      "pending");
        up.put("timestamp",   now);
        up.put("responded",   false);
        up.put("reapplyCount", FieldValue.increment(1));

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("matches").document(matchId)
                .collection("applicants").document(currentUid)
                .update(up)
                .addOnSuccessListener(v -> {
                    db.collection("matches").document(matchId)
                            .update("lastApplicantTs", now);

                    // ✅ teams/{teamId}/matchApplications 상태도 업데이트
                    Map<String, Object> teamApp = new HashMap<>();
                    teamApp.put("status",    "pending");
                    teamApp.put("timestamp", now);
                    db.collection("teams").document(myTeamId)
                            .collection("matchApplications").document(matchId)
                            .set(teamApp, com.google.firebase.firestore.SetOptions.merge());

                    CustomToast.success(this, "재신청 완료!");
                    applyState = APPLY_ALREADY;
                    btnApply.setText("신청 완료");
                })
                .addOnFailureListener(e ->
                        CustomToast.error(this, "재신청 실패: " + e.getMessage()));
    }

    private void loadRecentForm(String teamId) {
        TextView[] tiles = {tile1, tile2, tile3, tile4, tile5};
        for (TextView t : tiles) {
            if (t == null) continue;
            t.setText("-"); t.setTextColor(0xFF9E9E9E);
            t.setBackgroundResource(R.drawable.bg_result_neutral);
        }

        FirebaseFirestore.getInstance().collection("matches")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("status", "finished")
                .orderBy("matchTs", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .addOnSuccessListener(q -> {
                    int w=0, d=0, l=0, gf=0, ga=0, i=0;
                    for (DocumentSnapshot m : q) {
                        int sf = toInt(m.get("scoreFor"));
                        int sa = toInt(m.get("scoreAgainst"));
                        gf += sf; ga += sa;
                        String r; int color;
                        if      (sf > sa) { r="W"; color=0xFF4CAF50; w++; }
                        else if (sf==sa)  { r="D"; color=0xFF546E7A; d++; }
                        else              { r="L"; color=0xFFE53935; l++; }
                        if (i < tiles.length && tiles[i] != null) {
                            tiles[i].setText(r);
                            tiles[i].setTextColor(color);
                            i++;
                        }
                    }
                    if (tvRecentWin  != null) tvRecentWin.setText("승 " + w);
                    if (tvRecentDraw != null) tvRecentDraw.setText("  ·  무 " + d);
                    if (tvRecentLoss != null) tvRecentLoss.setText("  ·  패 " + l);
                    if (tvRecentGF   != null) tvRecentGF.setText("득점 " + gf);
                    if (tvRecentGA   != null) tvRecentGA.setText(" · 실점 " + ga);
                });
    }

    private int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}