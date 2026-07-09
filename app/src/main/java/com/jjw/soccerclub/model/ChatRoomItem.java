package com.jjw.soccerclub.model;

import java.util.List;

public class ChatRoomItem {

    private String roomId;
    private List<String> participants;
    private String lastMessage;
    private long lastTimestamp;
    private int unreadCount;
    private String peerNickname;
    private String peerProfileImage;

    public ChatRoomItem() {}

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }

    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public long getLastTimestamp() { return lastTimestamp; }
    public void setLastTimestamp(long lastTimestamp) { this.lastTimestamp = lastTimestamp; }

    public int getUnreadCount() { return unreadCount; }
    public void setUnreadCount(int unreadCount) { this.unreadCount = unreadCount; }

    public String getPeerNickname() { return peerNickname; }
    public void setPeerNickname(String peerNickname) { this.peerNickname = peerNickname; }

    public String getPeerProfileImage() { return peerProfileImage; }
    public void setPeerProfileImage(String peerProfileImage) { this.peerProfileImage = peerProfileImage; }
}