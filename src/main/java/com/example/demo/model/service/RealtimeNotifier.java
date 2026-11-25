package com.example.demo.model.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.demo.model.dto.Notification;
import com.example.demo.model.dto.SeatMessage;

@Component
public class RealtimeNotifier {
    private final SimpMessagingTemplate messagingTemplate;

    public RealtimeNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener // 기본 AFTER_COMMIT: 롤백 시 전송하지 않는다.
    public void sendSeats(SeatMessage message) {
        messagingTemplate.convertAndSend("/topic/seats", message);
    }

    @TransactionalEventListener
    public void sendNotification(Notification notification) {
        messagingTemplate.convertAndSendToUser(notification.to(), "/queue/notifications", notification);
    }
}
