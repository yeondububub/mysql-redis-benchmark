# MySQL vs Redis 성능 비교 벤치마크 시스템

이 프로젝트는 디바이스/네트워크 환경, 영속성 추상화 계층(JPA vs JDBC), 그리고 자료구조 차이에 따른 **MySQL**과 **Redis**의 성능 차이를 측정하고 시각화하는 벤치마크 대시보드 애플리케이션입니다.

---

## 🚀 주요 기능 및 테스트 시나리오

애플리케이션은 크게 **두 가지 벤치마크 모드**를 지원합니다.

### 1. 기본 읽기/쓰기 성능 분석 (JPA vs JDBC vs Redis)
다양한 라이브러리 및 통신 구조 하에서 CRUD 속도를 비교합니다.
*   **MySQL (JPA)**: Spring Data JPA의 기본 `save()`, `saveAll()`을 수행하며 발생하는 지연을 측정합니다. (Hibernate 영속성 관리 오버헤드 확인 가능)
*   **MySQL (JDBC)**: `JdbcTemplate` 기반의 로우 쿼리와 벌크 연산(`batchUpdate`)을 수행합니다. (ORM 오버헤드가 배제된 성능 측정)
*   **Redis (Standard)**: `StringRedisTemplate`을 이용한 단건 및 배치(`multiSet`/`multiGet`) 데이터 연산을 측정합니다.
*   **Redis (Pipeline)**: Pipelining 기법을 사용하여 네트워크 RTT(왕복 시간)를 최소화했을 때의 비약적인 속도 성능 향상을 직접 비교합니다.

### 2. 대용량 랭킹 조회 성능 분석 (Deep Offset Pagination)
대량의 랭킹 데이터 탐색 시 발생하는 인덱스 구조적 한계를 테스트합니다.
*   **MySQL (B-Tree Index)**: `ORDER BY score DESC LIMIT :limit OFFSET :offset` 쿼리를 수행합니다. 오프셋이 깊어질수록(예: 90만 위) 버려야 할 앞선 행들을 차례대로 모두 스캔해야 하므로 성능이 지수형으로 저하됩니다.
*   **Redis ZSET (Skip List)**: `ZREVRANGE` 명령어를 수행합니다. 내부적으로 Skip List를 타고 노드 사이를 성기게 점프하며 오프셋 인덱스를 찾으므로 오프셋의 깊이와 상관없이 수 밀리초(ms) 이하의 일정한 응답성을 유지합니다.
*   **결과 정밀 매칭**: 양측 DB에서 추출한 랭킹 결과를 나란히 배치하여 정합성이 일치하는지 확인할 수 있습니다.

---

## 🛠️ 기술 스택

### Backend
*   **Language**: Kotlin 1.9
*   **Framework**: Spring Boot 3.5.14
*   **Database**: MySQL 8.0, Redis (Alpine)
*   **Access Layer**: Spring Data JPA (Hibernate), NamedParameterJdbcTemplate, Spring Data Redis

### Frontend
*   **Framework**: Vue 3 (Composition API)
*   **Build Tool**: Vite 5
*   **Charting**: Chart.js
*   **Design**: Vanilla CSS (유리모피즘 테마 및 다크 테마 적용)

---

## ⚙️ 실행 및 구축 환경 설정

### 1. 전제 조건
- Java 21 및 Node.js 18 이상이 로컬에 설치되어 있어야 합니다.
- Docker가 실행 중이어야 합니다.

### 2. 데이터베이스 구동 (Docker)
도커 컴포즈 파일이 프로젝트 루트에 준비되어 있습니다. 다음 명령어로 데이터베이스 컨테이너들을 시작합니다:
```bash
docker-compose up -d
```
*   MySQL 포트: `3306` (ID/PW: `benchmark_user` / `benchmark_pass`, Database: `benchmark_db`)
*   Redis 포트: `6379`

### 3. 애플리케이션 실행

#### 옵션 A: 백엔드 + 프론트엔드 원터치 가동 (추천)
프론트엔드를 빌드하여 스프링 부트 정적 리소스에 정렬시킨 후, 스프링 서버 하나로 전체 서비스를 호스팅합니다.
```bash
# 1. 프론트엔드 빌드 (결과물이 백엔드 static 디렉토리로 자동 삽입됨)
npm run build --prefix frontend

# 2. 스프링 부트 실행
./gradlew bootRun
```
*   웹 페이지 주소: `http://localhost:8080`

#### 옵션 B: 로컬 개발자 모드 (핫 리로드 지원)
UI 컴포넌트 실시간 개발을 위해 Vite dev 서버와 Spring Boot 백엔드를 각각 기동합니다. (Vite는 API 호출을 8080포트로 자동 프록시 처리합니다.)
```bash
# 터미널 1 (백엔드)
./gradlew bootRun

# 터미널 2 (프론트엔드)
npm run dev --prefix frontend
```
*   웹 페이지 주소: `http://localhost:5173`

---

## 📊 성능 비교 요약 보고서 (인사이트)

*   **ORM 추상화의 대가**: 대용량 배치 쓰기 시, MySQL JPA(`saveAll`)는 Hibernate 영속성 컨텍스트 관리 및 오버헤드로 인해 `JDBC batchUpdate` 대비 수 배 이상 지연이 깁니다.
*   **네트워크 왕복의 대가**: Redis도 단건 루프로 수만 번 전송하면 네트워크 지연 누적으로 속도가 매우 저하됩니다. 이때 Pipelining을 사용하면 처리량이 **10배 이상** 비약적으로 증가합니다.
*   **인덱스 검색과 정렬 오프셋**: 100만 건 데이터 기준 오프셋이 90만인 구간을 조회할 때, MySQL은 페이징 정렬을 위해 수백 ms의 지연이 발생하는 반면, Redis Sorted Set은 Skip List 장점으로 인해 **약 300배 이상** 빠른 성능 격차를 유발합니다.
