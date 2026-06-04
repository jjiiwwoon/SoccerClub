package com.jjw.soccerclub.ui.team;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.common.StateLayout;
import com.jjw.soccerclub.ui.common.BaseActivity;
import com.jjw.soccerclub.util.AppUtils;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JoinRequestsActivity extends BaseActivity {

    private StateLayout state;
    private RecyclerView recyclerView;
    private TextView tvEmpty;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private ListenerRegistration requestsReg;
    private String teamId;

    private RequestAdapter adapter;
    private final List<RequestItem> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_requests);

        teamId = getIntent().getStringExtra("teamId");
        if (AppUtils.isEmpty(teamId)) { finish(); return; }

        state       = findViewById(R.id.stateLayout);
        recyclerView = findViewById(R.id.recyclerRequests);
        tvEmpty     = findViewById(R.id.tvEmpty);

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RequestAdapter();
        recyclerView.setAdapter(adapter);

        if (state != null) state.showLoading();
        listenRequests();
    }

    @Override
    protected void onDestroy() {
        if (requestsReg != null) { requestsReg.remove(); requestsReg = null; }
        super.onDestroy();
    }

    // ── 실시간 리스너 ─────────────────────────────────────────────────────────────

    private void listenRequests() {
        requestsReg = db.collection("teams").document(teamId)
                .collection("joinRequests")
                .whereEqualTo("status", "pending")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        if (state != null) state.showEmpty();
                        return;
                    }
                    items.clear();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        RequestItem it = new RequestItem();
                        it.uid       = doc.getString("uid");
                        it.nickname  = doc.getString("nickname");
                        it.skill     = doc.getLong("skill") != null
                                ? doc.getLong("skill").intValue() : -1;
                        it.timestamp = doc.getLong("timestamp") != null
                                ? doc.getLong("timestamp") : 0L;
                        items.add(it);
                    }
                    adapter.notifyDataSetChanged();

                    if (state != null) state.showContent();
                    if (tvEmpty != null)
                        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                });
    }

    // ── 수락 ─────────────────────────────────────────────────────────────────────

    private void acceptRequest(RequestItem item, int position) {
        if (AppUtils.isEmpty(item.uid)) return;

        DocumentReference teamRef    = db.collection("teams").document(teamId);
        DocumentReference profileRef = db.collection("profiles").document(item.uid);
        DocumentReference reqRef     = teamRef.collection("joinRequests").document(item.uid);

        db.runTransaction(transaction -> {
            DocumentSnapshot teamSnap    = transaction.get(teamRef);
            DocumentSnapshot profileSnap = transaction.get(profileRef);

            // 이미 멤버인지 확인
            List<String> members = (List<String>) teamSnap.get("members");
            if (members != null && members.contains(item.uid)) {
                throw new RuntimeException("이미 팀원입니다.");
            }

            long skill = profileSnap.getLong("skill") != null
                    ? profileSnap.getLong("skill") : 0L;

            // members 추가 + skillSum/memberCount/skillAverage 업데이트
            transaction.update(teamRef, "members", FieldValue.arrayUnion(item.uid));
            transaction.update(teamRef, "memberCount", FieldValue.increment(1L));
            transaction.update(teamRef, "skillSum", FieldValue.increment(skill));

            long curSum   = teamSnap.getLong("skillSum")    != null ? teamSnap.getLong("skillSum")   : 0L;
            long curCount = teamSnap.getLong("memberCount") != null ? teamSnap.getLong("memberCount") : 0L;
            long newSum   = curSum + skill;
            long newCount = curCount + 1;
            int  newAvg   = newCount > 0 ? (int)(newSum / newCount) : 0;
            transaction.update(teamRef, "skillAverage", newAvg);

            // 프로필에 myTeam 설정
            transaction.update(profileRef, "myTeam", teamId);

            // 신청 상태 accepted로 변경
            transaction.update(reqRef, "status", "accepted");

            return null;
        })
        .addOnSuccessListener(v -> {
            CustomToast.success(this, item.nickname + "님의 가입을 수락했어요!");
            // 리스너가 자동으로 목록 갱신
        })
        .addOnFailureListener(e -> {
            String msg = e.getMessage() != null ? e.getMessage() : "수락 실패";
            CustomToast.error(this, msg);
        });
    }

    // ── 거절 ─────────────────────────────────────────────────────────────────────

    private void rejectRequest(RequestItem item) {
        if (AppUtils.isEmpty(item.uid)) return;

        db.collection("teams").document(teamId)
                .collection("joinRequests").document(item.uid)
                .update("status", "rejected")
                .addOnSuccessListener(v ->
                        CustomToast.info(this, item.nickname + "님의 신청을 거절했어요."))
                .addOnFailureListener(e ->
                        CustomToast.error(this, "거절 실패: " + e.getMessage()));
    }

    // ── 데이터 모델 ───────────────────────────────────────────────────────────────

    static class RequestItem {
        String uid;
        String nickname;
        int    skill;
        long   timestamp;
    }

    // ── 어댑터 ────────────────────────────────────────────────────────────────────

    class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.VH> {

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_join_request, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH h, int pos) {
            RequestItem item = items.get(pos);

            h.tvNickname.setText(AppUtils.safe(item.nickname));
            h.tvSkill.setText("실력: " + (item.skill < 0 ? "-" : item.skill));
            h.tvTime.setText(com.jjw.soccerclub.util.DateUtils.formatRelativeTime(item.timestamp));

            h.btnAccept.setOnClickListener(v -> {
                h.btnAccept.setEnabled(false);
                h.btnReject.setEnabled(false);
                acceptRequest(item, pos);
            });

            h.btnReject.setOnClickListener(v -> {
                h.btnAccept.setEnabled(false);
                h.btnReject.setEnabled(false);
                rejectRequest(item);
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvNickname, tvSkill, tvTime;
            Button   btnAccept, btnReject;

            VH(View v) {
                super(v);
                tvNickname = v.findViewById(R.id.tvRequestNickname);
                tvSkill    = v.findViewById(R.id.tvRequestSkill);
                tvTime     = v.findViewById(R.id.tvRequestTime);
                btnAccept  = v.findViewById(R.id.btnAcceptRequest);
                btnReject  = v.findViewById(R.id.btnRejectRequest);
            }
        }
    }
}