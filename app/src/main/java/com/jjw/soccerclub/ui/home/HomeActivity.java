package com.jjw.soccerclub.ui.home;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.ui.chat.ChatFragment;
import com.jjw.soccerclub.ui.profile.MyProfileFragment;
import com.jjw.soccerclub.ui.recruit.RecruitMatchFragment;
import com.jjw.soccerclub.ui.team.AllTeamFragment;
import com.jjw.soccerclub.ui.team.MyTeamFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * [변경 전] MaterialButton 5개를 직접 관리
 *   - btnRecruitMatch, btnMyTeam, btnOtherTeams, btnMyProfile, btnChat 필드
 *   - updateNavColors() 로 수동 색상 변경
 *   - 각 버튼에 setOnClickListener 5개 등록
 *
 * [변경 후] BottomNavigationView 하나로 통합
 *   - navBar.setOnItemSelectedListener() 하나로 탭 전환 처리
 *   - 선택된 탭 색상/크기 변경 자동 처리
 *   - 뱃지 추가도 navBar.getOrCreateBadge(R.id.navChat).setNumber(3) 한 줄로 가능
 */
public class HomeActivity extends AppCompatActivity {

    // ── 변경: MaterialButton 5개 → BottomNavigationView 1개 ────────────────────
    private BottomNavigationView navBar;

    // Fragment 인스턴스 유지 — 탭 전환 시 상태(스크롤 위치 등) 보존
    private final RecruitMatchFragment fragRecruitMatch = new RecruitMatchFragment();
    private final MyTeamFragment       fragMyTeam       = new MyTeamFragment();
    private final AllTeamFragment      fragOtherTeams   = new AllTeamFragment();
    private final MyProfileFragment    fragMyProfile    = new MyProfileFragment();
    private final ChatFragment         fragChat         = new ChatFragment();

    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 엣지-투-엣지
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_home);

        // Window Insets 처리
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.homeLayout), (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(0, systemBars.top, 0, 0);
                    return insets;
                });

        // ── BottomNavigationView 초기화 ────────────────────────────────────────
        navBar = findViewById(R.id.bottomNavBar);

        // 모든 Fragment 를 미리 add (show/hide 방식 — Fragment 상태 보존)
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, fragChat,         "chat")
                .add(R.id.fragment_container, fragMyProfile,    "profile")
                .add(R.id.fragment_container, fragOtherTeams,   "otherTeams")
                .add(R.id.fragment_container, fragMyTeam,       "myTeam")
                .add(R.id.fragment_container, fragRecruitMatch, "recruitMatch")
                .commit();

        // 기본 탭: 찾기(모집/매치)
        activeFragment = fragRecruitMatch;
        hideAllExcept(fragRecruitMatch);

        // ── 탭 선택 리스너 ──────────────────────────────────────────────────────
        navBar.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.navRecruitMatch) {
                switchTo(fragRecruitMatch);
            } else if (id == R.id.navMyTeam) {
                switchTo(fragMyTeam);
            } else if (id == R.id.navOtherTeams) {
                switchTo(fragOtherTeams);
            } else if (id == R.id.navMyProfile) {
                switchTo(fragMyProfile);
            } else if (id == R.id.navChat) {
                // ✅ 채팅 탭 진입 시 뱃지 제거
                navBar.removeBadge(R.id.navChat);
                switchTo(fragChat);
            } else {
                return false;
            }
            return true;
        });

        // 기본 선택 상태
        navBar.setSelectedItemId(R.id.navRecruitMatch);
    }

    // ── Fragment 전환 ─────────────────────────────────────────────────────────

    private void switchTo(Fragment target) {
        if (activeFragment == target) return;
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.hide(activeFragment);
        tx.show(target);
        // ChatFragment 의 onHiddenChanged 가 리스너 on/off 를 담당
        tx.commit();
        activeFragment = target;
    }

    private void hideAllExcept(Fragment keep) {
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        for (Fragment f : new Fragment[]{
                fragChat, fragMyProfile, fragOtherTeams, fragMyTeam, fragRecruitMatch}) {
            if (f != keep) tx.hide(f);
        }
        tx.commit();
    }

    // ── 외부에서 채팅 뱃지 설정 (예: 신규 메시지 수신 시) ──────────────────────

    /**
     * 채팅 탭에 뱃지 숫자 표시.
     * 예: 푸시 알림 수신 후 호출.
     *
     * navBar.getOrCreateBadge(R.id.navChat).setNumber(count);
     */
    public void showChatBadge(int count) {
        if (count > 0) {
            navBar.getOrCreateBadge(R.id.navChat).setNumber(count);
        } else {
            navBar.removeBadge(R.id.navChat);
        }
    }
}