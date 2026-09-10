# Java Web 2주차 수업 정리

> 수업일: 2026년 9월 9일
>
> 학번·이름: 20231145 신동우

## 1. 오늘의 학습 내용

이번 수업에서는 Spring Boot 프로젝트의 기본 구조를 살펴보고, Spring MVC와 Thymeleaf를 이용해 컨트롤러에서 전달한 데이터를 웹 화면에 출력하는 방법을 학습했다.

### Spring Boot 애플리케이션 실행

- `@SpringBootApplication`은 설정, 자동 구성, 컴포넌트 탐색 기능을 포함한다.
- `SpringApplication.run()`을 호출하면 내장 웹 서버와 Spring 애플리케이션이 실행된다.
- 프로젝트의 시작 클래스는 `DemoApplication`이다.

### Spring MVC 컨트롤러

- `@Controller`를 사용해 웹 요청을 처리하는 클래스를 등록한다.
- `@GetMapping("/hello")`은 `/hello` 주소로 들어온 GET 요청을 `hello()` 메서드에 연결한다.
- 메서드가 반환하는 문자열 `"hello"`는 화면 파일인 `hello.html`을 의미한다.

```java
@GetMapping("/hello")
public String hello(Model model) {
    model.addAttribute("data", "반갑습니다.");
    return "hello";
}
```

### Model을 이용한 데이터 전달

- `Model`은 컨트롤러에서 화면으로 데이터를 전달할 때 사용한다.
- `model.addAttribute("data", "반갑습니다.")`는 `data`라는 이름으로 문자열 값을 저장한다.
- Thymeleaf 템플릿에서는 `${data}`로 해당 값을 사용할 수 있다.

### Thymeleaf 템플릿

- Thymeleaf는 서버에서 HTML을 생성하는 템플릿 엔진이다.
- 화면 파일은 `src/main/resources/templates`에 작성한다.
- `th:text="${data}"`는 컨트롤러가 전달한 값을 HTML 요소의 내용으로 출력한다.

```html
<p th:text="${data}"></p>
```

## 2. 요청 처리 흐름

1. 사용자가 브라우저에서 `/hello`에 접속한다.
2. `DemoController`의 `hello()` 메서드가 요청을 처리한다.
3. 컨트롤러가 `Model`에 `data` 값을 담는다.
4. 컨트롤러가 `hello` 뷰 이름을 반환한다.
5. Thymeleaf가 `templates/hello.html`을 렌더링한다.
6. 브라우저 화면에 전달된 데이터가 표시된다.

## 3. 프로젝트 주요 구조

```text
src/main
├── java/com/example/demo
│   ├── DemoApplication.java   # 애플리케이션 시작 클래스
│   └── DemoController.java    # 웹 요청 처리
└── resources
    ├── application.properties # 애플리케이션 설정
    └── templates
        ├── index.html          # 메인 화면
        └── hello.html          # 데이터 출력 화면
```

## 4. 실행 방법

Windows에서는 다음 명령으로 애플리케이션을 실행한다.

```powershell
.\mvnw.cmd spring-boot:run
```

실행 후 브라우저에서 `http://localhost:8080/hello`에 접속해 결과를 확인한다.

## 5. 핵심 정리

- Spring MVC는 요청을 컨트롤러 메서드와 연결한다.
- 컨트롤러는 `Model`을 통해 화면에 데이터를 전달한다.
- Thymeleaf는 전달받은 데이터를 HTML에 표현한다.
- 컨트롤러의 반환값과 `templates` 폴더의 HTML 파일 이름이 연결된다.

## 6. 수업 후 과제

두 번째 Hello 페이지를 추가해 컨트롤러에서 여러 데이터를 전달하는 과제를 수행했다.

- 기존 `hello.html`에 `/hello2`로 이동하는 링크 추가
- `DemoController`에 `/hello2` URL 매핑 추가
- `Model`에 이름, 인사말, 날짜 표현, 날씨 표현, 상태 등 5개 속성 추가
- `hello2.html`에서 5개 속성을 Thymeleaf로 출력

애플리케이션 실행 후 `http://localhost:8080/hello`에서 링크를 누르거나, `http://localhost:8080/hello2`에 직접 접속해 결과를 확인할 수 있다.
