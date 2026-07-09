package com.jjw.soccerclub.ui.team;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.adapter.TeamMemberAdapter;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.common.StateLayout;
import com.jjw.soccerclub.ui.common.BaseActivity;
import com.jjw.soccerclub.util.AppUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TeamDetailActivity extends BaseActivity {

    private StateLayout state;
    private ImageView teamLogo, teamPhoto, introToggle;
    private TextView teamName, teamIntro, teamRegion, teamSkill, teamAge;
    private TextView teamActivityDay, teamHomeStadiumName, teamHomeStadiumAddress;
    private LinearLayout recordSection;
    private TextView tvGames, tvWins, tvDraws, tvLosses, tvGF, tvGA, tvWinRate, tvSeeDetails;
    private TextView tvMemberTitle;
    private LinearLayout playerListLayout;
    private Button btnJoinTeam;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String teamId, currentUid;
    private String captainUid, viceCaptainUid;
    private boolean isMyTeam = false;
    private boolean isIntroExpanded = false;
    private boolean firstImageDrawn = false;
    private boolean teamLoaded = false;
    private boolean membersLoaded = false;

    private ListenerRegistration recordListener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_team_detail);

        teamId = getIntent().getStringExtra("teamId");
        if (AppUtils.isEmpty(teamId)) {
            CustomToast.error(this, "팀 정보를 불러올 수 없어요.");
            finish();
            return;
        }

        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        bindViews();
        state.showLoading();

        db.collection("profiles").document(currentUid).get()
                .addOnSuccessListener(profile -> {
                    if (profile.exists()) {
                        String myTeam = profile.getString("myTeam");
                        isMyTeam = teamId.equals(myTeam);
                    }
                    getTeamInfo();
                    loadPlayerList();
                    bindRecordSummary();
                })
                .addOnFailureListener(e -> {
                    getTeamInfo();
                    loadPlayerList();
                    bindRecordSummary();
                });

        tvSeeDetails.setOnClickListener(v -> {
            // RecordsActivity 완성 후 주석 해제
            Intent intent = new Intent(this, com.jjw.soccerclub.ui.common.RecordsActivity.class);
            intent.putExtra("myTeamId", teamId);
            startActivity(intent);
        });
    }

    private void bindViews() {
        state                  = findViewById(R.id.stateLayout);
        teamLogo               = findViewById(R.id.teamLogo);
        teamPhoto              = findViewById(R.id.teamPhoto);
        introToggle            = findViewById(R.id.introToggle);
        teamName               = findViewById(R.id.teamName);
        teamIntro              = findViewById(R.id.teamIntro);
        teamRegion             = findViewById(R.id.teamRegion);
        teamSkill              = findViewById(R.id.teamSkill);
        teamAge                = findViewById(R.id.teamAge);
        teamActivityDay        = findViewById(R.id.teamActivityDay);
        teamHomeStadiumName    = findViewById(R.id.teamHomeStadiumName);
        teamHomeStadiumAddress = findViewById(R.id.teamHomeStadiumAddress);
        recordSection          = findViewById(R.id.recordSection);
        tvGames                = findViewById(R.id.tvGames);
        tvWins                 = findViewById(R.id.tvWins);
        tvDraws                = findViewById(R.id.tvDraws);
        tvLosses               = findViewById(R.id.tvLosses);
        tvGF                   = findViewById(R.id.tvGF);
        tvGA                   = findViewById(R.id.tvGA);
        tvWinRate              = findViewById(R.id.tvWinRate);
        tvSeeDetails           = findViewById(R.id.tvSeeDetails);
        tvMemberTitle          = findViewById(R.id.tvMemberTitle);
        playerListLayout       = findViewById(R.id.playerListLayout);
        btnJoinTeam            = findViewById(R.id.btnJoinTeam);

        introToggle.setOnClickListener(v -> {
            isIntroExpanded = !isIntroExpanded;
            if (isIntroExpanded) {
                teamIntro.setMaxLines(Integer.MAX_VALUE);
                teamIntro.setEllipsize(null);
                introToggle.setImageResource(R.drawable.ic_arrow_up);
            } else {
                teamIntro.setMaxLines(10);
                teamIntro.setEllipsize(TextUtils.TruncateAt.END);
                introToggle.setImageResource(R.drawable.ic_arrow_down);
            }
        });
    }

    // ── 팀 정보 로드 ──────────────────────────────────────────────────────────────

    private void getTeamInfo() {
        firstImageDrawn = false;

        db.collection("teams").document(teamId)
                .get(Source.CACHE)
                .addOnSuccessListener(cache -> {
                    if (cache != null && cache.exists()) {
                        bindTeamTextFields(cache);
                        startImageLoads(cache);
                    }
                });

        db.collection("teams").document(teamId)
                .get(Source.SERVER)
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        state.setEmptyMessage("팀 정보를 찾을 수 없어요.");
                        state.showEmpty();
                        return;
                    }
                    bindTeamTextFields(doc);
                    startImageLoads(doc);
                    teamLoaded = true;
                    tryShowContent();
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "팀 정보를 불러오지 못했어요.");
                    state.showEmpty();
                });
    }

    private void bindTeamTextFields(DocumentSnapshot doc) {
        String name     = doc.getString("teamName");
        String intro    = doc.getString("intro");
        String region   = doc.getString("region");
        String skill    = doc.getString("skill");
        String ageRange = doc.getString("ageRange");
        String actDay   = doc.getString("activityDay");
        String stadName = doc.getString("homeStadiumName");
        String stadAddr = doc.getString("stadium");
        String timeS    = doc.getString("timeStart");
        String timeE    = doc.getString("timeEnd");

        captainUid     = doc.getString("captainUID");
        viceCaptainUid = doc.getString("viceCaptainUID");

        teamName.setText(AppUtils.safe(name));
        teamIntro.setText(AppUtils.safe(intro));
        teamIntro.setMaxLines(10);
        teamIntro.setEllipsize(TextUtils.TruncateAt.END);
        isIntroExpanded = false;
        introToggle.setImageResource(R.drawable.ic_arrow_down);

        teamRegion.setText(AppUtils.safe(region));
        teamSkill.setText(AppUtils.safe(skill));
        teamAge.setText(AppUtils.safe(ageRange));

        if (!AppUtils.isEmpty(actDay)) {
            String display = (!AppUtils.isEmpty(timeS) && !AppUtils.isEmpty(timeE))
                    ? actDay + " | " + timeS + " ~ " + timeE : actDay;
            setTextOrGone(teamActivityDay, display);
        } else {
            if (teamActivityDay != null) teamActivityDay.setVisibility(View.GONE);
        }

        setTextOrGone(teamHomeStadiumName,    stadName);
        setTextOrGone(teamHomeStadiumAddress, stadAddr);

        List<String> members = (List<String>) doc.get("members");
        boolean isMember = members != null && members.contains(currentUid);
        boolean isFull   = members != null && members.size() >= 30;

        if (isMyTeam || isMember) {
            if (btnJoinTeam != null) btnJoinTeam.setVisibility(View.GONE);
        } else {
            if (btnJoinTeam != null) btnJoinTeam.setVisibility(View.VISIBLE);
            if (btnJoinTeam != null) btnJoinTeam.setText(isFull ? "팀원 모집 마감" : "팀 가입 신청");
            if (btnJoinTeam != null) btnJoinTeam.setEnabled(!isFull);
            if (!isFull && btnJoinTeam != null) btnJoinTeam.setOnClickListener(v -> joinTeam());
        }
    }

    private void startImageLoads(DocumentSnapshot doc) {
        String photoUrl = doc.getString("teamPhotoUrl");
        String logoUrl  = doc.getString("logoUrl");

        RequestOptions opts = new RequestOptions()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(800, 800);

        if (!AppUtils.isEmpty(photoUrl)) {
            Glide.with(this).load(photoUrl).apply(opts)
                    .placeholder(R.drawable.default_team_photo)
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e,
                                                    Object m, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> t, boolean b) {
                            if (!firstImageDrawn) { firstImageDrawn = true; tryShowContent(); }
                            return false;
                        }
                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable r,
                                                       Object m, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> t,
                                                       com.bumptech.glide.load.DataSource ds, boolean b) {
                            if (!firstImageDrawn) { firstImageDrawn = true; tryShowContent(); }
                            return false;
                        }
                    }).into(teamPhoto);
        } else {
            teamPhoto.setImageResource(R.drawable.default_team_photo);
            if (!firstImageDrawn) { firstImageDrawn = true; tryShowContent(); }
        }

        if (!AppUtils.isEmpty(logoUrl)) {
            Glide.with(this).load(logoUrl).apply(opts)
                    .placeholder(R.drawable.ic_shield_gray).into(teamLogo);
        } else {
            teamLogo.setImageResource(R.drawable.ic_shield_gray);
        }
    }

    // ── 멤버 리스트 ───────────────────────────────────────────────────────────────

    private void loadPlayerList() {
        db.collection("teams").document(teamId).get()
                .addOnSuccessListener(teamSnap -> {
                    if (teamSnap == null || !teamSnap.exists()) return;

                    List<String> memberUids  = (List<String>) teamSnap.get("members");
                    String captainUID       = teamSnap.getString("captainUID");
                    String viceCaptainUID   = teamSnap.getString("viceCaptainUID");

                    if (memberUids == null || memberUids.isEmpty()) {
                        membersLoaded = true;
                        tryShowContent();
                        return;
                    }

                    RecyclerView recyclerView = findViewById(R.id.recyclerViewMembers);
                    if (recyclerView == null) {
                        membersLoaded = true;
                        tryShowContent();
                        return;
                    }

                    db.collection("profiles").whereIn("__name__", memberUids).get()
                            .addOnSuccessListener(profileSnap -> {
                                List<DocumentSnapshot> fwDocs = new ArrayList<>(),
                                        mfDocs = new ArrayList<>(),
                                        dfDocs = new ArrayList<>(),
                                        gkDocs = new ArrayList<>();

                                for (DocumentSnapshot p : profileSnap.getDocuments()) {
                                    String pos = p.getString("position");
                                    if (pos == null) { mfDocs.add(p); continue; }
                                    switch (pos.trim().toUpperCase()) {
                                        case "FW": fwDocs.add(p); break;
                                        case "MF": mfDocs.add(p); break;
                                        case "DF": dfDocs.add(p); break;
                                        case "GK": gkDocs.add(p); break;
                                        default:   mfDocs.add(p);
                                    }
                                }

                                List<TeamMemberAdapter.MemberItem> items = new ArrayList<>();
                                addGroup(items, "FW (" + fwDocs.size() + ")", fwDocs);
                                addGroup(items, "MF (" + mfDocs.size() + ")", mfDocs);
                                addGroup(items, "DF (" + dfDocs.size() + ")", dfDocs);
                                addGroup(items, "GK (" + gkDocs.size() + ")", gkDocs);

                                if (tvMemberTitle != null)
                                    tvMemberTitle.setText("팀 멤버 (" + profileSnap.size() + ")");

                                TeamMemberAdapter adapter = new TeamMemberAdapter(
                                        captainUID, viceCaptainUID, currentUid, null);

                                GridLayoutManager lm = new GridLayoutManager(this, 2);
                                lm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                                    @Override
                                    public int getSpanSize(int pos) {
                                        return adapter.getItemViewType(pos) == TeamMemberAdapter.TYPE_HEADER ? 2 : 1;
                                    }
                                });
                                recyclerView.setLayoutManager(lm);
                                adapter.setItems(items);
                                recyclerView.setAdapter(adapter);

                                membersLoaded = true;
                                tryShowContent();
                            })
                            .addOnFailureListener(e -> {
                                membersLoaded = true;
                                tryShowContent();
                            });
                });
    }

    private void addGroup(List<TeamMemberAdapter.MemberItem> items,
                          String header, List<DocumentSnapshot> docs) {
        TeamMemberAdapter.MemberItem h = new TeamMemberAdapter.MemberItem();
        h.type   = TeamMemberAdapter.TYPE_HEADER;
        h.header = header;
        items.add(h);
        for (DocumentSnapshot d : docs) {
            TeamMemberAdapter.MemberItem item = new TeamMemberAdapter.MemberItem();
            item.type     = TeamMemberAdapter.TYPE_PLAYER;
            item.uid      = d.getId();
            item.nickname = d.getString("nickname");
            item.photoUrl = d.getString("profileImageUrl");
            items.add(item);
        }
    }

    // ── 전적 요약 ─────────────────────────────────────────────────────────────────

    private void bindRecordSummary() {
        if (recordListener != null) recordListener.remove();
        recordListener = db.collection("teamStats").document(teamId)
                .addSnapshotListener((doc, e) -> {
                    if (doc == null || !doc.exists()) return;
                    long games  = AppUtils.safeLong(doc.getLong("games"), 0L);
                    long wins   = AppUtils.safeLong(doc.getLong("wins"), 0L);
                    long draws  = AppUtils.safeLong(doc.getLong("draws"), 0L);
                    long losses = AppUtils.safeLong(doc.getLong("losses"), 0L);
                    long gf     = AppUtils.safeLong(doc.getLong("goalsFor"), 0L);
                    long ga     = AppUtils.safeLong(doc.getLong("goalsAgainst"), 0L);

                    tvGames.setText(String.valueOf(games));
                    tvWins.setText(String.valueOf(wins));
                    tvDraws.setText(String.valueOf(draws));
                    tvLosses.setText(String.valueOf(losses));
                    tvGF.setText(String.valueOf(gf));
                    tvGA.setText(String.valueOf(ga));
                    tvWinRate.setText(games > 0
                            ? Math.round((wins * 100f) / games) + "%" : "-");
                });
    }

    // ✅ Bug 5: 즉시 가입 → joinRequests 서브컬렉션에 신청만 저장
    private void joinTeam() {
        new AlertDialog.Builder(this)
                .setTitle("팀 가입 신청")
                .setMessage("이 팀에 가입 신청하시겠어요?\n주장이 수락하면 팀원이 됩니다.")
                .setPositiveButton("신청", (d, i) -> {

                    db.collection("profiles").document(currentUid).get()
                            .addOnSuccessListener(profileSnap -> {
                                String nickname = AppUtils.safe(profileSnap.getString("nickname"));
                                Long skillL     = profileSnap.getLong("skill");
                                int  skill      = skillL != null ? skillL.intValue() : -1;

                                Map<String, Object> request = new HashMap<>();
                                request.put("uid",       currentUid);
                                request.put("nickname",  nickname);
                                request.put("skill",     skill);
                                request.put("status",    "pending");
                                request.put("timestamp", System.currentTimeMillis());

                                db.collection("teams").document(teamId)
                                        .collection("joinRequests").document(currentUid)
                                        .set(request)
                                        .addOnSuccessListener(v -> {
                                            CustomToast.success(this,
                                                    "가입 신청 완료!\n주장의 수락을 기다려주세요.");
                                            if (btnJoinTeam != null) btnJoinTeam.setText("신청 완료");
                                            if (btnJoinTeam != null) btnJoinTeam.setEnabled(false);
                                        })
                                        .addOnFailureListener(e ->
                                                CustomToast.error(this, "신청 실패: " + e.getMessage()));
                            })
                            .addOnFailureListener(e ->
                                    CustomToast.error(this, "프로필 정보를 불러오지 못했어요."));
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private void tryShowContent() {
        if (teamLoaded && membersLoaded && firstImageDrawn) {
            state.showContent();
        } else if (teamLoaded && firstImageDrawn) {
            state.showContent();
        }
    }

    private void setTextOrGone(TextView tv, String value) {
        if (tv == null) return;
        if (AppUtils.isEmpty(value)) tv.setVisibility(View.GONE);
        else { tv.setVisibility(View.VISIBLE); tv.setText(value); }
    }

    @Override
    protected void onDestroy() {
        if (recordListener != null) recordListener.remove();
        super.onDestroy();
    }
}