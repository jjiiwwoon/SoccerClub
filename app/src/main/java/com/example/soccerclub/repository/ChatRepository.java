package com.example.soccerclub.repository;

import com.example.soccerclub.model.ChatRoomItem;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── 콜백 인터페이스 ───────────────────────────────────────────────────────────

    /**
     * ProfileRepository.ProfileCallback 과 동일한 패턴.
     * MutableLiveData 를 파라미터로 받는 대신 콜백으로 결과 전달.
     */
    public interface ChatRoomsCallback {
        void onResult(List<ChatRoomItem> rooms);
    }

    // ── 채팅방 목록 실시간 리스너 ──────────────────────────────────────────────────

    /**
     * 채팅방 목록 실시간 리스너 등록.
     *
     * 각 방마다 상대방 프로필 + 안읽음 카운트를 병렬로 조회한 뒤
     * 모든 작업이 완료되면 callback.onResult() 로 최종 목록 전달.
     *
     * @param currentUid 현재 로그인 사용자 UID
     * @param callback   결과 수신 콜백
     * @return           해제용 ListenerRegistration
     */
    public ListenerRegistration listenChatRooms(
            String currentUid,
            ChatRoomsCallback callback) {

        return db.collection("chatRooms")
                .whereArrayContains("participants", currentUid)
                .orderBy("lastTimestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) {
                        callback.onResult(new ArrayList<>());
                        return;
                    }

                    List<ChatRoomItem> rooms   = new ArrayList<>();
                    List<Task<?>>      pending = new ArrayList<>();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ChatRoomItem item = doc.toObject(ChatRoomItem.class);
                        if (item == null) continue;
                        item.setRoomId(doc.getId());

                        // toObject() 보완
                        Long lastTs = doc.getLong("lastTimestamp");
                        if (lastTs != null) item.setLastTimestamp(lastTs);
                        String lastMsg = doc.getString("lastMessage");
                        if (lastMsg != null) item.setLastMessage(lastMsg);

                        // 상대방 프로필
                        String peerUid = resolvePeerUid(doc, currentUid);
                        if (peerUid != null) {
                            final ChatRoomItem fi = item;
                            Task<DocumentSnapshot> metaT =
                                    db.collection("profiles").document(peerUid).get()
                                            .addOnSuccessListener(p -> {
                                                if (p.exists()) {
                                                    fi.setPeerNickname(p.getString("nickname"));
                                                    fi.setPeerProfileImage(
                                                            p.getString("profileImageUrl"));
                                                }
                                            });
                            pending.add(metaT);
                        }

                        // 안읽음 카운트
                        long lastReadTs = resolveLastReadTs(doc, currentUid);
                        final ChatRoomItem fi2    = item;
                        final long        finalTs = lastReadTs;

                        Task<QuerySnapshot> unreadT =
                                db.collection("chatRooms")
                                        .document(doc.getId())
                                        .collection("messages")
                                        .whereGreaterThan("timestamp", finalTs)
                                        .orderBy("timestamp",
                                                com.google.firebase.firestore.Query.Direction.ASCENDING)
                                        .get()
                                        .addOnSuccessListener(qs -> {
                                            int cnt = 0;
                                            for (DocumentSnapshot m : qs.getDocuments()) {
                                                String sender = m.getString("senderId");
                                                if (sender != null
                                                        && !sender.equals(currentUid)) cnt++;
                                            }
                                            fi2.setUnreadCount(cnt);
                                        })
                                        .addOnFailureListener(ex -> fi2.setUnreadCount(0));
                        pending.add(unreadT);

                        rooms.add(item);
                    }

                    // 모든 보조 쿼리 완료 후 콜백 전달
                    Tasks.whenAllComplete(pending)
                            .addOnCompleteListener(done -> callback.onResult(rooms));
                });
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String resolvePeerUid(DocumentSnapshot doc, String currentUid) {
        List<String> participants = (List<String>) doc.get("participants");
        if (participants == null) return null;
        for (String p : participants) {
            if (p != null && !p.equals(currentUid)) return p;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private long resolveLastReadTs(DocumentSnapshot doc, String currentUid) {
        Map<String, Object> lastRead = (Map<String, Object>) doc.get("lastRead");
        if (lastRead != null && lastRead.get(currentUid) instanceof Number) {
            return ((Number) lastRead.get(currentUid)).longValue();
        }
        return 0L;
    }
}