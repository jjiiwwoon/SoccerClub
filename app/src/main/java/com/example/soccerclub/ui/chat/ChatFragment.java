package com.example.soccerclub.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.soccerclub.R;
import com.example.soccerclub.adapter.ChatRoomItemAdapter;
import com.example.soccerclub.common.CustomToast;
import com.example.soccerclub.common.StateLayout;
import com.example.soccerclub.model.ChatRoomItem;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatFragment extends Fragment {

    private RecyclerView recyclerChatList;
    private ChatRoomItemAdapter adapter;
    private final List<ChatRoomItem> chatRoomList = new ArrayList<>();

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String currentUid;
    private ListenerRegistration roomsReg;
    private StateLayout state;
    private boolean firstResultHandled = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        state            = view.findViewById(R.id.stateLayout);
        recyclerChatList = view.findViewById(R.id.recyclerChatList);

        if (state != null) state.showLoading();

        recyclerChatList.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChatRoomItemAdapter(chatRoomList, getContext(), this::onChatRoomClick);
        recyclerChatList.setAdapter(adapter);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        currentUid = auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;

        if (currentUid == null) {
            CustomToast.error(requireContext(), "로그인 정보를 확인할 수 없어요.");
            if (state != null) state.showEmpty();
        } else {
            listenChatRooms();
        }

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopListener();
    }

    // ✅ show/hide 방식에서 탭이 다시 보일 때 리스너 재시작
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!isAdded()) return;
        if (!hidden) {
            // 탭이 다시 보일 때 → 리스너 재시작 (새 채팅방 즉시 반영)
            firstResultHandled = false;
            listenChatRooms();
        } else {
            // 탭이 숨겨질 때 → 리스너 해제 (배터리/네트워크 절약)
            stopListener();
        }
    }

    private void stopListener() {
        if (roomsReg != null) {
            roomsReg.remove();
            roomsReg = null;
        }
    }

    private void listenChatRooms() {
        stopListener(); // 기존 리스너 먼저 해제

        if (currentUid == null) return;
        if (state != null) state.showLoading();

        roomsReg = db.collection("chatRooms")
                .whereArrayContains("participants", currentUid)
                .orderBy("lastTimestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snap, e) -> {
                    if (!isAdded()) return;
                    if (e != null || snap == null) {
                        Log.e("ChatFragment", "채팅방 로딩 실패: " + (e != null ? e.getMessage() : "null"));
                        if (!firstResultHandled && state != null) state.showEmpty();
                        return;
                    }

                    chatRoomList.clear();
                    List<Task<?>> pendingTasks = new ArrayList<>();

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        ChatRoomItem item = doc.toObject(ChatRoomItem.class);
                        if (item == null) continue;
                        item.setRoomId(doc.getId());

                        // ✅ toObject() 매핑이 불완전할 수 있으므로 직접 읽기
                        Long lastTs = doc.getLong("lastTimestamp");
                        if (lastTs != null) item.setLastTimestamp(lastTs);

                        String lastMsg = doc.getString("lastMessage");
                        if (lastMsg != null) item.setLastMessage(lastMsg);

                        List<String> participants = (List<String>) doc.get("participants");
                        String peerUid = null;
                        if (participants != null) {
                            for (String p : participants) {
                                if (p != null && !p.equals(currentUid)) { peerUid = p; break; }
                            }
                        }

                        if (peerUid != null) {
                            final String finalPeerUid = peerUid;
                            final ChatRoomItem finalItem = item;
                            Task<DocumentSnapshot> metaT = db.collection("profiles")
                                    .document(finalPeerUid).get()
                                    .addOnSuccessListener(p -> {
                                        if (p.exists()) {
                                            finalItem.setPeerNickname(p.getString("nickname"));
                                            finalItem.setPeerProfileImage(p.getString("profileImageUrl"));
                                        }
                                    });
                            pendingTasks.add(metaT);
                        }

                        long lastReadTs = 0L;
                        Map<String, Object> lastRead = (Map<String, Object>) doc.get("lastRead");
                        if (lastRead != null && lastRead.get(currentUid) instanceof Number) {
                            lastReadTs = ((Number) lastRead.get(currentUid)).longValue();
                        }
                        final long finalLastReadTs = lastReadTs;
                        final ChatRoomItem finalItem2 = item;

                        Task<QuerySnapshot> unreadT = db.collection("chatRooms")
                                .document(doc.getId()).collection("messages")
                                .whereGreaterThan("timestamp", finalLastReadTs)
                                .orderBy("timestamp", Query.Direction.ASCENDING)
                                .get()
                                .addOnSuccessListener(qs -> {
                                    int cnt = 0;
                                    for (DocumentSnapshot m : qs.getDocuments()) {
                                        String sender = m.getString("senderId");
                                        if (sender != null && !sender.equals(currentUid)) cnt++;
                                    }
                                    finalItem2.setUnreadCount(cnt);
                                })
                                .addOnFailureListener(ex -> finalItem2.setUnreadCount(0));
                        pendingTasks.add(unreadT);

                        chatRoomList.add(item);
                    }

                    if (chatRoomList.isEmpty()) {
                        if (!isAdded()) return;
                        adapter.notifyDataSetChanged();
                        firstResultHandled = true;
                        if (state != null) state.showEmpty();
                        return;
                    }

                    Tasks.whenAllComplete(pendingTasks).addOnCompleteListener(done -> {
                        if (!isAdded()) return;
                        adapter.notifyDataSetChanged();
                        if (!firstResultHandled) {
                            firstResultHandled = true;
                            if (state != null) state.showContent();
                        } else {
                            // ✅ 이후 업데이트도 content 상태 유지
                            if (state != null) state.showContent();
                        }
                    });
                });
    }

    private void onChatRoomClick(ChatRoomItem item) {
        Intent intent = new Intent(getContext(), ChatRoomActivity.class);
        intent.putExtra("roomId", item.getRoomId());
        startActivity(intent);
    }
}