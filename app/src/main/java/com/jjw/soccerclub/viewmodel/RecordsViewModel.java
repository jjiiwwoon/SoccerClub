package com.jjw.soccerclub.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.jjw.soccerclub.repository.RecordsRepository;
import com.jjw.soccerclub.repository.RecordsRepository.MemberInfo;
import com.jjw.soccerclub.repository.RecordsRepository.PlayerStat;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 전적 화면 상태 관리.
 *
 * [변경 전] RecordsActivity 가 직접 하던 일
 *   - db, statsReg 필드 직접 보유
 *   - members, personalData 리스트 직접 관리
 *   - currentKey, currentDir 정렬 상태 직접 관리
 *   - 화면 회전 시 모든 데이터 재로드
 *   - onDestroy() 에서 statsReg.remove() 수동 처리
 *
 * [변경 후] ViewModel 이 담당
 *   - LiveData 로 Activity 에 결과 전달
 *   - 정렬 상태 ViewModel 보관 → 화면 회전해도 유지
 *   - onCleared() 에서 statsReg 자동 해제
 *   - Activity 는 observe + UI 바인딩만 담당
 */
public class RecordsViewModel extends ViewModel {

    public enum SortKey { GAMES, GOALS, ASSISTS }
    public enum SortDir  { ASC, DESC }

    /** 정렬 상태를 하나의 객체로 묶어 LiveData 로 전달 */
    public static class SortState {
        public final SortKey key;
        public final SortDir dir;
        SortState(SortKey key, SortDir dir) { this.key = key; this.dir = dir; }
    }

    /** 팀 전적 요약 */
    public static class TeamStats {
        public final int games, wins, draws, losses, gf, ga;
        TeamStats(int games, int wins, int draws, int losses, int gf, int ga) {
            this.games = games; this.wins = wins; this.draws = draws;
            this.losses = losses; this.gf = gf; this.ga = ga;
        }
        public String winRate() {
            return games > 0 ? Math.round((wins * 100f) / games) + "%" : "-";
        }
        public int goalDiff() { return gf - ga; }
    }

    private final RecordsRepository repository = new RecordsRepository();
    private ListenerRegistration statsReg;

    // ── Activity 가 observe 하는 LiveData ─────────────────────────────────────────

    private final MutableLiveData<Boolean>        _isLoading    = new MutableLiveData<>(true);
    private final MutableLiveData<String>         _emptyMsg     = new MutableLiveData<>(null);
    private final MutableLiveData<TeamStats>      _teamStats    = new MutableLiveData<>();
    private final MutableLiveData<List<PlayerStat>> _personalStats = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<SortState>      _sortState    = new MutableLiveData<>(new SortState(SortKey.GOALS, SortDir.DESC));

    public final LiveData<Boolean>          isLoading     = _isLoading;
    public final LiveData<String>           emptyMsg      = _emptyMsg;
    public final LiveData<TeamStats>        teamStats     = _teamStats;
    public final LiveData<List<PlayerStat>> personalStats = _personalStats;
    public final LiveData<SortState>        sortState     = _sortState;

    // ── 내부 상태 ────────────────────────────────────────────────────────────────

    private boolean initialLoadDone = false;
    private Map<String, MemberInfo> memberMap;
    private List<PlayerStat> rawStats = new ArrayList<>();

    // ── 외부 API ─────────────────────────────────────────────────────────────────

    /** Activity 의 onCreate 에서 호출. 화면 회전 시 재요청 없음. */
    public void loadIfNeeded(String teamId) {
        if (initialLoadDone) return;
        initialLoadDone = true;
        _isLoading.setValue(true);
        startStatsListener(teamId);
        loadMembers(teamId);
    }

    /** 정렬 헤더 클릭 시 호출 */
    public void sort(SortKey key) {
        SortState cur = _sortState.getValue();
        SortDir newDir = (cur != null && cur.key == key && cur.dir == SortDir.DESC)
                ? SortDir.ASC : SortDir.DESC;
        SortState next = new SortState(key, newDir);
        _sortState.setValue(next);
        publishSorted(next);
    }

    // ── 내부 로직 ────────────────────────────────────────────────────────────────

    private void startStatsListener(String teamId) {
        if (statsReg != null) { statsReg.remove(); statsReg = null; }
        statsReg = repository.listenTeamStats(teamId,
                (games, wins, draws, losses, gf, ga) ->
                        _teamStats.setValue(new TeamStats(games, wins, draws, losses, gf, ga)));
    }

    private void loadMembers(String teamId) {
        repository.fetchMembers(teamId, new RecordsRepository.MembersCallback() {
            @Override public void onMembers(Map<String, MemberInfo> members) {
                memberMap = members;
                loadPersonalStats(teamId);
            }
            @Override public void onError(String msg) {
                _emptyMsg.setValue(msg);
                _isLoading.setValue(false);
            }
        });
    }

    private void loadPersonalStats(String teamId) {
        repository.fetchPersonalStats(teamId, memberMap, new RecordsRepository.PersonalStatsCallback() {
            @Override public void onStats(List<PlayerStat> stats) {
                rawStats = stats;
                publishSorted(_sortState.getValue());
                _isLoading.setValue(false);
            }
            @Override public void onError(String msg) {
                // 개인기록 실패해도 팀 전적은 보여줌
                _isLoading.setValue(false);
            }
        });
    }

    private void publishSorted(SortState state) {
        if (state == null) state = new SortState(SortKey.GOALS, SortDir.DESC);
        List<PlayerStat> sorted = new ArrayList<>(rawStats);
        final SortState s = state;
        sorted.sort((a, b) -> {
            int va, vb;
            switch (s.key) {
                case GAMES:   va = a.games;   vb = b.games;   break;
                case ASSISTS: va = a.assists; vb = b.assists; break;
                default:      va = a.goals;   vb = b.goals;   break;
            }
            int cmp = Integer.compare(va, vb);
            if (s.dir == SortDir.DESC) cmp = -cmp;
            return cmp != 0 ? cmp : a.nickname.compareTo(b.nickname);
        });
        _personalStats.setValue(sorted);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (statsReg != null) { statsReg.remove(); statsReg = null; }
    }
}