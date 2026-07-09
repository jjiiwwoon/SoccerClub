package com.jjw.soccerclub.ui.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.ui.home.HomeActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION = 1500L; // 1.5초

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this); // 반드시 super.onCreate()보다 먼저 호출
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthAndNavigate, SPLASH_DURATION);
    }

    private void checkAuthAndNavigate() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            // 로그인 안 된 상태 → 로그인 화면
            goTo(LoginActivity.class);
            return;
        }

        // 로그인 된 상태 → 프로필 존재 여부 확인
        FirebaseFirestore.getInstance()
                .collection("profiles")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // 프로필 있음 → 홈 화면
                        goTo(HomeActivity.class);
                    } else {
                        // 프로필 없음 → 프로필 생성 화면
                        goTo(CreateProfileActivity.class);
                    }
                })
                .addOnFailureListener(e -> {
                    // 네트워크 오류 시 → 로그인 화면으로 안전하게 이동
                    goTo(LoginActivity.class);
                });
    }

    private void goTo(Class<?> target) {
        Intent intent = new Intent(this, target);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}