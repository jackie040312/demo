package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.demo.model.domain.Member;
import com.example.demo.model.domain.Post;
import com.example.demo.model.dto.Notification;
import com.example.demo.model.dto.SeatMessage;
import com.example.demo.model.repository.EnrollmentRepository;
import com.example.demo.model.repository.MemberRepository;
import com.example.demo.model.repository.PostRepository;
import com.example.demo.model.service.CommentService;
import com.example.demo.model.service.EnrollmentService;

/** 실제 DB 커밋과 실제 WebSocket 연결을 사용한다. 테스트용 회원·글만 정리한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.jpa.show-sql=false")
class Week12RealtimeTests {
    @LocalServerPort int port;
    @Autowired MemberRepository members;
    @Autowired PostRepository posts;
    @Autowired EnrollmentRepository enrollments;
    @Autowired EnrollmentService enrollmentService;
    @Autowired CommentService commentService;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApplicationEventPublisher events;
    @Autowired SimpMessagingTemplate messaging;
    @Autowired @Qualifier("brokerChannel") SubscribableChannel brokerChannel;
    private final List<Message<?>> sent = new CopyOnWriteArrayList<>();
    private final MessageHandler recorder = sent::add;
    private final List<StompConnection> connections = new ArrayList<>();
    private Member writer;
    private Member applicant;
    private Member other;
    private Post open;
    private Post oneSeat;
    private TransactionTemplate transaction;

    @BeforeEach
    void fixture() {
        transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            writer = member("글쓴이");
            applicant = member("신청자");
            other = member("다른 사람");
            open = post("실시간 모집 A", 5);
            oneSeat = post("실시간 모집 C", 1);
        });
        brokerChannel.subscribe(recorder);
    }

    private Member member(String name) {
        Member member = new Member();
        member.setUsername("w12" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        member.setPassword(encoder.encode("test-password"));
        member.setName(name);
        member.setRole("USER");
        return members.saveAndFlush(member);
    }

    private Post post(String title, int capacity) {
        Post post = new Post();
        post.setTitle(title);
        post.setContent("12주차 실시간 검증용");
        post.setAuthor(writer);
        post.setCapacity(capacity);
        post.setCreatedAt(LocalDateTime.now());
        return posts.saveAndFlush(post);
    }

    @AfterEach
    void cleanup() {
        connections.forEach(connection -> connection.socket.abort());
        brokerChannel.unsubscribe(recorder);
        transaction.executeWithoutResult(status -> {
            for (Member member : List.of(writer, applicant, other)) {
                enrollments.deleteAll(enrollments.findByMember_UsernameOrderByIdDesc(member.getUsername()));
            }
            enrollments.flush();
            posts.deleteAllById(List.of(open.getId(), oneSeat.getId()));
            posts.flush();
            members.deleteAllById(List.of(writer.getId(), applicant.getId(), other.getId()));
        });
    }

    private List<String> payloads(String destination) {
        return sent.stream()
            .filter(message -> destination.equals(SimpMessageHeaderAccessor.getDestination(message.getHeaders())))
            .map(message -> new String((byte[]) message.getPayload(), StandardCharsets.UTF_8)).toList();
    }

    private String writerQueue() {
        return "/user/" + writer.getUsername() + "/queue/notifications";
    }

    @Test
    void publishesOnlyAfterCommitAndNeverForRolledBackCart() {
        transaction.executeWithoutResult(status -> {
            enrollmentService.confirm(applicant.getUsername(), List.of(open.getId()));
            assertThat(payloads("/topic/seats")).isEmpty();
            assertThat(payloads(writerQueue())).isEmpty();
        });
        assertThat(payloads("/topic/seats")).singleElement().asString().contains("\"remaining\":4");
        assertThat(payloads(writerQueue())).singleElement().asString().contains("신청했습니다.");
        enrollmentService.confirm(other.getUsername(), List.of(oneSeat.getId()));
        sent.clear();

        assertThatThrownBy(() -> enrollmentService.confirm(writer.getUsername(), List.of(open.getId(), oneSeat.getId())))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("정원이 마감되었습니다");
        assertThat(posts.findById(open.getId()).orElseThrow().getAppliedCount()).isEqualTo(1);
        assertThat(payloads("/topic/seats")).isEmpty();
        assertThat(payloads(writerQueue())).isEmpty();
    }

    @Test
    void cancellationReopensSeatAndNotifiesOnlyAfterCommit() {
        enrollmentService.confirm(applicant.getUsername(), List.of(oneSeat.getId()));
        Long id = enrollments.findByMember_UsernameOrderByIdDesc(applicant.getUsername()).getFirst().getId();
        sent.clear();
        transaction.executeWithoutResult(status -> {
            enrollmentService.cancel(id, applicant.getUsername());
            assertThat(payloads("/topic/seats")).isEmpty();
            status.setRollbackOnly();
        });
        assertThat(payloads(writerQueue())).isEmpty();
        assertThat(payloads("/topic/seats")).isEmpty();
        assertThat(posts.findById(oneSeat.getId()).orElseThrow().isFull()).isTrue();

        enrollmentService.cancel(id, applicant.getUsername());
        assertThat(payloads("/topic/seats")).singleElement().asString()
            .contains("\"remaining\":1", "\"appliedCount\":0");
        assertThat(payloads(writerQueue())).singleElement().asString().contains("신청을 취소했습니다.");
        assertThat(posts.findById(oneSeat.getId()).orElseThrow().isFull()).isFalse();
    }

    @Test
    void selfEnrollmentCancellationAndCommentsDoNotNotifyWriter() {
        enrollmentService.confirm(writer.getUsername(), List.of(open.getId()));
        Long id = enrollments.findByMember_UsernameOrderByIdDesc(writer.getUsername()).getFirst().getId();
        enrollmentService.cancel(id, writer.getUsername());
        commentService.write(open.getId(), "내 글의 댓글", writer.getUsername());
        assertThat(payloads(writerQueue())).isEmpty();
        assertThat(payloads("/topic/seats")).hasSize(2);
    }

    @Test
    void commentsNeedSuccessfulTransactionAndNotifyPostWriter() {
        transaction.executeWithoutResult(status -> {
            commentService.write(open.getId(), "롤백 댓글", applicant.getUsername());
            assertThat(payloads(writerQueue())).isEmpty();
            status.setRollbackOnly();
        });
        events.publishEvent(new Notification(writer.getUsername(), "트랜잭션 없는 이벤트", open.getId()));
        assertThat(payloads(writerQueue())).isEmpty();
        commentService.write(open.getId(), "저장된 댓글", applicant.getUsername());
        assertThat(payloads(writerQueue())).singleElement().asString().contains("신청자", "댓글을 남겼습니다.");
        assertThat(payloads("/topic/seats")).isEmpty();
    }

    @Test
    void realWebSocketsBroadcastSeatsButKeepNotificationsWithWriterSessions() throws Exception {
        StompConnection anonymous = connect(null, origin());
        StompConnection authorTab = connect(writer, origin());
        StompConnection secondAuthorTab = connect(writer, origin());
        StompConnection unrelated = connect(other, origin());
        ready(anonymous, null);
        ready(authorTab, writer);
        ready(secondAuthorTab, writer);
        ready(unrelated, other);

        enrollmentService.confirm(applicant.getUsername(), List.of(oneSeat.getId()));
        assertThat(anonymous.message()).contains("\"remaining\":0");
        assertThat(authorTab.message()).contains("신청했습니다.");
        assertThat(secondAuthorTab.message()).contains("신청했습니다.");
        assertThat(unrelated.message()).isNull();

        Long id = enrollments.findByMember_UsernameOrderByIdDesc(applicant.getUsername()).getFirst().getId();
        enrollmentService.cancel(id, applicant.getUsername());
        assertThat(anonymous.message()).contains("\"remaining\":1");
        assertThat(authorTab.message()).contains("신청을 취소했습니다.");
        assertThat(secondAuthorTab.message()).contains("신청을 취소했습니다.");
        commentService.write(open.getId(), "실시간 댓글", applicant.getUsername());
        assertThat(authorTab.message()).contains("댓글을 남겼습니다.");
        assertThat(secondAuthorTab.message()).contains("댓글을 남겼습니다.");
        assertThat(unrelated.message()).isNull();
    }

    @Test
    void rejectsForeignOriginsAnonymousPrivateSubscriptionsAndForgedNotifications() throws Exception {
        assertThatThrownBy(() -> connect(null, "http://evil.example.com"))
            .isInstanceOf(java.util.concurrent.ExecutionException.class)
            .cause().isInstanceOf(java.net.http.WebSocketHandshakeException.class);
        StompConnection anonymous = connect(null, origin());
        anonymous.send("SUBSCRIBE\nid:private\ndestination:/user/queue/notifications\n\n");
        assertThat(anonymous.frames.poll(5, TimeUnit.SECONDS)).startsWith("ERROR");
        StompConnection authenticated = connect(other, origin());
        authenticated.send("SUBSCRIBE\nid:steal\ndestination:/user/" + writer.getUsername() + "/queue/notifications\n\n");
        assertThat(authenticated.frames.poll(5, TimeUnit.SECONDS)).startsWith("ERROR");
        StompConnection sender = connect(other, origin());
        sender.send("SEND\ndestination:/topic/seats\n\n{}");
        assertThat(sender.frames.poll(5, TimeUnit.SECONDS)).startsWith("ERROR");
    }

    private String origin() { return "http://localhost:" + port; }

    private StompConnection connect(Member member, String origin) throws Exception {
        StompConnection connection = new StompConnection();
        WebSocket.Builder builder = HttpClient.newHttpClient().newWebSocketBuilder()
            .subprotocols("v12.stomp").header("Origin", origin);
        if (member != null) {
            builder.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (member.getUsername() + ":test-password").getBytes(StandardCharsets.UTF_8)));
        }
        connection.socket = builder.buildAsync(URI.create("ws://localhost:" + port + "/ws"), connection)
            .get(5, TimeUnit.SECONDS);
        connections.add(connection);
        connection.send("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n\n");
        assertThat(connection.frames.poll(5, TimeUnit.SECONDS)).startsWith("CONNECTED");
        return connection;
    }

    /** 연결 직후의 구독 처리가 끝날 때까지 같은 경로로 확인 메시지를 보낸다. */
    private void ready(StompConnection connection, Member member) throws Exception {
        String destination = member == null ? "/topic/seats" : "/user/queue/notifications";
        connection.send("SUBSCRIBE\nid:test\ndestination:" + destination + "\n\n");
        for (int attempt = 0; attempt < 30; attempt++) {
            if (member == null) messaging.convertAndSend("/topic/seats", new SeatMessage(-1L, 0, 1, 1));
            else messaging.convertAndSendToUser(member.getUsername(), "/queue/notifications",
                new Notification(member.getUsername(), "ready", -1L));
            if (connection.frames.poll(100, TimeUnit.MILLISECONDS) != null) return;
        }
        throw new AssertionError("실시간 구독 준비 실패");
    }

    private static class StompConnection implements WebSocket.Listener {
        private WebSocket socket;
        private final StringBuilder buffer = new StringBuilder();
        private final BlockingQueue<String> frames = new LinkedBlockingQueue<>();

        @Override public void onOpen(WebSocket webSocket) { webSocket.request(1); }

        @Override public CompletionStage<?> onText(WebSocket webSocket, CharSequence text, boolean last) {
            buffer.append(text);
            int end;
            while ((end = buffer.indexOf("\0")) >= 0) {
                frames.add(buffer.substring(0, end).stripLeading());
                buffer.delete(0, end + 1);
            }
            webSocket.request(1);
            return null;
        }

        void send(String frame) { socket.sendText(frame + "\0", true).join(); }

        String message() throws InterruptedException {
            String frame;
            do { frame = frames.poll(800, TimeUnit.MILLISECONDS); }
            while (frame != null && frame.contains("\"postId\":-1"));
            return frame;
        }
    }
}
