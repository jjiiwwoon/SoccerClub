package com.jjw.soccerclub.repository;

import androidx.annotation.NonNull;

import com.jjw.soccerclub.adapter.RecruitAdapter;
import com.jjw.soccerclub.util.AppUtils;
import com.google.android.gms.tasks.Task;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.jjw.soccerclub.util.DateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 모집글(recruitPosts) Firestore 호출 전담.
 *
 * ViewModel 이나 Fragment 는 이 클래스만 통해 데이터를 요청한다.
 * Firestore 가 어떤 컬렉션을 쓰는지, 어떻게 파싱하는지는 여기서만 안다.
 */
public class RecruitRepository {

    public static final int PAGE_SIZE = 20;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── 데이터 요청 ───────────────────────────────────────────────────────────────

    /**
     * 모집글 한 페이지 로드.
     *
     * @param lastDoc 페이지 커서 — 첫 페이지는 null 전달
     * @return Task<QuerySnapshot> : 성공/실패 처리는 ViewModel 에서 담당
     */
    public Task<QuerySnapshot> loadPage(DocumentSnapshot lastDoc) {
        Query q = db.collection("recruitPosts")
                .whereEqualTo("status", "open")
                .orderBy("createdAtMs", Query.Direction.DESCENDING)
                .limit(PAGE_SIZE);

        if (lastDoc != null) q = q.startAfter(lastDoc);

        return q.get();
    }

    // ── 파싱 ─────────────────────────────────────────────────────────────────────

    /**
     * Firestore DocumentSnapshot → RecruitItem 변환.
     *
     * 기존 RecruitListFragment 안에 흩어져 있던 파싱 로직을 한 곳으로 통합.
     * 필드명 변경이 생겨도 이 메서드만 수정하면 된다.
     */
    @SuppressWarnings("unchecked")
    public RecruitAdapter.RecruitItem parse(DocumentSnapshot d) {
        RecruitAdapter.RecruitItem it = new RecruitAdapter.RecruitItem();

        it.id             = d.getId();
        it.teamName       = d.getString("teamName");
        it.teamLogoUrl    = AppUtils.firstNonEmpty(
                d.getString("teamLogoUrl"), d.getString("logoUrl"));
        it.date           = d.getString("date");
        it.time           = d.getString("time");
        it.weekday        = d.getString("weekday");
        it.stadiumName    = AppUtils.firstNonEmpty(
                d.getString("stadiumName"), d.getString("stadium"));
        it.stadiumAddress = AppUtils.firstNonEmpty(
                d.getString("address"), d.getString("stadiumAddress"));
        it.recruitType    = d.getString("recruitType");
        it.relativeTime   = d.getString("relativeTime");

        Long skillMinL = d.getLong("skillMin");
        Long skillMaxL = d.getLong("skillMax");
        it.skillMin = skillMinL != null ? skillMinL.intValue() : null;
        it.skillMax = skillMaxL != null ? skillMaxL.intValue() : null;

        List<String> pos = (List<String>) d.get("positions");
        it.positions = pos != null ? pos : new ArrayList<>();

        // 타임스탬프 — 여러 필드명 혼재 대응 (AppUtils.normalizeToMillis 활용)
        Long createdAtMs  = d.getLong("createdAtMs");
        Timestamp createdAtTs = d.getTimestamp("createdAt");
        Long postTs  = d.getLong("postTs");
        Long matchTs = d.getLong("matchTs");

        it.createdAtMs = createdAtMs  != null ? createdAtMs  : 0L;
        it.createdAt   = createdAtTs  != null ? createdAtTs.toDate().getTime() : 0L;
        it.postTs      = postTs       != null ? postTs       : 0L;
        it.matchTs     = matchTs      != null ? matchTs      : 0L;

        return it;
    }

    // ── 모집글 등록 ──────────────────────────────────────────────────────────────

    /** 쓰기 결과 콜백 — UI 갱신(토스트, 버튼 상태)은 Activity 책임 */
    public interface WriteCallback {
        void onSuccess();
        void onFailure(@NonNull Exception e);
    }

    /** 모집글 등록 입력값 묶음 — 검증 완료된 값만 전달받는다 */
    public static class NewRecruitPost {
        public final String teamId, teamName, teamLogoUrl, region;
        public final String date, startTime, endTime;
        public final String stadiumName, stadiumAddress;
        public final int    skillMin, skillMax;
        public final List<String> positions;   // 호출 전에 정렬 완료된 리스트
        public final boolean isRegular;        // true=정식선수 / false=용병
        public final String intro;
        public final String authorUid;

        public NewRecruitPost(String teamId, String teamName, String teamLogoUrl,
                              String region,
                              String date, String startTime, String endTime,
                              String stadiumName, String stadiumAddress,
                              int skillMin, int skillMax,
                              List<String> positions, boolean isRegular,
                              String intro, String authorUid) {
            this.teamId = teamId;   this.teamName = teamName;
            this.teamLogoUrl = teamLogoUrl; this.region = region;
            this.date = date;       this.startTime = startTime; this.endTime = endTime;
            this.stadiumName = stadiumName; this.stadiumAddress = stadiumAddress;
            this.skillMin = skillMin; this.skillMax = skillMax;
            this.positions = positions; this.isRegular = isRegular;
            this.intro = intro;     this.authorUid = authorUid;
        }
    }

    /**
     * 모집글 등록.
     *
     * [변경 전] CreateRecruitActivity 가 25개 필드 Map 을 직접 구성해 add().
     *
     * [변경 후] 문서 스키마 구성과 쓰기를 이 Repository 가 담당.
     *   저장되는 필드명/값/파생값(matchTs, endTs, weekday, postTs) 계산은
     *   변경 전과 동일.
     */
    public void createPost(@NonNull NewRecruitPost p, @NonNull WriteCallback callback) {
        String recruitType = p.isRegular ? "regular" : "mercenary";
        String timeRange   = p.startTime + " ~ " + p.endTime;
        long matchTs       = DateUtils.computeStartMillis(p.date, p.startTime);
        long endTs         = DateUtils.computeEndMillis(p.date, timeRange);
        long nowMs         = System.currentTimeMillis();
        String weekday     = DateUtils.getKoreanWeekday(p.date);

        Map<String, Object> data = new HashMap<>();
        data.put("teamId",         p.teamId);
        data.put("teamName",       p.teamName);
        data.put("teamLogoUrl",    p.teamLogoUrl);
        data.put("region",         p.region);
        data.put("date",           p.date);
        data.put("time",           timeRange);
        data.put("timeStart",      p.startTime);
        data.put("timeEnd",        p.endTime);
        data.put("matchTs",        matchTs);
        data.put("endTs",          endTs);
        data.put("weekday",        weekday);
        data.put("postTs",         matchTs);
        data.put("timestamp",      nowMs);
        data.put("createdAtMs",    nowMs);
        data.put("stadiumName",    p.stadiumName);
        data.put("stadiumAddress", p.stadiumAddress);
        data.put("skillMin",       p.skillMin);
        data.put("skillMax",       p.skillMax);
        data.put("positions",      new ArrayList<>(p.positions));
        data.put("recruitType",    recruitType);
        data.put("intro",          p.intro);
        data.put("status",         "open");
        data.put("createdBy",      p.authorUid);
        data.put("authorUid",      p.authorUid); // ApplicationsListActivity 내 글 탭 조회용
        data.put("createdAt",      Timestamp.now());
        data.put("updatedAt",      Timestamp.now());

        db.collection("recruitPosts").add(data)
                .addOnSuccessListener(ref -> callback.onSuccess())
                .addOnFailureListener(callback::onFailure);
    }
}