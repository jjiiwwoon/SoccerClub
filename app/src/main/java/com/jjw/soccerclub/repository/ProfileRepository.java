package com.jjw.soccerclub.repository;

import androidx.lifecycle.MutableLiveData;

import com.jjw.soccerclub.model.Team;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * profiles / teamStats 컬렉션 Firestore 호출 전담.
 *
 * ProfileViewModel 과 MyTeamViewModel 이 공유한다.
 *
 * [변경 전] fetchProfile() 이 MutableLiveData 를 파라미터로 받아
 *   ViewModel 이 익명 서브클래스를 만들어 콜백을 끼워 넣는 방식.
 *   → MutableLiveData 의 의도된 사용법이 아님, 코드 스멜.
 *
 * [변경 후] ProfileCallback 인터페이스를 별도로 선언.
 *   Repository 는 결과를 callback.onResult() 로 전달하고,
 *   ViewModel 은 람다로 받아 처리한다.
 */
public class ProfileRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── 콜백 인터페이스 ───────────────────────────────────────────────────────────

    /**
     * 프로필 단건 조회 결과 콜백.
     * 성공 시 DocumentSnapshot, 실패/없음 시 null 전달.
     */
    public interface ProfileCallback {
        void onResult(DocumentSnapshot doc);
    }

    // ── 프로필 (단일 조회) ────────────────────────────────────────────────────────

    /**
     * 특정 사용자 프로필 1회 조회.
     *
     * [변경 전] MutableLiveData<DocumentSnapshot> 을 파라미터로 받아
     *   liveData::setValue 를 리스너에 직접 연결.
     *   ViewModel 이 익명 MutableLiveData 서브클래스를 만들어 setValue 를 오버라이드해야 했음.
     *
     * [변경 후] ProfileCallback 인터페이스를 통해 결과 전달.
     *   ViewModel 에서 람다로 간결하게 처리 가능.
     *
     * @param uid      조회할 UID
     * @param callback 결과 수신 콜백
     */
    public void fetchProfile(String uid, ProfileCallback callback) {
        db.collection("profiles")
                .document(uid)
                .get()
                .addOnSuccessListener(callback::onResult)
                .addOnFailureListener(e -> callback.onResult(null));
    }

    // ── 팀 정보 (단일 조회) ───────────────────────────────────────────────────────

    /**
     * 특정 팀 정보 1회 조회.
     *
     * @param teamId   조회할 팀 ID
     * @param liveData 결과를 흘려보낼 MutableLiveData<Team>
     */
    public void fetchTeam(String teamId, MutableLiveData<Team> liveData) {
        db.collection("teams")
                .document(teamId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Team team = doc.toObject(Team.class);
                        if (team != null) team.setTeamId(doc.getId());
                        liveData.setValue(team);
                    } else {
                        liveData.setValue(null);
                    }
                })
                .addOnFailureListener(e -> liveData.setValue(null));
    }

    // ── 팀 전적 통계 (실시간) ─────────────────────────────────────────────────────

    /**
     * teamStats 문서를 실시간으로 관찰.
     *
     * @param teamId   조회할 팀 ID
     * @param liveData 결과를 흘려보낼 MutableLiveData<DocumentSnapshot>
     * @return         해제용 ListenerRegistration
     */
    public ListenerRegistration listenTeamStats(
            String teamId,
            MutableLiveData<DocumentSnapshot> liveData) {

        return db.collection("teamStats")
                .document(teamId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null) return;
                    liveData.setValue(doc);
                });
    }
}