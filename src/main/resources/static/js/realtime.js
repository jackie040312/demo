// [12주차 제공] 실시간 알림 : STOMP 로 서버에 연결하고, 메시지가 오면 화면을 고친다
// - 남은 자리 : /topic/seats 를 구독 → data-seat-post 가 같은 곳의 숫자를 바꾼다 (누구나)
// - 내 알림   : /user/queue/notifications 를 구독 → 오른쪽 아래 알림 + 종 배지 (로그인한 사람만)
(function () {
    const status = document.getElementById('rt-status');   // 연결 상태 표시
    const bell = document.getElementById('noti-bell');      // 종 (로그인했을 때만 있다)
    const count = document.getElementById('noti-count');
    const log = (text) => console.log('[실시간] ' + text);

    function setStatus(text, on) {
        if (!status) return;
        status.textContent = text;
        status.classList.toggle('on', on);
    }

    // ① 남은 자리 메시지 : { postId, appliedCount, capacity, remaining }
    function updateSeats(m) {
        document.querySelectorAll('[data-seat-post="' + m.postId + '"]').forEach(function (box) {
            const full = m.remaining <= 0;
            const targets = box.hasAttribute('data-field') ? [box] : box.querySelectorAll('[data-field]');
            targets.forEach(function (el) {
                switch (el.dataset.field) {
                    case 'applied': el.textContent = m.appliedCount; break;
                    case 'remaining': el.textContent = m.remaining; break;
                    case 'bar': el.style.width = (m.appliedCount * 100 / m.capacity) + '%'; break;
                    case 'enroll-button': el.disabled = full; break;
                    case 'badge': el.textContent = full ? '마감' : '모집 중'; el.classList.toggle('closed', full); break;
                    case 'badge-list': el.textContent = full ? '마감' : '모집 ' + m.appliedCount + '/' + m.capacity; el.classList.toggle('closed', full); break;
                }
            });
            box.classList.remove('seat-flash');
            void box.offsetWidth; // 애니메이션 다시 시작
            box.classList.add('seat-flash');
        });
    }

    // ② 내 알림 : { to, text, postId }
    function showNotification(n) {
        let area = document.getElementById('toast-area');
        if (!area) {
            area = document.createElement('div');
            area.id = 'toast-area';
            area.className = 'toast-area';
            document.body.appendChild(area);
        }
        const t = document.createElement('a');
        t.className = 'rt-toast';
        t.href = '/board/' + n.postId;
        t.innerHTML = '<i class="bi-bell-fill me-2"></i>';
        t.appendChild(document.createTextNode(n.text)); // 글자로만 넣는다 (XSS 방지)
        area.appendChild(t);
        setTimeout(function () { t.remove(); }, 6000);
        if (count) {
            count.textContent = Number(count.textContent || 0) + 1;
            count.hidden = false;
        }
    }

    if (typeof StompJs === 'undefined') { setStatus('실시간 꺼짐', false); return; }
    const client = new StompJs.Client({
        brokerURL: (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws',
        reconnectDelay: 5000, // 끊기면 5초 뒤 다시 연결
    });
    client.onConnect = function () {
        setStatus('● 실시간 연결됨', true);
        log('연결됨');
        client.subscribe('/topic/seats', function (frame) {
            const m = JSON.parse(frame.body);
            log('남은 자리 ' + JSON.stringify(m));
            updateSeats(m);
        });
        if (bell) {
            client.subscribe('/user/queue/notifications', function (frame) {
                const n = JSON.parse(frame.body);
                log('알림 ' + n.text);
                showNotification(n);
            });
        }
    };
    client.onWebSocketClose = function () { setStatus('○ 실시간 연결 안 됨', false); };
    client.onStompError = function (frame) { log('오류 ' + frame.headers['message']); };
    setStatus('○ 실시간 연결 중…', false);
    client.activate();
    window.realtime = { client: client }; // 개발자 도구에서 확인용
})();
