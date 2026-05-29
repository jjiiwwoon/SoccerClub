package com.jjw.soccerclub.ui.chat;

import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.adapter.ChatMessageAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatRoomActivity extends AppCompatActivity {

    private RecyclerView recyclerChat;
    private View chatRoot, inputBar, btnSend;
    private EditText editMessage;

    private ChatMessageAdapter adapter;
    private final List<ChatMessageAdapter.ChatMessage> messageList = new ArrayList<>();

    private String roomId, currentUid;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ✅ Fix 1: 리스너를 필드로 보관해서 onDestroy에서 해제
    private ListenerRegistration msgReg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_chat_room);

        chatRoot     = findViewById(R.id.chatRoot);
        recyclerChat = findViewById(R.id.recyclerChat);
        inputBar     = findViewById(R.id.inputBar);
        editMessage  = findViewById(R.id.editMessage);
        btnSend      = findViewById(R.id.btnSend);

        currentUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        roomId     = getIntent().getStringExtra("roomId");

        recyclerChat.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatMessageAdapter(messageList, currentUid, roomId, this);
        recyclerChat.setAdapter(adapter);

        ViewCompat.setOnApplyWindowInsetsListener(chatRoot, (v, insets) -> {
            Insets sys = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());

            if (inputBar != null) {
                ViewGroup.MarginLayoutParams lp =
                        (ViewGroup.MarginLayoutParams) inputBar.getLayoutParams();
                lp.bottomMargin = Math.max(sys.bottom, ime.bottom);
                inputBar.setLayoutParams(lp);
            }
            return insets;
        });

        recyclerChat.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (b < ob) recyclerChat.post(this::scrollToBottom);
        });

        btnSend.setOnClickListener(v -> {
            String content = editMessage.getText().toString().trim();
            if (content.isEmpty()) return;
            sendMessage(content);
            editMessage.setText("");
        });

        editMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String content = editMessage.getText().toString().trim();
                if (content.isEmpty()) return true;
                sendMessage(content);
                editMessage.setText("");
                return true;
            }
            return false;
        });

        listenForMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        markRoomRead();
    }

    @Override
    protected void onPause() {
        super.onPause();
        markRoomRead();
    }

    // ✅ Fix 1: onDestroy에서 리스너 해제
    @Override
    protected void onDestroy() {
        if (msgReg != null) {
            msgReg.remove();
            msgReg = null;
        }
        super.onDestroy();
    }

    private void listenForMessages() {
        // ✅ Fix 1: addSnapshotListener 반환값을 msgReg 필드에 저장
        msgReg = db.collection("chatRooms").document(roomId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    // ✅ 매번 스냅샷 전체를 다시 빌드 → 항상 Firestore 순서 그대로
                    List<ChatMessageAdapter.ChatMessage> newList = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : snapshots.getDocuments()) {
                        String existingStatus = null;
                        for (ChatMessageAdapter.ChatMessage old : messageList) {
                            if (doc.getId().equals(old.messageDocId)) {
                                existingStatus = old.inviteStatus;
                                break;
                            }
                        }
                        ChatMessageAdapter.ChatMessage msg =
                                new ChatMessageAdapter.ChatMessage();
                        msg.senderId     = doc.getString("senderId");
                        msg.text         = doc.getString("content");
                        msg.timestamp    = getSafeTimestamp(doc);
                        msg.messageType  = doc.getString("messageType");
                        msg.teamId       = doc.getString("teamId");
                        msg.messageDocId = doc.getId();
                        String fsStatus  = doc.getString("inviteStatus");
                        msg.inviteStatus = fsStatus != null ? fsStatus : existingStatus;
                        newList.add(msg);
                    }
                    messageList.clear();
                    messageList.addAll(newList);
                    // ✅ Firestore pending write 등으로 순서가 틀어질 수 있으므로 명시적 정렬
                    messageList.sort((a, b) -> {
                        if (a.timestamp == 0 && b.timestamp == 0) return 0;
                        if (a.timestamp == 0) return 1;  // timestamp 없는 건 맨 뒤
                        if (b.timestamp == 0) return -1;
                        return Long.compare(a.timestamp, b.timestamp); // 오름차순
                    });
                    adapter.notifyDataSetChanged();
                    scrollToBottom();
                    markRoomRead();
                });
    }

    private void sendMessage(String content) {
        long localNow = System.currentTimeMillis();
        Map<String, Object> message = new HashMap<>();
        message.put("senderId",    currentUid);
        message.put("content",     content);
        message.put("messageType", "text");
        // ✅ 클라이언트 시계 차이로 인한 순서 오류 방지 → 서버 타임스탬프 사용
        message.put("timestamp",   com.google.firebase.firestore.FieldValue.serverTimestamp());
        // 로컬 표시용 클라이언트 타임스탬프도 함께 저장
        message.put("clientTs",    localNow);

        db.collection("chatRooms").document(roomId)
                .collection("messages").add(message)
                .addOnSuccessListener(d -> {
                    // lastMessage, lastTimestamp 업데이트
                    Map<String, Object> roomUpdates = new HashMap<>();
                    roomUpdates.put("lastMessage",   content);
                    roomUpdates.put("lastTimestamp", localNow);
                    db.collection("chatRooms").document(roomId)
                            .set(roomUpdates, SetOptions.merge());
                    // ✅ lastRead.uid 는 update() 로 별도 처리 (dotted path)
                    db.collection("chatRooms").document(roomId)
                            .update("lastRead." + currentUid, localNow);
                });
    }

    private void markRoomRead() {
        if (roomId == null || currentUid == null) return;
        long lastTs = messageList.isEmpty() ? System.currentTimeMillis()
                : messageList.get(messageList.size() - 1).timestamp;
        // ✅ set(merge) → update() 로 변경
        // dotted path "lastRead.uid"는 update()에서만 중첩 경로로 올바르게 저장됨
        db.collection("chatRooms").document(roomId)
                .update("lastRead." + currentUid,
                        lastTs > 0 ? lastTs : System.currentTimeMillis());
    }

    private void scrollToBottom() {
        if (!messageList.isEmpty())
            recyclerChat.scrollToPosition(messageList.size() - 1);
    }

    // ✅ timestamp 필드가 Long이든 Firestore Timestamp든 안전하게 읽기
    private long getSafeTimestamp(com.google.firebase.firestore.DocumentSnapshot doc) {
        try {
            Object raw = doc.get("timestamp");
            if (raw == null) return 0L;
            if (raw instanceof Number) return ((Number) raw).longValue();
            if (raw instanceof com.google.firebase.Timestamp) {
                return ((com.google.firebase.Timestamp) raw).toDate().getTime();
            }
        } catch (Exception ignored) {}
        return 0L;
    }
}