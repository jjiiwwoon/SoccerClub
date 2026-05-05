package com.example.soccerclub.ui.match;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.soccerclub.R;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.ui.common.StadiumSearchActivity;
import com.example.soccerclub.util.AppUtils;
import com.example.soccerclub.util.DateUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateMatchActivity extends AppCompatActivity {

    private TextView txtDate, txtTime, txtAddressSearch, txtSelectedAddress;
    private EditText editStadium, editDetails;
    private Button btnSubmit;

    private String selectedDate      = "";
    private String selectedStartTime = "";
    private String selectedEndTime   = "";
    private String selectedAddress   = "";
    private String myTeamId          = "";
    private String myTeamName        = "";
    private String myTeamLogoUrl     = "";
    private String myTeamRegion      = "";
    private int    mySkill           = -1;

    private final ActivityResultLauncher<Intent> stadiumLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == RESULT_OK && r.getData() != null) {
                    String name = r.getData().getStringExtra("stadium_name");
                    String addr = r.getData().getStringExtra("address");
                    if (!AppUtils.isEmpty(addr)) {
                        selectedAddress = addr.trim();
                        txtSelectedAddress.setText(selectedAddress);
                        txtSelectedAddress.setTextColor(0xFF000000);
                    }
                    if (!AppUtils.isEmpty(name) && editStadium != null
                            && AppUtils.isEmpty(editStadium.getText().toString())) {
                        editStadium.setText(name.trim());
                    }
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_match);

        txtDate            = findViewById(R.id.txtDate);
        txtTime            = findViewById(R.id.txtTime);
        editStadium        = findViewById(R.id.editStadium);
        txtAddressSearch   = findViewById(R.id.txtAddressSearch);
        txtSelectedAddress = findViewById(R.id.txtSelectedAddress);
        editDetails        = findViewById(R.id.editDetails);
        btnSubmit          = findViewById(R.id.btnSubmit);

        loadMyTeamInfo();

        txtDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, y, m, d) -> {
                selectedDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                txtDate.setText(DateUtils.appendWeekday(selectedDate));
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        txtTime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new android.app.TimePickerDialog(this, (view, h, min) -> {
                selectedStartTime = String.format(Locale.getDefault(), "%02d:%02d", h, min);
                selectedEndTime   = DateUtils.addHours(selectedStartTime, 2);
                txtTime.setText(selectedStartTime + " ~ " + selectedEndTime);
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        });

        txtAddressSearch.setOnClickListener(v ->
                stadiumLauncher.launch(new Intent(this, StadiumSearchActivity.class)));

        btnSubmit.setOnClickListener(v -> submitMatchPost());
    }

    private void loadMyTeamInfo() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance().collection("profiles").document(uid).get()
                .addOnSuccessListener(profileSnap -> {
                    myTeamId = AppUtils.safe(profileSnap.getString("myTeam"));
                    if (!AppUtils.isEmpty(myTeamId)) {
                        FirebaseFirestore.getInstance().collection("teams").document(myTeamId).get()
                                .addOnSuccessListener(teamSnap -> {
                                    myTeamName    = AppUtils.safe(teamSnap.getString("teamName"));
                                    myTeamLogoUrl = AppUtils.safe(teamSnap.getString("logoUrl"));
                                    myTeamRegion  = AppUtils.safe(teamSnap.getString("region"));
                                    Long avg      = teamSnap.getLong("skillAverage");
                                    if (avg != null) mySkill = avg.intValue();
                                });
                    }
                });
    }

    private void submitMatchPost() {
        if (AppUtils.isEmpty(myTeamId) || AppUtils.isEmpty(myTeamName)) {
            CustomToast.warning(this, "소속 팀이 없어요. 팀에 가입하거나 만들어주세요.");
            return;
        }
        if (AppUtils.isEmpty(selectedDate) || AppUtils.isEmpty(selectedStartTime)) {
            CustomToast.info(this, "경기 일자와 시간을 선택해 주세요.");
            return;
        }
        String stadium = editStadium != null ? editStadium.getText().toString().trim() : "";
        if (AppUtils.isEmpty(stadium)) {
            CustomToast.info(this, "시합 장소를 입력해 주세요.");
            return;
        }
        if (AppUtils.isEmpty(selectedAddress)) {
            CustomToast.info(this, "주소를 검색해서 선택해 주세요.");
            return;
        }
        String description = editDetails != null ? editDetails.getText().toString().trim() : "";
        if (AppUtils.isEmpty(description)) {
            CustomToast.info(this, "상세 내용을 입력해 주세요.");
            return;
        }
        if (mySkill < 0) {
            CustomToast.warning(this, "팀 실력 정보가 없어요. 팀 정보를 먼저 등록해 주세요.");
            return;
        }

        long matchTs = DateUtils.computeStartMillis(selectedDate, selectedStartTime);
        long endTs   = DateUtils.computeEndMillis(selectedDate,
                selectedStartTime + " ~ " + selectedEndTime);
        long nowMs   = System.currentTimeMillis();
        String weekday = DateUtils.getKoreanWeekday(selectedDate);

        Map<String, Object> data = new HashMap<>();
        data.put("teamId",          myTeamId);
        data.put("teamName",        myTeamName);
        data.put("logoUrl",         myTeamLogoUrl);
        data.put("teamLogoUrl",     myTeamLogoUrl);
        data.put("date",            selectedDate);
        data.put("time",            selectedStartTime + " ~ " + selectedEndTime);
        data.put("timeStart",       selectedStartTime);
        data.put("timeEnd",         selectedEndTime);
        data.put("matchTs",         matchTs);
        data.put("endTs",           endTs);
        data.put("timestamp",       nowMs);
        data.put("weekday",         weekday);
        data.put("stadiumName",     stadium);
        data.put("stadiumAddress",  selectedAddress);
        data.put("address",         selectedAddress);
        data.put("skill",           mySkill);
        data.put("description",     description);
        data.put("status",          "OPEN");
        data.put("region",          myTeamRegion);
        data.put("createdAt",       Timestamp.now());

        btnSubmit.setEnabled(false);
        FirebaseFirestore.getInstance().collection("matches").add(data)
                .addOnSuccessListener(ref -> {
                    CustomToast.success(this, "시합 모집글이 등록됐어요!");
                    setResult(RESULT_OK);
                    finish();
                })
                .addOnFailureListener(e -> {
                    CustomToast.error(this, "등록 실패: " + e.getMessage());
                    btnSubmit.setEnabled(true);
                });
    }
}