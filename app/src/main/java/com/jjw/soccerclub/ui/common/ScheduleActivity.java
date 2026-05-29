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
import com.jjw.soccerclub.util.GlideHelper;
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
            myUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            db.collection("profiles").document(myUid).get()
                    .addOnSuccessListener(doc -> {
                        myTeamId   = AppUtils.safe(doc.getString("myTeam"));
                        myNickname = AppUtils.safe(doc.getString("nickname"));
                        if (AppUtils.isEmpty(myTeamId)) {
                            if (state != null) { state.setEmptyMessage("소속 팀이 없어요."); state.showEmpty(); }
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
    }

    // ── 다가오는 일정 ─────────────────────────────────────────────────────────────

    private void findNextSchedule() {
        long now = System.currentTimeMillis();
        db.collection("schedules").document(myTeamId).collection("events").get()
                .addOnSuccessListener(snap -> {
                    long nearest = Long.MAX_VALUE;
                    DocumentSnapshot nearestDoc = null;
                    for (QueryDocumentSnapshot d : snap) {
                        Long ts = d.getLong("matchTs");
                        if (ts != null && ts > now && ts < nearest) {
                            nearest = ts;
                            nearestDoc = d;
                        }
                    }
                    if (nearestDoc != null) {
                        bindNextScheduleCard(nearestDoc);
                        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.VISIBLE);
                    } else {
                        if (nextScheduleCard != null) nextScheduleCard.setVisibility(View.GONE);
                    }
                });
    }

    private void bindNextScheduleCard(DocumentSnapshot doc) {
        if (scheduleLoading != null) scheduleLoading.setVisibility(View.GONE);
        if (scheduleContent != null) scheduleContent.setVisibility(View.VISIBLE);
        if (tvNextDateChip  != null) tvNextDateChip.setText(
                DateUtils.appendWeekday(doc.getString("date"))
                        + (AppUtils.isEmpty(doc.getString("time")) ? "" : " · " + doc.getString("time")));
        if (tvHomeName != null) tvHomeName.setText(AppUtils.isEmpty(myTeamName) ? "우리팀" : myTeamName);
        if (tvAwayName != null) tvAwayName.setText(AppUtils.safe(doc.getString("opponentTeamName")));
        if (tvPlace    != null) tvPlace.setText(AppUtils.nz(doc.getString("stadiumName"), "-"));
        if (tvAddress  != null) tvAddress.setText(AppUtils.safe(doc.getString("address")));

        if (imgHomeLogo != null) Glide.with(this).load(myTeamLogoUrl)
                .placeholder(R.drawable.ic_shield_gray).circleCrop().into(imgHomeLogo);
        if (imgAwayLogo != null) Glide.with(this).load(doc.getString("opponentLogoUrl"))
                .placeholder(R.drawable.ic_shield_gray).circleCrop().into(imgAwayLogo);

        // ✅ 참석 투표 버튼
        MaterialButton btnVote = nextScheduleCard != null
                ? nextScheduleCard.findViewById(R.id.btnVoteAttendance) : null;
        if (btnVote != null) {
            final String eventId = doc.getId();
            btnVote.setOnClickListener(v -> showAttendanceDialog(eventId));
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
                                AppUtils.safe(AppUtils.firstNonEmpty(
                                        d.getString("homeTeamName"), myTeamName)),
                                AppUtils.safe(d.getString("time")),
                                AppUtils.safe(d.getString("opponentTeamName")),
                                AppUtils.safe(d.getString("stadiumName")),
                                AppUtils.safe(d.getString("address"))
                        );
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
                    .inflate(R.layout.schedule_item, selectedDateList, false);

            TextView tvTitle    = card.findViewById(R.id.tvTitle);
            TextView tvTime     = card.findViewById(R.id.tvTime);
            TextView tvOpponent = card.findViewById(R.id.tvOpponent);
            TextView tvStadium  = card.findViewById(R.id.tvStadium);
            TextView tvAddr     = card.findViewById(R.id.tvAddress);
            MaterialButton btnWrite = card.findViewById(R.id.btnWriteRecord);
            // ✅ 참석 투표 버튼
            MaterialButton btnVote = card.findViewById(R.id.btnVoteAttendance);

            if (tvTitle    != null) tvTitle.setText(AppUtils.firstNonEmpty(it.title, it.opponentName, "-"));
            if (tvTime     != null) tvTime.setText("시간: " + AppUtils.nz(it.time, "-"));
            if (tvOpponent != null) tvOpponent.setText("상대팀: " + AppUtils.nz(it.opponentName, "-"));
            if (tvStadium  != null) tvStadium.setText("장소: " + AppUtils.nz(it.stadiumName, "-"));
            if (tvAddr     != null) tvAddr.setText(AppUtils.safe(it.address));

            // 기록하기 버튼
            if (btnWrite != null) {
                btnWrite.setVisibility(View.VISIBLE);
                btnWrite.setOnClickListener(v -> openWriteRecord(it));
            }

            // ✅ 참석 투표 버튼
            if (btnVote != null) {
                final String eventId = it.eventId;
                btnVote.setOnClickListener(v -> showAttendanceDialog(eventId));
            }

            selectedDateList.addView(card);
        }
    }

    private void openWriteRecord(ScheduleItem item) {
        android.content.Intent intent = new android.content.Intent(
                this, com.jjw.soccerclub.ui.team.WriteRecordFragment.class);
        intent.putExtra("eventId", item.eventId);
        intent.putExtra("myTeamId", myTeamId);
        startActivity(intent);
    }

    // ── ✅ 참석 투표 다이얼로그 ──────────────────────────────────────────────────

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

        // 투표 데이터 로드
        loadVotes(eventId, myUid, tvMyStatus, tvAttendHeader, tvAbsentHeader,
                tvNotVotedHeader, attendList, absentList, notVotedList,
                adAttend, adAbsent, adNotVoted);

        // 참석 버튼
        btnAttend.setOnClickListener(v -> {
            castVote(eventId, "attend");
            tvMyStatus.setText("✓ 참석으로 투표했어요");
            tvMyStatus.setTextColor(0xFF1565C0);
            loadVotes(eventId, myUid, tvMyStatus, tvAttendHeader, tvAbsentHeader,
                    tvNotVotedHeader, attendList, absentList, notVotedList,
                    adAttend, adAbsent, adNotVoted);
        });

        // 불참 버튼
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

    // ── 투표 저장 ─────────────────────────────────────────────────────────────────

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
                        CustomToast.success(this, "attend".equals(status) ? "참석으로 투표했어요!" : "불참으로 투표했어요."));
    }

    // ── 투표 현황 로드 ────────────────────────────────────────────────────────────

    private void loadVotes(String eventId, String myUid,
                           TextView tvMyStatus,
                           TextView tvAttendHeader, TextView tvAbsentHeader, TextView tvNotVotedHeader,
                           List<String> attendList, List<String> absentList, List<String> notVotedList,
                           AttendanceAdapter adAttend, AttendanceAdapter adAbsent, AttendanceAdapter adNotVoted) {

        // 먼저 팀원 목록 가져오기
        db.collection("teams").document(myTeamId).get()
                .addOnSuccessListener(teamDoc -> {
                    List<String> members = (List<String>) teamDoc.get("members");
                    if (members == null || members.isEmpty()) return;

                    // 투표 현황 가져오기
                    db.collection("schedules").document(myTeamId)
                            .collection("events").document(eventId)
                            .collection("votes").get()
                            .addOnSuccessListener(votesSnap -> {
                                Map<String, String> voteMap = new HashMap<>(); // uid → status
                                Map<String, String> nickMap = new HashMap<>(); // uid → nickname

                                for (QueryDocumentSnapshot v : votesSnap) {
                                    voteMap.put(v.getId(), AppUtils.safe(v.getString("status")));
                                    nickMap.put(v.getId(), AppUtils.safe(v.getString("nickname")));
                                }

                                // 내 투표 상태 업데이트
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

                                // 목록 분류 → 닉네임 조회 필요
                                List<String> attendUids   = new ArrayList<>();
                                List<String> absentUids   = new ArrayList<>();
                                List<String> notVotedUids = new ArrayList<>();

                                for (String uid : members) {
                                    String vote = voteMap.get(uid);
                                    if ("attend".equals(vote))       attendUids.add(uid);
                                    else if ("absent".equals(vote))  absentUids.add(uid);
                                    else                             notVotedUids.add(uid);
                                }

                                // 닉네임 조회 후 목록 업데이트
                                fetchNicknames(attendUids, nickMap, attendList, adAttend,
                                        tvAttendHeader, "참석");
                                fetchNicknames(absentUids, nickMap, absentList, adAbsent,
                                        tvAbsentHeader, "불참");
                                fetchNicknames(notVotedUids, nickMap, notVotedList, adNotVoted,
                                        tvNotVotedHeader, "미투표");
                            });
                });
    }

    private void fetchNicknames(List<String> uids, Map<String, String> nickMap,
                                List<String> targetList, AttendanceAdapter adapter,
                                TextView header, String label) {
        targetList.clear();
        if (uids.isEmpty()) {
            adapter.notifyDataSetChanged();
            header.setText(label + " (0)");
            return;
        }

        // 이미 nickMap에 있는 것은 바로 사용
        List<String> toFetch = new ArrayList<>();
        for (String uid : uids) {
            if (nickMap.containsKey(uid)) targetList.add(nickMap.get(uid));
            else toFetch.add(uid);
        }

        if (toFetch.isEmpty()) {
            adapter.notifyDataSetChanged();
            header.setText(label + " (" + targetList.size() + ")");
            return;
        }

        // 닉네임 없는 uid는 profiles에서 조회
        int[] remaining = {(toFetch.size() + 9) / 10};
        for (int i = 0; i < toFetch.size(); i += 10) {
            List<String> chunk = toFetch.subList(i, Math.min(i + 10, toFetch.size()));
            db.collection("profiles").whereIn("__name__", chunk).get()
                    .addOnSuccessListener(snap -> {
                        for (DocumentSnapshot d : snap.getDocuments()) {
                            targetList.add(AppUtils.safe(d.getString("nickname")));
                        }
                        remaining[0]--;
                        if (remaining[0] <= 0) {
                            adapter.notifyDataSetChanged();
                            header.setText(label + " (" + targetList.size() + ")");
                        }
                    });
        }
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