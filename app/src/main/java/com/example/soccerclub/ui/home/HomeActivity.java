package com.example.soccerclub.ui.home;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
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
        for (MaterialButton btn : buttons) {
            if (btn == selected) {
                btn.setTextColor(Color.parseColor("#42A5F5"));
                btn.setIconTint(ColorStateList.valueOf(Color.parseColor("#42A5F5")));
            } else {
                btn.setTextColor(Color.parseColor("#000000"));
                btn.setIconTint(ColorStateList.valueOf(Color.parseColor("#000000")));
            }
        }
    }
}