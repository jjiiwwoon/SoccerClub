package com.example.soccerclub.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.soccerclub.model.ChatRoomItem;
import com.example.soccerclub.repository.ChatRepository;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ChatViewModel extends ViewModel {

    private final ChatRepository repository = new ChatRepository();
    private ListenerRegistration listenerReg;

    // ── Fragment 가 observe 하는 LiveData ────────────────────────────────────────

    private final MutableLiveData<List<ChatRoomItem>> _chatRooms
            = new MutableLiveData<>(new ArrayList<>());
    public final LiveData<List<ChatRoomItem>> chatRooms = _chatRooms;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    public final LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<Boolean> _isEmpty = new MutableLiveData<>(false);
    public final LiveData<Boolean> isEmpty = _isEmpty;

    // ── 내부 상태 ────────────────────────────────────────────────────────────────

    private String cachedUid = null;

    // ── 외부에서 호출하는 메서드 ─────────────────────────────────────────────────

    /** 탭이 보일 때 호출 — onViewCreated 최초 진입 + onHiddenChanged(hidden=false) */
    public void startListening(String uid) {
        if (uid == null) return;
        cachedUid = uid;
        stopListening();
        _isLoading.setValue(true);
        _isEmpty.setValue(false);

        listenerReg = repository.listenChatRooms(uid, rooms -> {
            // ✅ 수정: 타입을 명시적으로 지정해 람다 파라미터 추론 오류 방지
            List<ChatRoomItem> result = rooms != null ? rooms : new ArrayList<>();
            _isLoading.setValue(false);
            _chatRooms.setValue(result);
            _isEmpty.setValue(result.isEmpty());
        });
    }

    /** 탭이 숨겨질 때 호출 — 배터리/네트워크 절약 */
    public void stopListening() {
        if (listenerReg != null) {
            listenerReg.remove();
            listenerReg = null;
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopListening();
    }
}