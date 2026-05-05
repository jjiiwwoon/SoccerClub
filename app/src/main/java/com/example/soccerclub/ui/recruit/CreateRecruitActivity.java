package com.example.soccerclub.ui.recruit;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.example.soccerclub.R;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.ui.common.StadiumSearchActivity;
import com.example.soccerclub.util.AppUtils;
import com.example.soccerclub.util.DateUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CreateRecruitActivity extends AppCompatActivity {

    private RadioGroup radioRecruitType;
    private RadioButton rdoRegular, rdoMercenary;
    private LinearLayout containerRegular, containerMercenary;

    private TextView tvRegularDate, tvRegularTime, tvRegularStadium, tvRegularAddress;
    private TextView txtDate, txtTime, txtAddressSearch, txtSelectedAddress;
    private EditText editStadium, editDetails;
    private Button btnLoadFromSchedule, btnSubmit;

    private Spinner spinnerSkillMin, spinnerSkillMax;
    private ChipGroup chipGroupPositions;
    private Chip chipGK, chipDF, chipMF, chipFW;

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String myUid          = "";
    private String myTeamId       = "";
    private String myTeamName     = "";
    private String myTeamLogo     = "";
    private String myRegion       = "";
    private String myHomeStadiumName = "";
    private String myStadiumAddress  = "";
    private String myActivityDay  = "";
    private String myTimeStart    = "";
    private String myTimeEnd      = "";

    private String selectedDate      = "";
    private String selectedStartTime = "";
    private String selectedEndTime   = "";
    private String stadiumName       = "";
    private String stadiumAddr       = "";

    private final List<String> positions = new ArrayList<>();
    private final Set<String>  positionSet = new HashSet<>();

    private final ActivityResultLauncher<Intent> addressSearchLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    String name = r.getData().getStringExtra("stadium_name");
                    String addr = r.getData().getStringExtra("address");
                    if (!AppUtils.isEmpty(addr)) {
                        stadiumAddr = addr.trim();
                        if (txtSelectedAddress != null) {
                            txtSelectedAddress.setText(stadiumAddr);
                            txtSelectedAddress.setTextColor(0xFF000000);
                        }
                    }
                    if (!AppUtils.isEmpty(name)) {
                        stadiumName = name.trim();
                        if (editStadium != null && AppUtils.isEmpty(editStadium.getText().toString())) {
                            editStadium.setText(stadiumName);
                        }
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_recruit);

        bindViews();
        initSpinners();
        setupPositionChips();
        initClicks();

        myUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
        fetchMyProfileThenTeam();
    }

    private void bindViews() {
        radioRecruitType   = findViewById(R.id.radioRecruitType);
        rdoRegular         = findViewById(R.id.rdoRegular);
        rdoMercenary       = findViewById(R.id.rdoMercenary);
        containerRegular   = findViewById(R.id.containerRegular);
        containerMercenary = findViewById(R.id.containerMercenary);

        tvRegularDate    = findViewById(R.id.tvRegularDate);
        tvRegularTime    = findViewById(R.id.tvRegularTime);
        tvRegularStadium = findViewById(R.id.tvRegularStadium);
        tvRegularAddress = findViewById(R.id.tvRegularAddress);

        txtDate           = findViewById(R.id.txtDate);
        txtTime           = findViewById(R.id.txtTime);
        txtAddressSearch  = findViewById(R.id.txtAddressSearch);
        txtSelectedAddress = findViewById(R.id.txtSelectedAddress);
        editStadium       = findViewById(R.id.editStadium);
        btnLoadFromSchedule = findViewById(R.id.btnLoadFromSchedule);
        editDetails       = findViewById(R.id.editDetails);
        spinnerSkillMin   = findViewById(R.id.spinnerSkillMin);
        spinnerSkillMax   = findViewById(R.id.spinnerSkillMax);
        chipGroupPositions = findViewById(R.id.chipGroupPositions);
        chipGK            = findViewById(R.id.chipGK);
        chipDF            = findViewById(R.id.chipDF);
        chipMF            = findViewById(R.id.chipMF);
        chipFW            = findViewById(R.id.chipFW);
        btnSubmit         = findViewById(R.id.btnSubmit);
    }

    private void initSpinners() {
        ArrayList<String> skills = new ArrayList<>();
        for (int i = 0; i <= 10; i++) skills.add(String.valueOf(i));
        ArrayAdapter<String> skillAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, skills);
        skillAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSkillMin.setAdapter(skillAdapter);
        spinnerSkillMax.setAdapter(skillAdapter);
        spinnerSkillMin.setSelection(0);
        spinnerSkillMax.setSelection(skills.size() - 1);

        spinnerSkillMin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (spinnerSkillMax.getSelectedItemPosition() < pos)
                    spinnerSkillMax.setSelection(pos);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void setupPositionChips() {
        View.OnClickListener toggle = v -> {
            Chip c = (Chip) v;
            String label = c.getText().toString();
            if (c.isChecked()) {
                if (!positionSet.contains(label)) { positionSet.add(label); positions.add(label); }
            } else {
                positionSet.remove(label);
                positions.remove(label);
            }
        };
        chipGK.setOnClickListener(toggle);
        chipDF.setOnClickListener(toggle);
        chipMF.setOnClickListener(toggle);
        chipFW.setOnClickListener(toggle);
    }

    private void initClicks() {
        radioRecruitType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isRegular = (checkedId == R.id.rdoRegular);
            containerRegular.setVisibility(isRegular ? View.VISIBLE : View.GONE);
            containerMercenary.setVisibility(isRegular ? View.GONE : View.VISIBLE);
            if (isRegular) applyTeamToRegularUI();
        });

        txtDate.setOnClickListener(v -> pickDate());
        txtTime.setOnClickListener(v -> showTimeRangePicker());
        txtAddressSearch.setOnClickListener(v ->
                addressSearchLauncher.launch(new Intent(this, StadiumSearchActivity.class)));

        btnLoadFromSchedule.setOnClickListener(v ->
                CustomToast.info(this, "일정 불러오기 기능은 준비 중이에요."));

        btnSubmit.setOnClickListener(v -> submit());
    }

    private void fetchMyProfileThenTeam() {
        if (AppUtils.isEmpty(myUid)) return;
        db.collection("profiles").document(myUid).get().addOnSuccessListener(pf -> {
            if (!pf.exists()) return;
            myTeamId = AppUtils.safe(pf.getString("myTeam"));
            if (AppUtils.isEmpty(myTeamId)) return;

            db.collection("teams").document(myTeamId).get().addOnSuccessListener(ts -> {
                if (!ts.exists()) return;
                myTeamName        = AppUtils.safe(ts.getString("teamName"));
                myTeamLogo        = AppUtils.safe(ts.getString("logoUrl"));
                myRegion          = AppUtils.safe(ts.getString("region"));
                myHomeStadiumName = AppUtils.safe(ts.getString("homeStadiumName"));
                String stadiumFromDoc = AppUtils.safe(ts.getString("stadium"));
                myStadiumAddress  = AppUtils.isEmpty(stadiumFromDoc) ? myRegion : stadiumFromDoc;
                myActivityDay     = AppUtils.safe(ts.getString("activityDay"));
                myTimeStart       = AppUtils.safe(ts.getString("timeStart"));
                myTimeEnd         = AppUtils.safe(ts.getString("timeEnd"));
                applyTeamToRegularUI();
            });
        });
    }

    private void applyTeamToRegularUI() {
        if (tvRegularStadium != null)
            tvRegularStadium.setText(AppUtils.isEmpty(myHomeStadiumName) ? "주활동구장 미설정" : myHomeStadiumName);
        if (tvRegularAddress != null)
            tvRegularAddress.setText(AppUtils.isEmpty(myStadiumAddress) ? "주소 미설정" : myStadiumAddress);

        stadiumName = myHomeStadiumName;
        stadiumAddr = myStadiumAddress;

        String timeRange = (!AppUtils.isEmpty(myTimeStart) && !AppUtils.isEmpty(myTimeEnd))
                ? myTimeStart + " ~ " + myTimeEnd : "";
        selectedStartTime = myTimeStart;
        selectedEndTime   = myTimeEnd;
        if (tvRegularTime != null) tvRegularTime.setText(AppUtils.isEmpty(timeRange) ? "시간 미설정" : timeRange);
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            selectedDate = String.format("%04d-%02d-%02d", y, m + 1, d);
            if (txtDate != null) txtDate.setText(DateUtils.appendWeekday(selectedDate));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showTimeRangePicker() {
        Calendar c = Calendar.getInstance();
        new android.app.TimePickerDialog(this, (v, h, min) -> {
            selectedStartTime = String.format("%02d:%02d", h, min);
            selectedEndTime   = DateUtils.addHours(selectedStartTime, 2);
            String range = selectedStartTime + " ~ " + selectedEndTime;
            if (txtTime != null) txtTime.setText(range);
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
    }

    private void submit() {
        boolean isRegular = (radioRecruitType.getCheckedRadioButtonId() == R.id.rdoRegular);

        if (isRegular) {
            if (AppUtils.isEmpty(myTeamId)) { CustomToast.error(this, "팀 정보를 불러오지 못했습니다."); return; }
            if (AppUtils.isEmpty(selectedDate)) { CustomToast.info(this, "활동 날짜가 설정되지 않았습니다."); return; }
            if (AppUtils.isEmpty(selectedStartTime) || AppUtils.isEmpty(selectedEndTime)) {
                CustomToast.info(this, "팀 활동시간이 설정되지 않았습니다."); return;
            }
            if (AppUtils.isEmpty(stadiumName)) stadiumName = myHomeStadiumName;
            if (AppUtils.isEmpty(stadiumAddr)) stadiumAddr = myStadiumAddress;
        } else {
            stadiumName = editStadium != null ? editStadium.getText().toString().trim() : "";
            if (AppUtils.isEmpty(selectedDate)) { CustomToast.info(this, "날짜를 선택하세요."); return; }
            if (AppUtils.isEmpty(selectedStartTime) || AppUtils.isEmpty(selectedEndTime)) {
                CustomToast.info(this, "시간을 선택하세요."); return;
            }
            if (AppUtils.isEmpty(stadiumName)) { CustomToast.info(this, "시합장소를 입력하세요."); return; }
        }

        String details  = editDetails != null ? editDetails.getText().toString().trim() : "";
        int skillMin    = parseInt(spinnerSkillMin.getSelectedItem().toString(), 0);
        int skillMax    = parseInt(spinnerSkillMax.getSelectedItem().toString(), 10);
        if (skillMin > skillMax) { CustomToast.warning(this, "실력 범위를 올바르게 선택하세요."); return; }

        String recruitType = isRegular ? "regular" : "mercenary";
        String timeRange   = selectedStartTime + " ~ " + selectedEndTime;
        long matchTs       = DateUtils.computeStartMillis(selectedDate, selectedStartTime);
        long endTs         = DateUtils.computeEndMillis(selectedDate, timeRange);
        long nowMs         = System.currentTimeMillis();
        String weekday     = DateUtils.getKoreanWeekday(selectedDate);

        Map<String, Object> data = new HashMap<>();
        data.put("teamId",       myTeamId);
        data.put("teamName",     myTeamName);
        data.put("teamLogoUrl",  myTeamLogo);
        data.put("region",       myRegion);
        data.put("date",         selectedDate);
        data.put("time",         timeRange);
        data.put("timeStart",    selectedStartTime);
        data.put("timeEnd",      selectedEndTime);
        data.put("matchTs",      matchTs);
        data.put("endTs",        endTs);
        data.put("weekday",      weekday);
        data.put("postTs",       matchTs);
        data.put("timestamp",    nowMs);
        data.put("createdAtMs",  nowMs);
        data.put("stadiumName",  stadiumName);
        data.put("stadiumAddress", stadiumAddr);
        data.put("skillMin",     skillMin);
        data.put("skillMax",     skillMax);
        data.put("positions",    new ArrayList<>(positions));
        data.put("recruitType",  recruitType);
        data.put("intro",        details);
        data.put("status",       "open");
        data.put("createdBy",    myUid);
        data.put("createdAt",    com.google.firebase.Timestamp.now());
        data.put("updatedAt",    com.google.firebase.Timestamp.now());

        btnSubmit.setEnabled(false);
        db.collection("recruitPosts").add(data)
                .addOnSuccessListener(r -> {
                    CustomToast.success(this, "모집글이 등록되었습니다.");
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "등록 실패: " + e.getMessage());
                    btnSubmit.setEnabled(true);
                });
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}