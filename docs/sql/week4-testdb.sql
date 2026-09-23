-- 27~29페이지: 서버 실행으로 testdb 테이블이 생성된 뒤 실행한다.
-- 같은 이름의 실습 데이터가 있으면 추가하지 않는다.
USE spring;
SET NAMES utf8mb4;

INSERT INTO testdb (name)
SELECT '홍길동'
WHERE NOT EXISTS (SELECT 1 FROM testdb WHERE name = '홍길동');

INSERT INTO testdb (name)
SELECT '아저씨'
WHERE NOT EXISTS (SELECT 1 FROM testdb WHERE name = '아저씨');

SELECT id, name FROM testdb ORDER BY id;
