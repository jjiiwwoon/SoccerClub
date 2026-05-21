package com.example.soccerclub.ui.profile;

import android.app.Activity;
import android.content.Intent;
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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.soccerclub.R;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.common.StateLayout;
import com.example.soccerclub.ui.auth.LoginActivity;
import com.example.soccerclub.ui.team.TeamDetailActivity;
import com.example.soccerclub.util.AppUtils;
import com.example.soccerclub.viewmodel.ProfileViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * 내 프로필 화면.
 *
 * ✅ 패키지: com.example.soccerclub.ui.profile
 * → ui/profile/ 폴더에 배치하세요.
 *
 * [변경 전] Fragment 가 직접 하던 일
 *   - AtomicInteger pendingOps 로 3개 비동기 작업 수동 동기화
 *   - contentShown 플래그로 중복 showContent() 방지
 *   - profiles / teams / teamStats Firestore 직접 호출
 *
 * [변경 후] Fragment 가 하는 일
 *   - ProfileViewModel 의 LiveData 3개 observe 만 담당
 *   - pendingOps, contentShown 완전 제거
 */
public class MyProfileFragment extends Fragment {

    // ── 뷰 ────────────────────────────────────────────────────────────────────────
    private StateLayout state;
    private ImageView   profileImageView, teamLogo, toggleIntroArrow;
    private TextView    textNickname, textAge, textPositionBox, textSkill, textFoot;
    private TextView    textHeight, textWeight, textPlayerType, textIntroContent, textTeam;
    private TextView    statsTeamGames, statsTeamGoals, statsTeamAssists;
    private TextView    statsMercGames, statsMercGoals, statsMercAssists;

    private boolean isIntroExpanded = false;

    // ── ViewModel ────────────────────────────────────────────────────────────────
    private ProfileViewModel viewModel;

    // ── 프로필 편집 후 새로고침 ───────────────────────────────────────────────────
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

    // ── 생명주기 ──────────────────────────────────────────────────────────────────

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

        // ── LiveData 구독 ─────────────────────────────────────────────────────────

        // 1) 프로필
        viewModel.profile.observe(getViewLifecycleOwner(), doc -> {
            if (doc == null || !doc.exists()) {
                if (state != null) state.showEmpty();
                return;
            }
            bindProfileFields(doc);
            loadProfileImage(doc.getString("profileImageUrl"));
            if (state != null) state.showContent();
        });

        // 2) 팀 정보
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

        // 3) 통계
        viewModel.teamStats.observe(getViewLifecycleOwner(), doc -> {
            if (doc != null && doc.exists()) bindStats(doc);
        });

        // 로딩 상태
        viewModel.isLoading.observe(getViewLifecycleOwner(), loading -> {
            if (loading != null && loading && state != null) state.showLoading();
        });

        // 최초 로드
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
        if (textPositionBox != null) textPositionBox.setText(!TextUtils.isEmpty(position) ? position : "-");
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

        // 소개글 토글
        if (toggleIntroArrow != null && textIntroContent != null) {
            toggleIntroArrow.setOnClickListener(v -> {
                isIntroExpanded = !isIntroExpanded;
                textIntroContent.setMaxLines(isIntroExpanded ? Integer.MAX_VALUE : 2);
                toggleIntroArrow.setRotation(isIntroExpanded ? 180f : 0f);
            });
        }

        // 프로필 편집 버튼
        View btnEdit = getView() != null ? getView().findViewById(R.id.btnEditProfile) : null;
        if (btnEdit != null) {
            btnEdit.setOnClickListener(v ->
                    editProfileLauncher.launch(
                            new Intent(requireContext(), EditProfileActivity.class)));
        }
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