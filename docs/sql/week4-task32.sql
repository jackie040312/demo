-- 32페이지 과제: 서버 재시작으로 age, gender 컬럼이 생성된 뒤 실행한다.
-- VS Code MySQL 확장에서 spring DB를 선택하고 실행해도 된다.
USE spring;
SET NAMES utf8mb4;

-- 이전 실습 데이터는 삭제하지 않고 비어 있는 정보만 채운다.
UPDATE testdb SET age = COALESCE(age, 25), gender = COALESCE(gender, '남')
WHERE name = '홍길동';
UPDATE testdb SET age = COALESCE(age, 40), gender = COALESCE(gender, '남')
WHERE name = '아저씨';

-- 이름이 이미 있으면 중복으로 추가하지 않는다.
INSERT INTO testdb (name, age, gender)
SELECT '홍길동', 25, '남'
WHERE NOT EXISTS (SELECT 1 FROM testdb WHERE name = '홍길동');
INSERT INTO testdb (name, age, gender)
SELECT '아저씨', 40, '남'
WHERE NOT EXISTS (SELECT 1 FROM testdb WHERE name = '아저씨');
INSERT INTO testdb (name, age, gender)
SELECT '김영희', 30, '여'
WHERE NOT EXISTS (SELECT 1 FROM testdb WHERE name = '김영희');
INSERT INTO testdb (name, age, gender)
SELECT '이철수', 22, '남'
WHERE NOT EXISTS (SELECT 1 FROM testdb WHERE name = '이철수');

SELECT id, name, age, gender FROM testdb ORDER BY id;
