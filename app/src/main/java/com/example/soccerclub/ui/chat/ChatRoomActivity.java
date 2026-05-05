package com.example.soccerclub.ui.chat;

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

import com.example.soccerclub.R;
import com.example.soccerclub.adapter.ChatMessageAdapter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.FirebaseFirestore;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_chat_room);

        chatRoot    = findViewById(R.id.chatRoot);
        recyclerChat = findViewById(R.id.recyclerChat);
        inputBar    = findViewById(R.id.inputBar);
        editMessage = findViewById(R.id.editMessage);
        btnSend     = findViewById(R.id.btnSend);

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

    private void listenForMessages() {
        db.collection("chatRooms").document(roomId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    boolean added = false;
                    for (DocumentChange dc : snapshots.getDocumentChanges()) {
                        if (dc.getType() == DocumentChange.Type.ADDED) {
                            ChatMessageAdapter.ChatMessage msg =
                                    new ChatMessageAdapter.ChatMessage();
                            msg.senderId  = dc.getDocument().getString("senderId");
                            msg.text      = dc.getDocument().getString("content");
                            Long ts = dc.getDocument().getLong("timestamp");
                            msg.timestamp = ts != null ? ts : 0L;
                            messageList.add(msg);
                            adapter.notifyItemInserted(messageList.size() - 1);
                            added = true;
                        }
                    }
                    if (added) {
                        scrollToBottom();
                        markRoomRead();
                    }
                });
    }

    private void sendMessage(String content) {
        long now = System.currentTimeMillis();
        Map<String, Object> message = new HashMap<>();
        message.put("senderId",    currentUid);
        message.put("content",     content);
        message.put("messageType", "text");
        message.put("timestamp",   now);

        db.collection("chatRooms").document(roomId)
                .collection("messages").add(message)
                .addOnSuccessListener(d -> {
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("lastMessage",  content);
                    updates.put("lastTimestamp", now);
                    updates.put("lastRead." + currentUid, now);
                    db.collection("chatRooms").document(roomId)
                            .set(updates, SetOptions.merge());
                });
    }

    private void markRoomRead() {
        if (roomId == null || currentUid == null) return;
        long lastTs = messageList.isEmpty() ? System.currentTimeMillis()
                : messageList.get(messageList.size() - 1).timestamp;
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastRead." + currentUid, lastTs > 0 ? lastTs : System.currentTimeMillis());
        db.collection("chatRooms").document(roomId).set(updates, SetOptions.merge());
    }

    private void scrollToBottom() {
        if (!messageList.isEmpty())
            recyclerChat.scrollToPosition(messageList.size() - 1);
    }
}