package com.jjw.soccerclub.ui.common;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomCalendarView;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.common.StateLayout;
import com.jjw.soccerclub.model.ScheduleItem;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ScheduleActivity extends AppCompatActivity {

    private StateLayout state;
    private TextView tvSelectedDateTitle;
    private ProgressBar progressSelectedDate;
    private LinearLayout selectedDateList;
    private View nextScheduleCard, scheduleLoading, scheduleContent;
    private TextView tvNextDateChip, tvHomeName, tvAwayName, tvPlace, tvAddress;
    private ImageView imgHomeLogo, imgAwayLogo;

    // ✅ 다가오는 일정 투표 버튼 (제목 옆) + 대상 eventId
    private MaterialButton btnVoteAttendance;
    private String nextEventId = "";
    private long nextStartMs = -1L;

    private final List<ScheduleItem> scheduleList = new ArrayList<>();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private String myTeamId = "", myTeamName = "", myTeamLogoUrl = "";
    private String myUid = "", myNickname = "";

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

        // ✅ 투표 버튼 — 제목 옆 (activity_schedule.xml 에 추가됨)
        btnVoteAttendance = findViewById(R.id.btnVoteAttendance);
        if (btnVoteAttendance != null) {
            btnVoteAttendance.setOnClickListener(v -> {
                if (!AppUtils.isEmpty(nextEventId)) showAttendanceDialog(nextEventId);
                else CustomToast.warning(this, "투표할 일정이 없어요.");
            });
        }

        if (state != null) state.showLoading();

        // 달력
        FrameLayout calendarContainer = findViewById(R.id.calendarContainer);
        if (calendarContainer != null) {
            CustomCalendarView customCalendar = new CustomCalendarView(this);
            calendarContainer.addView(customCalendar);
            customCalendar.setOnDateClickListener((year, month, day) -> {
                String selectedDate = String.format(Locale.KOREAN, "%04d-%02d-%02d",
                        year, month + 1, day);
                if (tvSelectedDateTitle != null)
                    tvSelectedDateTitle.setText("선택된 날짜 (" + selectedDate + ")");
                if (progressSelectedDate != null) progressSelectedDate.setVisibility(View.VISIBLE);
                loadScheduleForDate(selectedDate);
            });
        }

        loadProfileThenTeam();
    }

    // ── 프로필 → 팀 → 일정 ────────────────────────────────────────────────────────

    private void loadProfileThenTeam() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            if (state != null) { state.setEmptyMessage("로그인이 필요해요."); state.showEmpty(); }
            return;
        }
        myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        db.collection("profiles").document(myUid).get()
                .addOnSuccessListener(snapshot -> {
                    myTeamId   = AppUtils.safe(snapshot.getString("myTeam"));
                    myNickname = AppUtils.safe(snapshot.getString("nickname"));

                    if (AppUtils.isEmpty(myTeamId)) {
                        if (state != null) {
                            state.setEmptyMessage("팀 정보가 없어요.\n먼저 '팀 만들기'로 팀을 생성해 주세요.");
                            state.showEmpty();
                        }
                        return;
                    }
                    db.collection("teams").document(myTeamId).get()
                            .addOnSuccessListener(teamDoc -> {
                                myTeamName    = AppUtils.safe(teamDoc.getString("teamName"));
                                myTeamLogoUrl = AppUtils.safe(teamDoc.getString("logoUrl"));
                                if (state != null) state.showContent();
                                findNextSchedule();
                                String today = todayString();
                                if (tvSelectedDateTitle != null)
                                    tvSelectedDateTitle.setText("선택된 날짜 (" + today + ")");
                                loadScheduleForDate(today);
                            });
                });
    }

    // ── 다가오는 일정 ─────────────────────────────────────────────────────────────

    private void findNextSchedule() {
        long now = System.currentTimeMillis();
        db.collection("schedules").document(myTeamId).collection("events").get()
                .addOnSuccessListener(snap -> {
                    long bestEnd = Long.MAX_VALUE;
                    DocumentSnapshot bestDoc = null;
                    long bestStart = -1L;

                    for (QueryDocumentSnapshot d : snap) {
                        String date = d.getString("date");
                        String time = d.getString("time");
                        Long matchTs = d.getLong("matchTs");
                        Long endTs   = d.getLong("endTs");

                        long start, end;
                        if (matchTs != null && matchTs > 0 && endTs != null && endTs > 0) {
                            start = matchTs;
                            end = endTs;
                        } else {
                            long[] se = computeStartEndFromDateTimeStrings(date, time);
                            start = se[0];
                            end = se[1];
                        }

                        // 아직 끝나지 않은 가장 가까운 일정
                        if (end > now && end < bestEnd) {
                            bestEnd = end;
                            bestStart = start;
                            bestDoc = d;
                        }
                    }

                    if (bestDoc != null) {
                        nextStartMs = bestStart;
                        bindNextScheduleCard(bestDoc);
                        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.VISIBLE);
                    } else {
                        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.GONE);
                        if (btnVoteAttendance != null) btnVoteAttendance.setVisibility(View.GONE);
                    }
                });
    }

    private void bindNextScheduleCard(DocumentSnapshot doc) {
        if (scheduleLoading != null) scheduleLoading.setVisibility(View.GONE);
        if (scheduleContent != null) scheduleContent.setVisibility(View.VISIBLE);

        if (tvNextDateChip != null) tvNextDateChip.setText(
                DateUtils.appendWeekday(doc.getString("date"))
                        + (AppUtils.isEmpty(doc.getString("time")) ? "" : " · " + doc.getString("time")));
        if (tvHomeName != null) tvHomeName.setText(AppUtils.isEmpty(myTeamName) ? "우리팀" : myTeamName);
        if (tvAwayName != null) tvAwayName.setText(AppUtils.safe(doc.getString("opponentTeamName")));

        // stadium / stadiumName 호환
        String stadiumName = AppUtils.firstNonEmpty(
                doc.getString("stadiumName"), doc.getString("stadium"));
        String address = AppUtils.firstNonEmpty(
                doc.getString("stadiumAddress"), doc.getString("address"));
        if (tvPlace   != null) tvPlace.setText(AppUtils.isEmpty(stadiumName) ? "-" : stadiumName);
        if (tvAddress != null) tvAddress.setText(AppUtils.safe(address));

        if (imgHomeLogo != null) Glide.with(this).load(myTeamLogoUrl)
                .placeholder(R.drawable.ic_shield_gray).circleCrop().into(imgHomeLogo);
        if (imgAwayLogo != null) Glide.with(this).load(doc.getString("opponentLogoUrl"))
                .placeholder(R.drawable.ic_shield_gray).circleCrop().into(imgAwayLogo);

        // ✅ 상단 투표 버튼용 eventId 저장 + 경기 시작 전까지만 노출
        nextEventId = doc.getId();
        if (btnVoteAttendance != null) {
            long now = System.currentTimeMillis();
            boolean canVote = (nextStartMs <= 0) || (now < nextStartMs);
            btnVoteAttendance.setVisibility(canVote ? View.VISIBLE : View.GONE);
        }
    }

    // ── 날짜별 일정 ───────────────────────────────────────────────────────────────

    private void loadScheduleForDate(String date) {
        if (AppUtils.isEmpty(myTeamId)) return;
        scheduleList.clear();

        db.collection("schedules").document(myTeamId).collection("events")
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener(snap -> {
                    for (QueryDocumentSnapshot d : snap) {
                        ScheduleItem item = new ScheduleItem(
                                d.getId(), myTeamId, date,
                                AppUtils.safe(d.getString("status")),
                                AppUtils.safe(d.getString("matchId")),
                                AppUtils.safe(d.getString("title")),
                                AppUtils.safe(d.getString("time")),
                                AppUtils.safe(d.getString("opponentTeamName")),
                                AppUtils.safe(AppUtils.firstNonEmpty(
                                        d.getString("stadiumName"), d.getString("stadium"))),
                                AppUtils.safe(AppUtils.firstNonEmpty(
                                        d.getString("stadiumAddress"), d.getString("address")))
                        );
                        item.opponentLogoUrl = AppUtils.safe(d.getString("opponentLogoUrl"));
                        scheduleList.add(item);
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

            ProgressBar loading = card.findViewById(R.id.scheduleLoading);
            View content        = card.findViewById(R.id.scheduleContent);
            TextView chip       = card.findViewById(R.id.tvNextDateChip);
            ImageView ivHome    = card.findViewById(R.id.imgHomeLogo);
            ImageView ivAway    = card.findViewById(R.id.imgAwayLogo);
            TextView tvHome     = card.findViewById(R.id.tvHomeName);
            TextView tvAway     = card.findViewById(R.id.tvAwayName);
            TextView tvP        = card.findViewById(R.id.tvPlace);
            TextView tvA        = card.findViewById(R.id.tvAddress);

            if (loading != null) loading.setVisibility(View.GONE);
            if (content != null) content.setVisibility(View.VISIBLE);

            String dateChip = DateUtils.appendWeekday(it.date);
            if (!AppUtils.isEmpty(it.time)) dateChip += " · " + it.time;
            if (chip != null) chip.setText(dateChip);

            if (tvHome != null) tvHome.setText(AppUtils.isEmpty(myTeamName) ? "우리팀" : myTeamName);
            if (tvAway != null) tvAway.setText(AppUtils.isEmpty(it.opponentName) ? "-" : it.opponentName);

            if (ivHome != null) {
                if (!AppUtils.isEmpty(myTeamLogoUrl))
                    Glide.with(this).load(myTeamLogoUrl)
                            .placeholder(R.drawable.ic_shield_gray).circleCrop().into(ivHome);
                else ivHome.setImageResource(R.drawable.ic_shield_gray);
            }
            if (ivAway != null) {
                if (!AppUtils.isEmpty(it.opponentLogoUrl))
                    Glide.with(this).load(it.opponentLogoUrl)
                            .placeholder(R.drawable.ic_shield_gray).circleCrop().into(ivAway);
                else ivAway.setImageResource(R.drawable.ic_shield_gray);
            }

            if (tvP != null) tvP.setText(AppUtils.isEmpty(it.stadiumName) ? "-" : it.stadiumName);
            if (tvA != null) tvA.setText(AppUtils.safe(it.address));

            // ✅ 카드 하단에 선수목록 버튼 동적 추가
            addPlayerListFooter(card, it);

            selectedDateList.addView(card);
        }
    }

    /** 카드 하단: 구분선 + 선수목록 버튼 */
    private void addPlayerListFooter(View cardRoot, ScheduleItem it) {
        View contentView = cardRoot.findViewById(R.id.scheduleContent);
        if (!(contentView instanceof LinearLayout)) return;
        LinearLayout content = (LinearLayout) contentView;

        // 구분선
        View divider = new View(this);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dLp.topMargin = dp(12);
        divider.setLayoutParams(dLp);
        divider.setBackgroundColor(0xFFE0E0E0);
        content.addView(divider);

        // 버튼 행
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(8);
        row.setLayoutParams(rowLp);
        row.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        content.addView(row);

        MaterialButton btnPlayers = new MaterialButton(this);
        btnPlayers.setText("선수목록");
        btnPlayers.setAllCaps(false);
        btnPlayers.setInsetTop(0);
        btnPlayers.setInsetBottom(0);
        btnPlayers.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF1E88E5));
        btnPlayers.setTextColor(0xFFFFFFFF);
        btnPlayers.setCornerRadius(dp(16));
        btnPlayers.setPadding(dp(16), dp(10), dp(16), dp(10));
        btnPlayers.setOnClickListener(v -> showAttendanceDialog(it.eventId));
        row.addView(btnPlayers);
    }

    // ── 참석 투표 다이얼로그 ──────────────────────────────────────────────────────

    private void showAttendanceDialog(String eventId) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_attendance_list, null);

        MaterialButton btnAttend    = dialogView.findViewById(R.id.btnVoteAttend);
        MaterialButton btnAbsent    = dialogView.findViewById(R.id.btnVoteAbsent);
        TextView tvMyStatus         = dialogView.findViewById(R.id.tvMyVoteStatus);
        TextView tvAttendHeader     = dialogView.findViewById(R.id.tvAttendHeader);
        TextView tvAbsentHeader     = dialogView.findViewById(R.id.tvAbsentHeader);
        TextView tvNotVotedHeader   = dialogView.findViewById(R.id.tvNotVotedHeader);
        RecyclerView rvAttend       = dialogView.findViewById(R.id.rvAttend);
        RecyclerView rvAbsent       = dialogView.findViewById(R.id.rvAbsent);
        RecyclerView rvNotVoted     = dialogView.findViewById(R.id.rvNotVoted);

        List<String> attendList   = new ArrayList<>();
        List<String> absentList   = new ArrayList<>();
        List<String> notVotedList = new ArrayList<>();

        AttendanceAdapter adAttend   = new AttendanceAdapter(attendList);
        AttendanceAdapter adAbsent   = new AttendanceAdapter(absentList);
        AttendanceAdapter adNotVoted = new AttendanceAdapter(notVotedList);

        rvAttend.setLayoutManager(new LinearLayoutManager(this));
        rvAbsent.setLayoutManager(new LinearLayoutManager(this));
        rvNotVoted.setLayoutManager(new LinearLayoutManager(this));
        rvAttend.setAdapter(adAttend);
        rvAbsent.setAdapter(adAbsent);
        rvNotVoted.setAdapter(adNotVoted);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("경기 참석 투표")
                .setView(new ScrollView(this) {{
                    addView(dialogView);
                }})
                .setPositiveButton("닫기", null)
                .create();

        loadVotes(eventId, myUid, tvMyStatus, tvAttendHeader, tvAbsentHeader,
                tvNotVotedHeader, attendList, absentList, notVotedList,
                adAttend, adAbsent, adNotVoted);

        btnAttend.setOnClickListener(v -> {
            castVote(eventId, "attend");
            tvMyStatus.setText("✓ 참석으로 투표했어요");
            tvMyStatus.setTextColor(0xFF1565C0);
            loadVotes(eventId, myUid, tvMyStatus, tvAttendHeader, tvAbsentHeader,
                    tvNotVotedHeader, attendList, absentList, notVotedList,
                    adAttend, adAbsent, adNotVoted);
        });

        btnAbsent.setOnClickListener(v -> {
            castVote(eventId, "absent");
            tvMyStatus.setText("✗ 불참으로 투표했어요");
            tvMyStatus.setTextColor(0xFFC62828);
            loadVotes(eventId, myUid, tvMyStatus, tvAttendHeader, tvAbsentHeader,
                    tvNotVotedHeader, attendList, absentList, notVotedList,
                    adAttend, adAbsent, adNotVoted);
        });

        dialog.show();
    }

    private void castVote(String eventId, String status) {
        if (AppUtils.isEmpty(myTeamId) || AppUtils.isEmpty(myUid)) return;
        Map<String, Object> vote = new HashMap<>();
        vote.put("status",    status);
        vote.put("nickname",  myNickname);
        vote.put("uid",       myUid);
        vote.put("timestamp", System.currentTimeMillis());
        db.collection("schedules").document(myTeamId)
                .collection("events").document(eventId)
                .collection("votes").document(myUid)
                .set(vote)
                .addOnSuccessListener(v ->
                        CustomToast.success(this, "attend".equals(status)
                                ? "참석으로 투표했어요!" : "불참으로 투표했어요."));
    }

    @SuppressWarnings("unchecked")
    private void loadVotes(String eventId, String myUid,
                           TextView tvMyStatus,
                           TextView tvAttendHeader, TextView tvAbsentHeader, TextView tvNotVotedHeader,
                           List<String> attendList, List<String> absentList, List<String> notVotedList,
                           AttendanceAdapter adAttend, AttendanceAdapter adAbsent, AttendanceAdapter adNotVoted) {

        db.collection("teams").document(myTeamId).get()
                .addOnSuccessListener(teamDoc -> {
                    List<String> members = (List<String>) teamDoc.get("members");
                    if (members == null || members.isEmpty()) return;

                    db.collection("schedules").document(myTeamId)
                            .collection("events").document(eventId)
                            .collection("votes").get()
                            .addOnSuccessListener(votesSnap -> {
                                Map<String, String> voteMap = new HashMap<>();
                                Map<String, String> nickMap = new HashMap<>();

                                for (QueryDocumentSnapshot v : votesSnap) {
                                    voteMap.put(v.getId(), AppUtils.safe(v.getString("status")));
                                    nickMap.put(v.getId(), AppUtils.safe(v.getString("nickname")));
                                }

                                String myVote = voteMap.get(myUid);
                                if ("attend".equals(myVote)) {
                                    tvMyStatus.setText("현재 참석으로 투표했어요 ✓");
                                    tvMyStatus.setTextColor(0xFF1565C0);
                                } else if ("absent".equals(myVote)) {
                                    tvMyStatus.setText("현재 불참으로 투표했어요 ✗");
                                    tvMyStatus.setTextColor(0xFFC62828);
                                } else {
                                    tvMyStatus.setText("아직 투표하지 않았어요.");
                                    tvMyStatus.setTextColor(0xFF6B7280);
                                }

                                attendList.clear();
                                absentList.clear();
                                notVotedList.clear();

                                // 팀원 닉네임 보강 조회
                                List<com.google.android.gms.tasks.Task<DocumentSnapshot>> tasks
                                        = new ArrayList<>();
                                for (String uid : members) {
                                    if (!nickMap.containsKey(uid)) {
                                        tasks.add(db.collection("profiles").document(uid).get());
                                    }
                                }

                                com.google.android.gms.tasks.Tasks.whenAllSuccess(tasks)
                                        .addOnSuccessListener(results -> {
                                            for (Object obj : results) {
                                                DocumentSnapshot p = (DocumentSnapshot) obj;
                                                if (p.exists())
                                                    nickMap.put(p.getId(),
                                                            AppUtils.safe(p.getString("nickname")));
                                            }
                                            for (String uid : members) {
                                                String nick = AppUtils.firstNonEmpty(
                                                        nickMap.get(uid), "(이름없음)");
                                                String st = voteMap.get(uid);
                                                if ("attend".equals(st)) attendList.add(nick);
                                                else if ("absent".equals(st)) absentList.add(nick);
                                                else notVotedList.add(nick);
                                            }
                                            tvAttendHeader.setText("참석자 (" + attendList.size() + ")");
                                            tvAbsentHeader.setText("불참자 (" + absentList.size() + ")");
                                            tvNotVotedHeader.setText("미투표자 (" + notVotedList.size() + ")");
                                            adAttend.notifyDataSetChanged();
                                            adAbsent.notifyDataSetChanged();
                                            adNotVoted.notifyDataSetChanged();
                                        });
                            });
                });
    }

    // ── 어댑터 ────────────────────────────────────────────────────────────────────

    static class AttendanceAdapter extends RecyclerView.Adapter<AttendanceAdapter.VH> {
        private final List<String> names;
        AttendanceAdapter(List<String> names) { this.names = names; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_attendance_member, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            h.tvNickname.setText(names.get(pos));
        }

        @Override public int getItemCount() { return names.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvNickname;
            VH(@NonNull View v) {
                super(v);
                tvNickname = v.findViewById(R.id.tvMemberNickname);
            }
        }
    }

    // ── 시간 계산 헬퍼 ────────────────────────────────────────────────────────────

    private static final long DEFAULT_MATCH_DURATION_MS = 2L * 60L * 60L * 1000L;

    private long[] computeStartEndFromDateTimeStrings(String date, String timeRange) {
        long start = 0L, end = 0L;
        try {
            String startHHmm = null, endHHmm = null;
            if (timeRange != null && timeRange.contains("~")) {
                String[] p = timeRange.split("~");
                startHHmm = p[0].trim();
                endHHmm   = p[1].trim();
            }
            if (startHHmm == null || endHHmm == null || startHHmm.isEmpty() || endHHmm.isEmpty()) {
                long base = dateToMs(date);
                start = base + 9 * 60 * 60 * 1000L;
                end   = start + DEFAULT_MATCH_DURATION_MS;
            } else {
                start = computeMatchTs(date, startHHmm);
                end   = computeMatchTs(date, endHHmm);
                if (end <= start) end += 24L * 60L * 60L * 1000L;
            }
        } catch (Exception e) {
            long base = System.currentTimeMillis();
            start = base;
            end   = base + DEFAULT_MATCH_DURATION_MS;
        }
        return new long[]{ start, end };
    }

    private long computeMatchTs(String date, String hhmm) {
        try {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return sdf.parse(date + " " + hhmm).getTime();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private long dateToMs(String date) {
        try {
            String[] p = date.split("-");
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.YEAR, Integer.parseInt(p[0]));
            cal.set(Calendar.MONTH, Integer.parseInt(p[1]) - 1);
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(p[2]));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────────────

    private String todayString() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.KOREAN, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}