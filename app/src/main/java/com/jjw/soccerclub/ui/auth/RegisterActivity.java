package com.jjw.soccerclub.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.repository.AuthRepository;

/**
 * 회원가입 화면.
 *
 * Firestore/Auth 호출은 모두 AuthRepository 가 담당하고,
 * 이 Activity 는 입력 검증 / 화면 전환 / 사용자 안내만 처리한다.
 *
 * 프로젝트 공통 규칙(상세/작성/인증 화면 = Repository 콜백 분리)에 따라
 * ViewModel 은 적용하지 않는다 — 회전 시 유지할 상태가 없는 일회성 요청 화면.
 */
public class RegisterActivity extends AppCompatActivity {

    private final AuthRepository authRepository = new AuthRepository();

    private EditText etUsername, etEmail, etPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        etUsername = findViewById(R.id.etUsername);
        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);

        findViewById(R.id.btnRegister).setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String username = etUsername.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // ── 입력 검증 (Activity 책임) ─────────────────────────────────────────────
        if (username.isEmpty()) {
            CustomToast.warning(this, "아이디를 입력해주세요.");
            return;
        }
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            CustomToast.warning(this, "올바른 이메일 형식이 아닙니다.");
            return;
        }
        if (password.length() < 6) {
            CustomToast.warning(this, "비밀번호는 6자 이상이어야 합니다.");
            return;
        }

        // ── 회원가입 요청 (Repository 위임) ──────────────────────────────────────
        authRepository.register(username, email, password,
                new AuthRepository.RegisterCallback() {
                    @Override
                    public void onSuccess() {
                        // 비동기 응답 도착 시점에 화면이 이미 닫혔으면 무시
                        if (isFinishing() || isDestroyed()) return;

                        CustomToast.success(RegisterActivity.this, "회원가입이 완료되었습니다.");
                        startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                        finish();
                    }

                    @Override
                    public void onError(AuthRepository.RegisterError error, String detail) {
                        if (isFinishing() || isDestroyed()) return;

                        switch (error) {
                            case USERNAME_TAKEN:
                                CustomToast.error(RegisterActivity.this,
                                        "이미 사용 중인 사용자 이름입니다.");
                                break;
                            case DUPLICATE_CHECK_FAILED:
                                CustomToast.error(RegisterActivity.this,
                                        "중복 확인 중 오류: " + detail);
                                break;
                            case EMAIL_TAKEN:
                                CustomToast.error(RegisterActivity.this,
                                        "이미 사용 중인 이메일입니다.");
                                break;
                            case ACCOUNT_CREATE_FAILED:
                                CustomToast.error(RegisterActivity.this,
                                        "계정 생성 실패: " + detail);
                                break;
                            case SAVE_FAILED:
                                CustomToast.error(RegisterActivity.this,
                                        "정보 저장 실패: " + detail);
                                break;
                        }
                    }
                });
    }
}