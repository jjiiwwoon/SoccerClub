package com.example.soccerclub.ui.home;

import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.soccerclub.R;
import com.example.soccerclub.ui.chat.ChatFragment;
import com.example.soccerclub.ui.profile.MyProfileFragment;
import com.example.soccerclub.ui.recruit.RecruitMatchFragment;
import com.example.soccerclub.ui.team.AllTeamFragment;
import com.example.soccerclub.ui.team.MyTeamFragment;
import com.google.android.material.button.MaterialButton;

public class HomeActivity extends AppCompatActivity {

    private MaterialButton btnRecruitMatch, btnMyTeam, btnOtherTeams, btnMyProfile, btnChat;

    // ✅ Fragment 인스턴스 유지 → 탭 전환 시 상태(스크롤 위치 등) 보존
    private final RecruitMatchFragment fragRecruitMatch = new RecruitMatchFragment();
    private final MyTeamFragment       fragMyTeam       = new MyTeamFragment();
    private final AllTeamFragment      fragOtherTeams   = new AllTeamFragment();
    private final MyProfileFragment    fragMyProfile    = new MyProfileFragment();
    private final ChatFragment         fragChat         = new ChatFragment();

    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        btnRecruitMatch = findViewById(R.id.btnRecruitMatch);
        btnMyTeam       = findViewById(R.id.btnMyTeam);
        btnOtherTeams   = findViewById(R.id.btnOtherTeams);
        btnMyProfile    = findViewById(R.id.btnMyProfile);
        btnChat         = findViewById(R.id.btnChat);

        // ✅ 모든 Fragment를 한 번에 add 후 show/hide로 전환 (상태 보존)
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(R.id.fragment_container, fragChat,         "chat");
        ft.add(R.id.fragment_container, fragMyProfile,    "profile");
        ft.add(R.id.fragment_container, fragOtherTeams,   "teams");
        ft.add(R.id.fragment_container, fragMyTeam,       "myteam");
        ft.add(R.id.fragment_container, fragRecruitMatch, "recruit");
        ft.hide(fragChat);
        ft.hide(fragMyProfile);
        ft.hide(fragOtherTeams);
        ft.hide(fragMyTeam);
        ft.commit();

        // ✅ 첫 화면: 모집/매치로 변경 (기존: 내 프로필)
        activeFragment = fragRecruitMatch;
        updateBottomNavColor(btnRecruitMatch);

        btnRecruitMatch.setOnClickListener(v -> switchTo(fragRecruitMatch, btnRecruitMatch));
        btnMyTeam.setOnClickListener(v       -> switchTo(fragMyTeam,       btnMyTeam));
        btnOtherTeams.setOnClickListener(v   -> switchTo(fragOtherTeams,   btnOtherTeams));
        btnMyProfile.setOnClickListener(v    -> switchTo(fragMyProfile,    btnMyProfile));
        btnChat.setOnClickListener(v         -> switchTo(fragChat,         btnChat));
    }

    // ✅ show/hide 방식 → 스크롤 위치, 로딩 상태 모두 유지됨
    private void switchTo(Fragment target, MaterialButton btn) {
        if (activeFragment == target) return;
        getSupportFragmentManager()
                .beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
        updateBottomNavColor(btn);
    }

    private void updateBottomNavColor(MaterialButton selected) {
        MaterialButton[] buttons = {
                btnRecruitMatch, btnMyTeam, btnOtherTeams, btnMyProfile, btnChat
        };
        int selectedColor   = ContextCompat.getColor(this, R.color.badge_blue);
        int unselectedColor = ContextCompat.getColor(this, R.color.text_primary);
        for (MaterialButton btn : buttons) {
            int color = (btn == selected) ? selectedColor : unselectedColor;
            btn.setTextColor(color);
            btn.setIconTint(ColorStateList.valueOf(color));
        }
    }
}
