# 자바웹프로그래밍 2주차

> 2026년 9월 10일
>
> 20231145 신동우

## 이번 주에 배운 내용

이번 수업에서는 VS Code로 Spring Boot 개발 환경을 설정하고, Controller와 Thymeleaf를 사용해 웹페이지를 연결해 봤다.

- Java 25, Spring Boot 4.1.1, Maven 프로젝트 생성
- Java와 Spring Boot용 VS Code 확장 프로그램 설치
- Spring Boot 내장 Tomcat을 8080 포트로 실행
- MVC 구조와 DispatcherServlet의 역할 확인
- Controller의 데이터를 Thymeleaf 화면에 출력
- GitHub에 실습 코드 업로드

## 프로젝트 구조와 실행

주요 파일은 다음과 같다.

```text
src/main
├── java/com/example/demo
│   ├── DemoApplication.java
│   └── DemoController.java
└── resources/templates
    ├── index.html
    ├── hello.html
    └── hello2.html
```

[DemoApplication.java](src/main/java/com/example/demo/DemoApplication.java)가 프로그램의 시작점이다. VS Code에서 파일을 열고 `Ctrl + F5`를 누르면 실행할 수 있다.

```powershell
.\mvnw.cmd spring-boot:run
```

- 메인 페이지: `http://localhost:8080/`
- 첫 번째 페이지: `http://localhost:8080/hello`
- 두 번째 페이지: `http://localhost:8080/hello2`

## MVC와 Thymeleaf

브라우저에서 URL을 요청하면 `DispatcherServlet`이 알맞은 Controller를 찾는다. Controller는 Model에 데이터를 담고 화면 이름을 반환하며, Thymeleaf가 그 데이터를 HTML에 넣어 보여 준다.

```java
@GetMapping("/hello")
public String hello(Model model) {
    model.addAttribute("data", "반갑습니다.");
    return "hello";
}
```

```html
<p th:text="${data}"></p>
```

처음에는 `/hello.html`로 직접 연결했지만 404 오류가 났다. `templates` 안의 HTML은 Controller를 통해 연결해야 하고, Model의 속성 이름과 `${...}`의 변수 이름도 같아야 한다는 것을 알게 됐다.

## 2주차 과제

수업 후에는 두 번째 Hello 페이지를 추가했다.

- [hello.html](src/main/resources/templates/hello.html)에 `/hello2` 링크 추가
- [DemoController.java](src/main/java/com/example/demo/DemoController.java)에 `hello2()` 매핑 추가
- 이름, 인사말, 오늘, 날씨, 상태 총 5개 속성을 Model에 저장
- [hello2.html](src/main/resources/templates/hello2.html)에서 5개 값 출력

### 최종 실행 화면

![두 번째 Hello 페이지 실행 결과](docs/images/hello2-result.png)

## 느낀 점

직접 URL 매핑을 해 보면서 URL, Controller의 반환값, HTML 파일 이름이 서로 연결된다는 점을 이해했다. Model과 Thymeleaf로 데이터를 전달하는 과정도 실습해 보니 MVC 구조가 전보다 조금 더 익숙해졌다.
