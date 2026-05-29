package com.jjw.soccerclub.ui.profile;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.bumptech.glide.Glide;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditProfileActivity extends AppCompatActivity {

    private EditText etNickname, etAge, etHeight, etWeight, etIntro;
    private Spinner spinnerPosition, spinnerSkill, spinnerFoot, spinnerPlayerLevel;
    private RadioGroup radioGroupPlayerType;
    private LinearLayout layoutSelectPlayerLevel;

    private ImageView imageProfile;
    private View profileClickableArea;
    private TextView tvEditPhotoHint;

    private MaterialButton btnSaveProfile;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private StorageReference storageRef;

    // 새로 선택한 사진 URI (null이면 기존 사진 유지)
    private Uri selectedImageUri = null;
    // 기존 프로필 이미지 URL (사진 미변경 시 사용)
    private String existingImageUrl = null;

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    imageProfile.setImageURI(selectedImageUri);
                    if (tvEditPhotoHint != null)
                        tvEditPhotoHint.setText("사진이 변경됩니다");
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
        setContentView(R.layout.activity_create_profile); // 기존 레이아웃 재사용

        // ── 인셋 처리 ──────────────────────────────────────────────────────────────
        View contentContainer = findViewById(R.id.contentContainer);
        if (contentContainer != null) {
            final int bL = contentContainer.getPaddingLeft();
            final int bT = contentContainer.getPaddingTop();
            final int bR = contentContainer.getPaddingRight();
            final int bB = contentContainer.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(contentContainer, (v, insets) -> {
                Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
                v.setPadding(bL, bT + sys.top, bR, bB + Math.max(sys.bottom, ime.bottom));
                return insets;
            });
        }

        // ── Firebase 초기화 ────────────────────────────────────────────────────────
        auth       = FirebaseAuth.getInstance();
        firestore  = FirebaseFirestore.getInstance();
        storageRef = FirebaseStorage.getInstance().getReference();

        // ── 뷰 바인딩 ─────────────────────────────────────────────────────────────
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
        tvEditPhotoHint      = findViewById(R.id.textProfileHint);

        // ── 제목 변경 ─────────────────────────────────────────────────────────────
        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setText("프로필 편집");
        btnSaveProfile.setText("저장하기");

        // ── 선출 여부 토글 ────────────────────────────────────────────────────────
        radioGroupPlayerType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isPro = (checkedId == R.id.radioPro);
            layoutSelectPlayerLevel.setVisibility(isPro ? View.VISIBLE : View.GONE);
        });

        // ── 사진 클릭 → 갤러리 ───────────────────────────────────────────────────
        if (profileClickableArea != null) {
            profileClickableArea.setOnClickListener(v ->
                    permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES));
        } else if (imageProfile != null) {
            imageProfile.setOnClickListener(v ->
                    permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES));
        }

        // ── 저장 버튼 ─────────────────────────────────────────────────────────────
        btnSaveProfile.setOnClickListener(v -> saveProfile());

        // ── 기존 데이터 로드 ──────────────────────────────────────────────────────
        loadCurrentProfile();
    }

    // ── 기존 프로필 데이터 불러와서 필드 미리 채우기 ──────────────────────────────

    private void loadCurrentProfile() {
        String uid = auth.getCurrentUser().getUid();
        btnSaveProfile.setEnabled(false);

        firestore.collection("profiles").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        btnSaveProfile.setEnabled(true);
                        return;
                    }
                    prefillFields(doc);
                    btnSaveProfile.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "프로필을 불러오지 못했어요.");
                    btnSaveProfile.setEnabled(true);
                });
    }

    private void prefillFields(DocumentSnapshot doc) {
        // 텍스트 필드
        setText(etNickname, doc.getString("nickname"));
        setText(etIntro,    doc.getString("introduction"));

        Long age    = doc.getLong("age");
        Long height = doc.getLong("height");
        Long weight = doc.getLong("weight");
        if (age    != null) etAge.setText(String.valueOf(age));
        if (height != null) etHeight.setText(String.valueOf(height));
        if (weight != null) etWeight.setText(String.valueOf(weight));

        // 스피너 선택값 복원
        setSpinner(spinnerPosition, R.array.position_array, doc.getString("position"));
        setSpinner(spinnerFoot,     R.array.foot_array,     doc.getString("foot"));

        Long skillLong = doc.getLong("skill");
        if (skillLong != null)
            setSpinner(spinnerSkill, R.array.skill_array, String.valueOf(skillLong));

        // 선출/비선출
        String playerType = doc.getString("playerType");
        if ("선출".equals(playerType)) {
            radioGroupPlayerType.check(R.id.radioPro);
            layoutSelectPlayerLevel.setVisibility(View.VISIBLE);
            setSpinner(spinnerPlayerLevel, R.array.player_level_array, doc.getString("playerLevel"));
        } else {
            radioGroupPlayerType.check(R.id.radioNonPro);
            layoutSelectPlayerLevel.setVisibility(View.GONE);
        }

        // 기존 프로필 사진 표시
        existingImageUrl = doc.getString("profileImageUrl");
        if (existingImageUrl != null && !existingImageUrl.isEmpty() && imageProfile != null) {
            Glide.with(this).load(existingImageUrl)
                    .placeholder(R.drawable.ic_person_placeholder)
                    .circleCrop()
                    .into(imageProfile);
        }
    }

    // ── 저장 로직 ─────────────────────────────────────────────────────────────────

    private void saveProfile() {
        String nickname  = etNickname.getText().toString().trim();
        String ageStr    = etAge.getText().toString().trim();
        String heightStr = etHeight.getText().toString().trim();
        String weightStr = etWeight.getText().toString().trim();
        String intro     = etIntro.getText().toString().trim();

        if (nickname.isEmpty()) {
            CustomToast.warning(this, "닉네임을 입력해주세요."); return;
        }
        if (ageStr.isEmpty()) {
            CustomToast.warning(this, "나이를 입력해주세요."); return;
        }

        int age    = Integer.parseInt(ageStr);
        int height = heightStr.isEmpty() ? 0 : Integer.parseInt(heightStr);
        int weight = weightStr.isEmpty() ? 0 : Integer.parseInt(weightStr);
        int skill  = Integer.parseInt(spinnerSkill.getSelectedItem().toString());

        String position   = spinnerPosition.getSelectedItem().toString();
        String foot       = spinnerFoot.getSelectedItem().toString();
        String playerType = (radioGroupPlayerType.getCheckedRadioButtonId() == R.id.radioPro)
                ? "선출" : "비선출";
        String playerLevel = "선출".equals(playerType)
                ? spinnerPlayerLevel.getSelectedItem().toString() : null;

        btnSaveProfile.setEnabled(false);
        btnSaveProfile.setText("저장 중...");

        // 사진이 새로 선택됐으면 업로드 후 저장, 아니면 바로 저장
        if (selectedImageUri != null) {
            uploadImageAndSave(nickname, age, height, weight, position, skill,
                    foot, playerType, playerLevel, intro);
        } else {
            // 기존 이미지 URL 그대로 사용
            saveToFirestore(nickname, age, height, weight, position, skill,
                    foot, playerType, playerLevel, intro, existingImageUrl);
        }
    }

    private void uploadImageAndSave(String nickname, int age, int height, int weight,
                                    String position, int skill, String foot,
                                    String playerType, String playerLevel, String intro) {
        String uid = auth.getCurrentUser().getUid();
        StorageReference imageRef = storageRef.child("profile_images/" + uid + ".jpg");

        // ✅ 이미지 압축을 백그라운드 스레드에서 처리
        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> {
            try {
                byte[] bytes = compressImage(selectedImageUri);
                runOnUiThread(() ->
                        imageRef.putBytes(bytes)
                                .addOnSuccessListener(t -> imageRef.getDownloadUrl()
                                        .addOnSuccessListener(uri ->
                                                saveToFirestore(nickname, age, height, weight, position,
                                                        skill, foot, playerType, playerLevel, intro,
                                                        uri.toString())))
                                .addOnFailureListener(e -> {
                                    CustomToast.error(this, "사진 업로드 실패: " + e.getMessage());
                                    resetSaveButton();
                                })
                );
            } catch (IOException e) {
                runOnUiThread(() -> {
                    CustomToast.error(this, "이미지 처리 실패: " + e.getMessage());
                    resetSaveButton();
                });
            }
        });
    }

    private void saveToFirestore(String nickname, int age, int height, int weight,
                                 String position, int skill, String foot,
                                 String playerType, String playerLevel, String intro,
                                 String imageUrl) {
        String uid = auth.getCurrentUser().getUid();

        // ✅ set() 대신 update() — myTeam, username 등 기존 필드 유지
        Map<String, Object> updates = new HashMap<>();
        updates.put("nickname",     nickname);
        updates.put("age",          age);
        updates.put("height",       height);
        updates.put("weight",       weight);
        updates.put("position",     position);
        updates.put("skill",        skill);
        updates.put("foot",         foot);
        updates.put("playerType",   playerType);
        updates.put("introduction", intro);
        if (playerLevel != null) updates.put("playerLevel", playerLevel);
        if (imageUrl    != null) updates.put("profileImageUrl", imageUrl);

        firestore.collection("profiles").document(uid)
                .update(updates)
                .addOnSuccessListener(v -> {
                    CustomToast.success(this, "프로필이 수정됐어요!");
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "저장 실패: " + e.getMessage());
                    resetSaveButton();
                });
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void resetSaveButton() {
        btnSaveProfile.setEnabled(true);
        btnSaveProfile.setText("저장하기");
    }

    private void setText(EditText et, String value) {
        if (et != null && value != null) et.setText(value);
    }

    private void setSpinner(Spinner spinner, int arrayResId, String value) {
        if (spinner == null || value == null) return;
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, arrayResId, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).toString().equals(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private byte[] compressImage(Uri uri) throws IOException {
        InputStream is = getContentResolver().openInputStream(uri);
        Bitmap bitmap  = BitmapFactory.decodeStream(is);
        // 최대 720px로 리사이즈
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int longer = Math.max(w, h);
        float scale = longer > 720 ? 720f / longer : 1f;
        if (scale < 1f)
            bitmap = Bitmap.createScaledBitmap(bitmap,
                    Math.round(w * scale), Math.round(h * scale), true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        return baos.toByteArray();
    }
}
