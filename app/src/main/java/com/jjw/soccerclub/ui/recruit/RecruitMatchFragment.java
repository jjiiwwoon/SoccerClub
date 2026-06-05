package com.jjw.soccerclub.ui.recruit;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.model.MatchFilters;
import com.jjw.soccerclub.model.RecruitFilters;
import com.jjw.soccerclub.ui.common.ApplicationsListActivity;
import com.jjw.soccerclub.ui.match.MatchFilterSheet;
import com.jjw.soccerclub.ui.match.MatchListFragment;
import com.jjw.soccerclub.ui.team.TeamMatchFragment;
import com.jjw.soccerclub.util.AppUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class RecruitMatchFragment extends Fragment
        implements RecruitFilterSheet.OnRecruitFilterApplied,
        MatchFilterSheet.OnMatchFilterApplied {

    private View btnRecruitTab, btnMatchTab;
    private View btnApplicationsList;
    private View matchModeTabsCard;
    private View btnMatchListTab, btnTeamMatchTab;
    private View btnTeamFindGlobal;
    private View filterBarContainer;
    private View btnOpenFilter;
    private LinearLayout selectedFilterChips;
    private View badgeNew;

    private boolean isRecruitTab = true;
    private boolean isTeamMode   = false;
    private boolean isOpen       = false;
    private boolean hasTeam      = false;
    private boolean canWrite     = false;

    private View scrim, actions;
    private FloatingActionButton fab;
    private View btnActionRecruit, btnActionMatch;

    private RecruitFilters currentRecruitFilters = null;
    private MatchFilters   currentMatchFilters   = null;

    private String myTeamId = "";
    private ListenerRegistration profileReg;
    private ListenerRegistration matchesReg, recruitsReg, matchesBadgeReg;
    private final Map<String, ListenerRegistration> applicantRegs = new HashMap<>();
    private final Map<String, Boolean> postHasNew    = new HashMap<>();
    private final Map<String, Long>    latestTsByPost = new HashMap<>();

    private final FirebaseAuth      auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db   = FirebaseFirestore.getInstance();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_recruit_match, container, false);

        btnRecruitTab     = v.findViewById(R.id.btnRecruitTab);
        btnMatchTab       = v.findViewById(R.id.btnMatchTab);
        matchModeTabsCard = v.findViewById(R.id.matchModeTabsCard);
        btnMatchListTab   = v.findViewById(R.id.btnMatchListTab);
        btnTeamMatchTab   = v.findViewById(R.id.btnTeamMatchTab);
        btnTeamFindGlobal = v.findViewById(R.id.btnTeamFindGlobal);
        filterBarContainer = v.findViewById(R.id.filterBarContainer);
        btnOpenFilter     = v.findViewById(R.id.btnOpenFilter);
        selectedFilterChips = v.findViewById(R.id.selectedFilterChips);
        btnApplicationsList = v.findViewById(R.id.btnApplicationsList);
        badgeNew          = v.findViewById(R.id.badgeNewApplications);
        scrim             = v.findViewById(R.id.speedDialScrim);
        actions           = v.findViewById(R.id.speedDialActions);
        fab               = v.findViewById(R.id.fabSpeedDial);
        btnActionRecruit  = v.findViewById(R.id.btnActionRecruit);
        btnActionMatch    = v.findViewById(R.id.btnActionMatch);

        if (btnApplicationsList != null) {
            btnApplicationsList.setOnClickListener(view -> {
                if (badgeNew != null) badgeNew.setVisibility(View.GONE);
                startActivity(new Intent(requireContext(), ApplicationsListActivity.class));
            });
        }

        if (btnOpenFilter != null) btnOpenFilter.setOnClickListener(view -> openFilterSheet());

        loadChildFragment(new RecruitListFragment(), true);

        btnRecruitTab.setOnClickListener(view -> {
            selectRecruitTab();
            loadChildFragment(new RecruitListFragment(), true);
        });
        btnMatchTab.setOnClickListener(view -> {
            selectMatchTab();
            isTeamMode = false;
            loadChildFragment(new MatchListFragment(), false);
        });

        if (btnMatchListTab != null) {
            btnMatchListTab.setOnClickListener(view -> {
                isRecruitTab = false;
                isTeamMode   = false;
                loadChildFragment(new MatchListFragment(), false);
            });
        }

        if (btnTeamMatchTab != null) {
            btnTeamMatchTab.setOnClickListener(view -> {
                isRecruitTab = false;
                isTeamMode   = true;
                loadChildFragment(new TeamMatchFragment(), false);
            });
        }

        if (btnTeamFindGlobal != null) {
            btnTeamFindGlobal.setOnClickListener(view -> {
                Fragment current = getChildFragmentManager()
                        .findFragmentById(R.id.recruit_match_container);
                if (current instanceof TeamMatchFragment) {
                    ((TeamMatchFragment) current).openFilterDialog();
                }
            });
        }

        fab.setOnClickListener(view -> toggleSpeedDial());
        scrim.setOnClickListener(view -> closeSpeedDial());

        btnActionRecruit.setOnClickListener(view -> {
            if (!ensureCanWrite()) return;
            closeSpeedDial();
            startActivity(new Intent(requireContext(), CreateRecruitActivity.class));
        });

        btnActionMatch.setOnClickListener(view -> {
            if (!ensureCanWrite()) return;
            closeSpeedDial();
            startActivity(new Intent(requireContext(),
                    com.jjw.soccerclub.ui.match.CreateMatchActivity.class));
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                new OnBackPressedCallback(true) {
                    @Override public void handleOnBackPressed() {
                        if (isOpen) closeSpeedDial();
                        else setEnabled(false);
                    }
                });

        startProfileListen();
        renderFilterChips();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        recomputeBadgeFromPrefs();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (profileReg != null) { profileReg.remove(); profileReg = null; }
        stopBadgeWatch();
    }

    private void loadChildFragment(Fragment fragment, boolean recruitSelected) {
        getChildFragmentManager().beginTransaction()
                .replace(R.id.recruit_match_container, fragment)
                .commit();

        isRecruitTab = recruitSelected;
        if (!recruitSelected) isTeamMode = fragment instanceof TeamMatchFragment;

        updateTabs(recruitSelected);
        updateMatchModeTabs();

        if (recruitSelected && fragment instanceof RecruitListFragment)
            ((RecruitListFragment) fragment).applyExternalFilters(currentRecruitFilters);
        if (!recruitSelected && fragment instanceof MatchListFragment)
            ((MatchListFragment) fragment).applyExternalFilters(currentMatchFilters);

        renderFilterChips();
        if (isOpen) closeSpeedDial();
    }

    private void updateTabs(boolean recruitSelected) {
        setTabStyle(btnRecruitTab, recruitSelected);
        setTabStyle(btnMatchTab, !recruitSelected);
    }

    private void setTabStyle(View btn, boolean selected) {
        if (btn instanceof TextView) {
            ((TextView) btn).setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            btn.setSelected(selected);
        }
    }

    private void updateMatchModeTabs() {
        if (matchModeTabsCard == null) return;
        matchModeTabsCard.setVisibility(isRecruitTab ? View.GONE : View.VISIBLE);

        setMatchTabStyle(btnMatchListTab, !isTeamMode);
        setMatchTabStyle(btnTeamMatchTab, isTeamMode);

        if (filterBarContainer != null)
            filterBarContainer.setVisibility(
                    (!isRecruitTab && isTeamMode) ? View.GONE : View.VISIBLE);
        if (btnTeamFindGlobal != null)
            btnTeamFindGlobal.setVisibility(
                    (!isRecruitTab && isTeamMode) ? View.VISIBLE : View.GONE);
    }

    private void setMatchTabStyle(View btn, boolean selected) {
        if (btn instanceof TextView) {
            ((TextView) btn).setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            ((TextView) btn).setTextColor(selected ? 0xFF2196F3 : 0xFF9E9E9E);
        }
    }

    private void openFilterSheet() {
        if (!isRecruitTab && isTeamMode) return;
        if (isRecruitTab) {
            RecruitFilterSheet.newInstance(currentRecruitFilters)
                    .show(getChildFragmentManager(), "RecruitFilterSheet");
        } else {
            MatchFilterSheet.newInstance(currentMatchFilters)
                    .show(getChildFragmentManager(), "MatchFilterSheet");
        }
    }

    @Override
    public void onRecruitFilterApplied(@NonNull RecruitFilters filters) {
        this.currentRecruitFilters = filters;
        Fragment current = getChildFragmentManager()
                .findFragmentById(R.id.recruit_match_container);
        if (current instanceof RecruitListFragment)
            ((RecruitListFragment) current).applyExternalFilters(filters);
        renderFilterChips();
    }

    @Override
    public void onMatchFilterApplied(@NonNull MatchFilters filters) {
        this.currentMatchFilters = filters;
        Fragment current = getChildFragmentManager()
                .findFragmentById(R.id.recruit_match_container);
        if (current instanceof MatchListFragment)
            ((MatchListFragment) current).applyExternalFilters(filters);
        renderFilterChips();
    }

    private void renderFilterChips() {
        if (!isAdded() || selectedFilterChips == null) return;
        selectedFilterChips.removeAllViews();
        if (!isRecruitTab && isTeamMode) return;
        if (isRecruitTab) renderRecruitChips();
        else renderMatchChips();
    }

    private void renderRecruitChips() {
        if (currentRecruitFilters == null) return;
        RecruitFilters f = currentRecruitFilters;
        if (isDefault(f.recruitType)) addFilterChip("정식선수, 용병");
        else {
            String label = "regular".equals(f.recruitType) ? "정식선수"
                    : "mercenary".equals(f.recruitType) ? "용병" : f.recruitType;
            addFilterChip(label);
        }
        String city = f.common != null ? f.common.city : null;
        String dist = f.common != null ? f.common.district : null;
        if (!isDefault(city) && !isDefault(dist)) addFilterChip(city + " " + dist);
        else if (!isDefault(city)) addFilterChip(city);
        if (f.skillMin != null || f.skillMax != null)
            addFilterChip("실력: " + (f.skillMin != null ? f.skillMin : "전체")
                    + "~" + (f.skillMax != null ? f.skillMax : "전체"));
        if (!isDefault(f.position)) addFilterChip(f.position);
        if (!isDefault(f.weekday)) addFilterChip(f.weekday);
    }

    private void renderMatchChips() {
        if (currentMatchFilters == null) return;
        MatchFilters f = currentMatchFilters;
        String city = f.common != null ? f.common.city : null;
        String dist = f.common != null ? f.common.district : null;
        if (!isDefault(city) && !isDefault(dist)) addFilterChip(city + " " + dist);
        else if (!isDefault(city)) addFilterChip(city);
        if (f.skillMin != null || f.skillMax != null)
            addFilterChip("실력: " + (f.skillMin != null ? f.skillMin : "전체")
                    + "~" + (f.skillMax != null ? f.skillMax : "전체"));
        if (!isDefault(f.weekday)) addFilterChip(f.weekday);
    }

    private void addFilterChip(@NonNull String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setPadding(26, 10, 26, 10);
        tv.setBackgroundResource(R.drawable.bg_filter_chip_selector);
        tv.setTextColor(0xFF000000);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(8, 0, 0, 0);
        tv.setLayoutParams(lp);
        selectedFilterChips.addView(tv);
    }

    private boolean isDefault(@Nullable String s) {
        return s == null || s.trim().isEmpty() || "전체".equals(s.trim());
    }

    private void toggleSpeedDial() {
        if (isOpen) closeSpeedDial();
        else { if (!ensureCanWrite()) return; openSpeedDial(); }
    }

    private void openSpeedDial() {
        if (isOpen) return;
        isOpen = true;
        scrim.setVisibility(View.VISIBLE);
        actions.setVisibility(View.VISIBLE);
        actions.setAlpha(0f);
        actions.setTranslationY(40f);
        actions.animate().alpha(1f).translationY(0f).setDuration(160).start();
        fab.animate().rotation(45f).setDuration(160).start();
    }

    private void closeSpeedDial() {
        if (!isOpen) return;
        isOpen = false;
        actions.animate().alpha(0f).translationY(40f).setDuration(140)
                .withEndAction(() -> actions.setVisibility(View.GONE)).start();
        scrim.setVisibility(View.GONE);
        fab.animate().rotation(0f).setDuration(140).start();
    }

    private boolean ensureCanWrite() {
        if (!hasTeam) {
            CustomToast.info(requireContext(), "팀이 없어서 글쓰기를 할 수 없어요.");
            return false;
        }
        if (!canWrite) {
            CustomToast.error(requireContext(), "글쓰기는 주장과 부주장만 가능합니다.");
            return false;
        }
        return true;
    }

    private void startProfileListen() {
        if (auth.getCurrentUser() == null) {
            hasTeam = false; canWrite = false; myTeamId = "";
            updateActionsEnabledState();
            return;
        }
        String uid = auth.getCurrentUser().getUid();
        if (profileReg != null) profileReg.remove();

        profileReg = db.collection("profiles").document(uid)
                .addSnapshotListener((@Nullable DocumentSnapshot snap,
                                      @Nullable FirebaseFirestoreException e) -> {
                    if (e != null || snap == null || !snap.exists()) {
                        hasTeam = false; canWrite = false; myTeamId = "";
                        updateActionsEnabledState();
                        stopBadgeWatch();
                        return;
                    }

                    String myTeam = snap.getString("myTeam");
                    hasTeam  = !AppUtils.isEmpty(myTeam);
                    myTeamId = hasTeam ? myTeam : "";

                    if (!hasTeam) {
                        canWrite = false;
                        stopBadgeWatch();
                        if (badgeNew != null) badgeNew.setVisibility(View.GONE);
                        updateActionsEnabledState();
                        return;
                    }

                    // ✅ captainUID/viceCaptainUID는 profiles가 아닌 teams 문서에서 읽어야 함
                    db.collection("teams").document(myTeamId).get()
                            .addOnSuccessListener(teamSnap -> {
                                if (!isAdded()) return;
                                if (teamSnap != null && teamSnap.exists()) {
                                    String captainUid = teamSnap.getString("captainUID");
                                    String viceUid    = teamSnap.getString("viceCaptainUID");
                                    canWrite = uid.equals(captainUid) || uid.equals(viceUid);
                                } else {
                                    canWrite = false;
                                }
                                startBadgeWatch();
                                updateActionsEnabledState();
                            })
                            .addOnFailureListener(err -> {
                                if (!isAdded()) return;
                                canWrite = false;
                                updateActionsEnabledState();
                            });
                });
    }

    private void updateActionsEnabledState() {
        boolean enabled = hasTeam && canWrite;
        if (btnActionRecruit != null) { btnActionRecruit.setEnabled(enabled); btnActionRecruit.setAlpha(enabled ? 1f : 0.3f); }
        if (btnActionMatch   != null) { btnActionMatch.setEnabled(enabled);   btnActionMatch.setAlpha(enabled ? 1f : 0.3f); }
        if (fab != null) fab.setAlpha(enabled ? 1f : 0.4f);
    }

    private void startBadgeWatch() {
        stopBadgeWatch();
        if (AppUtils.isEmpty(myTeamId)) return;

        matchesReg = db.collection("matches").whereEqualTo("teamId", myTeamId)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    Set<String> current = new HashSet<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        String postId = d.getId();
                        current.add("match:" + postId);
                        watchApplicantsForPost("match", postId);
                    }
                    cleanupMissingApplicantRegs(current);
                });

        recruitsReg = db.collection("recruitPosts").whereEqualTo("teamId", myTeamId)
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    Set<String> current = new HashSet<>();
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        String postId = d.getId();
                        current.add("recruit:" + postId);
                        watchApplicantsForPost("recruit", postId);
                    }
                    cleanupMissingApplicantRegs(current);
                });
    }

    private void stopBadgeWatch() {
        if (matchesReg != null)      { matchesReg.remove();      matchesReg = null; }
        if (recruitsReg != null)     { recruitsReg.remove();      recruitsReg = null; }
        if (matchesBadgeReg != null) { matchesBadgeReg.remove(); matchesBadgeReg = null; }
        for (ListenerRegistration r : applicantRegs.values()) if (r != null) r.remove();
        applicantRegs.clear();
        postHasNew.clear();
        latestTsByPost.clear();
    }

    private void cleanupMissingApplicantRegs(Set<String> currentKeys) {
        Iterator<Map.Entry<String, ListenerRegistration>> it = applicantRegs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ListenerRegistration> entry = it.next();
            if (!currentKeys.contains(entry.getKey())) {
                if (entry.getValue() != null) entry.getValue().remove();
                it.remove();
                postHasNew.remove(entry.getKey());
                latestTsByPost.remove(entry.getKey());
            }
        }
        updateBadgeVisibility();
    }

    private void watchApplicantsForPost(@NonNull String postType, @NonNull String postId) {
        String key = postType + ":" + postId;
        ListenerRegistration old = applicantRegs.get(key);
        if (old != null) old.remove();

        String collection = "match".equals(postType) ? "matches" : "recruitPosts";
        ListenerRegistration reg = db.collection(collection).document(postId)
                .collection("applicants")
                .addSnapshotListener((qs, err) -> {
                    if (err != null || qs == null) return;
                    long savedTs = getLastSeenTs(postType, postId);
                    boolean hasNew = false;
                    long maxTs = 0;
                    for (DocumentSnapshot d : qs.getDocuments()) {
                        Long ts = d.getLong("timestamp");
                        if (ts == null) ts = 0L;
                        if (ts > maxTs) maxTs = ts;
                        if (ts > savedTs) hasNew = true;
                    }
                    postHasNew.put(key, hasNew);
                    if (maxTs > 0) latestTsByPost.put(key, maxTs);
                    updateBadgeVisibility();
                });
        applicantRegs.put(key, reg);
    }

    private void updateBadgeVisibility() {
        if (!isAdded() || badgeNew == null) return;
        boolean anyNew = postHasNew.containsValue(true);
        badgeNew.setVisibility(anyNew ? View.VISIBLE : View.GONE);
    }

    private long getLastSeenTs(String postType, String postId) {
        if (!isAdded()) return 0L;
        String key = "last_seen_" + postType + "_" + postId;
        return requireActivity().getSharedPreferences("badge_prefs", 0).getLong(key, 0L);
    }

    private void recomputeBadgeFromPrefs() {
        if (!isAdded() || badgeNew == null) return;
        updateBadgeVisibility();
    }
    // 모집 탭 선택 시
    private void selectRecruitTab() {
        btnRecruitTab.setBackgroundResource(R.drawable.bg_tab_pill_selected);
        btnMatchTab.setBackgroundResource(R.drawable.bg_tab_pill_unselected);
        if (btnRecruitTab instanceof TextView) {
            ((TextView) btnRecruitTab).setTextColor(getResources().getColor(R.color.primary));
            ((TextView) btnRecruitTab).setTypeface(null, android.graphics.Typeface.BOLD);
        }
        if (btnMatchTab instanceof TextView) {
            ((TextView) btnMatchTab).setTextColor(getResources().getColor(R.color.text_hint));
            ((TextView) btnMatchTab).setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    // 시합 탭 선택 시
    private void selectMatchTab() {
        btnMatchTab.setBackgroundResource(R.drawable.bg_tab_pill_selected);
        btnRecruitTab.setBackgroundResource(R.drawable.bg_tab_pill_unselected);
        if (btnMatchTab instanceof TextView) {
            ((TextView) btnMatchTab).setTextColor(getResources().getColor(R.color.primary));
            ((TextView) btnMatchTab).setTypeface(null, android.graphics.Typeface.BOLD);
        }
        if (btnRecruitTab instanceof TextView) {
            ((TextView) btnRecruitTab).setTextColor(getResources().getColor(R.color.text_hint));
            ((TextView) btnRecruitTab).setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }
}