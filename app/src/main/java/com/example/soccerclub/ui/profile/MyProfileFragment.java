package com.example.soccerclub.ui.profile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.soccerclub.R;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.common.StateLayout;
import com.example.soccerclub.ui.auth.LoginActivity;
import com.example.soccerclub.ui.team.TeamDetailActivity;
import com.example.soccerclub.util.AppUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.concurrent.atomic.AtomicInteger;

public class MyProfileFragment extends Fragment {

    private static final boolean WAIT_IMAGES = true;

    private StateLayout state;
    private ImageView profileImageView, teamLogo, toggleIntroArrow;
    private TextView textNickname, textAge, textPositionBox, textSkill, textFoot;
    private TextView textHeight, textWeight, textPlayerType, textIntroContent, textTeam;
    private TextView statsTeamGames, statsTeamGoals, statsTeamAssists;
    private TextView statsMercGames, statsMercGoals, statsMercAssists;

    private boolean isIntroExpanded = false;
    private String currentUid = null;
    private String myTeamId = null;

    private final AtomicInteger pendingOps = new AtomicInteger(0);
    private volatile boolean contentShown = false;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    // 편집 화면에서 돌아왔을 때 프로필 새로고침
    private final ActivityResultLauncher<Intent> editProfileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && isUiSafe()) {
                            contentShown = false;
                            pendingOps.set(0);
                            if (state != null) state.showLoading();
                            loadUserProfile();
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_profile, container, false);

        state            = view.findViewById(R.id.state);
        profileImageView = view.findViewById(R.id.profileImageView);
        textNickname     = view.findViewById(R.id.textNickname);
        textAge          = view.findViewById(R.id.textAge);
        textPositionBox  = view.findViewById(R.id.textPositionBox);
        textSkill        = view.findViewById(R.id.textSkill);
        textFoot         = view.findViewById(R.id.textFoot);
        textHeight       = view.findViewById(R.id.textHeight);
        textWeight       = view.findViewById(R.id.textWeight);
        textPlayerType   = view.findViewById(R.id.textPlayerType);
        textIntroContent = view.findViewById(R.id.textIntroContent);
        toggleIntroArrow = view.findViewById(R.id.toggleIntroArrow);
        textTeam         = view.findViewById(R.id.textTeam);
        teamLogo         = view.findViewById(R.id.teamLogo);

        statsTeamGames   = view.findViewById(R.id.statsTeamGames);
        statsTeamGoals   = view.findViewById(R.id.statsTeamGoals);
        statsTeamAssists = view.findViewById(R.id.statsTeamAssists);
        statsMercGames   = view.findViewById(R.id.statsMercGames);
        statsMercGoals   = view.findViewById(R.id.statsMercGoals);
        statsMercAssists = view.findViewById(R.id.statsMercAssists);

        db   = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        if (state != null) state.showLoading();

        toggleIntroArrow.setRotation(0f);
        toggleIntroArrow.setOnClickListener(v -> toggleIntro());

        View.OnClickListener teamClick = v -> openTeamOrWarn();
        teamLogo.setOnClickListener(teamClick);
        textTeam.setOnClickListener(teamClick);

        // 편집 버튼
        View btnEditProfile = view.findViewById(R.id.btnEditProfile);
        if (btnEditProfile != null) {
            btnEditProfile.setOnClickListener(v ->
                    editProfileLauncher.launch(
                            new Intent(requireContext(), EditProfileActivity.class)));
        }

        // ✅ 로그아웃 버튼
        View btnLogout = view.findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> showLogoutConfirm());
        }

        loadUserProfile();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadUserStatsOnly();
    }

    // ── 로그아웃 ──────────────────────────────────────────────────────────────────

    // ✅ 로그아웃 확인 다이얼로그
    private void showLogoutConfirm() {
        new AlertDialog.Builder(requireContext())
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃 하시겠어요?")
                .setPositiveButton("로그아웃", (d, i) -> {
                    auth.signOut();
                    // 로그인 화면으로 이동 + 백스택 전부 제거
                    Intent intent = new Intent(requireContext(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ── 프로필 로드 ───────────────────────────────────────────────────────────────

    private void loadUserProfile() {
        if (auth.getCurrentUser() == null) {
            if (state != null) state.showEmpty();
            if (isUiSafe()) CustomToast.error(getContext(), "로그인이 필요해요.");
            return;
        }

        currentUid = auth.getCurrentUser().getUid();
        contentShown = false;
        pendingOps.set(0);
        addWait(1);

        db.collection("profiles").document(currentUid).get()
                .addOnSuccessListener(doc -> {
                    if (!isUiSafe()) return;
                    if (!doc.exists()) {
                        if (state != null) state.showEmpty();
                        CustomToast.info(getContext(), "프로필 정보가 없어요.");
                        doneOne();
                        return;
                    }

                    bindProfileFields(doc);

                    String profileImageUrl = doc.getString("profileImageUrl");
                    myTeamId = doc.getString("myTeam");

                    loadProfileImage(profileImageUrl);
                    loadTeamInfo();
                    loadUserStats();

                    doneOne();
                    tryShowImmediatelyIfNoWait();
                })
                .addOnFailureListener(e -> {
                    if (!isUiSafe()) return;
                    if (state != null) state.showEmpty();
                    CustomToast.error(getContext(), "프로필 불러오기 실패했어요.");
                });
    }

    private void bindProfileFields(DocumentSnapshot doc) {
        String nickname   = doc.getString("nickname");
        Long ageLong      = doc.getLong("age");
        String position   = doc.getString("position");
        Long skillLong    = doc.getLong("skill");
        String foot       = doc.getString("foot");
        String intro      = doc.getString("introduction");
        Long h            = doc.getLong("height");
        Long w            = doc.getLong("weight");
        String playerType  = doc.getString("playerType");
        String playerLevel = doc.getString("playerLevel");

        textNickname.setText(!TextUtils.isEmpty(nickname) ? nickname : "닉네임 없음");
        textAge.setText(ageLong != null ? ageLong + "세" : "-");
        textPositionBox.setText(!TextUtils.isEmpty(position) ? position : "-");
        textSkill.setText(skillLong != null ? String.valueOf(skillLong) : "-");
        textFoot.setText(!TextUtils.isEmpty(foot) ? foot : "-");
        textHeight.setText(h != null ? h + "cm" : "-");
        textWeight.setText(w != null ? w + "kg" : "-");
        textIntroContent.setText(!TextUtils.isEmpty(intro) ? intro : "자기소개 없음");
        textIntroContent.setMaxLines(2);
        isIntroExpanded = false;
        toggleIntroArrow.setRotation(0f);

        if ("비선출".equals(playerType)) {
            textPlayerType.setText("비선출");
        } else if ("선출".equals(playerType) && !TextUtils.isEmpty(playerLevel)) {
            textPlayerType.setText(playerLevel);
        } else if ("선출".equals(playerType)) {
            textPlayerType.setText("선출");
        } else {
            textPlayerType.setText("-");
        }
    }

    private void loadProfileImage(String url) {
        if (!TextUtils.isEmpty(url)) {
            if (WAIT_IMAGES) addWait(1);
            Glide.with(this).load(url)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                    Target<Drawable> t, boolean first) {
                            if (WAIT_IMAGES) doneOne();
                            return false;
                        }
                        @Override
                        public boolean onResourceReady(Drawable r, Object model,
                                                       Target<Drawable> t,
                                                       com.bumptech.glide.load.DataSource ds,
                                                       boolean first) {
                            if (WAIT_IMAGES) doneOne();
                            return false;
                        }
                    })
                    .into(profileImageView);
        } else {
            profileImageView.setImageResource(R.drawable.ic_person_placeholder);
        }
    }

    private void loadTeamInfo() {
        if (AppUtils.isEmpty(myTeamId)) {
            showNoTeamPlaceholder();
            return;
        }
        addWait(1);
        db.collection("teams").document(myTeamId).get()
                .addOnSuccessListener(teamDoc -> {
                    if (!isUiSafe()) { doneOne(); return; }
                    if (teamDoc.exists()) {
                        String teamName = teamDoc.getString("teamName");
                        String logoUrl  = teamDoc.getString("logoUrl");
                        textTeam.setText(!TextUtils.isEmpty(teamName) ? teamName : "-");
                        teamLogo.setVisibility(View.VISIBLE);
                        if (!TextUtils.isEmpty(logoUrl)) {
                            if (WAIT_IMAGES) addWait(1);
                            Glide.with(this).load(logoUrl)
                                    .placeholder(R.drawable.ic_shield_gray)
                                    .listener(new RequestListener<Drawable>() {
                                        @Override
                                        public boolean onLoadFailed(@Nullable GlideException e,
                                                                    Object model, Target<Drawable> t, boolean first) {
                                            if (WAIT_IMAGES) doneOne();
                                            return false;
                                        }
                                        @Override
                                        public boolean onResourceReady(Drawable r, Object model,
                                                                       Target<Drawable> t,
                                                                       com.bumptech.glide.load.DataSource ds, boolean first) {
                                            if (WAIT_IMAGES) doneOne();
                                            return false;
                                        }
                                    })
                                    .into(teamLogo);
                        } else {
                            teamLogo.setImageResource(R.drawable.ic_shield_gray);
                        }
                    } else {
                        showNoTeamPlaceholder();
                    }
                    doneOne();
                })
                .addOnFailureListener(e -> {
                    if (!isUiSafe()) return;
                    showNoTeamPlaceholder();
                    doneOne();
                });
    }

    private void loadUserStats() {
        addWait(1);
        db.collection("userStats").document(currentUid).get()
                .addOnSuccessListener(us -> {
                    if (!isUiSafe()) { doneOne(); return; }
                    long tg = 0, tgo = 0, ta = 0, mg = 0, mgo = 0, ma = 0;
                    if (us.exists()) {
                        tg  = AppUtils.safeLong(us.getLong("teamGames"), 0L);
                        tgo = AppUtils.safeLong(us.getLong("teamGoals"), 0L);
                        ta  = AppUtils.safeLong(us.getLong("teamAssists"), 0L);
                        mg  = AppUtils.safeLong(us.getLong("mercGames"), 0L);
                        mgo = AppUtils.safeLong(us.getLong("mercGoals"), 0L);
                        ma  = AppUtils.safeLong(us.getLong("mercAssists"), 0L);
                    }
                    statsTeamGames.setText(String.valueOf(tg));
                    statsTeamGoals.setText(String.valueOf(tgo));
                    if (statsTeamAssists != null) statsTeamAssists.setText(String.valueOf(ta));
                    if (statsMercGames   != null) statsMercGames.setText(String.valueOf(mg));
                    if (statsMercGoals   != null) statsMercGoals.setText(String.valueOf(mgo));
                    if (statsMercAssists != null) statsMercAssists.setText(String.valueOf(ma));
                    doneOne();
                })
                .addOnFailureListener(e -> {
                    if (!isUiSafe()) return;
                    statsTeamGames.setText("0");
                    statsTeamGoals.setText("0");
                    doneOne();
                });
    }

    private void reloadUserStatsOnly() {
        if (!isUiSafe() || auth.getCurrentUser() == null) return;
        db.collection("userStats").document(auth.getCurrentUser().getUid()).get()
                .addOnSuccessListener(us -> {
                    if (!isUiSafe() || !us.exists()) return;
                    long tg  = AppUtils.safeLong(us.getLong("teamGames"), 0L);
                    long tgo = AppUtils.safeLong(us.getLong("teamGoals"), 0L);
                    long ta  = AppUtils.safeLong(us.getLong("teamAssists"), 0L);
                    long mg  = AppUtils.safeLong(us.getLong("mercGames"), 0L);
                    long mgo = AppUtils.safeLong(us.getLong("mercGoals"), 0L);
                    long ma  = AppUtils.safeLong(us.getLong("mercAssists"), 0L);
                    statsTeamGames.setText(String.valueOf(tg));
                    statsTeamGoals.setText(String.valueOf(tgo));
                    if (statsTeamAssists != null) statsTeamAssists.setText(String.valueOf(ta));
                    if (statsMercGames   != null) statsMercGames.setText(String.valueOf(mg));
                    if (statsMercGoals   != null) statsMercGoals.setText(String.valueOf(mgo));
                    if (statsMercAssists != null) statsMercAssists.setText(String.valueOf(ma));
                });
    }

    private void showNoTeamPlaceholder() {
        textTeam.setText("소속팀 없음");
        teamLogo.setVisibility(View.VISIBLE);
        teamLogo.setImageResource(R.drawable.ic_shield_gray);
    }

    private void toggleIntro() {
        if (isIntroExpanded) {
            textIntroContent.setMaxLines(2);
            toggleIntroArrow.animate().rotation(0f).setDuration(200).start();
        } else {
            textIntroContent.setMaxLines(Integer.MAX_VALUE);
            toggleIntroArrow.animate().rotation(180f).setDuration(200).start();
        }
        isIntroExpanded = !isIntroExpanded;
    }

    private void openTeamOrWarn() {
        if (!isUiSafe()) return;
        Context ctx = getContext();
        if (!AppUtils.isEmpty(myTeamId)) {
            Intent intent = new Intent(ctx, TeamDetailActivity.class);
            intent.putExtra("teamId", myTeamId);
            startActivity(intent);
        } else {
            CustomToast.info(ctx, "소속된 팀이 없어요.");
        }
    }

    private void addWait(int count) { if (count > 0) pendingOps.addAndGet(count); }

    private void doneOne() {
        int left = pendingOps.decrementAndGet();
        maybeShowContent(left);
    }

    private void maybeShowContent(int left) {
        if (!contentShown && left <= 0) {
            contentShown = true;
            if (isUiSafe() && state != null) state.showContent();
        }
    }

    private void tryShowImmediatelyIfNoWait() { maybeShowContent(pendingOps.get()); }

    private boolean isUiSafe() {
        return isAdded() && getView() != null && getContext() != null;
    }
}
