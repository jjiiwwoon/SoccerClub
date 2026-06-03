package com.jjw.soccerclub.repository;

import com.jjw.soccerclub.adapter.ApplicationsAdapter;
import com.jjw.soccerclub.util.AppUtils;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class ApplicationsRepository {

    private static final String POST_MATCH   = "match";
    private static final String POST_RECRUIT = "recruit";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── 콜백 인터페이스 ───────────────────────────────────────────────────────────

    public interface ItemsCallback {
        void onResult(List<ApplicationsAdapter.Item> items);
    }

    // ── 내 글 수집 ────────────────────────────────────────────────────────────────

    /**
     * 내가 작성한 글 목록 수집.
     * matches / recruitPosts 를 typeFilter 에 따라 선택적으로 조회.
     */
    public void fetchMine(String currentUid, String myTeamId,
                          String typeFilter, ItemsCallback callback) {
        List<Task<List<ApplicationsAdapter.Item>>> tasks = new ArrayList<>();

        if (!"recruit".equals(typeFilter))
            tasks.add(collectMine("matches",      POST_MATCH,   currentUid, myTeamId));
        if (!"match".equals(typeFilter))
            tasks.add(collectMine("recruitPosts", POST_RECRUIT, currentUid, myTeamId));

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(res -> {
            List<ApplicationsAdapter.Item> merged = new ArrayList<>();
            for (Object r : res) merged.addAll((List<ApplicationsAdapter.Item>) r);
            merged.sort((a, b) -> Long.compare(b.matchTs, a.matchTs));
            callback.onResult(merged);
        }).addOnFailureListener(e -> callback.onResult(Collections.emptyList()));
    }

    /** 단일 컬렉션에서 내 글 수집 + 신청자 서브컬렉션 조회 */
    @SuppressWarnings("unchecked")
    private Task<List<ApplicationsAdapter.Item>> collectMine(
            String collection, String postType,
            String currentUid, String myTeamId) {

        TaskCompletionSource<List<ApplicationsAdapter.Item>> tcs = new TaskCompletionSource<>();

        List<Task<QuerySnapshot>> queries = new ArrayList<>();
        queries.add(db.collection(collection).whereEqualTo("authorUid", currentUid).get());
        queries.add(db.collection(collection).whereEqualTo("createdBy",  currentUid).get());

        Tasks.whenAllSuccess(queries).addOnSuccessListener(results -> {
            Map<String, DocumentSnapshot> dedup = new LinkedHashMap<>();
            for (Object r : results) {
                QuerySnapshot qs = (QuerySnapshot) r;
                for (DocumentSnapshot d : qs.getDocuments()) dedup.put(d.getId(), d);
            }

            List<ApplicationsAdapter.Item> list = new ArrayList<>();
            if (dedup.isEmpty()) { tcs.setResult(list); return; }

            int total  = dedup.size();
            int[] done = {0};

            for (DocumentSnapshot d : dedup.values()) {
                ApplicationsAdapter.Item it = buildItem(d, postType);
                String coll = POST_MATCH.equals(postType) ? "matches" : "recruitPosts";

                db.collection(coll).document(d.getId())
                        .collection("applicants").get()
                        .addOnSuccessListener(apSnap -> {
                            List<ApplicationsAdapter.Applicant> applicants = new ArrayList<>();
                            for (DocumentSnapshot ap : apSnap.getDocuments()) {
                                ApplicationsAdapter.Applicant a = new ApplicationsAdapter.Applicant();
                                a.applicantDocId  = ap.getId();
                                a.teamId          = AppUtils.safe(ap.getString("teamId"));
                                a.teamName        = AppUtils.safe(ap.getString("teamName"));
                                a.logoUrl         = AppUtils.firstNonEmpty(
                                        ap.getString("teamLogoUrl"), ap.getString("logoUrl"));
                                a.nickname        = AppUtils.safe(ap.getString("nickname"));
                                a.applicantUserId = AppUtils.firstNonEmpty(
                                        ap.getString("userId"),
                                        ap.getString("applicantUserId"),
                                        ap.getId());
                                a.status          = AppUtils.safe(ap.getString("status"));
                                Long sk = ap.getLong("skill");
                                a.skill = sk != null ? sk.intValue() : -1;
                                Long ts = ap.getLong("timestamp");
                                a.timestamp = ts != null ? ts : 0L;
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

    // ── 신청한 글 수집 ────────────────────────────────────────────────────────────

    /**
     * 내가 신청한 글 목록 수집.
     */
    @SuppressWarnings("unchecked")
    public void fetchApplied(String currentUid, String myTeamId,
                             String typeFilter, ItemsCallback callback) {

        List<Task<QuerySnapshot>> tasks = new ArrayList<>();
        if (!"recruit".equals(typeFilter)) {
            if (!AppUtils.isEmpty(myTeamId)) {
                tasks.add(db.collection("teams").document(myTeamId)
                        .collection("matchApplications")
                        .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                        .get());
            }
        }
        if (!"match".equals(typeFilter)) {
            tasks.add(db.collection("profiles").document(currentUid)
                    .collection("applications")
                    .whereEqualTo("postType", "recruit")
                    .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .get());
        }

        if (tasks.isEmpty()) { callback.onResult(Collections.emptyList()); return; }

        Tasks.whenAllSuccess(tasks).addOnSuccessListener(results -> {
            Map<String, ApplicationsAdapter.Item> dedup = new LinkedHashMap<>();
            List<Task<DocumentSnapshot>> postGets = new ArrayList<>();

            for (int i = 0; i < results.size(); i++) {
                QuerySnapshot qs = (QuerySnapshot) results.get(i);
                boolean isMatch = !"recruit".equals(typeFilter) && !AppUtils.isEmpty(myTeamId) && i == 0;

                for (DocumentSnapshot ap : qs.getDocuments()) {
                    String postId   = isMatch ? ap.getString("matchId") : ap.getString("postId");
                    String postType = isMatch ? POST_MATCH : POST_RECRUIT;

                    if (AppUtils.isEmpty(postId) || dedup.containsKey(postId)) continue;

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

            if (dedup.isEmpty()) { callback.onResult(Collections.emptyList()); return; }

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
                callback.onResult(list);
            }).addOnFailureListener(e -> callback.onResult(Collections.emptyList()));

        }).addOnFailureListener(e -> callback.onResult(Collections.emptyList()));
    }

    // ── 수락 ─────────────────────────────────────────────────────────────────────

    public void accept(String currentUid, String myTeamId,
                       ApplicationsAdapter.Item post,
                       ApplicationsAdapter.Applicant applicant,
                       Runnable onSuccess, Runnable onFailure) {

        String coll = POST_MATCH.equals(post.postType) ? "matches" : "recruitPosts";
        db.collection(coll).document(post.postId)
                .collection("applicants").document(applicant.applicantDocId)
                .update("status", "accepted")
                .addOnSuccessListener(v -> {
                    if (POST_MATCH.equals(post.postType)) {
                        if (!AppUtils.isEmpty(applicant.teamId)) {
                            db.collection("teams").document(applicant.teamId)
                                    .collection("matchApplications").document(post.postId)
                                    .update("status", "accepted");
                        }
                        db.collection("matches").document(post.postId)
                                .update("status", "confirmed",
                                        "opponentTeamId", applicant.teamId,
                                        "opponentName", AppUtils.safe(applicant.teamName),
                                        "opponentLogoUrl", AppUtils.safe(applicant.logoUrl));
                        registerSchedule(post, applicant, currentUid, myTeamId);
                    } else {
                        if (!AppUtils.isEmpty(applicant.applicantUserId)) {
                            db.collection("profiles").document(applicant.applicantUserId)
                                    .collection("applications").document(post.postId)
                                    .update("status", "accepted");
                        }

                        // ★ recruitType 확인 → 용병 vs 정식선수 분기
                        db.collection("recruitPosts").document(post.postId).get()
                                .addOnSuccessListener(recruitDoc -> {
                                    String rType = AppUtils.normalizeRecruitType(
                                            AppUtils.safe(recruitDoc.getString("recruitType")));

                                    if ("mercenary".equals(rType)) {
                                        // ★ 용병: 팀 합류 X → 이벤트에 용병 등록 + 개인 기록 저장
                                        addMercenaryToEvent(post, applicant, myTeamId, recruitDoc);
                                    } else {
                                        // 정식선수: 팀 합류
                                        joinTeamMember(applicant, myTeamId);
                                    }
                                });
                    }
                    String msg = POST_MATCH.equals(post.postType)
                            ? "시합 신청을 수락했어요 ✅ 일정을 확인해주세요!"
                            : "모집 신청을 수락했어요 ✅ 채팅으로 소통해요!";
                    openChatWithMessage(currentUid, applicant.applicantUserId, msg);
                    if (onSuccess != null) onSuccess.run();
                })
                .addOnFailureListener(e -> { if (onFailure != null) onFailure.run(); });
    }

    // ★ 새 메서드: 용병을 이벤트에 등록
    private void addMercenaryToEvent(ApplicationsAdapter.Item post,
                                     ApplicationsAdapter.Applicant applicant,
                                     String myTeamId,
                                     DocumentSnapshot recruitDoc) {
        if (AppUtils.isEmpty(myTeamId) || AppUtils.isEmpty(applicant.applicantUserId)) return;

        String date = AppUtils.safe(recruitDoc.getString("date"));
        String time = AppUtils.safe(recruitDoc.getString("time"));
        String stadiumName = AppUtils.firstNonEmpty(
                recruitDoc.getString("stadiumName"), recruitDoc.getString("stadium"));
        String teamName = AppUtils.safe(recruitDoc.getString("teamName"));

        // 1) 해당 날짜의 이벤트 찾기 → mercenaryCandidateIds에 추가
        db.collection("schedules").document(myTeamId)
                .collection("events")
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener(qs -> {
                    String targetEventId = null;
                    for (DocumentSnapshot ev : qs.getDocuments()) {
                        String evTime = AppUtils.safe(ev.getString("time"));
                        if (evTime.equals(time) || qs.size() == 1) {
                            targetEventId = ev.getId();
                            break;
                        }
                    }
                    if (targetEventId == null && !qs.isEmpty()) {
                        targetEventId = qs.getDocuments().get(0).getId();
                    }

                    if (targetEventId != null) {
                        // 이벤트에 용병 등록
                        db.collection("schedules").document(myTeamId)
                                .collection("events").document(targetEventId)
                                .update("mercenaryCandidateIds",
                                        FieldValue.arrayUnion(applicant.applicantUserId));

                        // 투표에도 자동 참석 등록
                        Map<String, Object> voteData = new java.util.LinkedHashMap<>();
                        voteData.put("status", "attend");
                        voteData.put("isMercenary", true);
                        voteData.put("nickname", AppUtils.safe(applicant.nickname));
                        voteData.put("timestamp", System.currentTimeMillis());
                        db.collection("schedules").document(myTeamId)
                                .collection("events").document(targetEventId)
                                .collection("votes").document(applicant.applicantUserId)
                                .set(voteData, com.google.firebase.firestore.SetOptions.merge());
                    }
                });

        // 2) 용병 개인 프로필에 활동 기록 저장
        Map<String, Object> mercActivity = new java.util.LinkedHashMap<>();
        mercActivity.put("postId", post.postId);
        mercActivity.put("teamId", myTeamId);
        mercActivity.put("teamName", teamName);
        mercActivity.put("date", date);
        mercActivity.put("time", time);
        mercActivity.put("stadium", stadiumName);
        mercActivity.put("status", "accepted");
        mercActivity.put("timestamp", System.currentTimeMillis());
        db.collection("profiles").document(applicant.applicantUserId)
                .collection("mercenaryActivities").document(post.postId)
                .set(mercActivity, com.google.firebase.firestore.SetOptions.merge());
    }

    // ── 거절 ─────────────────────────────────────────────────────────────────────

    /**
     * 신청 거절 처리.
     */
    public void reject(String currentUid,
                       ApplicationsAdapter.Item post,
                       ApplicationsAdapter.Applicant applicant,
                       Runnable onSuccess, Runnable onFailure) {

        String coll = POST_MATCH.equals(post.postType) ? "matches" : "recruitPosts";
        db.collection(coll).document(post.postId)
                .collection("applicants").document(applicant.applicantDocId)
                .update("status", "rejected")
                .addOnSuccessListener(v -> {
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
                    String msg = POST_MATCH.equals(post.postType)
                            ? "시합 신청을 거절했어요 ❌ 다음 기회에 만나요!"
                            : "모집 신청을 거절했어요 ❌ 다음 기회에 만나요!";
                    openChatWithMessage(currentUid, applicant.applicantUserId, msg);
                    if (onSuccess != null) onSuccess.run();
                })
                .addOnFailureListener(e -> { if (onFailure != null) onFailure.run(); });
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────────────

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

    @SuppressWarnings("unchecked")
    private void joinTeamMember(ApplicationsAdapter.Applicant applicant, String myTeamId) {
        if (AppUtils.isEmpty(myTeamId) || AppUtils.isEmpty(applicant.applicantUserId)) return;
        DocumentReference teamRef    = db.collection("teams").document(myTeamId);
        DocumentReference profileRef = db.collection("profiles").document(applicant.applicantUserId);

        db.runTransaction(tr -> {
            DocumentSnapshot teamSnap    = tr.get(teamRef);
            DocumentSnapshot profileSnap = tr.get(profileRef);
            List<String> members = (List<String>) teamSnap.get("members");
            if (members != null && members.contains(applicant.applicantUserId)) return null;

            long skill = profileSnap.getLong("skill") != null ? profileSnap.getLong("skill") : 0L;
            tr.update(teamRef, "members",     FieldValue.arrayUnion(applicant.applicantUserId));
            tr.update(teamRef, "memberCount", FieldValue.increment(1L));
            tr.update(teamRef, "skillSum",    FieldValue.increment(skill));
            long curSum   = teamSnap.getLong("skillSum")    != null ? teamSnap.getLong("skillSum")   : 0L;
            long curCount = teamSnap.getLong("memberCount") != null ? teamSnap.getLong("memberCount") : 0L;
            int  newAvg   = (curCount + 1) > 0 ? (int)((curSum + skill) / (curCount + 1)) : 0;
            tr.update(teamRef, "skillAverage", newAvg);
            tr.update(profileRef, "myTeam", myTeamId);
            return null;
        });
    }

    private void registerSchedule(ApplicationsAdapter.Item post,
                                  ApplicationsAdapter.Applicant applicant,
                                  String currentUid, String myTeamId) {
        if (AppUtils.isEmpty(myTeamId) || AppUtils.isEmpty(applicant.teamId)) return;

        // 매치 문서에서 상세 정보 가져옴
        db.collection("matches").document(post.postId).get()
                .addOnSuccessListener(matchDoc -> {
                    if (matchDoc == null || !matchDoc.exists()) return;

                    // 내 팀(글 작성팀) 정보
                    String myName = AppUtils.safe(matchDoc.getString("teamName"));
                    String myLogo = AppUtils.firstNonEmpty(
                            matchDoc.getString("teamLogoUrl"),
                            matchDoc.getString("logoUrl"));

                    // 상대 팀(신청팀) 정보
                    String oppId   = applicant.teamId;
                    String oppName = AppUtils.safe(applicant.teamName);
                    String oppLogo = AppUtils.safe(applicant.logoUrl);

                    // 공통 경기 정보
                    String date = AppUtils.safe(matchDoc.getString("date"));
                    String time = AppUtils.safe(matchDoc.getString("time"));
                    Long matchTsLong = matchDoc.getLong("matchTs");
                    long matchTs = matchTsLong != null ? matchTsLong : System.currentTimeMillis();
                    String stadiumName = AppUtils.firstNonEmpty(
                            matchDoc.getString("stadiumName"),
                            matchDoc.getString("stadium"));
                    String stadiumAddress = AppUtils.firstNonEmpty(
                            matchDoc.getString("stadiumAddress"),
                            matchDoc.getString("address"));

                    // ===== 우리팀 일정 =====
                    Map<String, Object> evMine = new java.util.LinkedHashMap<>();
                    evMine.put("date",              date);
                    evMine.put("time",              time);
                    evMine.put("matchId",           post.postId);
                    evMine.put("matchTs",           matchTs);
                    evMine.put("opponentTeamId",    oppId);
                    evMine.put("opponentTeamName",  oppName);
                    evMine.put("opponentLogoUrl",   oppLogo);
                    evMine.put("stadiumName",       stadiumName);
                    evMine.put("stadiumAddress",    stadiumAddress);
                    evMine.put("status",            "scheduled");
                    evMine.put("title",             myName + " vs " + oppName);
                    evMine.put("ownerTeamId",       myTeamId);
                    evMine.put("createdAt",         System.currentTimeMillis());

                    // ===== 상대팀 일정 (상대 시점: 상대의 opponent = 우리) =====
                    Map<String, Object> evOpp = new java.util.LinkedHashMap<>();
                    evOpp.put("date",              date);
                    evOpp.put("time",              time);
                    evOpp.put("matchId",           post.postId);
                    evOpp.put("matchTs",           matchTs);
                    evOpp.put("opponentTeamId",    myTeamId);
                    evOpp.put("opponentTeamName",  myName);
                    evOpp.put("opponentLogoUrl",   myLogo);
                    evOpp.put("stadiumName",       stadiumName);
                    evOpp.put("stadiumAddress",    stadiumAddress);
                    evOpp.put("status",            "scheduled");
                    evOpp.put("title",             oppName + " vs " + myName);
                    evOpp.put("ownerTeamId",       myTeamId);
                    evOpp.put("createdAt",         System.currentTimeMillis());

                    // 각 팀의 schedules 에 저장 (문서 ID = matchId 로 통일)
                    db.collection("schedules").document(myTeamId)
                            .collection("events").document(post.postId)
                            .set(evMine, com.google.firebase.firestore.SetOptions.merge());

                    db.collection("schedules").document(oppId)
                            .collection("events").document(post.postId)
                            .set(evOpp, com.google.firebase.firestore.SetOptions.merge());
                });
    }

    private void openChatWithMessage(String myUid, String otherUid, String message) {
        if (AppUtils.isEmpty(otherUid)) return;
        String roomId = myUid.compareTo(otherUid) < 0
                ? myUid + "_" + otherUid : otherUid + "_" + myUid;
        long now = System.currentTimeMillis();
        DocumentReference roomRef = db.collection("chatRooms").document(roomId);

        Map<String, Object> base = new LinkedHashMap<>();
        base.put("participants",  Arrays.asList(myUid, otherUid));
        base.put("lastMessage",   AppUtils.isEmpty(message) ? "" : message);
        base.put("lastTimestamp", now);

        roomRef.get().addOnSuccessListener(snap -> {
            Task<?> roomTask = snap.exists()
                    ? roomRef.update("lastMessage", base.get("lastMessage"), "lastTimestamp", now)
                    : roomRef.set(base);
            roomTask.addOnSuccessListener(v -> {
                if (!AppUtils.isEmpty(message)) {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("senderId",    myUid);
                    msg.put("content",     message);
                    msg.put("messageType", "text");
                    msg.put("timestamp",   now);
                    roomRef.collection("messages").add(msg);
                }
            });
        });
    }
}