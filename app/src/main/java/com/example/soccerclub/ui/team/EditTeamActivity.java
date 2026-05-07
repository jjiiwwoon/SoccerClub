package com.example.soccerclub.ui.team;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;

import com.bumptech.glide.Glide;
import com.example.soccerclub.R;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.ui.common.StadiumSearchActivity;
import com.example.soccerclub.util.AppUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditTeamActivity extends AppCompatActivity {

    private ImageView imageTeamLogo;
    private Button btnSelectLogo, btnSaveTeam, btnSearchStadium;
    private EditText editTeamName, editTeamIntro, editStadiumName;
    private TextView editStadium;
    private Spinner spinnerCity, spinnerDistrict, spinnerAgeStart, spinnerAgeEnd;

    // 새로 선택한 로고 URI (null이면 기존 유지)
    private Uri selectedImageUri = null;
    private String existingLogoUrl = "";
    private String selectedAddress = "";
    private String selectedPlaceName = "";
    private String teamId = "";

    private FirebaseStorage storage;
    private StorageReference storageRef;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    private Map<String, List<String>> districtMap;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openImagePicker();
                else CustomToast.warning(this, "사진 권한이 필요합니다.");
            });

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    selectedImageUri = r.getData().getData();
                    if (selectedImageUri != null)
                        imageTeamLogo.setImageURI(selectedImageUri);
                }
            });

    private final ActivityResultLauncher<Intent> stadiumSearchLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    Intent data = r.getData();
                    String addr = data.getStringExtra("address");
                    if (AppUtils.isEmpty(addr)) addr = data.getStringExtra("stadium_address");
                    String name = data.getStringExtra("stadium_name");
                    if (!AppUtils.isEmpty(addr)) {
                        selectedAddress = addr;
                        editStadium.setText(addr);
                        editStadium.setTextColor(0xFF333333);
                    }
                    if (!AppUtils.isEmpty(name)) {
                        selectedPlaceName = name;
                        if (editStadiumName != null && AppUtils.isEmpty(editStadiumName.getText().toString()))
                            editStadiumName.setText(name);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ✅ CreateTeamActivity 레이아웃 재사용
        setContentView(R.layout.activity_create_team);

        teamId = getIntent().getStringExtra("teamId");
        if (AppUtils.isEmpty(teamId)) {
            CustomToast.error(this, "팀 정보를 불러올 수 없어요.");
            finish();
            return;
        }

        bindViews();
        initFirebase();
        initDistrictMap();
        setupSpinners();

        // ✅ 제목 변경
        TextView tvTitle = findViewById(R.id.tvTitle);
        if (tvTitle != null) tvTitle.setText("팀 정보 수정");
        btnSaveTeam.setText("저장하기");

        editTeamIntro.setVerticalScrollBarEnabled(true);
        editTeamIntro.setMovementMethod(new ScrollingMovementMethod());
        editTeamIntro.setOnTouchListener((v, e) -> {
            if (v.hasFocus()) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if (e.getAction() == MotionEvent.ACTION_UP
                        || e.getAction() == MotionEvent.ACTION_CANCEL)
                    v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });

        btnSelectLogo.setOnClickListener(v ->
                permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES));
        btnSearchStadium.setOnClickListener(v ->
                stadiumSearchLauncher.launch(new Intent(this, StadiumSearchActivity.class)));
        btnSaveTeam.setOnClickListener(v -> saveTeam());

        // ✅ 기존 팀 데이터 불러와서 필드 채우기
        loadCurrentTeamData();
    }

    // ── 기존 팀 데이터 로드 ───────────────────────────────────────────────────────

    private void loadCurrentTeamData() {
        btnSaveTeam.setEnabled(false);
        firestore.collection("teams").document(teamId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        CustomToast.error(this, "팀 정보를 찾을 수 없어요.");
                        finish();
                        return;
                    }
                    prefillFields(doc);
                    btnSaveTeam.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "팀 정보를 불러오지 못했어요.");
                    btnSaveTeam.setEnabled(true);
                });
    }

    private void prefillFields(DocumentSnapshot doc) {
        // 텍스트 필드
        String name  = doc.getString("teamName");
        String intro = doc.getString("intro");
        String addr  = doc.getString("stadium");
        String stadName = doc.getString("homeStadiumName");

        if (!AppUtils.isEmpty(name))     editTeamName.setText(name);
        if (!AppUtils.isEmpty(intro))    editTeamIntro.setText(intro);
        if (!AppUtils.isEmpty(addr))     { editStadium.setText(addr); selectedAddress = addr; }
        if (!AppUtils.isEmpty(stadName) && editStadiumName != null)
            editStadiumName.setText(stadName);

        // 기존 로고 표시
        existingLogoUrl = AppUtils.safe(doc.getString("logoUrl"));
        if (!AppUtils.isEmpty(existingLogoUrl)) {
            Glide.with(this).load(existingLogoUrl).circleCrop().into(imageTeamLogo);
        }

        // 지역 스피너 복원
        String region = AppUtils.safe(doc.getString("region"));
        if (!region.isEmpty()) {
            String[] parts = region.split(" ", 2);
            setSpinnerSelection(spinnerCity,     parts[0]);
            if (parts.length > 1) setSpinnerSelection(spinnerDistrict, parts[1]);
        }

        // 나이대 스피너
        String ageRange = AppUtils.safe(doc.getString("ageRange"));
        if (!ageRange.isEmpty()) {
            String[] ages = ageRange.split("~");
            setSpinnerSelection(spinnerAgeStart, ages[0].trim());
            if (ages.length > 1) setSpinnerSelection(spinnerAgeEnd, ages[1].trim());
        }

        // 활동 요일/시간
        Spinner spDay   = findViewById(R.id.spinnerActivityDay);
        Spinner spStart = findViewById(R.id.spinnerTimeStart);
        Spinner spEnd   = findViewById(R.id.spinnerTimeEnd);
        if (spDay   != null) setSpinnerSelection(spDay,   doc.getString("activityDay"));
        if (spStart != null) setSpinnerSelection(spStart, doc.getString("timeStart"));
        if (spEnd   != null) setSpinnerSelection(spEnd,   doc.getString("timeEnd"));
    }

    // ── 저장 ─────────────────────────────────────────────────────────────────────

    private void saveTeam() {
        String teamName        = editTeamName.getText().toString().trim();
        String intro           = editTeamIntro.getText().toString().trim();
        String stadiumAddress  = AppUtils.isEmpty(selectedAddress)
                ? editStadium.getText().toString().trim() : selectedAddress;
        String homeStadiumName = editStadiumName != null
                ? editStadiumName.getText().toString().trim() : "";
        String finalStadiumName = AppUtils.isEmpty(homeStadiumName) ? selectedPlaceName : homeStadiumName;

        if (AppUtils.isEmpty(teamName)) {
            CustomToast.warning(this, "팀 이름을 입력해 주세요.");
            return;
        }

        String city     = getItem(spinnerCity);
        String district = getItem(spinnerDistrict);
        String ageStart = getItem(spinnerAgeStart);
        String ageEnd   = getItem(spinnerAgeEnd);
        String ageRange = joinRange(ageStart, ageEnd);
        String region   = (city + " " + district).trim();

        Spinner spDay   = findViewById(R.id.spinnerActivityDay);
        Spinner spStart = findViewById(R.id.spinnerTimeStart);
        Spinner spEnd   = findViewById(R.id.spinnerTimeEnd);
        String activityDay = sel(spDay, "요일");
        String timeStart   = sel(spStart, "시작");
        String timeEnd     = sel(spEnd, "종료");

        // ✅ update() 사용 — members, skillAverage 등 기존 필드 유지
        Map<String, Object> updates = new HashMap<>();
        updates.put("teamName", teamName);
        updates.put("region",   region);
        updates.put("ageRange", ageRange);
        updates.put("intro",    intro);
        updates.put("updateAt", Timestamp.now());
        if (!AppUtils.isEmpty(stadiumAddress))   updates.put("stadium", stadiumAddress);
        if (!AppUtils.isEmpty(finalStadiumName)) updates.put("homeStadiumName", finalStadiumName);
        if (!AppUtils.isEmpty(activityDay)) updates.put("activityDay", activityDay);
        if (!AppUtils.isEmpty(timeStart))   updates.put("timeStart", timeStart);
        if (!AppUtils.isEmpty(timeEnd))     updates.put("timeEnd", timeEnd);

        btnSaveTeam.setEnabled(false);
        btnSaveTeam.setText("저장 중...");

        if (selectedImageUri != null) {
            // 새 로고 업로드 후 저장
            uploadLogoAndSave(updates);
        } else {
            // 기존 로고 유지, 바로 저장
            firestore.collection("teams").document(teamId).update(updates)
                    .addOnSuccessListener(v -> {
                        CustomToast.success(this, "팀 정보가 수정됐어요!");
                        setResult(RESULT_OK);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        CustomToast.error(this, "저장 실패: " + e.getMessage());
                        resetSaveButton();
                    });
        }
    }

    private void uploadLogoAndSave(Map<String, Object> updates) {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        exec.execute(() -> {
            try {
                InputStream is = getContentResolver().openInputStream(selectedImageUri);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
                byte[] bytes = baos.toByteArray();

                storageRef.child("team_logos/" + teamId + ".jpg")
                        .putBytes(bytes)
                        .addOnSuccessListener(t -> t.getStorage().getDownloadUrl()
                                .addOnSuccessListener(uri -> {
                                    updates.put("logoUrl",       uri.toString());
                                    updates.put("logoStatus",    "ready");
                                    updates.put("logoUpdatedAt", Timestamp.now());
                                    firestore.collection("teams").document(teamId)
                                            .update(updates)
                                            .addOnSuccessListener(v -> {
                                                CustomToast.success(this, "팀 정보가 수정됐어요!");
                                                setResult(RESULT_OK);
                                                finish();
                                            })
                                            .addOnFailureListener(e -> {
                                                CustomToast.error(this, "저장 실패: " + e.getMessage());
                                                resetSaveButton();
                                            });
                                }))
                        .addOnFailureListener(e -> {
                            CustomToast.error(this, "로고 업로드 실패: " + e.getMessage());
                            resetSaveButton();
                        });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    CustomToast.error(this, "이미지 처리 실패: " + e.getMessage());
                    resetSaveButton();
                });
            }
        });
    }

    private void resetSaveButton() {
        btnSaveTeam.setEnabled(true);
        btnSaveTeam.setText("저장하기");
    }

    // ── 기존 CreateTeamActivity 헬퍼 그대로 ──────────────────────────────────────

    private void bindViews() {
        imageTeamLogo    = findViewById(R.id.imageTeamLogo);
        btnSelectLogo    = findViewById(R.id.btnSelectLogo);
        btnSaveTeam      = findViewById(R.id.btnSaveTeam);
        editTeamName     = findViewById(R.id.editTeamName);
        editTeamIntro    = findViewById(R.id.editTeamIntro);
        editStadium      = findViewById(R.id.editStadium);
        editStadiumName  = findViewById(R.id.editStadiumName);
        btnSearchStadium = findViewById(R.id.btnSearchStadium);
        spinnerCity      = findViewById(R.id.spinnerCity);
        spinnerDistrict  = findViewById(R.id.spinnerDistrict);
        spinnerAgeStart  = findViewById(R.id.spinnerAgeStart);
        spinnerAgeEnd    = findViewById(R.id.spinnerAgeEnd);
    }

    private void initFirebase() {
        storage    = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
        firestore  = FirebaseFirestore.getInstance();
        auth       = FirebaseAuth.getInstance();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void setupSpinners() {
        setSpinnerWithHint(spinnerAgeStart, R.array.age_array, "시작나이");
        setSpinnerWithHint(spinnerAgeEnd,   R.array.age_array, "끝나이");

        List<String> cities = arrayToList(R.array.city_array);
        spinnerCity.setAdapter(makeAdapter(cities, "시/도"));
        spinnerCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String selected = cities.get(pos);
                List<String> districts = districtMap.getOrDefault(
                        selected, Collections.singletonList("전체"));
                spinnerDistrict.setAdapter(makeAdapter(districts, "시/군/구"));
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        Spinner spDay   = findViewById(R.id.spinnerActivityDay);
        Spinner spStart = findViewById(R.id.spinnerTimeStart);
        Spinner spEnd   = findViewById(R.id.spinnerTimeEnd);
        if (spDay   != null) setSpinnerWithHint(spDay,   R.array.day_array,  "요일");
        if (spStart != null) setSpinnerWithHint(spStart, R.array.time_array, "시작");
        if (spEnd   != null) setSpinnerWithHint(spEnd,   R.array.time_array, "종료");
    }

    private void setSpinnerWithHint(Spinner spinner, int arrayRes, String hint) {
        List<String> items = new ArrayList<>();
        items.add(hint);
        items.addAll(arrayToList(arrayRes));
        spinner.setAdapter(makeAdapter(items, hint));
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        if (spinner == null || AppUtils.isEmpty(value)) return;
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        if (adapter == null) return;
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).toString().equals(value)) {
                spinner.setSelection(i);
                break;
            }
        }
    }

    private ArrayAdapter<String> makeAdapter(List<String> items, String hint) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private List<String> arrayToList(int arrayRes) {
        return new ArrayList<>(Arrays.asList(getResources().getStringArray(arrayRes)));
    }

    private String getItem(Spinner spinner) {
        if (spinner == null || spinner.getSelectedItem() == null) return "";
        String s = spinner.getSelectedItem().toString();
        return (s.equals("시/도") || s.equals("시/군/구")
                || s.equals("시작나이") || s.equals("끝나이")) ? "" : s;
    }

    private String sel(Spinner sp, String hint) {
        if (sp == null || sp.getSelectedItem() == null) return "";
        String s = sp.getSelectedItem().toString();
        return s.equals(hint) ? "" : s;
    }

    private String joinRange(String start, String end) {
        if (AppUtils.isEmpty(start) && AppUtils.isEmpty(end)) return "";
        if (AppUtils.isEmpty(start)) return end;
        if (AppUtils.isEmpty(end))   return start;
        return start + " ~ " + end;
    }

    private void initDistrictMap() {
        districtMap = new HashMap<>();
        String[][] data = getResources().getStringArray(R.array.city_array) != null
                ? new String[0][] : new String[0][];
        // 실제 districtMap은 CreateTeamActivity와 동일하게 초기화
        // (기존 CreateTeamActivity의 initDistrictMap() 내용을 그대로 복사)
        districtMap.put("서울", Arrays.asList("전체","강남구","강동구","강북구","강서구","관악구","광진구","구로구","금천구","노원구","도봉구","동대문구","동작구","마포구","서대문구","서초구","성동구","성북구","송파구","양천구","영등포구","용산구","은평구","종로구","중구","중랑구"));
        districtMap.put("부산", Arrays.asList("전체","강서구","금정구","기장군","남구","동구","동래구","부산진구","북구","사상구","사하구","서구","수영구","연제구","영도구","중구","해운대구"));
        districtMap.put("인천", Arrays.asList("전체","강화군","계양구","남동구","동구","미추홀구","부평구","서구","연수구","옹진군","중구"));
        districtMap.put("대구", Arrays.asList("전체","군위군","남구","달서구","달성군","동구","북구","서구","수성구","중구"));
        districtMap.put("대전", Arrays.asList("전체","대덕구","동구","서구","유성구","중구"));
        districtMap.put("광주", Arrays.asList("전체","광산구","남구","동구","북구","서구"));
        districtMap.put("울산", Arrays.asList("전체","남구","동구","북구","울주군","중구"));
        districtMap.put("세종", Collections.singletonList("전체"));
        districtMap.put("경기", Arrays.asList("전체","가평군","고양시","과천시","광명시","광주시","구리시","군포시","김포시","남양주시","동두천시","부천시","성남시","수원시","시흥시","안산시","안성시","안양시","양주시","양평군","여주시","연천군","오산시","용인시","의왕시","의정부시","이천시","파주시","평택시","포천시","하남시","화성시"));
        districtMap.put("강원", Arrays.asList("전체","강릉시","고성군","동해시","삼척시","속초시","양구군","양양군","영월군","원주시","인제군","정선군","철원군","춘천시","태백시","평창군","홍천군","화천군","횡성군"));
        districtMap.put("충북", Arrays.asList("전체","괴산군","단양군","보은군","영동군","옥천군","음성군","제천시","증평군","진천군","청주시","충주시"));
        districtMap.put("충남", Arrays.asList("전체","계룡시","공주시","금산군","논산시","당진시","보령시","부여군","서산시","서천군","아산시","예산군","천안시","청양군","태안군","홍성군"));
        districtMap.put("전북", Arrays.asList("전체","고창군","군산시","김제시","남원시","무주군","부안군","순창군","완주군","익산시","임실군","장수군","전주시","정읍시","진안군"));
        districtMap.put("전남", Arrays.asList("전체","강진군","고흥군","곡성군","광양시","구례군","나주시","담양군","목포시","무안군","보성군","순천시","신안군","여수시","영광군","영암군","완도군","장성군","장흥군","진도군","함평군","해남군","화순군"));
        districtMap.put("경북", Arrays.asList("전체","경산시","경주시","고령군","구미시","군위군","김천시","문경시","봉화군","상주시","성주군","안동시","영덕군","영양군","영주시","영천시","예천군","울릉군","울진군","의성군","청도군","청송군","칠곡군","포항시"));
        districtMap.put("경남", Arrays.asList("전체","거제시","거창군","고성군","김해시","남해군","밀양시","사천시","산청군","양산시","의령군","진주시","창녕군","창원시","통영시","하동군","함안군","함양군","합천군"));
        districtMap.put("제주", Arrays.asList("전체","서귀포시","제주시"));
    }
}