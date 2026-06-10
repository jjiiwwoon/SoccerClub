package com.jjw.soccerclub.viewmodel;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.jjw.soccerclub.model.Team;
import com.jjw.soccerclub.repository.TeamRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * 전체 팀 목록 화면 상태 관리.
 *
 * 검색어와 필터(지역/실력/나이)를 보관하고,
 * 원본 목록(allTeams) 에 적용해 displayTeams 를 업데이트한다.
 *
 * Firestore 실시간 리스너는 ViewModel 이 살아있는 동안 유지되고
 * onCleared() 에서 해제된다. Fragment 가 재생성(화면 회전)돼도
 * 리스너는 끊기지 않는다.
 *
 * [변경 전] 원본 목록을 내부 MutableLiveData(allTeams) 로 보관하고
 *   allTeams.observeForever(teams -> publishFiltered()) 로 필터를 연결.
 *   → observer 를 해제하는 코드가 없어 ViewModel 소멸 후에도 콜백이 잔존하고,
 *     재생성 시 observer 가 누적돼 publishFiltered 가 중복 실행될 수 있었음.
 *     (MyTeamViewModel 에서는 같은 문제를 observer 필드 보관으로 고쳤으나 여기는 누락)
 *
 * [변경 후] TeamRepository.listenAllTeams 가 TeamsCallback 으로 결과를 전달.
 *   콜백 안에서 원본 갱신 + publishFiltered() 를 직접 호출한다.
 *   중간 LiveData 와 observeForever 가 모두 사라져 누수 원인 자체가 제거됨.
 *   외부 동작(목록/검색/필터/실시간 갱신)은 변경 전과 동일.
 */
public class AllTeamViewModel extends ViewModel {

    private final TeamRepository repository = new TeamRepository();
    private ListenerRegistration listenerReg;

    // ── Fragment 가 observe 하는 LiveData ────────────────────────────────────────

    /** 검색+필터 적용된 최종 팀 목록 */
    private final MutableLiveData<List<Team>> _displayTeams
            = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<Team>> displayTeams = _displayTeams;

    // ── 내부 상태 ────────────────────────────────────────────────────────────────

    /**
     * Firestore 에서 받아온 전체 팀 목록.
     * Fragment 에 직접 노출되지 않는 내부 상태이므로 LiveData 일 필요가 없다.
     */
    private List<Team> allTeams = new ArrayList<>();

    /** 현재 검색어 */
    private String searchQuery = "";

    /** 현재 필터 값들 */
    private String filterCity     = "전체";
    private String filterDistrict = "전체";
    private String filterSkill    = "전체";
    private String filterAgeStart = "전체";
    private String filterAgeEnd   = "전체";

    private boolean listenerStarted = false;

    // ── 외부에서 호출하는 메서드 ─────────────────────────────────────────────────

    /** Fragment 의 onViewCreated 에서 호출 — 최초 1회만 리스너 등록 */
    public void startListeningIfNeeded() {
        if (listenerStarted) return;
        listenerStarted = true;

        listenerReg = repository.listenAllTeams(teams -> {
            allTeams = teams;
            publishFiltered();
        });
    }

    /** 검색어 변경 */
    public void setSearchQuery(String query) {
        this.searchQuery = query == null ? "" : query.trim().toLowerCase();
        publishFiltered();
    }

    /** 필터 변경 */
    public void setFilter(String city, String district,
                          String skill, String ageStart, String ageEnd) {
        this.filterCity     = city     != null ? city     : "전체";
        this.filterDistrict = district != null ? district : "전체";
        this.filterSkill    = skill    != null ? skill    : "전체";
        this.filterAgeStart = ageStart != null ? ageStart : "전체";
        this.filterAgeEnd   = ageEnd   != null ? ageEnd   : "전체";
        publishFiltered();
    }

    // ── 내부 로직 ────────────────────────────────────────────────────────────────

    /** allTeams 에 검색어+필터 적용 → displayTeams 업데이트 */
    private void publishFiltered() {
        List<Team> source = allTeams;

        // 나이 필터 범위 파싱
        boolean filterByAge = !isAll(filterAgeStart) && !isAll(filterAgeEnd);
        int ageStart = 0, ageEnd = 100;
        if (filterByAge) {
            ageStart = parseIntOrDefault(filterAgeStart.replace("~", ""), 0);
            ageEnd   = parseIntOrDefault(filterAgeEnd.replace("~", ""), 100);
            if (ageStart > ageEnd) { int tmp = ageStart; ageStart = ageEnd; ageEnd = tmp; }
        }

        boolean filterBySkill = !isAll(filterSkill);
        int skillTarget = filterBySkill ? parseIntOrDefault(filterSkill, -1) : -1;

        List<Team> out = new ArrayList<>();
        for (Team team : source) {
            if (team == null) continue;
            if (!passFilter(team, filterByAge, ageStart, ageEnd,
                    filterBySkill, skillTarget)) continue;
            out.add(team);
        }

        _displayTeams.setValue(out);
    }

    private boolean passFilter(Team team,
                               boolean filterByAge, int ageStart, int ageEnd,
                               boolean filterBySkill, int skillTarget) {
        // 검색어
        if (!searchQuery.isEmpty()) {
            String name = team.getTeamName() == null ? "" : team.getTeamName().toLowerCase();
            if (!name.contains(searchQuery)) return false;
        }

        // 지역 필터
        String region = team.getRegion() == null ? "" : team.getRegion();
        if (!isAll(filterCity)     && !region.contains(filterCity))     return false;
        if (!isAll(filterDistrict) && !region.contains(filterDistrict)) return false;

        // 실력 필터
        if (filterBySkill && skillTarget >= 0) {
            int avg = team.getSkillAverage() != null ? team.getSkillAverage() : -1;
            if (avg != skillTarget) return false;
        }

        // 나이 필터
        if (filterByAge) {
            String ageRange = team.getAgeRange() == null ? "" : team.getAgeRange();
            if (!ageRange.isEmpty()) {
                String[] parts = ageRange.split("~");
                int teamAgeStart = parseIntOrDefault(parts[0].trim(), 0);
                int teamAgeEnd   = parts.length > 1
                        ? parseIntOrDefault(parts[1].trim(), 100) : teamAgeStart;
                if (teamAgeEnd < ageStart || teamAgeStart > ageEnd) return false;
            }
        }

        return true;
    }

    private boolean isAll(@Nullable String v) {
        return v == null || v.isEmpty() || "전체".equals(v.trim());
    }

    private int parseIntOrDefault(String s, int defaultVal) {
        try { return Integer.parseInt(s.trim()); }
        catch (Exception e) { return defaultVal; }
    }

    // ── 생명주기 ──────────────────────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        super.onCleared();
        // ViewModel 이 완전히 소멸될 때 Firestore 리스너 해제.
        // 해제할 observer 자체가 사라졌으므로 listenerReg.remove() 만으로 충분.
        if (listenerReg != null) {
            listenerReg.remove();
            listenerReg = null;
        }
    }
}