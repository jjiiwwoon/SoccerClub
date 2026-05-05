package com.example.soccerclub.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.soccerclub.R;
import com.example.soccerclub.common.CustomCalendarView;
import com.example.soccerclub.common.StateLayout;
import com.example.soccerclub.model.ScheduleItem;
import com.example.soccerclub.util.AppUtils;
import com.example.soccerclub.util.DateUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ScheduleActivity extends AppCompatActivity {

    private StateLayout state;
    private TextView tvSelectedDateTitle;
    private ProgressBar progressSelectedDate;
    private LinearLayout selectedDateList;
    private View nextScheduleCard, scheduleLoading, scheduleContent;
    private TextView tvNextDateChip, tvHomeName, tvAwayName, tvPlace, tvAddress;
    private ImageView imgHomeLogo, imgAwayLogo;

    private final List<ScheduleItem> scheduleList = new ArrayList<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String myTeamId = "", myTeamName = "", myTeamLogoUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        state                = findViewById(R.id.stateLayout);
        tvSelectedDateTitle  = findViewById(R.id.tvSelectedDateTitle);
        progressSelectedDate = findViewById(R.id.progressSelectedDate);
        selectedDateList     = findViewById(R.id.selectedDateList);
        nextScheduleCard     = findViewById(R.id.nextScheduleCard);
        scheduleLoading      = findViewById(R.id.scheduleLoading);
        scheduleContent      = findViewById(R.id.scheduleContent);
        tvNextDateChip       = findViewById(R.id.tvNextDateChip);
        imgHomeLogo          = findViewById(R.id.imgHomeLogo);
        imgAwayLogo          = findViewById(R.id.imgAwayLogo);
        tvHomeName           = findViewById(R.id.tvHomeName);
        tvAwayName           = findViewById(R.id.tvAwayName);
        tvPlace              = findViewById(R.id.tvPlace);
        tvAddress            = findViewById(R.id.tvAddress);

        if (state != null) state.showLoading();

        FrameLayout calendarContainer = findViewById(R.id.calendarContainer);
        CustomCalendarView calendar = new CustomCalendarView(this);
        calendarContainer.addView(calendar);

        calendar.setOnDateClickListener((year, month, day) -> {
            String date = String.format(Locale.KOREAN, "%04d-%02d-%02d", year, month + 1, day);
            if (tvSelectedDateTitle != null)
                tvSelectedDateTitle.setText("선택된 날짜 (" + date + ")");
            if (progressSelectedDate != null) progressSelectedDate.setVisibility(View.VISIBLE);
            loadScheduleForDate(date);
        });

        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            db.collection("profiles").document(uid).get()
                    .addOnSuccessListener(doc -> {
                        myTeamId = AppUtils.safe(doc.getString("myTeam"));
                        if (AppUtils.isEmpty(myTeamId)) {
                            if (state != null) { state.setEmptyMessage("소속 팀이 없어요."); state.showEmpty(); }
                            return;
                        }
                        db.collection("teams").document(myTeamId).get()
                                .addOnSuccessListener(teamDoc -> {
                                    myTeamName    = AppUtils.safe(teamDoc.getString("teamName"));
                                    myTeamLogoUrl = AppUtils.safe(teamDoc.getString("logoUrl"));
                                    findNextSchedule();
                                    String today = todayString();
                                    if (tvSelectedDateTitle != null)
                                        tvSelectedDateTitle.setText("선택된 날짜 (" + today + ")");
                                    loadScheduleForDate(today);
                                });
                    });
        }
    }

    private void findNextSchedule() {
        long now = System.currentTimeMillis();
        db.collection("schedules").document(myTeamId).collection("events").get()
                .addOnSuccessListener(snap -> {
                    ScheduleItem next = null;
                    long minTs = Long.MAX_VALUE;
                    for (QueryDocumentSnapshot d : snap) {
                        Long matchTs = d.getLong("matchTs");
                        if (matchTs == null) continue;
                        long ms = AppUtils.normalizeToMillis(matchTs);
                        if (ms > now && ms < minTs) {
                            minTs = ms;
                            next = new ScheduleItem(
                                    d.getId(), myTeamId,
                                    AppUtils.safe(d.getString("date")),
                                    AppUtils.safe(d.getString("status")),
                                    AppUtils.safe(d.getString("matchId")),
                                    AppUtils.safe(d.getString("title")),
                                    AppUtils.safe(d.getString("time")),
                                    AppUtils.safe(d.getString("opponentTeamName")),
                                    AppUtils.safe(d.getString("stadiumName")),
                                    AppUtils.safe(d.getString("address"))
                            );
                            next.opponentLogoUrl = AppUtils.safe(d.getString("opponentLogoUrl"));
                        }
                    }
                    if (next != null) renderNextSchedule(next);
                    else if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.GONE);
                    if (state != null) state.showContent();
                });
    }

    private void renderNextSchedule(ScheduleItem item) {
        if (nextScheduleCard == null) return;
        nextScheduleCard.setVisibility(View.VISIBLE);
        if (scheduleLoading != null) scheduleLoading.setVisibility(View.GONE);
        if (scheduleContent  != null) scheduleContent.setVisibility(View.VISIBLE);

        String date = item.date;
        String time = item.time;
        if (tvNextDateChip != null) tvNextDateChip.setText(DateUtils.appendWeekday(date)
                + (AppUtils.isEmpty(time) ? "" : " · " + time));
        if (tvHomeName != null) tvHomeName.setText(AppUtils.isEmpty(myTeamName) ? "우리팀" : myTeamName);
        if (tvAwayName != null) tvAwayName.setText(AppUtils.isEmpty(item.opponentName) ? "-" : item.opponentName);
        if (tvPlace    != null) tvPlace.setText(AppUtils.nz(item.stadiumName, "-"));
        if (tvAddress  != null) tvAddress.setText(AppUtils.safe(item.address));

        if (imgHomeLogo != null) Glide.with(this).load(myTeamLogoUrl)
                .placeholder(R.drawable.ic_shield_gray).circleCrop().into(imgHomeLogo);
        if (imgAwayLogo != null) Glide.with(this).load(item.opponentLogoUrl)
                .placeholder(R.drawable.ic_shield_gray).circleCrop().into(imgAwayLogo);
    }

    private void loadScheduleForDate(String date) {
        if (AppUtils.isEmpty(myTeamId)) return;
        scheduleList.clear();

        db.collection("schedules").document(myTeamId).collection("events")
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot d : snap) {
                        scheduleList.add(new ScheduleItem(
                                d.getId(), myTeamId, date,
                                AppUtils.safe(d.getString("status")),
                                AppUtils.safe(d.getString("matchId")),
                                AppUtils.safe(d.getString("title")),
                                AppUtils.safe(d.getString("time")),
                                AppUtils.safe(d.getString("opponentTeamName")),
                                AppUtils.safe(d.getString("stadiumName")),
                                AppUtils.safe(d.getString("address"))
                        ));
                    }
                    if (progressSelectedDate != null) progressSelectedDate.setVisibility(View.GONE);
                    renderDateCards();
                });
    }

    private void renderDateCards() {
        if (selectedDateList == null) return;
        selectedDateList.removeAllViews();

        if (scheduleList.isEmpty()) {
            TextView msg = new TextView(this);
            msg.setText("선택된 날짜에 일정이 없습니다.");
            msg.setTextSize(14f);
            msg.setTextColor(0xFF6B7280);
            msg.setGravity(android.view.Gravity.CENTER);
            msg.setPadding(0, dp(24), 0, dp(24));
            selectedDateList.addView(msg);
            return;
        }

        for (ScheduleItem it : scheduleList) {
            View card = LayoutInflater.from(this)
                    .inflate(R.layout.view_next_schedule_card, selectedDateList, false);
            View loading  = card.findViewById(R.id.scheduleLoading);
            View content  = card.findViewById(R.id.scheduleContent);
            TextView chip = card.findViewById(R.id.tvNextDateChip);
            TextView home = card.findViewById(R.id.tvHomeName);
            TextView away = card.findViewById(R.id.tvAwayName);
            TextView place = card.findViewById(R.id.tvPlace);
            TextView addr  = card.findViewById(R.id.tvAddress);
            ImageView iHome = card.findViewById(R.id.imgHomeLogo);
            ImageView iAway = card.findViewById(R.id.imgAwayLogo);

            if (loading != null) loading.setVisibility(View.GONE);
            if (content != null) content.setVisibility(View.VISIBLE);
            if (chip  != null) chip.setText(DateUtils.appendWeekday(it.date)
                    + (AppUtils.isEmpty(it.time) ? "" : " · " + it.time));
            if (home  != null) home.setText(AppUtils.isEmpty(myTeamName) ? "우리팀" : myTeamName);
            if (away  != null) away.setText(AppUtils.isEmpty(it.opponentName) ? "-" : it.opponentName);
            if (place != null) place.setText(AppUtils.nz(it.stadiumName, "-"));
            if (addr  != null) addr.setText(AppUtils.safe(it.address));
            if (iHome != null) Glide.with(this).load(myTeamLogoUrl)
                    .placeholder(R.drawable.ic_shield_gray).circleCrop().into(iHome);
            if (iAway != null) Glide.with(this).load(it.opponentLogoUrl)
                    .placeholder(R.drawable.ic_shield_gray).circleCrop().into(iAway);

            selectedDateList.addView(card);
        }
    }

    private String todayString() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.KOREAN, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}