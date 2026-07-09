package com.jjw.soccerclub.common;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class CustomCalendarView extends LinearLayout {

    private TextView tvCurrentMonth, btnPrevMonth, btnNextMonth;
    private RecyclerView calendarRecyclerView;
    private com.jjw.soccerclub.adapter.CalendarAdapter adapter;

    public static Calendar currentCalendar;

    private int selectedYear = -1, selectedMonth = -1, selectedDay = -1;

    private static final long FAR_FUTURE = Long.MAX_VALUE / 4;

    public interface OnDateClickListener {
        void onDateClick(int year, int month, int day);
    }
    private OnDateClickListener onDateClickListener;
    public void setOnDateClickListener(OnDateClickListener listener) {
        this.onDateClickListener = listener;
    }

    public CustomCalendarView(Context context) {
        super(context);
        init(context);
    }

    public CustomCalendarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_custom_calendar, this, true);

        tvCurrentMonth       = findViewById(R.id.tvCurrentMonth);
        btnPrevMonth         = findViewById(R.id.btnPrevMonth);
        btnNextMonth         = findViewById(R.id.btnNextMonth);
        calendarRecyclerView = findViewById(R.id.calendarRecyclerView);

        calendarRecyclerView.setLayoutManager(new GridLayoutManager(context, 7));
        adapter = new com.jjw.soccerclub.adapter.CalendarAdapter();
        calendarRecyclerView.setAdapter(adapter);

        currentCalendar = Calendar.getInstance();
        updateCalendar();

        btnPrevMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, -1);
            selectedDay = -1;
            updateCalendar();
        });
        btnNextMonth.setOnClickListener(v -> {
            currentCalendar.add(Calendar.MONTH, 1);
            selectedDay = -1;
            updateCalendar();
        });

        tvCurrentMonth.setOnClickListener(v -> showMonthYearPicker());

        adapter.setOnItemClickListener((position, calendarDate) -> {
            if (!calendarDate.inThisMonth || onDateClickListener == null) return;
            int y = currentCalendar.get(Calendar.YEAR);
            int m = currentCalendar.get(Calendar.MONTH);
            int d = calendarDate.day;
            selectedYear  = y;
            selectedMonth = m;
            selectedDay   = d;
            adapter.setSelectedDay(y, m, d);
            onDateClickListener.onDateClick(y, m, d);
        });
    }

    private void updateCalendar() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 M월", Locale.KOREAN);
        tvCurrentMonth.setText(sdf.format(currentCalendar.getTime()));

        List<CalendarDate> days = new ArrayList<>();

        Calendar temp = (Calendar) currentCalendar.clone();
        temp.set(Calendar.DAY_OF_MONTH, 1);
        int firstDayOfWeek = temp.get(Calendar.DAY_OF_WEEK) - 1;

        temp.add(Calendar.MONTH, -1);
        int prevMonthLastDay = temp.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = firstDayOfWeek - 1; i >= 0; i--) {
            days.add(new CalendarDate(prevMonthLastDay - i, false, false));
        }

        temp = (Calendar) currentCalendar.clone();
        int maxDay = temp.getActualMaximum(Calendar.DAY_OF_MONTH);
        for (int i = 1; i <= maxDay; i++) {
            days.add(new CalendarDate(i, true, false));
        }

        int addCount = 42 - days.size();
        for (int i = 1; i <= addCount; i++) {
            days.add(new CalendarDate(i, false, false));
        }

        adapter.setDays(days);

        int y = currentCalendar.get(Calendar.YEAR);
        int m = currentCalendar.get(Calendar.MONTH);
        adapter.setYearMonth(y, m);

        if (selectedYear == y && selectedMonth == m) {
            adapter.setSelectedDay(y, m, selectedDay);
        } else {
            adapter.setSelectedDay(y, m, -1);
        }

        fetchScheduleDays(days);
    }

    private void showMonthYearPicker() {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_select_year_month, null);
        Spinner spinnerYear  = dialogView.findViewById(R.id.spinnerYear);
        Spinner spinnerMonth = dialogView.findViewById(R.id.spinnerMonth);

        List<Integer> years = new ArrayList<>();
        for (int i = 2015; i <= 2035; i++) years.add(i);

        List<String> months = new ArrayList<>();
        for (int i = 1; i <= 12; i++) months.add(i + "월");

        ArrayAdapter<Integer> yearAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, years);
        ArrayAdapter<String> monthAdapter = new ArrayAdapter<>(
                getContext(), android.R.layout.simple_spinner_item, months);
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerYear.setAdapter(yearAdapter);
        spinnerMonth.setAdapter(monthAdapter);
        spinnerYear.setSelection(years.indexOf(currentCalendar.get(Calendar.YEAR)));
        spinnerMonth.setSelection(currentCalendar.get(Calendar.MONTH));

        new AlertDialog.Builder(getContext())
                .setTitle("연도 / 월 선택")
                .setView(dialogView)
                .setPositiveButton("확인", (d, which) -> {
                    currentCalendar.set(Calendar.YEAR,  years.get(spinnerYear.getSelectedItemPosition()));
                    currentCalendar.set(Calendar.MONTH, spinnerMonth.getSelectedItemPosition());
                    selectedDay = -1;
                    updateCalendar();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void fetchScheduleDays(List<CalendarDate> days) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) return;
        String myUid = auth.getCurrentUser().getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("profiles").document(myUid).get()
                .addOnSuccessListener(profileSnap -> {
                    String myTeamId = profileSnap.getString("myTeam");
                    if (AppUtils.isEmpty(myTeamId)) return;

                    String ym = new SimpleDateFormat("yyyy-MM", Locale.KOREAN)
                            .format(currentCalendar.getTime());
                    final long now = System.currentTimeMillis();

                    db.collection("schedules").document(myTeamId)
                            .collection("events")
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                for (QueryDocumentSnapshot doc : querySnapshot) {
                                    String date = doc.getString("date");
                                    if (date == null || !date.startsWith(ym)) continue;

                                    Long endTs = getNumberAsLong(doc.get("endTs"));
                                    String time = doc.getString("time");

                                    long endMs = (endTs != null)
                                            ? AppUtils.normalizeToMillis(endTs)
                                            : DateUtils.computeEndMillis(date, time);

                                    int eD;
                                    try {
                                        eD = Integer.parseInt(date.substring(8, 10));
                                    } catch (Exception e) {
                                        continue;
                                    }

                                    for (CalendarDate cd : days) {
                                        if (cd.inThisMonth && cd.day == eD) {
                                            cd.hasMatch    = true;
                                            cd.isPastMatch = (endMs <= now);
                                            break;
                                        }
                                    }
                                }

                                adapter.setDays(days);
                                int y = currentCalendar.get(Calendar.YEAR);
                                int m = currentCalendar.get(Calendar.MONTH);
                                adapter.setYearMonth(y, m);
                                if (selectedYear == y && selectedMonth == m) {
                                    adapter.setSelectedDay(y, m, selectedDay);
                                } else {
                                    adapter.setSelectedDay(y, m, -1);
                                }
                            });
                });
    }

    private Long getNumberAsLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        return null;
    }

    public static class CalendarDate {
        public int day;
        public boolean inThisMonth;
        public boolean hasMatch;
        public boolean isPastMatch;

        public CalendarDate(int day, boolean inThisMonth) {
            this(day, inThisMonth, false);
        }

        public CalendarDate(int day, boolean inThisMonth, boolean hasMatch) {
            this.day = day;
            this.inThisMonth = inThisMonth;
            this.hasMatch = hasMatch;
            this.isPastMatch = false;
        }
    }
}