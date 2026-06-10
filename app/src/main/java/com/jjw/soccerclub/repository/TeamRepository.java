package com.jjw.soccerclub.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.jjw.soccerclub.model.Team;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * teams 컬렉션 Firestore 호출 전담.
 *
 * AllTeamViewModel 과 MyTeamViewModel 이 이 Repository 를 공유한다.
 * 두 화면 모두 teams 컬렉션을 쓰지만, 이 클래스에 Firestore 코드가
 * 한 곳에 모여있어 필드명 변경이 생겨도 여기만 수정하면 된다.
 */
public class TeamRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── 콜백 인터페이스 ───────────────────────────────────────────────────────────

    /** 전체 팀 목록 실시간 수신 콜백 */
    public interface TeamsCallback {
        void onTeams(@NonNull List<Team> teams);
    }

    // ── 전체 팀 목록 (실시간) ─────────────────────────────────────────────────────

    /**
     * 전체 팀 목록을 실시간으로 관찰.
     *
     * AllTeamViewModel 이 사용. addSnapshotListener 로 연결하고
     * 반환된 ListenerRegistration 을 ViewModel.onCleared() 에서 해제한다.
     *
     * [변경 전] MutableLiveData<List<Team>> 을 파라미터로 받아 setValue 직접 호출.
     *   → ViewModel 이 내부 중간 LiveData + observeForever 를 둬야 했고,
     *     observer 해제 누락으로 누수 발생.
     *     (ProfileRepository 리팩토링 때 제거한 것과 동일한 코드 스멜)
     *
     * [변경 후] TeamsCallback 인터페이스로 결과 전달.
     *   ViewModel 은 람다 안에서 바로 필터를 적용한다.
     *   중간 LiveData 와 observeForever 가 모두 사라진다.
     *
     * @param callback 팀 목록 수신 콜백
     * @return         해제용 ListenerRegistration
     */
    public ListenerRegistration listenAllTeams(@NonNull TeamsCallback callback) {

        return db.collection("teams")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    List<Team> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        try {
                            Team team = doc.toObject(Team.class);
                            if (team == null) continue;
                            team.setTeamId(doc.getId());
                            list.add(team);
                        } catch (Exception ex) {
                            // 파싱 실패한 개별 문서는 스킵
                        }
                    }
                    callback.onTeams(list);
                });
    }

    // ── 단일 팀 정보 (실시간) ─────────────────────────────────────────────────────

    /**
     * 특정 팀 문서를 실시간으로 관찰하는 ListenerRegistration 반환.
     *
     * MyTeamFragment 가 사용. 팀 정보 변경(이름, 로고 등)이 즉시 반영된다.
     *
     * @param teamId    조회할 팀 ID
     * @param liveData  데이터를 흘려보낼 MutableLiveData
     * @return          해제용 ListenerRegistration
     */
    public ListenerRegistration listenTeam(
            String teamId,
            MutableLiveData<Team> liveData) {

        return db.collection("teams")
                .document(teamId)
                .addSnapshotListener((doc, e) -> {
                    if (e != null || doc == null || !doc.exists()) return;
                    Team team = doc.toObject(Team.class);
                    if (team != null) {
                        team.setTeamId(doc.getId());
                        liveData.setValue(team);
                    }
                });
    }
}