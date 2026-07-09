package com.jjw.soccerclub.ui.chat;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jjw.soccerclub.R;
import com.jjw.soccerclub.adapter.ChatRoomItemAdapter;
import com.jjw.soccerclub.common.CustomToast;
import com.jjw.soccerclub.common.StateLayout;
import com.jjw.soccerclub.model.ChatRoomItem;
import com.jjw.soccerclub.viewmodel.ChatViewModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;

/**
 * 채팅방 목록 화면.
 *
 * [변경 전] Fragment 가 직접 하던 일
 *   - FirebaseFirestore db 직접 사용
 *   - ListenerRegistration roomsReg 직접 보관/해제
 *   - firstResultHandled 플래그 직접 관리
 *   - listenChatRooms() 에서 채팅방/프로필/안읽음 3중 Firestore 호출
 *   - onHiddenChanged 에서 리스너 수동 on/off
 *
 * [변경 후] Fragment 가 하는 일
 *   - viewModel.chatRooms observe → adapter 업데이트
 *   - viewModel.isLoading / isEmpty observe → StateLayout 제어
 *   - onHiddenChanged → viewModel.startListening / stopListening 위임
 *
 * Firestore 코드가 Fragment 에 단 한 줄도 없다.
 */
public class ChatFragment extends Fragment {

    // ── 뷰 ────────────────────────────────────────────────────────────────────────
    private RecyclerView       recyclerChatList;
    private ChatRoomItemAdapter adapter;
    private StateLayout         state;

    // ── ViewModel ────────────────────────────────────────────────────────────────
    private ChatViewModel viewModel;

    // ── UID ──────────────────────────────────────────────────────────────────────
    private String currentUid = null;

    // ── 생명주기 ──────────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);

        state            = view.findViewById(R.id.stateLayout);
        recyclerChatList = view.findViewById(R.id.recyclerChatList);

        if (state != null) state.showLoading();

        // adapter 초기화 — 빈 리스트로 시작
        List<ChatRoomItem> emptyList = new ArrayList<>();
        adapter = new ChatRoomItemAdapter(emptyList, requireContext(), this::onChatRoomClick);
        recyclerChatList.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerChatList.setAdapter(adapter);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            CustomToast.error(requireContext(), "로그인 정보를 확인할 수 없어요.");
            if (state != null) state.showEmpty();
            return view;
        }
        currentUid = user.getUid();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (currentUid == null) return;

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        // ── LiveData 구독 ─────────────────────────────────────────────────────────

        viewModel.chatRooms.observe(getViewLifecycleOwner(), rooms -> {
            // ChatRoomItemAdapter 가 내부 리스트를 직접 참조하는 구조이므로
            // clear + addAll 후 notifyDataSetChanged 호출
            adapter.updateRooms(rooms != null ? rooms : new ArrayList<>());
        });

        viewModel.isLoading.observe(getViewLifecycleOwner(), loading -> {
            if (state == null) return;
            if (loading) state.showLoading();
        });

        viewModel.isEmpty.observe(getViewLifecycleOwner(), empty -> {
            if (state == null) return;
            if (empty) state.showEmpty();
            else       state.showContent();
        });

        // 최초 리스너 시작
        viewModel.startListening(currentUid);
    }

    @Override
    public void onDestroyView() {
        // Fragment 뷰가 파괴될 때 리스너 해제
        if (viewModel != null) viewModel.stopListening();
        super.onDestroyView();
    }

    /**
     * HomeActivity 가 show/hide 방식으로 탭을 전환하므로
     * 탭이 숨겨질 때 리스너 해제, 다시 보일 때 재등록.
     *
     * [변경 전] Fragment 에서 직접 roomsReg.remove() / listenChatRooms() 호출
     * [변경 후] viewModel.stopListening() / startListening() 으로 위임
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!isAdded() || viewModel == null) return;
        if (hidden) {
            viewModel.stopListening();
        } else {
            viewModel.startListening(currentUid);
        }
    }

    // ── 클릭 ─────────────────────────────────────────────────────────────────────

    private void onChatRoomClick(ChatRoomItem item) {
        Intent intent = new Intent(requireContext(), ChatRoomActivity.class);
        intent.putExtra("roomId", item.getRoomId());
        startActivity(intent);
    }
}