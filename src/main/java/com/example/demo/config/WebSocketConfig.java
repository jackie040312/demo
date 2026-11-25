package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration // [12주차 제공] WebSocket + STOMP 설정
@EnableWebSocketMessageBroker // STOMP 메시지 중개(브로커) 기능을 켠다
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override // ① 연결 주소 : ws://localhost:8080/ws
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws"); // 허용 출처를 따로 정하지 않으면 "같은 사이트"에서만 연결된다 (setAllowedOrigins("*") 금지)
    }

    @Override // ② 주소 규칙
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");    // 서버 → 브라우저 : /topic = 여러 명, /queue = 한 사람
        registry.setApplicationDestinationPrefixes("/app"); // 브라우저 → 서버 (@MessageMapping, 오늘은 쓰지 않음)
        registry.setUserDestinationPrefix("/user");         // 특정 사용자에게 : /user/queue/...
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (headers == null) return message;
                // 이 화면은 서버 알림만 받는다. 클라이언트의 알림 위조 전송은 허용하지 않는다.
                if (StompCommand.SEND.equals(headers.getCommand())) {
                    throw new AccessDeniedException("서버 알림은 직접 전송할 수 없습니다.");
                }
                if (StompCommand.SUBSCRIBE.equals(headers.getCommand())) {
                    if ("/topic/seats".equals(headers.getDestination())) return message;
                    boolean loggedIn = headers.getUser() instanceof Authentication authentication
                        && authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken);
                    if (loggedIn && "/user/queue/notifications".equals(headers.getDestination())) return message;
                    throw new AccessDeniedException("구독할 수 없는 알림 주소입니다.");
                }
                return message;
            }
        });
    }
}
