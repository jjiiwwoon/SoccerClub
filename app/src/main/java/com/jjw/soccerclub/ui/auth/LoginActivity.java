package com.jjw.soccerclub.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.ui.home.HomeActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

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

        auth      = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

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

        firestore.collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        CustomToast.error(this, "아이디를 확인해주세요.");
                        return;
                    }
                    String email = querySnapshot.getDocuments().get(0).getString("email");
                    auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener(this, task -> {
                                if (!task.isSuccessful()) {
                                    CustomToast.error(this, "비밀번호를 확인해주세요.");
                                    return;
                                }
                                FirebaseUser user = auth.getCurrentUser();
                                if (user == null) return;

                                firestore.collection("profiles")
                                        .whereEqualTo("username", username)
                                        .limit(1)
                                        .get()
                                        .addOnSuccessListener(profileSnapshot -> {
                                            if (!profileSnapshot.isEmpty()) {
                                                startActivity(new Intent(this, HomeActivity.class));
                                                finish();
                                            } else {
                                                CustomToast.info(this, "프로필을 만들어주세요.");
                                                Intent intent = new Intent(this, CreateProfileActivity.class);
                                                intent.putExtra("username", username);
                                                startActivity(intent);
                                                finish();
                                            }
                                        })
                                        .addOnFailureListener(e ->
                                                CustomToast.error(this, "프로필 확인 중 오류 발생"));
                            });
                })
                .addOnFailureListener(e -> CustomToast.error(this, "로그인 중 오류 발생"));
    }
}