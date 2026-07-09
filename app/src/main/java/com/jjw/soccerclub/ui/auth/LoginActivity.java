package com.jjw.soccerclub.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.repository.AuthRepository;
import com.jjw.soccerclub.ui.home.HomeActivity;

/**
 * 로그인 화면.
 *
 * Firestore/Auth 호출은 모두 AuthRepository 가 담당하고,
 * 이 Activity 는 입력 검증 / 화면 전환 / 사용자 안내만 처리한다.
 *
 * 프로젝트 공통 규칙(상세/작성/인증 화면 = Repository 콜백 분리)에 따라
 * ViewModel 은 적용하지 않는다 — 회전 시 유지할 상태가 없는 일회성 요청 화면.
 */
public class LoginActivity extends AppCompatActivity {

    private final AuthRepository authRepository = new AuthRepository();

    private EditText etUsername, etPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);

        findViewById(R.id.btnLogin).setOnClickListener(v -> loginUser());
        findViewById(R.id.btnRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void loginUser() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            CustomToast.warning(this, "아이디와 비밀번호를 입력해주세요.");
            return;
        }

        authRepository.login(username, password, new AuthRepository.LoginCallback() {
            @Override
            public void onSuccess(String uid, boolean hasProfile) {
                if (hasProfile) {
                    startActivity(new Intent(LoginActivity.this, HomeActivity.class));
                } else {
                    Intent intent = new Intent(LoginActivity.this, CreateProfileActivity.class);
                    intent.putExtra("username", username);
                    startActivity(intent);
                }
                finish();
            }

            @Override
            public void onError(AuthRepository.LoginError error) {
                switch (error) {
                    case USERNAME_NOT_FOUND:
                        CustomToast.error(LoginActivity.this, "아이디를 확인해주세요.");
                        break;
                    case WRONG_PASSWORD:
                        CustomToast.error(LoginActivity.this, "비밀번호를 확인해주세요.");
                        break;
                    case PROFILE_CHECK_FAILED:
                        CustomToast.error(LoginActivity.this, "프로필 확인 중 오류 발생");
                        break;
                    default:
                        CustomToast.error(LoginActivity.this, "로그인 중 오류 발생");
                        break;
                }
            }
        });
    }
}