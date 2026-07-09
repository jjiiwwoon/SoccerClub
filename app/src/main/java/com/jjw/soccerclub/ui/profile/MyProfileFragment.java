package com.jjw.soccerclub.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
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
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.StateLayout;
import com.jjw.soccerclub.ui.auth.LoginActivity;
import com.jjw.soccerclub.ui.team.TeamDetailActivity;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.viewmodel.ProfileViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

public class MyProfileFragment extends Fragment {

    private StateLayout state;
    private ImageView   profileImageView, teamLogo, toggleIntroArrow;
    private TextView    textNickname, textAge, textPositionBox, textSkill, textFoot;
    private TextView    textHeight, textWeight, textPlayerType, textIntroContent, textTeam;
    private TextView    statsTeamGames, statsTeamGoals, statsTeamAssists;
    private TextView    statsMercGames, statsMercGoals, statsMercAssists;

    private boolean isIntroExpanded = false;
    private ProfileViewModel viewModel;

    private final ActivityResultLauncher<Intent> editProfileLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK) {
                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                            if (user != null && viewModel != null) {
                                if (state != null) state.showLoading();
                                viewModel.reload(user.getUid());
                            }
                        }
                    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_profile, container, false);

        state             = view.findViewById(R.id.state);
        profileImageView  = view.findViewById(R.id.profileImageView);
        textNickname      = view.findViewById(R.id.textNickname);
        textAge           = view.findViewById(R.id.textAge);
        textPositionBox   = view.findViewById(R.id.textPositionBox);
        textSkill         = view.findViewById(R.id.textSkill);
        textFoot          = view.findViewById(R.id.textFoot);
        textHeight        = view.findViewById(R.id.textHeight);
        textWeight        = view.findViewById(R.id.textWeight);
        textPlayerType    = view.findViewById(R.id.textPlayerType);
        textIntroContent  = view.findViewById(R.id.textIntroContent);
        toggleIntroArrow  = view.findViewById(R.id.toggleIntroArrow);
        textTeam          = view.findViewById(R.id.textTeam);
        teamLogo          = view.findViewById(R.id.teamLogo);
        statsTeamGames    = view.findViewById(R.id.statsTeamGames);
        statsTeamGoals    = view.findViewById(R.id.statsTeamGoals);
        statsTeamAssists  = view.findViewById(R.id.statsTeamAssists);
        statsMercGames    = view.findViewById(R.id.statsMercGames);
        statsMercGoals    = view.findViewById(R.id.statsMercGoals);
        statsMercAssists  = view.findViewById(R.id.statsMercAssists);

        if (state != null) state.showLoading();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(requireContext(), LoginActivity.class));
            return;
        }

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        viewModel.profile.observe(getViewLifecycleOwner(), doc -> {
            if (doc == null || !doc.exists()) {
                if (state != null) state.showEmpty();
                return;
            }
            bindProfileFields(doc);
            loadProfileImage(doc.getString("profileImageUrl"));
            if (state != null) state.showContent();
        });

        viewModel.teamInfo.observe(getViewLifecycleOwner(), team -> {
            if (team == null) {
                showNoTeamPlaceholder();
                return;
            }
            if (textTeam != null) textTeam.setText(AppUtils.safe(team.getTeamName()));
            if (teamLogo != null) {
                String logoUrl = team.getLogoUrl();
                if (!AppUtils.isEmpty(logoUrl)) {
                    Glide.with(this).load(logoUrl).circleCrop().into(teamLogo);
                }
                teamLogo.setOnClickListener(v -> {
                    Intent i = new Intent(requireContext(), TeamDetailActivity.class);
                    i.putExtra("teamId", team.getTeamId());
                    startActivity(i);
                });
            }
        });

        viewModel.userStats.observe(getViewLifecycleOwner(), doc -> {
            if (doc != null && doc.exists()) bindStats(doc);
        });

        viewModel.isLoading.observe(getViewLifecycleOwner(), loading -> {
            if (loading != null && loading && state != null) state.showLoading();
        });

        View btnMercDetail = view.findViewById(R.id.btnMercDetail);
        if (btnMercDetail != null) {
            btnMercDetail.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(),
                            com.jjw.soccerclub.ui.profile.MercenaryActivitiesActivity.class)));
        }

        View btnLogout = view.findViewById(R.id.btnLogout);
        if (btnLogout != null) {
            btnLogout.setOnClickListener(v ->
                    new AlertDialog.Builder(requireContext())
                            .setTitle("로그아웃")
                            .setMessage("로그아웃 하시겠습니까?")
                            .setPositiveButton("로그아웃", (d, i) -> {
                                FirebaseAuth.getInstance().signOut();
                                Intent intent = new Intent(requireContext(), LoginActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            })
                            .setNegativeButton("취소", null)
                            .show());
        }

        viewModel.loadIfNeeded(user.getUid());
    }

    // ── 바인딩 ────────────────────────────────────────────────────────────────────

    private void bindProfileFields(DocumentSnapshot doc) {
        String nickname    = doc.getString("nickname");
        Long   ageLong     = doc.getLong("age");
        String position    = doc.getString("position");
        Long   skillLong   = doc.getLong("skill");
        String foot        = doc.getString("foot");
        String intro       = doc.getString("introduction");
        Long   h           = doc.getLong("height");
        Long   w           = doc.getLong("weight");
        String playerType  = doc.getString("playerType");
        String playerLevel = doc.getString("playerLevel");

        if (textNickname    != null) textNickname.setText(!TextUtils.isEmpty(nickname) ? nickname : "닉네임 없음");
        if (textAge         != null) textAge.setText(ageLong != null ? ageLong + "세" : "-");

        // ★ 포지션 — 칩 스타일 색상 적용 (모집 칩과 동일 색상)
        if (textPositionBox != null) {
            String pos = !TextUtils.isEmpty(position) ? position : "-";
            textPositionBox.setText(pos);
            applyPositionColor(textPositionBox, pos);
        }

        if (textSkill       != null) textSkill.setText(skillLong != null ? String.valueOf(skillLong) : "-");
        if (textFoot        != null) textFoot.setText(!TextUtils.isEmpty(foot) ? foot : "-");
        if (textHeight      != null) textHeight.setText(h != null ? h + "cm" : "-");
        if (textWeight      != null) textWeight.setText(w != null ? w + "kg" : "-");

        if (textIntroContent != null) {
            textIntroContent.setText(!TextUtils.isEmpty(intro) ? intro : "자기소개 없음");
            textIntroContent.setMaxLines(2);
            isIntroExpanded = false;
            if (toggleIntroArrow != null) toggleIntroArrow.setRotation(0f);
        }

        if (textPlayerType != null) {
            if ("비선출".equals(playerType))
                textPlayerType.setText("비선출");
            else if ("선출".equals(playerType) && !TextUtils.isEmpty(playerLevel))
                textPlayerType.setText(playerLevel);
            else if ("선출".equals(playerType))
                textPlayerType.setText("선출");
            else
                textPlayerType.setText("-");
        }

        if (toggleIntroArrow != null && textIntroContent != null) {
            toggleIntroArrow.setOnClickListener(v -> {
                isIntroExpanded = !isIntroExpanded;
                textIntroContent.setMaxLines(isIntroExpanded ? Integer.MAX_VALUE : 2);
                toggleIntroArrow.setRotation(isIntroExpanded ? 180f : 0f);
            });
        }

        View btnEdit = getView() != null ? getView().findViewById(R.id.btnEditProfile) : null;
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v ->
                    editProfileLauncher.launch(
                            new Intent(requireContext(), EditProfileActivity.class)));
        }
    }

    // ★ 포지션별 색상 — 모집 칩과 통일 (FW=빨강, MF=초록, DF=파랑, GK=노랑)
    private void applyPositionColor(TextView tv, String pos) {
        if (tv == null || pos == null) return;
        int color;
        switch (pos.trim().toUpperCase()) {
            case "FW": color = 0xFFD50000; break;
            case "MF": color = 0xFF00C853; break;
            case "DF": color = 0xFF2962FF; break;
            case "GK": color = 0xFFF9A825; break;
            default:   color = 0xFF666666; break;
        }
        tv.setTextColor(color);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(40f);
        gd.setColor(0x00000000);
        gd.setStroke(4, color);
        tv.setBackground(gd);
    }

    private void bindStats(DocumentSnapshot doc) {
        long teamGames   = AppUtils.safeLong(doc.getLong("teamGames"),   0L);
        long teamGoals   = AppUtils.safeLong(doc.getLong("teamGoals"),   0L);
        long teamAssists = AppUtils.safeLong(doc.getLong("teamAssists"), 0L);
        long mercGames   = AppUtils.safeLong(doc.getLong("mercGames"),   0L);
        long mercGoals   = AppUtils.safeLong(doc.getLong("mercGoals"),   0L);
        long mercAssists = AppUtils.safeLong(doc.getLong("mercAssists"), 0L);

        if (statsTeamGames   != null) statsTeamGames.setText(String.valueOf(teamGames));
        if (statsTeamGoals   != null) statsTeamGoals.setText(String.valueOf(teamGoals));
        if (statsTeamAssists != null) statsTeamAssists.setText(String.valueOf(teamAssists));
        if (statsMercGames   != null) statsMercGames.setText(String.valueOf(mercGames));
        if (statsMercGoals   != null) statsMercGoals.setText(String.valueOf(mercGoals));
        if (statsMercAssists != null) statsMercAssists.setText(String.valueOf(mercAssists));
    }

    private void loadProfileImage(String url) {
        if (profileImageView == null) return;
        if (!TextUtils.isEmpty(url)) {
            Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .circleCrop()
                    .into(profileImageView);
        } else {
            profileImageView.setImageResource(R.drawable.ic_person_placeholder);
        }
    }

    private void showNoTeamPlaceholder() {
        if (textTeam != null) textTeam.setText("소속 팀 없음");
        if (teamLogo != null) teamLogo.setImageResource(R.drawable.ic_shield_gray);
    }
}