package com.jjw.soccerclub.ui.recruit;

import com.jjw.soccerclub.ui.common.BaseActivity;
import com.jjw.soccerclub.ui.common.SchedulePickerDialog;
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

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.ui.common.StadiumSearchActivity;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CreateRecruitActivity extends BaseActivity {

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

    private String myUid             = "";
    private String myTeamId          = "";
    private String myTeamName        = "";
    private String myTeamLogo        = "";
    private String myRegion          = "";
    private String myHomeStadiumName = "";
    private String myStadiumAddress  = "";
    private String myActivityDay     = "";
    private String myTimeStart       = "";
    private String myTimeEnd         = "";

    private String selectedDate      = "";
    private String selectedStartTime = "";
    private String selectedEndTime   = "";
    private String stadiumName       = "";
    private String stadiumAddr       = "";

    private final List<String> positions   = new ArrayList<>();
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

        // ✅ 뒤로가기 취소 확인 다이얼로그
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirm();
            }
        });
    }

    // ✅ 작성 취소 확인 다이얼로그 — 입력 내용이 있을 때만 표시
    private void showExitConfirm() {
        boolean hasInput = !AppUtils.isEmpty(selectedDate)
                || !AppUtils.isEmpty(stadiumAddr)
                || (editDetails != null && !editDetails.getText().toString().trim().isEmpty())
                || (editStadium != null && !editStadium.getText().toString().trim().isEmpty());

        if (!hasInput) {
            finish();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("작성 취소")
                .setMessage("작성 중인 내용이 있어요.\n취소하면 저장되지 않습니다.")
                .setPositiveButton("나가기", (d, i) -> finish())
                .setNegativeButton("계속 작성", null)
                .show();
    }

    private void bindViews() {
        radioRecruitType    = findViewById(R.id.radioRecruitType);
        rdoRegular          = findViewById(R.id.rdoRegular);
        rdoMercenary        = findViewById(R.id.rdoMercenary);
        containerRegular    = findViewById(R.id.containerRegular);
        containerMercenary  = findViewById(R.id.containerMercenary);

        tvRegularDate       = findViewById(R.id.tvRegularDate);
        tvRegularTime       = findViewById(R.id.tvRegularTime);
        tvRegularStadium    = findViewById(R.id.tvRegularStadium);
        tvRegularAddress    = findViewById(R.id.tvRegularAddress);

        txtDate             = findViewById(R.id.txtDate);
        txtTime             = findViewById(R.id.txtTime);
        txtAddressSearch    = findViewById(R.id.txtAddressSearch);
        txtSelectedAddress  = findViewById(R.id.txtSelectedAddress);
        editStadium         = findViewById(R.id.editStadium);
        btnLoadFromSchedule = findViewById(R.id.btnLoadFromSchedule);
        editDetails         = findViewById(R.id.editDetails);
        spinnerSkillMin     = findViewById(R.id.spinnerSkillMin);
        spinnerSkillMax     = findViewById(R.id.spinnerSkillMax);
        chipGroupPositions  = findViewById(R.id.chipGroupPositions);
        chipGK              = findViewById(R.id.chipGK);
        chipDF              = findViewById(R.id.chipDF);
        chipMF              = findViewById(R.id.chipMF);
        chipFW              = findViewById(R.id.chipFW);
        btnSubmit           = findViewById(R.id.btnSubmit);
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
            applyPositionChipStyle(c, label, c.isChecked());
        };
        chipGK.setOnClickListener(toggle);
        chipDF.setOnClickListener(toggle);
        chipMF.setOnClickListener(toggle);
        chipFW.setOnClickListener(toggle);
    }

    private void applyPositionChipStyle(Chip chip, String pos, boolean checked) {
        int textColor, bgColor;
        switch (pos) {
            case "FW": textColor = 0xFFD50000; bgColor = 0x33D50000; break;
            case "MF": textColor = 0xFF00C853; bgColor = 0x3300C853; break;
            case "DF": textColor = 0xFF2962FF; bgColor = 0x332962FF; break;
            case "GK": textColor = 0xFFF9A825; bgColor = 0x33F9A825; break;
            default:   textColor = 0xFF666666; bgColor = 0x33666666; break;
        }
        if (checked) {
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(bgColor));
            chip.setTextColor(textColor);
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(textColor));
            chip.setChipStrokeWidth(2f);
        } else {
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0xFFF5F5F5));
            chip.setTextColor(0xFF888888);
            chip.setChipStrokeColor(android.content.res.ColorStateList.valueOf(0xFFE0E0E0));
            chip.setChipStrokeWidth(1f);
        }
    }

    private void initClicks() {
        radioRecruitType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isRegular = (checkedId == R.id.rdoRegular);
            containerRegular.setVisibility(isRegular ? View.VISIBLE : View.GONE);
            containerMercenary.setVisibility(isRegular ? View.GONE : View.VISIBLE);
            if (isRegular) applyTeamToRegularUI();
        });

        // 용병 모집 필드
        txtDate.setOnClickListener(v -> pickMercenaryDate());
        txtTime.setOnClickListener(v -> showTimeRangePicker(false));
        txtAddressSearch.setOnClickListener(v ->
                addressSearchLauncher.launch(new Intent(this, StadiumSearchActivity.class)));

        // ✅ 일정에서 가져오기 — 용병 쪽에서만 사용
        if (btnLoadFromSchedule != null) {
            if (btnLoadFromSchedule != null) {
                btnLoadFromSchedule.setOnClickListener(v -> {
                    if (AppUtils.isEmpty(myTeamId)) {
                        CustomToast.warning(this, "팀 정보를 먼저 불러와야 해요.");
                        return;
                    }

                    SchedulePickerDialog picker = SchedulePickerDialog.newInstance(
                            myTeamId, myTeamId, myTeamName);

                    picker.setOnScheduleSelected(doc -> {
                        // ★ 선택한 일정의 정보를 용병 모집 폼에 자동 채움
                        String date = AppUtils.safe(doc.getString("date"));
                        String time = AppUtils.safe(doc.getString("time"));
                        String sName = AppUtils.firstNonEmpty(
                                doc.getString("stadiumName"), doc.getString("stadium"));
                        String sAddr = AppUtils.firstNonEmpty(
                                doc.getString("stadiumAddress"), doc.getString("address"));

                        // 날짜
                        if (!AppUtils.isEmpty(date)) {
                            selectedDate = date;
                            if (txtDate != null) txtDate.setText(DateUtils.appendWeekday(date));
                        }

                        // 시간
                        if (!AppUtils.isEmpty(time)) {
                            // "HH:mm ~ HH:mm" 또는 "HH:mm" 형태
                            if (time.contains("~")) {
                                String[] parts = time.split("~");
                                selectedStartTime = parts[0].trim();
                                selectedEndTime   = parts.length > 1 ? parts[1].trim() : "";
                            } else {
                                selectedStartTime = time.trim();
                                selectedEndTime   = DateUtils.addHours(selectedStartTime, 2);
                            }
                            String range = selectedStartTime +
                                    (!AppUtils.isEmpty(selectedEndTime) ? " ~ " + selectedEndTime : "");
                            if (txtTime != null) txtTime.setText(range);
                        }

                        // 경기장
                        if (!AppUtils.isEmpty(sName)) {
                            stadiumName = sName;
                            if (editStadium != null) editStadium.setText(sName);
                        }

                        // 주소
                        if (!AppUtils.isEmpty(sAddr)) {
                            stadiumAddr = sAddr;
                            if (txtSelectedAddress != null) txtSelectedAddress.setText(sAddr);
                        }

                        CustomToast.success(CreateRecruitActivity.this,
                                "일정을 불러왔어요! 필요하면 수정할 수 있어요.");
                    });

                    picker.show(getSupportFragmentManager(), "SchedulePicker");
                });
            }
        }

        btnSubmit.setOnClickListener(v -> submit());
    }

    // ✅ 용병 모집용 날짜 선택
    private void pickMercenaryDate() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            selectedDate = String.format("%04d-%02d-%02d", y, m + 1, d);
            if (txtDate != null) txtDate.setText(DateUtils.appendWeekday(selectedDate));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
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
        // ✅ 구장 - 팀 정보로 기본 세팅 (클릭하면 변경 가능)
        if (tvRegularStadium != null) {
            boolean hasStadium = !AppUtils.isEmpty(myHomeStadiumName);
            tvRegularStadium.setText(hasStadium ? myHomeStadiumName : "주활동구장 미설정 (탭하여 설정)");
            tvRegularStadium.setTextColor(hasStadium ? 0xFF000000 : 0xFF999999);
            tvRegularStadium.setOnClickListener(v ->
                    addressSearchLauncher.launch(new Intent(this, StadiumSearchActivity.class)));
        }
        if (tvRegularAddress != null) {
            boolean hasAddr = !AppUtils.isEmpty(myStadiumAddress);
            tvRegularAddress.setText(hasAddr ? myStadiumAddress : "주소 미설정");
            tvRegularAddress.setTextColor(hasAddr ? 0xFF000000 : 0xFF999999);
        }

        stadiumName = myHomeStadiumName;
        stadiumAddr = myStadiumAddress;

        // ✅ 시간 - 팀 정보로 기본 세팅 (클릭하면 변경 가능)
        String timeRange = (!AppUtils.isEmpty(myTimeStart) && !AppUtils.isEmpty(myTimeEnd))
                ? myTimeStart + " ~ " + myTimeEnd : "";
        selectedStartTime = myTimeStart;
        selectedEndTime   = myTimeEnd;
        if (tvRegularTime != null) {
            boolean hasTime = !AppUtils.isEmpty(timeRange);
            tvRegularTime.setText(hasTime ? timeRange : "시간 미설정 (탭하여 설정)");
            tvRegularTime.setTextColor(hasTime ? 0xFF000000 : 0xFF999999);
            tvRegularTime.setOnClickListener(v -> showTimeRangePicker(true));
        }

        // ✅ 날짜 - 팀 활동일 기본 세팅 (클릭하면 변경 가능)
        if (tvRegularDate != null) {
            if (!AppUtils.isEmpty(myActivityDay)) {
                selectedDate = myActivityDay;
                tvRegularDate.setText(DateUtils.appendWeekday(myActivityDay));
                tvRegularDate.setTextColor(0xFF000000);
            } else {
                tvRegularDate.setText("활동 날짜 선택 (탭하여 설정)");
                tvRegularDate.setTextColor(0xFF999999);
            }
            tvRegularDate.setOnClickListener(v -> pickRegularDate());
        }
    }

    // ✅ 팀원 모집용 날짜 선택
    private void pickRegularDate() {
        Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> {
            selectedDate = String.format("%04d-%02d-%02d", y, m + 1, d);
            if (tvRegularDate != null) tvRegularDate.setText(DateUtils.appendWeekday(selectedDate));
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ✅ 시간 선택 (isRegular: true면 tvRegularTime, false면 txtTime 업데이트)
    // ✅ 시간 선택 — 시작/종료 각각 선택 (dialog_time_range_picker 사용)
    private void showTimeRangePicker(boolean isRegular) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_time_range_picker, null);
        android.widget.NumberPicker sh = dialogView.findViewById(R.id.pickerStartHour);
        android.widget.NumberPicker eh = dialogView.findViewById(R.id.pickerEndHour);
        android.widget.NumberPicker sm = dialogView.findViewById(R.id.pickerStartMinute);
        android.widget.NumberPicker em = dialogView.findViewById(R.id.pickerEndMinute);

        for (android.widget.NumberPicker p : new android.widget.NumberPicker[]{sh, eh}) {
            p.setMinValue(0); p.setMaxValue(23);
            p.setFormatter(i -> String.format(java.util.Locale.getDefault(), "%02d", i));
        }
        String[] mins = {"00","10","20","30","40","50"};
        for (android.widget.NumberPicker p : new android.widget.NumberPicker[]{sm, em}) {
            p.setMinValue(0); p.setMaxValue(mins.length - 1);
            p.setDisplayedValues(mins);
        }

        // 기존 값 복원
        if (!AppUtils.isEmpty(selectedStartTime) && selectedStartTime.contains(":")) {
            try {
                String[] parts = selectedStartTime.split(":");
                sh.setValue(Integer.parseInt(parts[0]));
                for (int i = 0; i < mins.length; i++) { if (mins[i].equals(parts[1])) { sm.setValue(i); break; } }
            } catch (Exception ignored) {}
        }
        if (!AppUtils.isEmpty(selectedEndTime) && selectedEndTime.contains(":")) {
            try {
                String[] parts = selectedEndTime.split(":");
                eh.setValue(Integer.parseInt(parts[0]));
                for (int i = 0; i < mins.length; i++) { if (mins[i].equals(parts[1])) { em.setValue(i); break; } }
            } catch (Exception ignored) {}
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("확인", (d, w) -> {
                    selectedStartTime = String.format(java.util.Locale.getDefault(), "%02d", sh.getValue()) + ":" + mins[sm.getValue()];
                    selectedEndTime   = String.format(java.util.Locale.getDefault(), "%02d", eh.getValue()) + ":" + mins[em.getValue()];
                    String range = selectedStartTime + " ~ " + selectedEndTime;
                    if (isRegular) {
                        if (tvRegularTime != null) tvRegularTime.setText(range);
                    } else {
                        if (txtTime != null) txtTime.setText(range);
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void submit() {
        boolean isRegular = (radioRecruitType.getCheckedRadioButtonId() == R.id.rdoRegular);

        if (isRegular) {
            if (AppUtils.isEmpty(myTeamId)) { CustomToast.error(this, "팀 정보를 불러오지 못했습니다."); return; }
            // ✅ 날짜 없으면 오늘 날짜로 기본 설정
            if (AppUtils.isEmpty(selectedDate)) {
                Calendar c = Calendar.getInstance();
                selectedDate = String.format("%04d-%02d-%02d",
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
                if (tvRegularDate != null) tvRegularDate.setText(DateUtils.appendWeekday(selectedDate));
            }
            if (AppUtils.isEmpty(selectedStartTime) || AppUtils.isEmpty(selectedEndTime)) {
                // 시간 미설정이면 기본값 00:00 ~ 02:00
                selectedStartTime = "00:00";
                selectedEndTime   = "02:00";
            }
            if (AppUtils.isEmpty(stadiumName)) stadiumName = myHomeStadiumName;
            if (AppUtils.isEmpty(stadiumAddr)) stadiumAddr = myStadiumAddress;
        } else {
            // ✅ 용병 모집 - 날짜/시간/장소 선택 사항
            stadiumName = editStadium != null ? editStadium.getText().toString().trim() : "";
            // 날짜 없으면 빈 값으로 저장 (선택 사항)
            if (AppUtils.isEmpty(selectedStartTime)) selectedStartTime = "";
            if (AppUtils.isEmpty(selectedEndTime))   selectedEndTime   = "";
        }

        String details = editDetails != null ? editDetails.getText().toString().trim() : "";
        int skillMin   = parseInt(spinnerSkillMin.getSelectedItem().toString(), 0);
        int skillMax   = parseInt(spinnerSkillMax.getSelectedItem().toString(), 10);
        if (skillMin > skillMax) { CustomToast.warning(this, "실력 범위를 올바르게 선택하세요."); return; }

        String recruitType = isRegular ? "regular" : "mercenary";
        String timeRange   = selectedStartTime + " ~ " + selectedEndTime;
        long matchTs       = DateUtils.computeStartMillis(selectedDate, selectedStartTime);
        long endTs         = DateUtils.computeEndMillis(selectedDate, timeRange);
        long nowMs         = System.currentTimeMillis();
        String weekday     = DateUtils.getKoreanWeekday(selectedDate);

        Map<String, Object> data = new HashMap<>();
        data.put("teamId",         myTeamId);
        data.put("teamName",       myTeamName);
        data.put("teamLogoUrl",    myTeamLogo);
        data.put("region",         myRegion);
        data.put("date",           selectedDate);
        data.put("time",           timeRange);
        data.put("timeStart",      selectedStartTime);
        data.put("timeEnd",        selectedEndTime);
        data.put("matchTs",        matchTs);
        data.put("endTs",          endTs);
        data.put("weekday",        weekday);
        data.put("postTs",         matchTs);
        data.put("timestamp",      nowMs);
        data.put("createdAtMs",    nowMs);
        data.put("stadiumName",    stadiumName);
        data.put("stadiumAddress", stadiumAddr);
        data.put("skillMin",       skillMin);
        data.put("skillMax",       skillMax);
        positions.sort((a, b) -> posOrder(a) - posOrder(b));
        data.put("positions",      new ArrayList<>(positions));
        data.put("recruitType",    recruitType);
        data.put("intro",          details);
        data.put("status",         "open");
        data.put("createdBy",      myUid);
        data.put("authorUid",      myUid); // ✅ ApplicationsListActivity 내 글 탭 조회용
        data.put("createdAt",      com.google.firebase.Timestamp.now());
        data.put("updatedAt",      com.google.firebase.Timestamp.now());

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
    private int posOrder(String pos) {
        switch (pos) {
            case "GK": return 0;
            case "DF": return 1;
            case "MF": return 2;
            case "FW": return 3;
            default:   return 9;
        }
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }
}