package com.example.soccerclub.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.adapter.ApplicationsAdapter;
import com.example.soccerclub.ui.chat.ChatRoomActivity;
import com.example.soccerclub.util.AppUtils;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

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

    private String currentUid = "";
    private String myTeamId   = "";

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
            mineSelected = true;
            setBtnStyle(true);
            loadData();
        });
        btnSubjectApplied.setOnClickListener(v -> {
            mineSelected = false;
            setBtnStyle(false);
            loadData();
        });
        chipTypeAll.setOnClickListener(v -> {
            typeFilter = "all";
            setTypeSelected("all");
            loadData();
        });
        chipTypeRecruit.setOnClickListener(v -> {
            typeFilter = "recruit";
            setTypeSelected("recruit");
            loadData();
        });
        chipTypeMatch.setOnClickListener(v -> {
            typeFilter = "match";
            setTypeSelected("match");
            loadData();
        });

        db.collection("profiles").document(currentUid).get()
                .addOnSuccessListener(doc -> {
                    myTeamId = AppUtils.safe(doc.getString("myTeam"));
                    loadData();
                });
    }

    private void loadData() {
        if (mineSelected) loadMine();
        else              loadApplied();
    }

    private void loadMine() {
        if (AppUtils.isEmpty(myTeamId)) {
            adapter.setItems(Collections.emptyList(), ApplicationsAdapter.TYPE_MINE);
            return;
        }

        String matchColl   = "match".equals(typeFilter)   ? "matches"     : null;
        String recruitColl = "recruit".equals(typeFilter) ? "recruitPosts" : null;

        List<Task<List<ApplicationsAdapter.Item>>> tasks = new ArrayList<>();
        if (!"recruit".equals(typeFilter)) tasks.add(collectMine("matches",     POST_MATCH));
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

        db.collection(collection).whereEqualTo("teamId", myTeamId)
                .orderBy("matchTs", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(qs -> {
                    List<ApplicationsAdapter.Item> list = new ArrayList<>();
                    if (qs.isEmpty()) { tcs.setResult(list); return; }

                    int total = qs.size();
                    int[] done = {0};

                    for (DocumentSnapshot d : qs.getDocuments()) {
                        ApplicationsAdapter.Item it = buildItem(d, postType);
                        String coll = POST_MATCH.equals(postType) ? "matches" : "recruitPosts";
                        db.collection(coll).document(d.getId()).collection("applicants").get()
                                .addOnSuccessListener(apSnap -> {
                                    List<ApplicationsAdapter.Applicant> applicants = new ArrayList<>();
                                    for (DocumentSnapshot ap : apSnap.getDocuments()) {
                                        ApplicationsAdapter.Applicant a = new ApplicationsAdapter.Applicant();
                                        a.applicantDocId  = ap.getId();
                                        a.applicantUserId = AppUtils.safe(ap.getString("userId"));
                                        a.teamId    = AppUtils.safe(ap.getString("teamId"));
                                        a.teamName  = AppUtils.safe(ap.getString("teamName"));
                                        a.nickname  = AppUtils.safe(ap.getString("nickname"));
                                        a.logoUrl   = AppUtils.safe(ap.getString("teamLogoUrl"));
                                        a.status    = AppUtils.safe(ap.getString("status"));
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
                })
                .addOnFailureListener(e -> tcs.setResult(new ArrayList<>()));
        return tcs.getTask();
    }

    private void loadApplied() {
        List<Task<QuerySnapshot>> waits = new ArrayList<>();

        Task<QuerySnapshot> tUser = db.collectionGroup("applicants")
                .whereEqualTo("userId", currentUid).get();
        waits.add(tUser);

        Task<QuerySnapshot> tTeam = null;
        if (!AppUtils.isEmpty(myTeamId)) {
            tTeam = db.collectionGroup("applicants")
                    .whereEqualTo("teamId", myTeamId).get();
            waits.add(tTeam);
        }

        final Task<QuerySnapshot> finalTTeam = tTeam;
        Tasks.whenAllSuccess(waits).addOnSuccessListener(res -> {
            Map<String, ApplicationsAdapter.Item> dedup = new LinkedHashMap<>();
            List<Task<DocumentSnapshot>> postGets = new ArrayList<>();

            for (Object r : res) {
                QuerySnapshot qs = (QuerySnapshot) r;
                for (DocumentSnapshot ap : qs.getDocuments()) {
                    String path     = ap.getReference().getPath();
                    boolean isMatch = path.contains("/matches/");
                    if ("recruit".equals(typeFilter) && isMatch)    continue;
                    if ("match".equals(typeFilter)   && !isMatch)   continue;

                    String postId   = ap.getReference().getParent().getParent().getId();
                    String postType = isMatch ? POST_MATCH : POST_RECRUIT;
                    if (dedup.containsKey(postId)) continue;

                    ApplicationsAdapter.Item it = new ApplicationsAdapter.Item();
                    it.postId   = postId;
                    it.postType = postType;
                    it.status   = AppUtils.safe(ap.getString("status"));
                    Long ts = ap.getLong("timestamp");
                    it.timestamp = ts != null ? ts : 0L;
                    dedup.put(postId, it);

                    String coll = isMatch ? "matches" : "recruitPosts";
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
                    it.teamName   = AppUtils.safe(m.getString("teamName"));
                    it.teamLogoUrl = AppUtils.firstNonEmpty(m.getString("teamLogoUrl"), m.getString("logoUrl"));
                    it.date       = AppUtils.safe(m.getString("date"));
                    it.time       = AppUtils.safe(m.getString("time"));
                    it.stadium    = AppUtils.firstNonEmpty(m.getString("stadiumAddress"), m.getString("address"));
                    Long mts = m.getLong("matchTs");
                    it.matchTs = mts != null ? mts : it.timestamp;
                }
                List<ApplicationsAdapter.Item> list = new ArrayList<>(dedup.values());
                list.sort((a, b) -> Long.compare(b.matchTs, a.matchTs));
                adapter.setItems(list, ApplicationsAdapter.TYPE_APPLIED);
            });
        });
    }

    private ApplicationsAdapter.Item buildItem(DocumentSnapshot d, String postType) {
        ApplicationsAdapter.Item it = new ApplicationsAdapter.Item();
        it.postId     = d.getId();
        it.postType   = postType;
        it.teamName   = AppUtils.safe(d.getString("teamName"));
        it.teamLogoUrl = AppUtils.firstNonEmpty(d.getString("teamLogoUrl"), d.getString("logoUrl"));
        it.date       = AppUtils.safe(d.getString("date"));
        it.time       = AppUtils.safe(d.getString("time"));
        it.stadium    = AppUtils.firstNonEmpty(d.getString("stadiumAddress"), d.getString("address"));
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

    private void handleAccept(ApplicationsAdapter.Item post, ApplicationsAdapter.Applicant applicant) {
        String coll = POST_MATCH.equals(post.postType) ? "matches" : "recruitPosts";
        Map<String, Object> up = new LinkedHashMap<>();
        up.put("status", "accepted");
        db.collection(coll).document(post.postId)
                .collection("applicants").document(applicant.applicantDocId)
                .update(up)
                .addOnSuccessListener(v -> {
                    adapter.updateApplicantStatus(post.postId, applicant.applicantDocId, "accepted");
                    openChat(applicant.applicantUserId);
                });
    }

    private void handleReject(ApplicationsAdapter.Item post, ApplicationsAdapter.Applicant applicant) {
        String coll = POST_MATCH.equals(post.postType) ? "matches" : "recruitPosts";
        db.collection(coll).document(post.postId)
                .collection("applicants").document(applicant.applicantDocId)
                .update("status", "rejected")
                .addOnSuccessListener(v ->
                        adapter.updateApplicantStatus(post.postId, applicant.applicantDocId, "rejected"));
    }

    private void openChat(String otherUid) {
        if (AppUtils.isEmpty(otherUid) || AppUtils.isEmpty(currentUid)) return;
        String roomId = currentUid.compareTo(otherUid) < 0
                ? currentUid + "_" + otherUid : otherUid + "_" + currentUid;
        long now = System.currentTimeMillis();
        DocumentReference roomRef = db.collection("chatRooms").document(roomId);
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("participants",   Arrays.asList(currentUid, otherUid));
        base.put("lastMessage",    "");
        base.put("lastTimestamp",  now);
        roomRef.get().addOnSuccessListener(snap -> {
            Task<?> t = snap.exists()
                    ? roomRef.update("lastTimestamp", now)
                    : roomRef.set(base);
            t.addOnSuccessListener(v -> {
                Intent i = new Intent(this, ChatRoomActivity.class);
                i.putExtra("roomId", roomId);
                startActivity(i);
            });
        });
    }

    private void setBtnStyle(boolean mineSelected) {
        if (mineSelected) {
            btnSubjectMine.setTextColor(0xFF2196F3);
            btnSubjectApplied.setTextColor(0xFF666666);
        } else {
            btnSubjectApplied.setTextColor(0xFF2196F3);
            btnSubjectMine.setTextColor(0xFF666666);
        }
    }

    private void setTypeSelected(String t) {
        chipTypeAll.setTextColor("all".equals(t)     ? 0xFF2196F3 : 0xFF666666);
        chipTypeRecruit.setTextColor("recruit".equals(t) ? 0xFF2196F3 : 0xFF666666);
        chipTypeMatch.setTextColor("match".equals(t)   ? 0xFF2196F3 : 0xFF666666);
    }
}