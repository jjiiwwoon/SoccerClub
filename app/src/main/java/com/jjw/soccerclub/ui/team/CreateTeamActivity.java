package com.jjw.soccerclub.ui.team;

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

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.ui.common.StadiumSearchActivity;
import com.jjw.soccerclub.util.AppUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
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

public class CreateTeamActivity extends AppCompatActivity {

    private ImageView imageTeamLogo;
    private Button btnSearchStadium, btnSelectLogo, btnSaveTeam;
    private EditText editTeamName, editTeamIntro, editStadiumName;
    private TextView editStadium;
    private Spinner spinnerCity, spinnerDistrict, spinnerAgeStart, spinnerAgeEnd;

    private Uri selectedImageUri;
    private String selectedAddress = "";
    private String selectedPlaceName = "";

    private FirebaseStorage storage;
    private StorageReference storageRef;
    private FirebaseFirestore firestore;
    private FirebaseAuth auth;

    private Map<String, List<String>> districtMap;

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
                    } else {
                        CustomToast.warning(this, "주소를 불러오지 못했어요.");
                    }
                    if (!AppUtils.isEmpty(name)) {
                        selectedPlaceName = name;
                        if (editStadiumName != null
                                && AppUtils.isEmpty(editStadiumName.getText().toString())) {
                            editStadiumName.setText(name);
                        }
                    }
                }
            });

    private final ActivityResultLauncher<Intent> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    selectedImageUri = r.getData().getData();
                    imageTeamLogo.setImageURI(selectedImageUri);
                }
            });

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openImagePicker();
                else CustomToast.warning(this, "사진 권한이 필요합니다.");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_team);

        bindViews();
        initFirebase();
        initDistrictMap();
        setupSpinners();

        editTeamIntro.setVerticalScrollBarEnabled(true);
        editTeamIntro.setMovementMethod(new ScrollingMovementMethod());
        editTeamIntro.setOnTouchListener((v, e) -> {
            if (v.hasFocus()) {
                v.getParent().requestDisallowInterceptTouchEvent(true);
                if (e.getAction() == MotionEvent.ACTION_UP
                        || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
            }
            return false;
        });

        btnSelectLogo.setOnClickListener(v -> checkAndRequestPermission());
        btnSearchStadium.setOnClickListener(v ->
                stadiumSearchLauncher.launch(new Intent(this, StadiumSearchActivity.class)));
        btnSaveTeam.setOnClickListener(v -> saveTeam());
    }

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

    private void checkAndRequestPermission() {
        permissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void saveTeam() {
        String teamName       = editTeamName.getText().toString().trim();
        String intro          = editTeamIntro.getText().toString().trim();
        String stadiumAddress = AppUtils.isEmpty(selectedAddress)
                ? editStadium.getText().toString().trim() : selectedAddress;
        String homeStadiumName = editStadiumName != null
                ? editStadiumName.getText().toString().trim() : "";
        String finalStadiumName = AppUtils.isEmpty(homeStadiumName) ? selectedPlaceName : homeStadiumName;

        if (AppUtils.isEmpty(teamName)) {
            CustomToast.warning(this, "팀 이름을 입력해 주세요.");
            return;
        }

        String city      = getItem(spinnerCity);
        String district  = getItem(spinnerDistrict);
        String ageStart  = getItem(spinnerAgeStart);
        String ageEnd    = getItem(spinnerAgeEnd);
        String ageRange  = joinRange(ageStart, ageEnd);
        String region    = (city + " " + district).trim();

        Spinner spDay   = findViewById(R.id.spinnerActivityDay);
        Spinner spStart = findViewById(R.id.spinnerTimeStart);
        Spinner spEnd   = findViewById(R.id.spinnerTimeEnd);
        String activityDay = sel(spDay, "요일");
        String timeStart   = sel(spStart, "시작");
        String timeEnd     = sel(spEnd, "종료");

        String uid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        if (AppUtils.isEmpty(uid)) {
            CustomToast.error(this, "로그인이 필요합니다.");
            return;
        }

        firestore.collection("profiles").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!AppUtils.isEmpty(doc.getString("myTeam"))) {
                        CustomToast.info(this, "이미 다른 팀에 소속되어 있습니다.");
                        return;
                    }

                    long initSkill = AppUtils.safeLong(doc.getLong("skill"), 0L);

                    Map<String, Object> team = new HashMap<>();
                    team.put("teamName", teamName);
                    team.put("region", region);
                    team.put("ageRange", ageRange);
                    team.put("intro", intro);
                    if (!AppUtils.isEmpty(stadiumAddress)) team.put("stadium", stadiumAddress);
                    if (!AppUtils.isEmpty(finalStadiumName)) team.put("homeStadiumName", finalStadiumName);
                    team.put("captainUID", uid);
                    team.put("viceCaptainUID", "");
                    team.put("members", Collections.singletonList(uid));
                    if (!AppUtils.isEmpty(activityDay)) team.put("activityDay", activityDay);
                    if (!AppUtils.isEmpty(timeStart)) team.put("timeStart", timeStart);
                    if (!AppUtils.isEmpty(timeEnd)) team.put("timeEnd", timeEnd);
                    team.put("skillAverage", (int) initSkill);
                    team.put("skillSum", initSkill);
                    team.put("memberCount", 1L);
                    team.put("updateAt", Timestamp.now());
                    team.put("logoStatus", selectedImageUri != null ? "uploading" : "none");
                    team.put("logoUrl", "");

                    firestore.collection("teams").add(team)
                            .addOnSuccessListener(ref -> {
                                String teamId = ref.getId();
                                firestore.collection("profiles").document(uid)
                                        .update("myTeam", teamId)
                                        .addOnSuccessListener(v -> {
                                            if (selectedImageUri != null) {
                                                uploadLogo(ref, selectedImageUri);
                                            } else {
                                                CustomToast.success(this, "팀이 생성되었습니다!");
                                                finish();
                                            }
                                        });
                            })
                            .addOnFailureListener(e ->
                                    CustomToast.error(this, "팀 저장 실패: " + e.getMessage()));
                })
                .addOnFailureListener(e ->
                        CustomToast.error(this, "사용자 정보 로드 실패"));
    }

    private void uploadLogo(com.google.firebase.firestore.DocumentReference ref, Uri imageUri) {
        try {
            InputStream is = getContentResolver().openInputStream(imageUri);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
            byte[] bytes = baos.toByteArray();

            storageRef.child("team_logos/" + ref.getId() + ".jpg")
                    .putBytes(bytes)
                    .addOnSuccessListener(t -> t.getStorage().getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                Map<String, Object> upd = new HashMap<>();
                                upd.put("logoUrl", uri.toString());
                                upd.put("logoStatus", "ready");
                                upd.put("logoUpdatedAt", Timestamp.now());
                                ref.update(upd)
                                        .addOnSuccessListener(v -> {
                                            CustomToast.success(this, "팀이 생성되었습니다!");
                                            finish();
                                        });
                            }))
                    .addOnFailureListener(e -> {
                        ref.update("logoStatus", "failed");
                        CustomToast.error(this, "로고 업로드 실패: " + e.getMessage());
                    });
        } catch (IOException e) {
            CustomToast.error(this, "이미지 처리 실패: " + e.getMessage());
        }
    }

    private void setupSpinners() {
        setSpinnerWithHint(spinnerAgeStart, R.array.age_array, "시작나이");
        setSpinnerWithHint(spinnerAgeEnd, R.array.age_array, "끝나이");

        List<String> cities = arrayToList(R.array.city_array);
        spinnerCity.setAdapter(makeAdapter(cities, "시/도"));
        spinnerCity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String selected = cities.get(pos);
                List<String> districts = districtMap.getOrDefault(selected, Collections.singletonList("전체"));
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
        return (s.equals("시/도") || s.equals("시/군/구") || s.equals("시작나이") || s.equals("끝나이")) ? "" : s;
    }

    private String sel(Spinner sp, String hint) {
        if (sp == null || sp.getSelectedItem() == null) return "";
        String s = sp.getSelectedItem().toString();
        return s.equals(hint) ? "" : s;
    }

    private String joinRange(String start, String end) {
        if (AppUtils.isEmpty(start) && AppUtils.isEmpty(end)) return "";
        if (AppUtils.isEmpty(start)) return end;
        if (AppUtils.isEmpty(end)) return start;
        return start + "~" + end;
    }

    private void initDistrictMap() {
        districtMap = new HashMap<>();
        districtMap.put("서울", Arrays.asList("전체","강남구","강동구","강북구","강서구","관악구","광진구","구로구","금천구","노원구","도봉구","동대문구","동작구","마포구","서대문구","서초구","성동구","성북구","송파구","양천구","영등포구","용산구","은평구","종로구","중구","중랑구"));
        districtMap.put("부산", Arrays.asList("전체","강서구","금정구","기장군","남구","동구","동래구","부산진구","북구","사상구","사하구","서구","수영구","연제구","영도구","중구","해운대구"));
        districtMap.put("대구", Arrays.asList("전체","남구","달서구","달성군","동구","북구","서구","수성구","중구"));
        districtMap.put("인천", Arrays.asList("전체","강화군","계양구","남동구","동구","미추홀구","부평구","서구","연수구","옹진군","중구"));
        districtMap.put("광주", Arrays.asList("전체","광산구","남구","동구","북구","서구"));
        districtMap.put("대전", Arrays.asList("전체","대덕구","동구","서구","유성구","중구"));
        districtMap.put("울산", Arrays.asList("전체","남구","동구","북구","중구","울주군"));
        districtMap.put("세종", Arrays.asList("전체","세종시"));
        districtMap.put("경기도", Arrays.asList("전체","가평군","고양시","과천시","광명시","광주시","구리시","군포시","김포시","남양주시","동두천시","부천시","성남시","수원시","시흥시","안산시","안성시","안양시","양주시","양평군","여주시","연천군","오산시","용인시","의왕시","의정부시","이천시","파주시","평택시","포천시","하남시","화성시"));
        districtMap.put("강원도", Arrays.asList("전체","강릉시","고성군","동해시","삼척시","속초시","양구군","양양군","영월군","원주시","인제군","정선군","철원군","춘천시","태백시","평창군","홍천군","화천군","횡성군"));
        districtMap.put("충북", Arrays.asList("전체","괴산군","단양군","보은군","영동군","옥천군","음성군","제천시","증평군","진천군","청주시","충주시"));
        districtMap.put("충남", Arrays.asList("전체","계룡시","공주시","금산군","논산시","당진시","보령시","부여군","서산시","서천군","아산시","예산군","천안시","청양군","태안군","홍성군"));
        districtMap.put("전북", Arrays.asList("전체","고창군","군산시","김제시","남원시","무주군","부안군","순창군","완주군","익산시","임실군","장수군","전주시","정읍시","진안군"));
        districtMap.put("전남", Arrays.asList("전체","강진군","고흥군","곡성군","광양시","구례군","나주시","담양군","목포시","무안군","보성군","순천시","신안군","여수시","영광군","영암군","완도군","장성군","장흥군","진도군","함평군","해남군","화순군"));
        districtMap.put("경북", Arrays.asList("전체","경산시","경주시","고령군","구미시","군위군","김천시","문경시","봉화군","상주시","성주군","안동시","영덕군","영양군","영주시","영천시","예천군","울릉군","울진군","의성군","청도군","청송군","칠곡군","포항시"));
        districtMap.put("경남", Arrays.asList("전체","거제시","거창군","고성군","김해시","남해군","밀양시","사천시","산청군","양산시","의령군","진주시","창녕군","창원시","통영시","하동군","함안군","함양군","합천군"));
        districtMap.put("제주도", Arrays.asList("전체","서귀포시","제주시"));
    }
}