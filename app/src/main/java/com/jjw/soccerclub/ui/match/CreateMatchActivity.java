package com.jjw.soccerclub.ui.match;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.repository.MatchRepository;
import com.jjw.soccerclub.ui.common.BaseActivity;
import com.jjw.soccerclub.ui.common.StadiumSearchActivity;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CreateMatchActivity extends BaseActivity {

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

    private final MatchRepository matchRepository = new MatchRepository();
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

        txtTime.setOnClickListener(v -> showTimeRangePicker());

        txtAddressSearch.setOnClickListener(v ->
                stadiumLauncher.launch(new Intent(this, StadiumSearchActivity.class)));

        btnSubmit.setOnClickListener(v -> submitMatchPost());

        // ✅ 뒤로가기 취소 확인 다이얼로그
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirm();
            }
        });
    }

    // ✅ 시간 선택 — 시작/종료 각각 선택 (dialog_time_range_picker 사용)
    private void showTimeRangePicker() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_time_range_picker, null);
        NumberPicker sh = dialogView.findViewById(R.id.pickerStartHour);
        NumberPicker eh = dialogView.findViewById(R.id.pickerEndHour);
        NumberPicker sm = dialogView.findViewById(R.id.pickerStartMinute);
        NumberPicker em = dialogView.findViewById(R.id.pickerEndMinute);

        for (NumberPicker p : new NumberPicker[]{sh, eh}) {
            p.setMinValue(0);
            p.setMaxValue(23);
            p.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));
        }
        String[] mins = {"00", "10", "20", "30", "40", "50"};
        for (NumberPicker p : new NumberPicker[]{sm, em}) {
            p.setMinValue(0);
            p.setMaxValue(mins.length - 1);
            p.setDisplayedValues(mins);
        }

        // 기존 값 복원
        if (!AppUtils.isEmpty(selectedStartTime) && selectedStartTime.contains(":")) {
            try {
                String[] parts = selectedStartTime.split(":");
                sh.setValue(Integer.parseInt(parts[0]));
                for (int i = 0; i < mins.length; i++) {
                    if (mins[i].equals(parts[1])) { sm.setValue(i); break; }
                }
            } catch (Exception ignored) {}
        }
        if (!AppUtils.isEmpty(selectedEndTime) && selectedEndTime.contains(":")) {
            try {
                String[] parts = selectedEndTime.split(":");
                eh.setValue(Integer.parseInt(parts[0]));
                for (int i = 0; i < mins.length; i++) {
                    if (mins[i].equals(parts[1])) { em.setValue(i); break; }
                }
            } catch (Exception ignored) {}
        }

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("확인", (d, w) -> {
                    selectedStartTime = String.format(Locale.getDefault(), "%02d", sh.getValue()) + ":" + mins[sm.getValue()];
                    selectedEndTime   = String.format(Locale.getDefault(), "%02d", eh.getValue()) + ":" + mins[em.getValue()];
                    txtTime.setText(selectedStartTime + " ~ " + selectedEndTime);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ✅ 작성 취소 확인 다이얼로그
    private void showExitConfirm() {
        boolean hasInput = !AppUtils.isEmpty(selectedDate)
                || !AppUtils.isEmpty(selectedAddress)
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
        // ── 입력 검증 (기존 그대로) ──────────────────────────────────────────────
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

        // ── 등록 (Repository 위임) ───────────────────────────────────────────────
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        MatchRepository.NewMatchPost post = new MatchRepository.NewMatchPost(
                myTeamId, myTeamName, myTeamLogoUrl, myTeamRegion, mySkill,
                selectedDate, selectedStartTime, selectedEndTime,
                stadium, selectedAddress, description, currentUid);

        btnSubmit.setEnabled(false);
        matchRepository.createPost(post, new MatchRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                if (isFinishing() || isDestroyed()) return;
                CustomToast.success(CreateMatchActivity.this, "시합 모집글이 등록됐어요!");
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onFailure(Exception e) {
                if (isFinishing() || isDestroyed()) return;
                CustomToast.error(CreateMatchActivity.this, "등록 실패: " + e.getMessage());
                btnSubmit.setEnabled(true);
            }
        });
    }
}