package com.example.soccerclub.ui.home;

import android.content.res.ColorStateList;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.soccerclub.R;
import com.example.soccerclub.ui.chat.ChatFragment;
import com.example.soccerclub.ui.profile.MyProfileFragment;
import com.example.soccerclub.ui.recruit.RecruitMatchFragment;
import com.example.soccerclub.ui.team.AllTeamFragment;
import com.example.soccerclub.ui.team.MyTeamFragment;
import com.google.android.material.button.MaterialButton;

public class HomeActivity extends AppCompatActivity {

    private MaterialButton btnRecruitMatch, btnMyTeam, btnOtherTeams, btnMyProfile, btnChat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        btnRecruitMatch = findViewById(R.id.btnRecruitMatch);
        btnMyTeam       = findViewById(R.id.btnMyTeam);
        btnOtherTeams   = findViewById(R.id.btnOtherTeams);
        btnMyProfile    = findViewById(R.id.btnMyProfile);
        btnChat         = findViewById(R.id.btnChat);

        loadFragment(new MyProfileFragment());
        updateBottomNavColor(btnMyProfile);

        btnRecruitMatch.setOnClickListener(v -> {
            loadFragment(new RecruitMatchFragment());
            updateBottomNavColor(btnRecruitMatch);
        });
        btnMyTeam.setOnClickListener(v -> {
            loadFragment(new MyTeamFragment());
            updateBottomNavColor(btnMyTeam);
        });
        btnOtherTeams.setOnClickListener(v -> {
            loadFragment(new AllTeamFragment());
            updateBottomNavColor(btnOtherTeams);
        });
        btnMyProfile.setOnClickListener(v -> {
            loadFragment(new MyProfileFragment());
            updateBottomNavColor(btnMyProfile);
        });
        btnChat.setOnClickListener(v -> {
            loadFragment(new ChatFragment());
            updateBottomNavColor(btnChat);
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    private void updateBottomNavColor(MaterialButton selected) {
        MaterialButton[] buttons = {
                btnRecruitMatch, btnMyTeam, btnOtherTeams, btnMyProfile, btnChat
        };

        // ✅ 하드코딩 제거 → colors.xml 리소스 참조 (다크모드 자동 대응)
        int selectedColor   = ContextCompat.getColor(this, R.color.badge_blue);
        int unselectedColor = ContextCompat.getColor(this, R.color.text_primary);

        for (MaterialButton btn : buttons) {
            int color = (btn == selected) ? selectedColor : unselectedColor;
            btn.setTextColor(color);
            btn.setIconTint(ColorStateList.valueOf(color));
        }
    }
}
