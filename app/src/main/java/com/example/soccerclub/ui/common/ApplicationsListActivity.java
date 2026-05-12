package com.example.soccerclub.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.adapter.ApplicationsAdapter;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.ui.chat.ChatRoomActivity;
import com.example.soccerclub.util.AppUtils;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ApplicationsListActivity extends AppCompatActivity {

    private static final String POST_MATCH   = "match";
    private static final String POST_RECRUIT = "recruit";

    private TextView btnSubjectMine, btnSubjectApplied;
    private TextView chipTypeAll, chipTypeRecruit, chipTypeMatch;
    private RecyclerView recycler;
    private ApplicationsAdapter adapter;

    private boolean mineSelected = true;
    private String  typeFilter   = "all";

    private String currentUid   = "";
    private String myTeamId     = "";
    private boolean profileLoaded = false; // ✅ 프로필 로딩 완료 플래그

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Map<String, Long> sessionMaxTs = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_applications_list);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) { finish(); return; }
        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        btnSubjectMine    = findViewById(R.id.btnSubjectMine);
        btnSubjectApplied = findViewById(R.id.btnSubjectApplied);
        chipTypeAll       = findViewById(R.id.chipTypeAll);
        chipTypeRecruit   = findViewById(R.id.chipTypeRecruit);
        chipTypeMatch     = findViewById(R.id.chipTypeMatch);
        recycler          = findViewById(R.id.recycler);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ApplicationsAdapter();
        adapter.setOnItemClickListener(new ApplicationsAdapter.OnItemClickListener() {
            @Override public void onPostClicked(ApplicationsAdapter.Item item) {}
            @Override public void onApplicantAccept(ApplicationsAdapter.Item post,
                                                    ApplicationsAdapter.Applicant applicant) {
                handleAccept(post, applicant);
            }
            @Override public void onApplicantReject(ApplicationsAdapter.Item post,
                                                    ApplicationsAdapter.Applicant applicant) {
                handleReject(post, applicant);
            }
            @Override public void onApplicantChat(ApplicationsAdapter.Item post,
                                                  ApplicationsAdapter.Applicant applicant) {
                openChat(applicant.applicantUserId);
            }
        });
        recycler.setAdapter(adapter);

        setBtnStyle(true);
        setTypeSelected("all");

        btnSubjectMine.setOnClickListener(v -> {
            mineSelected = true; setBtnStyle(true); loadData();
        });
        btnSubjectApplied.setOnClickListener(v -> {
            mineSelected = false; setBtnStyle(false); loadData();
        });
        chipTypeAll.setOnClickListener(v -> {
            typeFilter = "all"; setTypeSelected("all"); loadData();
        });
        chipTypeRecruit.setOnClickListener(v -> {
            typeFilter = "recruit"; setTypeSelected("recruit"); loadData();
        });
        chipTypeMatch.setOnClickListener(v -> {
            typeFilter = "match"; setTypeSelected("match"); loadData();
        });

        // ✅ 프로필 로딩 완료 후 loadData (myTeamId 세팅 보장)
        db.collection("profiles").document(currentUid).get()
                .addOnSuccessListener(doc -> {
                    myTeamId = AppUtils.safe(doc.getString("myTeam"));
                    profileLoaded = true;
                    loadData();
                })
                .addOnFailureListener(e -> {
                    profileLoaded = true;
                    loadData();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ✅ 프로필 로딩 완료된 후에만 새로고침 (myTeamId 보장)
        if (profileLoaded && !AppUtils.isEmpty(currentUid)) loadData();
    }

    private void loadData() {
        if (mineSelected) loadMine();
        else              loadApplied();
    }

    // ── 내 글 탭 ─────────────────────────────────────────────────────────────────

    private void loadMine() {
        List<Task<List<ApplicationsAdapter.Item>>> tasks = new ArrayList<>();

        if (!"recruit".equals(typeFilter)) tasks.add(collectMine("matches",      POST_MATCH));
        if (!"match".equals(typeFilter))   tasks.add(collectMine("recruitPosts", POST_RECRUIT));

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(res -> {
            List<ApplicationsAdapter.Item> merged = new ArrayList<>();
            for (Object r : res) merged.addAll((List<ApplicationsAdapter.Item>) r);
            merged.sort((a, b) -> Long.compare(b.matchTs, a.matchTs));
            applySessionBadges(merged);
            adapter.setItems(merged, ApplicationsAdapter.TYPE_MINE);
        });
    }

    private Task<List<ApplicationsAdapter.Item>> collectMine(String collection, String postType) {
        com.google.android.gms.tasks.TaskCompletionSource<List<ApplicationsAdapter.Item>> tcs =
                new com.google.android.gms.tasks.TaskCompletionSource<>();

        // ✅ Fix 1: teamId 뿐만 아니라 authorUid로도 검색
        // → 팀 없어도 개인이 올린 글을 찾기 위해 OR 방식으로 두 쿼리 병행
        List<Task<QuerySnapshot>> queries = new ArrayList<>();

        // 팀 글 (teamId 기준)
        if (!AppUtils.isEmpty(myTeamId)) {
            queries.add(db.collection(collection)
                    .whereEqualTo("teamId", myTeamId)
                    .orderBy("matchTs", Query.Direction.DESCENDING)
                    .get());
        }

        // 개인 글 (authorUid/createdBy/writerUid 기준) → 3가지 필드 모두 시도
        queries.add(db.collection(collection)
                .whereEqualTo("authorUid", currentUid)
                .get());
        queries.add(db.collection(collection)
                .whereEqualTo("createdBy", currentUid)
                .get());

        Tasks.whenAllSuccess(queries).addOnSuccessListener(results -> {
            // 중복 제거
            Map<String, DocumentSnapshot> dedup = new LinkedHashMap<>();
            for (Object r : results) {
                QuerySnapshot qs = (QuerySnapshot) r;
                for (DocumentSnapshot d : qs.getDocuments()) {
                    dedup.put(d.getId(), d);
                }
            }

            List<ApplicationsAdapter.Item> list = new ArrayList<>();
            if (dedup.isEmpty()) { tcs.setResult(list); return; }

            int total  = dedup.size();
            int[] done = {0};

            for (DocumentSnapshot d : dedup.values()) {
                ApplicationsAdapter.Item it = buildItem(d, postType);
                String coll = POST_MATCH.equals(postType) ? "matches" : "recruitPosts";

                db.collection(coll).document(d.getId()).collection("applicants").get()
                        .addOnSuccessListener(apSnap -> {
                            List<ApplicationsAdapter.Applicant> applicants = new ArrayList<>();
                            for (DocumentSnapshot ap : apSnap.getDocuments()) {
                                ApplicationsAdapter.Applicant a = new ApplicationsAdapter.Applicant();
                                a.applicantDocId  = ap.getId();
                                // ✅ Fix 2: "userId" 와 "applicantUserId" 둘 다 확인
                                String uid = ap.getString("applicantUserId");
                                if (AppUtils.isEmpty(uid)) uid = ap.getString("userId");
                                a.applicantUserId = AppUtils.safe(uid);
                                a.teamId          = AppUtils.safe(ap.getString("teamId"));
                                a.teamName        = AppUtils.safe(ap.getString("teamName"));
                                a.nickname        = AppUtils.safe(ap.getString("nickname"));
                                a.logoUrl         = AppUtils.safe(ap.getString("teamLogoUrl"));
                                a.status          = AppUtils.safe(ap.getString("status"));
                                Long ts = ap.getLong("timestamp");
                                a.timestamp = ts != null ? ts : 0L;
                                Long sk = ap.getLong("skill");
                                a.skill = sk != null ? sk.intValue() : -1;
                                applicants.add(a);
                            }
                            it.applicants = applicants;
                            list.add(it);
                            if (++done[0] >= total) tcs.setResult(list);
                        })
                        .addOnFailureListener(e -> {
                            list.add(it);
                            if (++done[0] >= total) tcs.setResult(list);
                        });
            }
        }).addOnFailureListener(e -> tcs.setResult(new ArrayList<>()));

        return tcs.getTask();
    }

    // ── 신청한 글 탭 ─────────────────────────────────────────────────────────────

    private void loadApplied() {
        List<Task<?>> allTasks = new ArrayList<>();
        final Map<String, ApplicationsAdapter.Item> dedup = new LinkedHashMap<>();

        // ✅ 선수 모집 신청 — profiles/{uid}/applications (개인 기준)
        // postType + timestamp 복합 인덱스 필요 (Firebase 콘솔에서 생성)
        Task<com.google.firebase.firestore.QuerySnapshot> recruitTask =
                db.collection("profiles").document(currentUid)
                        .collection("applications")
                        .whereEqualTo("postType", "recruit")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .get();

        // ✅ 시합 신청 — teams/{myTeamId}/matchApplications (팀 기준, 인덱스 불필요)
        List<Task<com.google.firebase.firestore.QuerySnapshot>> tasks = new ArrayList<>();
        tasks.add(recruitTask);
        final Task<com.google.firebase.firestore.QuerySnapshot> finalMatchTask;
        if (!AppUtils.isEmpty(myTeamId)) {
            finalMatchTask = db.collection("teams").document(myTeamId)
                    .collection("matchApplications")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get();
            tasks.add(finalMatchTask);
        } else {
            finalMatchTask = null;
        }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            List<Task<DocumentSnapshot>> postGets = new ArrayList<>();

            for (int i = 0; i < results.size(); i++) {
                com.google.firebase.firestore.QuerySnapshot qs =
                        (com.google.firebase.firestore.QuerySnapshot) results.get(i);
                // ✅ finalMatchTask 사용 (effectively final)
                boolean isMatchTask = (finalMatchTask != null && i == tasks.indexOf(finalMatchTask));

                for (DocumentSnapshot ap : qs.getDocuments()) {
                    String postId   = isMatchTask
                            ? ap.getString("matchId")
                            : ap.getString("postId");
                    String postType = isMatchTask ? POST_MATCH : POST_RECRUIT;

                    if (AppUtils.isEmpty(postId)) continue;
                    if ("recruit".equals(typeFilter) && POST_MATCH.equals(postType)) continue;
                    if ("match".equals(typeFilter)   && POST_RECRUIT.equals(postType)) continue;
                    if (dedup.containsKey(postId)) continue;

                    ApplicationsAdapter.Item it = new ApplicationsAdapter.Item();
                    it.postId   = postId;
                    it.postType = postType;
                    it.status   = AppUtils.safe(ap.getString("status"));
                    Long ts = ap.getLong("timestamp");
                    it.timestamp = ts != null ? ts : 0L;
                    dedup.put(postId, it);

                    String coll = POST_MATCH.equals(postType) ? "matches" : "recruitPosts";
                    postGets.add(db.collection(coll).document(postId).get());
                }
            }

            if (dedup.isEmpty()) {
                adapter.setItems(Collections.emptyList(), ApplicationsAdapter.TYPE_APPLIED);
                return;
            }

            Tasks.whenAllSuccess(postGets).addOnSuccessListener(ms -> {
                for (Object o : ms) {
                    DocumentSnapshot m = (DocumentSnapshot) o;
                    if (!m.exists()) continue;
                    ApplicationsAdapter.Item it = dedup.get(m.getId());
                    if (it == null) continue;
                    it.teamName    = AppUtils.safe(m.getString("teamName"));
                    it.teamLogoUrl = AppUtils.firstNonEmpty(
                            m.getString("teamLogoUrl"), m.getString("logoUrl"));
                    it.date    = AppUtils.safe(m.getString("date"));
                    it.time    = AppUtils.safe(m.getString("time"));
                    it.stadium = AppUtils.firstNonEmpty(
                            m.getString("stadiumAddress"), m.getString("address"));
                    Long mts = m.getLong("matchTs");
                    it.matchTs = mts != null ? mts : it.timestamp;
                }
                List<ApplicationsAdapter.Item> list = new ArrayList<>(dedup.values());
                list.sort((a, b) -> Long.compare(b.matchTs, a.matchTs));
                adapter.setItems(list, ApplicationsAdapter.TYPE_APPLIED);
            });
        }).addOnFailureListener(e ->
                adapter.setItems(Collections.emptyList(), ApplicationsAdapter.TYPE_APPLIED));
    }

    // ── 수락 / 거절 ───────────────────────────────────────────────────────────────

    private void handleAccept(ApplicationsAdapter.Item post, ApplicationsAdapter.Applicant applicant) {
        String coll = POST_MATCH.equals(post.postType) ? "matches" : "recruitPosts";

        db.collection(coll).document(post.postId)
                .collection("applicants").document(applicant.applicantDocId)
                .update("status", "accepted")
                .addOnSuccessListener(v -> {
                    adapter.updateApplicantStatus(post.postId, applicant.applicantDocId, "accepted");

                    if (POST_MATCH.equals(post.postType)) {
                        // 시합: teams/{awayTeamId}/matchApplications 상태 업데이트
                        if (!AppUtils.isEmpty(applicant.teamId)) {
                            db.collection("teams").document(applicant.teamId)
                                    .collection("matchApplications").document(post.postId)
                                    .update("status", "accepted");
                        }
                        registerSchedule(post, applicant);
                    } else {
                        // 선수 모집: profiles/{uid}/applications 상태 업데이트
                        if (!AppUtils.isEmpty(applicant.applicantUserId)) {
                            db.collection("profiles").document(applicant.applicantUserId)
                                    .collection("applications").document(post.postId)
                                    .update("status", "accepted");
                        }
                        // ✅ 팀원 모집 수락 시 팀에 합류 처리
                        joinTeamMember(applicant);
                    }

                    String msg = POST_MATCH.equals(post.postType)
                            ? "시합 신청을 수락했어요 ✅ 일정을 확인해주세요!"
                            : "모집 신청을 수락했어요 ✅ 채팅으로 소통해요!";
                    openChatWithMessage(applicant.applicantUserId, msg);
                });
    }

    // ✅ 팀원 모집 수락 → runTransaction으로 팀 합류 처리
    private void joinTeamMember(ApplicationsAdapter.Applicant applicant) {
        if (AppUtils.isEmpty(myTeamId) || AppUtils.isEmpty(applicant.applicantUserId)) return;

        com.google.firebase.firestore.DocumentReference teamRef =
                db.collection("teams").document(myTeamId);
        com.google.firebase.firestore.DocumentReference profileRef =
                db.collection("profiles").document(applicant.applicantUserId);

        db.runTransaction(transaction -> {
                    com.google.firebase.firestore.DocumentSnapshot teamSnap    = transaction.get(teamRef);
                    com.google.firebase.firestore.DocumentSnapshot profileSnap = transaction.get(profileRef);

                    // 이미 팀원인지 확인
                    java.util.List<String> members = (java.util.List<String>) teamSnap.get("members");
                    if (members != null && members.contains(applicant.applicantUserId)) return null;

                    long skill = profileSnap.getLong("skill") != null
                            ? profileSnap.getLong("skill") : 0L;

                    // members 추가 + skillAverage 재계산
                    transaction.update(teamRef, "members",
                            com.google.firebase.firestore.FieldValue.arrayUnion(applicant.applicantUserId));
                    transaction.update(teamRef, "memberCount",
                            com.google.firebase.firestore.FieldValue.increment(1L));
                    transaction.update(teamRef, "skillSum",
                            com.google.firebase.firestore.FieldValue.increment(skill));

                    long curSum   = teamSnap.getLong("skillSum")    != null ? teamSnap.getLong("skillSum")   : 0L;
                    long curCount = teamSnap.getLong("memberCount") != null ? teamSnap.getLong("memberCount") : 0L;
                    int  newAvg   = (curCount + 1) > 0 ? (int)((curSum + skill) / (curCount + 1)) : 0;
                    transaction.update(teamRef, "skillAverage", newAvg);

                    // 프로필에 myTeam 설정
                    transaction.update(profileRef, "myTeam", myTeamId);

                    return null;
                }).addOnSuccessListener(v ->
                        CustomToast.success(this, applicant.nickname + "님이 팀에 합류했어요!"))
                .addOnFailureListener(e ->
                        CustomToast.error(this, "팀 합류 처리 실패: " + e.getMessage()));
    }

    private void handleReject(ApplicationsAdapter.Item post, ApplicationsAdapter.Applicant applicant) {
        String coll = POST_MATCH.equals(post.postType) ? "matches" : "recruitPosts";
        db.collection(coll).document(post.postId)
                .collection("applicants").document(applicant.applicantDocId)
                .update("status", "rejected")
                .addOnSuccessListener(v -> {
                    adapter.updateApplicantStatus(post.postId, applicant.applicantDocId, "rejected");

                    if (POST_MATCH.equals(post.postType)) {
                        if (!AppUtils.isEmpty(applicant.teamId)) {
                            db.collection("teams").document(applicant.teamId)
                                    .collection("matchApplications").document(post.postId)
                                    .update("status", "rejected");
                        }
                    } else {
                        if (!AppUtils.isEmpty(applicant.applicantUserId)) {
                            db.collection("profiles").document(applicant.applicantUserId)
                                    .collection("applications").document(post.postId)
                                    .update("status", "rejected");
                        }
                    }

                    // ✅ 채팅방 이동 + 거절 메시지 전송
                    String msg = POST_MATCH.equals(post.postType)
                            ? "시합 신청을 거절했어요 ❌ 다음 기회에 만나요!"
                            : "모집 신청을 거절했어요 ❌ 다음 기회에 만나요!";
                    openChatWithMessage(applicant.applicantUserId, msg);
                });
    }

    // ✅ 양팀 일정 등록 (시합 수락 시)
    private void registerSchedule(ApplicationsAdapter.Item post,
                                  ApplicationsAdapter.Applicant applicant) {
        db.collection("matches").document(post.postId).get()
                .addOnSuccessListener(matchDoc -> {
                    if (!matchDoc.exists()) return;

                    String homeTeamId   = AppUtils.safe(matchDoc.getString("teamId"));
                    String homeTeamName = AppUtils.safe(matchDoc.getString("teamName"));
                    String homeLogoUrl  = AppUtils.firstNonEmpty(
                            matchDoc.getString("logoUrl"), matchDoc.getString("teamLogoUrl"));
                    String awayTeamId   = AppUtils.safe(applicant.teamId);
                    String awayTeamName = AppUtils.safe(applicant.teamName);
                    String awayLogoUrl  = AppUtils.safe(applicant.logoUrl);
                    String date    = AppUtils.safe(matchDoc.getString("date"));
                    String time    = AppUtils.safe(matchDoc.getString("time"));
                    String stadium = AppUtils.firstNonEmpty(
                            matchDoc.getString("stadiumName"),
                            matchDoc.getString("stadiumAddress"),
                            matchDoc.getString("address"));
                    Long matchTsL  = matchDoc.getLong("matchTs");
                    long ts        = matchTsL != null ? matchTsL : System.currentTimeMillis();

                    if (AppUtils.isEmpty(homeTeamId) || AppUtils.isEmpty(awayTeamId)) {
                        CustomToast.warning(this, "팀 정보가 없어 일정 등록에 실패했어요.");
                        return;
                    }

                    // 공통 일정 데이터
                    Map<String, Object> base = new LinkedHashMap<>();
                    base.put("matchId",       post.postId);
                    base.put("homeTeamId",    homeTeamId);
                    base.put("homeTeamName",  homeTeamName);
                    base.put("homeLogoUrl",   homeLogoUrl);
                    base.put("awayTeamId",    awayTeamId);
                    base.put("awayTeamName",  awayTeamName);
                    base.put("awayLogoUrl",   awayLogoUrl);
                    base.put("date",          date);
                    base.put("time",          time);
                    base.put("stadiumName",   stadium);
                    base.put("address",       stadium);
                    base.put("matchTs",       ts);
                    base.put("endTs",         ts + 5400000L); // +90분
                    base.put("status",        "confirmed");

                    // ✅ schedules/{homeTeamId}/events/{matchId} — ScheduleActivity 구조에 맞춤
                    Map<String, Object> homeEvent = new LinkedHashMap<>(base);
                    homeEvent.put("opponentTeamName", awayTeamName);
                    homeEvent.put("opponentLogoUrl",  awayLogoUrl);
                    homeEvent.put("isHome",           true);
                    db.collection("schedules").document(homeTeamId)
                            .collection("events").document(post.postId)
                            .set(homeEvent)
                            .addOnSuccessListener(r ->
                                    CustomToast.success(this, "시합 일정이 등록됐어요!"));

                    // ✅ schedules/{awayTeamId}/events/{matchId}
                    Map<String, Object> awayEvent = new LinkedHashMap<>(base);
                    awayEvent.put("opponentTeamName", homeTeamName);
                    awayEvent.put("opponentLogoUrl",  homeLogoUrl);
                    awayEvent.put("isHome",           false);
                    db.collection("schedules").document(awayTeamId)
                            .collection("events").document(post.postId)
                            .set(awayEvent);

                    // 시합 글 상태 CONFIRMED로 변경
                    db.collection("matches").document(post.postId)
                            .update("status", "CONFIRMED");
                })
                .addOnFailureListener(e ->
                        CustomToast.error(this, "일정 등록 실패: " + e.getMessage()));
    }

    private void openChatWithMessage(String otherUid, String message) {
        if (AppUtils.isEmpty(otherUid) || AppUtils.isEmpty(currentUid)) return;
        String roomId = currentUid.compareTo(otherUid) < 0
                ? currentUid + "_" + otherUid
                : otherUid + "_" + currentUid;
        long now = System.currentTimeMillis();
        DocumentReference roomRef = db.collection("chatRooms").document(roomId);

        Map<String, Object> base = new LinkedHashMap<>();
        base.put("participants",  Arrays.asList(currentUid, otherUid));
        base.put("lastMessage",   AppUtils.isEmpty(message) ? "" : message);
        base.put("lastTimestamp", now);

        roomRef.get().addOnSuccessListener(snap -> {
            Task<?> roomTask = snap.exists()
                    ? roomRef.update("lastMessage", base.get("lastMessage"), "lastTimestamp", now)
                    : roomRef.set(base);
            roomTask.addOnSuccessListener(v -> {
                // ✅ 메시지가 있으면 채팅방에 전송
                if (!AppUtils.isEmpty(message)) {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("senderId",    currentUid);
                    msg.put("content",     message);
                    msg.put("messageType", "text");
                    msg.put("timestamp",   now);
                    roomRef.collection("messages").add(msg);
                }
                // 채팅방으로 이동
                Intent intent = new Intent(this, ChatRoomActivity.class);
                intent.putExtra("roomId", roomId);
                startActivity(intent);
            });
        });
    }

    private void openChat(String otherUid) {
        openChatWithMessage(otherUid, "");
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    private ApplicationsAdapter.Item buildItem(DocumentSnapshot d, String postType) {
        ApplicationsAdapter.Item it = new ApplicationsAdapter.Item();
        it.postId      = d.getId();
        it.postType    = postType;
        it.teamName    = AppUtils.safe(d.getString("teamName"));
        it.teamLogoUrl = AppUtils.firstNonEmpty(
                d.getString("teamLogoUrl"), d.getString("logoUrl"));
        it.date    = AppUtils.safe(d.getString("date"));
        it.time    = AppUtils.safe(d.getString("time"));
        it.stadium = AppUtils.firstNonEmpty(
                d.getString("stadiumAddress"), d.getString("address"));
        Long mts = d.getLong("matchTs");
        it.matchTs = mts != null ? mts : 0L;
        return it;
    }

    private void applySessionBadges(List<ApplicationsAdapter.Item> items) {
        for (ApplicationsAdapter.Item it : items) {
            if (it.applicants == null) continue;
            long saved = sessionMaxTs.getOrDefault(it.postId, 0L);
            long maxTs = 0L;
            for (ApplicationsAdapter.Applicant a : it.applicants) {
                if (a.timestamp > maxTs) maxTs = a.timestamp;
            }
            it.hasSessionNew = maxTs > saved;
            if (maxTs > 0) sessionMaxTs.put(it.postId, maxTs);
        }
        adapter.setSessionNewApplicantKeys(Collections.emptySet());
    }

    private void setBtnStyle(boolean mineActive) {
        if (btnSubjectMine    == null || btnSubjectApplied == null) return;
        btnSubjectMine.setTextColor(mineActive ? 0xFF1976D2 : 0xFF888888);
        btnSubjectApplied.setTextColor(mineActive ? 0xFF888888 : 0xFF1976D2);
    }

    private void setTypeSelected(String type) {
        if (chipTypeAll == null || chipTypeRecruit == null || chipTypeMatch == null) return;
        chipTypeAll.setTextColor("all".equals(type)     ? 0xFF1976D2 : 0xFF888888);
        chipTypeRecruit.setTextColor("recruit".equals(type) ? 0xFF1976D2 : 0xFF888888);
        chipTypeMatch.setTextColor("match".equals(type)   ? 0xFF1976D2 : 0xFF888888);
    }
}