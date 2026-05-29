package com.jjw.soccerclub.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

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

        auth      = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        etUsername = findViewById(R.id.etUsername);
        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);

        findViewById(R.id.btnRegister).setOnClickListener(v -> registerUser());
    }

    private void registerUser() {
        String username = etUsername.getText().toString().trim();
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

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

        firestore.collection("users")
                .whereEqualTo("username", username)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) {
                        CustomToast.error(this, "이미 사용 중인 사용자 이름입니다.");
                        return;
                    }
                    createNewAccount(username, email, password);
                })
                .addOnFailureListener(e ->
                        CustomToast.error(this, "중복 확인 중 오류: " + e.getMessage()));
    }

    private void createNewAccount(String username, String email, String password) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null) saveUserToFirestore(user.getUid(), username, email);
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        CustomToast.error(this, "이미 사용 중인 이메일입니다.");
                    } else {
                        CustomToast.error(this, "계정 생성 실패: " + e.getMessage());
                    }
                });
    }

    private void saveUserToFirestore(String uid, String username, String email) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", username);
        userMap.put("email", email);

        firestore.collection("users").document(uid)
                .set(userMap)
                .addOnSuccessListener(v -> {
                    CustomToast.success(this, "회원가입이 완료되었습니다.");
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        CustomToast.error(this, "정보 저장 실패: " + e.getMessage()));
    }
}