package com.jjw.soccerclub.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jjw.soccerclub.R;
import com.jjw.soccerclub.ui.common.BaseActivity;
import com.jjw.soccerclub.util.AppUtils;
import com.jjw.soccerclub.util.DateUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MercenaryActivitiesActivity extends BaseActivity {

    private RecyclerView recycler;
    private TextView tvEmpty;
    private View progress;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final List<MercActivity> items = new ArrayList<>();
    private MercAdapter adapter;
    private String myUid = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mercenary_activities);

        recycler = findViewById(R.id.recycler);
        tvEmpty  = findViewById(R.id.tvEmpty);
        progress = findViewById(R.id.progress);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MercAdapter();
        recycler.setAdapter(adapter);

        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        loadActivities();
    }

    @SuppressWarnings("unchecked")
    private void loadActivities() {
        if (AppUtils.isEmpty(myUid)) { showEmpty("로그인이 필요해요."); return; }
        if (progress != null) progress.setVisibility(View.VISIBLE);

        db.collection("profiles").document(myUid)
                .collection("mercenaryActivities")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    items.clear();
                    if (qs.isEmpty()) {
                        if (progress != null) progress.setVisibility(View.GONE);
                        showEmpty("아직 용병 활동이 없어요.");
                        return;
                    }

                    final int[] pending = {qs.size()};
                    for (DocumentSnapshot doc : qs.getDocuments()) {
                        MercActivity ma = new MercActivity();
                        ma.postId   = doc.getId();
                        ma.teamId   = AppUtils.safe(doc.getString("teamId"));
                        ma.teamName = AppUtils.safe(doc.getString("teamName"));
                        ma.date     = AppUtils.safe(doc.getString("date"));
                        ma.time     = AppUtils.safe(doc.getString("time"));
                        ma.stadium  = AppUtils.safe(doc.getString("stadium"));

                        loadMatchDetails(ma, () -> {
                            items.add(ma);
                            pending[0]--;
                            if (pending[0] <= 0) {
                                items.sort((a, b) -> b.date.compareTo(a.date));
                                if (progress != null) progress.setVisibility(View.GONE);
                                adapter.notifyDataSetChanged();
                                if (items.isEmpty()) showEmpty("아직 용병 활동이 없어요.");
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    if (progress != null) progress.setVisibility(View.GONE);
                    showEmpty("불러오기 실패: " + e.getMessage());
                });
    }

    @SuppressWarnings("unchecked")
    private void loadMatchDetails(MercActivity ma, Runnable done) {
        if (AppUtils.isEmpty(ma.teamId)) { done.run(); return; }

        db.collection("schedules").document(ma.teamId)
                .collection("events")
                .whereEqualTo("date", ma.date)
                .limit(3)
                .get()
                .addOnSuccessListener(qs -> {
                    String eventId = null;
                    for (DocumentSnapshot ev : qs.getDocuments()) {
                        ma.opponentName    = AppUtils.safe(ev.getString("opponentTeamName"));
                        ma.opponentLogoUrl = AppUtils.safe(ev.getString("opponentLogoUrl"));
                        ma.address         = AppUtils.safe(AppUtils.firstNonEmpty(
                                ev.getString("stadiumAddress"), ev.getString("address")));
                        if (AppUtils.isEmpty(ma.stadium))
                            ma.stadium = AppUtils.safe(AppUtils.firstNonEmpty(
                                    ev.getString("stadiumName"), ev.getString("stadium")));

                        Long sf = ev.getLong("scoreFor");
                        Long sa = ev.getLong("scoreAgainst");
                        if (sf != null && sa != null) {
                            ma.scoreFor = sf.intValue();
                            ma.scoreAgainst = sa.intValue();
                            ma.hasResult = true;
                        }
                        eventId = ev.getId();
                        break;
                    }

                    final String finalEventId = eventId;
                    db.collection("teams").document(ma.teamId).get()
                            .addOnSuccessListener(team -> {
                                ma.teamLogoUrl = AppUtils.safe(team.getString("logoUrl"));

                                if (finalEventId != null) {
                                    String matchDocId = finalEventId + "_" + ma.teamId;
                                    db.collection("matches").document(matchDocId).get()
                                            .addOnSuccessListener(matchDoc -> {
                                                if (matchDoc.exists()) {
                                                    List<Map<String, Object>> events =
                                                            (List<Map<String, Object>>) matchDoc.get("goalEvents");
                                                    if (events != null) {
                                                        for (Map<String, Object> ge : events) {
                                                            if (myUid.equals(ge.get("scorerId"))) ma.myGoals++;
                                                            if (myUid.equals(ge.get("assistId"))) ma.myAssists++;
                                                        }
                                                    }
                                                }
                                                done.run();
                                            })
                                            .addOnFailureListener(e -> done.run());
                                } else {
                                    done.run();
                                }
                            })
                            .addOnFailureListener(e -> done.run());
                })
                .addOnFailureListener(e -> done.run());
    }

    private void showEmpty(String msg) {
        if (tvEmpty != null) { tvEmpty.setVisibility(View.VISIBLE); tvEmpty.setText(msg); }
        recycler.setVisibility(View.GONE);
    }

    // ── 모델 ──────────────────────────────────────────────────────────────────────

    static class MercActivity {
        String postId, teamId, teamName, date, time, stadium, address;
        String opponentName = "", teamLogoUrl = "", opponentLogoUrl = "";
        int scoreFor = -1, scoreAgainst = -1;
        boolean hasResult = false;
        int myGoals = 0, myAssists = 0;
    }

    // ── 어댑터 ────────────────────────────────────────────────────────────────────

    class MercAdapter extends RecyclerView.Adapter<MercAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int vt) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_mercenary_activity, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            MercActivity m = items.get(pos);

            // ── 일정 카드 부분 (view_next_schedule_card의 뷰들) ──
            if (h.scheduleLoading != null) h.scheduleLoading.setVisibility(View.GONE);
            if (h.scheduleContent != null) h.scheduleContent.setVisibility(View.VISIBLE);

            // 날짜+시간 칩
            String dateChip = DateUtils.appendWeekday(m.date);
            if (!AppUtils.isEmpty(m.time)) dateChip += " · " + m.time;
            if (h.tvNextDateChip != null) h.tvNextDateChip.setText(dateChip);

            // 팀명
            if (h.tvHomeName != null) h.tvHomeName.setText(
                    AppUtils.isEmpty(m.teamName) ? "-" : m.teamName);
            if (h.tvAwayName != null) h.tvAwayName.setText(
                    AppUtils.isEmpty(m.opponentName) ? "상대팀" : m.opponentName);

            // 팀 로고
            if (h.imgHomeLogo != null) {
                if (!AppUtils.isEmpty(m.teamLogoUrl))
                    Glide.with(h.imgHomeLogo.getContext()).load(m.teamLogoUrl)
                            .placeholder(R.drawable.ic_shield_gray).circleCrop().into(h.imgHomeLogo);
                else h.imgHomeLogo.setImageResource(R.drawable.ic_shield_gray);
            }
            if (h.imgAwayLogo != null) {
                if (!AppUtils.isEmpty(m.opponentLogoUrl))
                    Glide.with(h.imgAwayLogo.getContext()).load(m.opponentLogoUrl)
                            .placeholder(R.drawable.ic_shield_gray).circleCrop().into(h.imgAwayLogo);
                else h.imgAwayLogo.setImageResource(R.drawable.ic_shield_gray);
            }

            // 장소
            if (h.tvPlace != null) h.tvPlace.setText(
                    AppUtils.isEmpty(m.stadium) ? "-" : m.stadium);
            if (h.tvAddress != null) h.tvAddress.setText(AppUtils.safe(m.address));

            // ── 스코어 (카드 내에 동적 추가) ──
            if (m.hasResult && h.scheduleContent instanceof android.widget.LinearLayout) {
                android.widget.LinearLayout content = (android.widget.LinearLayout) h.scheduleContent;
                // 중복 방지
                if (content.findViewWithTag("mercScore") == null) {
                    TextView scoreView = new TextView(content.getContext());
                    scoreView.setTag("mercScore");
                    scoreView.setText(m.scoreFor + " : " + m.scoreAgainst);
                    scoreView.setTextSize(22f);
                    scoreView.setTypeface(null, android.graphics.Typeface.BOLD);
                    scoreView.setGravity(android.view.Gravity.CENTER);

                    if (m.scoreFor > m.scoreAgainst) scoreView.setTextColor(0xFF1565C0);
                    else if (m.scoreFor < m.scoreAgainst) scoreView.setTextColor(0xFFC62828);
                    else scoreView.setTextColor(0xFF757575);

                    android.widget.LinearLayout.LayoutParams lp =
                            new android.widget.LinearLayout.LayoutParams(
                                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
                    float density = content.getResources().getDisplayMetrics().density;
                    lp.topMargin = (int)(4 * density);
                    lp.bottomMargin = (int)(4 * density);
                    scoreView.setLayoutParams(lp);

                    // tvNextDateChip 다음에 삽입
                    int insertIdx = 1;
                    for (int i = 0; i < content.getChildCount(); i++) {
                        if (content.getChildAt(i).getId() == R.id.tvNextDateChip) {
                            insertIdx = i + 1;
                            break;
                        }
                    }
                    content.addView(scoreView, Math.min(insertIdx, content.getChildCount()));
                }
            }

            // ── 내 기록 ──
            if (h.tvMyGoals != null) h.tvMyGoals.setText(String.valueOf(m.myGoals));
            if (h.tvMyAssists != null) h.tvMyAssists.setText(String.valueOf(m.myAssists));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            // view_next_schedule_card 뷰들
            View scheduleLoading, scheduleContent;
            TextView tvNextDateChip, tvHomeName, tvAwayName, tvPlace, tvAddress;
            ImageView imgHomeLogo, imgAwayLogo;
            // 내 기록
            TextView tvMyGoals, tvMyAssists;

            VH(@NonNull View v) {
                super(v);
                // include된 카드 안의 뷰
                scheduleLoading = v.findViewById(R.id.scheduleLoading);
                scheduleContent = v.findViewById(R.id.scheduleContent);
                tvNextDateChip  = v.findViewById(R.id.tvNextDateChip);
                imgHomeLogo     = v.findViewById(R.id.imgHomeLogo);
                imgAwayLogo     = v.findViewById(R.id.imgAwayLogo);
                tvHomeName      = v.findViewById(R.id.tvHomeName);
                tvAwayName      = v.findViewById(R.id.tvAwayName);
                tvPlace         = v.findViewById(R.id.tvPlace);
                tvAddress       = v.findViewById(R.id.tvAddress);
                // 하단 내 기록
                tvMyGoals       = v.findViewById(R.id.tvMyGoals);
                tvMyAssists     = v.findViewById(R.id.tvMyAssists);
            }
        }
    }
}