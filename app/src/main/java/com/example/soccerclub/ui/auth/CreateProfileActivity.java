package com.example.soccerclub.ui.auth;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.example.soccerclub.R;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.ui.home.HomeActivity;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CreateProfileActivity extends AppCompatActivity {

    private EditText etNickname, etAge, etHeight, etWeight, etIntro;
    private Spinner spinnerPosition, spinnerSkill, spinnerFoot, spinnerPlayerLevel;
    private RadioGroup radioGroupPlayerType;
    private LinearLayout layoutSelectPlayerLevel;

    private ImageView imageProfile;
    private View profileClickableArea;
    private Uri selectedImageUri;

    private MaterialButton btnSaveProfile;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private StorageReference storageRef;
    private String username;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    imageProfile.setImageURI(selectedImageUri);
                }
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) openImagePicker();
                else CustomToast.warning(this, "사진 권한이 필요합니다.");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_profile);

        NestedScrollView mainRoot       = findViewById(R.id.mainRoot);
        View contentContainer           = findViewById(R.id.contentContainer);

        final int baseLeft   = contentContainer.getPaddingLeft();
        final int baseTop    = contentContainer.getPaddingTop();
        final int baseRight  = contentContainer.getPaddingRight();
        final int baseBottom = contentContainer.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(contentContainer, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(baseLeft, baseTop + sys.top, baseRight, baseBottom + Math.max(sys.bottom, ime.bottom));
            return insets;
        });

        auth       = FirebaseAuth.getInstance();
        firestore  = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();
        username   = getIntent().getStringExtra("username");

        etNickname   = findViewById(R.id.editNickname);
        etAge        = findViewById(R.id.editAge);
        etHeight     = findViewById(R.id.editHeight);
        etWeight     = findViewById(R.id.editWeight);
        etIntro      = findViewById(R.id.editIntroduction);

        spinnerPosition    = findViewById(R.id.spinnerPosition);
        spinnerSkill       = findViewById(R.id.spinnerSkill);
        spinnerFoot        = findViewById(R.id.spinnerFoot);
        spinnerPlayerLevel = findViewById(R.id.spinnerPlayerLevel);

        radioGroupPlayerType    = findViewById(R.id.radioGroupPlayerType);
        layoutSelectPlayerLevel = findViewById(R.id.layoutSelectPlayerLevel);

        imageProfile         = findViewById(R.id.imageProfile);
        profileClickableArea = findViewById(R.id.profileClickableArea);
        btnSaveProfile       = findViewById(R.id.btnSaveProfile);

        radioGroupPlayerType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isPro = checkedId == R.id.radioPro;
            layoutSelectPlayerLevel.setVisibility(isPro ? View.VISIBLE : View.GONE);
        });

        profileClickableArea.setOnClickListener(v ->
                permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES));

        btnSaveProfile.setOnClickListener(v -> saveProfile());
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void saveProfile() {
        String nickname = etNickname.getText().toString().trim();
        String ageStr   = etAge.getText().toString().trim();
        String heightStr = etHeight.getText().toString().trim();
        String weightStr = etWeight.getText().toString().trim();
        String intro    = etIntro.getText().toString().trim();

        if (nickname.isEmpty()) {
            CustomToast.warning(this, "닉네임을 입력해주세요.");
            return;
        }
        if (ageStr.isEmpty()) {
            CustomToast.warning(this, "나이를 입력해주세요.");
            return;
        }
        if (selectedImageUri == null) {
            CustomToast.info(this, "프로필 사진을 선택해주세요.");
            return;
        }

        int age    = Integer.parseInt(ageStr);
        int height = heightStr.isEmpty() ? 0 : Integer.parseInt(heightStr);
        int weight = weightStr.isEmpty() ? 0 : Integer.parseInt(weightStr);
        int skill  = Integer.parseInt(spinnerSkill.getSelectedItem().toString());

        String position   = spinnerPosition.getSelectedItem().toString();
        String foot       = spinnerFoot.getSelectedItem().toString();
        String playerType = (radioGroupPlayerType.getCheckedRadioButtonId() == R.id.radioPro) ? "선출" : "비선출";
        String playerLevel = "선출".equals(playerType) ? spinnerPlayerLevel.getSelectedItem().toString() : null;

        btnSaveProfile.setEnabled(false);

        String uid = auth.getCurrentUser().getUid();
        StorageReference imageRef = storageRef.child("profile_images/" + uid + ".jpg");

        byte[] imageBytes;
        try {
            imageBytes = compressImage(selectedImageUri);
        } catch (IOException e) {
            CustomToast.error(this, "이미지 처리 실패: " + e.getMessage());
            btnSaveProfile.setEnabled(true);
            return;
        }

        imageRef.putBytes(imageBytes)
                .addOnSuccessListener(taskSnapshot ->
                        imageRef.getDownloadUrl().addOnSuccessListener(uri ->
                                saveProfileToFirestore(uid, nickname, age, height, weight,
                                        position, skill, foot, playerType, playerLevel, intro, uri.toString())))
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "사진 업로드 실패: " + e.getMessage());
                    btnSaveProfile.setEnabled(true);
                });
    }

    private void saveProfileToFirestore(String uid, String nickname, int age, int height, int weight,
                                        String position, int skill, String foot, String playerType,
                                        String playerLevel, String intro, String imageUrl) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("username", username);
        profile.put("nickname", nickname);
        profile.put("age", age);
        profile.put("height", height);
        profile.put("weight", weight);
        profile.put("position", position);
        profile.put("skill", skill);
        profile.put("foot", foot);
        profile.put("playerType", playerType);
        if (playerLevel != null) profile.put("playerLevel", playerLevel);
        profile.put("introduction", intro);
        profile.put("myTeam", null);
        profile.put("profileImageUrl", imageUrl);

        firestore.collection("profiles").document(uid)
                .set(profile)
                .addOnSuccessListener(v -> {
                    CustomToast.success(this, "프로필 저장 완료!");
                    startActivity(new Intent(this, HomeActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "저장 실패: " + e.getMessage());
                    btnSaveProfile.setEnabled(true);
                });
    }

    private byte[] compressImage(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(inputStream);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos);
        return baos.toByteArray();
    }
}