# xtrmETL 프로젝트 분석 요약

## 프로젝트 목적 역추적 결과

### 프로젝트 개요

**xtrmETL**은 실시간 Change Data Capture(CDC)와 Extract-Transform-Load(ETL) 기능을 제공하는 마이크로서비스 기반 엔터프라이즈 데이터 통합 플랫폼입니다.

### 핵심 목표

이 프로그램은 다음과 같은 문제를 해결하기 위해 개발되었습니다:

1. **실시간 데이터베이스 변경 캡처**: PostgreSQL 데이터베이스의 변경사항(INSERT, UPDATE, DELETE)을 실시간으로 감지
2. **데이터 변환 및 로딩**: JSON 형식의 데이터를 받아 비즈니스 규칙에 따라 변환하고 저장
3. **시스템 간 데이터 동기화**: 서로 다른 시스템 간의 데이터를 실시간으로 동기화
4. **확장 가능한 아키텍처**: 마이크로서비스 패턴을 통한 독립적인 확장 및 배포

## 주요 기능

### 1. CDC (Change Data Capture) 서비스

- **목적**: 데이터베이스 변경사항을 실시간으로 캡처
- **기술**: Debezium Embedded Engine 사용
- **작동 방식**:
  - PostgreSQL의 Write-Ahead Log(WAL)를 모니터링
  - 변경사항을 Kafka 토픽으로 발행
  - 원본 데이터베이스 성능에 최소한의 영향

### 2. ETL (Extract-Transform-Load) 서비스

- **목적**: 데이터 추출, 변환, 로딩 파이프라인 제공
- **기능**:
  - JSON 데이터 수신 및 파싱
  - 병렬 처리를 통한 높은 처리량
  - 비즈니스 규칙 적용 (이름 대문자화, 이메일 소문자화, 금액 포맷팅 등)
  - 변환된 데이터를 PostgreSQL에 저장
  - 실패 시 자동 재시도 (3회, 1초 지연)

### 3. 보안 및 인증

- **JWT 기반 인증**: 토큰 기반 보안 시스템
- **역할 기반 접근 제어**: USER, ADMIN 역할 지원
- **비밀번호 암호화**: BCrypt 해싱 사용

## 시스템 아키텍처

### 마이크로서비스 구성

```text
외부 클라이언트
     ↓
Zuul API Gateway (8080) ← 인증 및 라우팅
     ↓
┌────────────────┬────────────────┐
↓                ↓                ↓
ETL Service    CDC Service    기타 서비스
(8000)         (8001)
↓                ↓
PostgreSQL     Kafka
(대상 DB)      (이벤트 스트림)
```

### 인프라 서비스

- **Eureka Server (8761)**: 서비스 디스커버리
- **Config Server (8888)**: 중앙 집중식 설정 관리
- **Zipkin (9412)**: 분산 추적 및 모니터링

## 기술 스택

| 구성요소 | 기술 | 버전 |
| --------- | ----- | ------ |
| 런타임 | Java | 17 |
| 프레임워크 | Spring Boot | 2.7.14 |
| 클라우드 | Spring Cloud | 2021.0.8 |
| CDC 엔진 | Debezium | 2.3.x - 2.5.x |
| 데이터베이스 | PostgreSQL | 12+ |
| 메시징 | Apache Kafka | - |
| 게이트웨이 | Netflix Zuul | - |
| 서비스 디스커버리 | Netflix Eureka | - |
| 추적 | Zipkin | - |
| 빌드 도구 | Maven | - |

## 사용 사례

### 사례 1: 실시간 데이터 동기화

1. 원본 애플리케이션이 PostgreSQL 데이터를 수정
2. CDC 서비스가 변경사항 감지
3. 변경 이벤트를 Kafka로 발행
4. 다운스트림 소비자가 이벤트 처리
5. 대상 시스템이 1초 이내에 업데이트

### 사례 2: 배치 데이터 변환

1. 외부 시스템이 JWT로 인증
2. JSON 배열을 `/api/etl/process`로 전송
3. ETL 서비스가 데이터 검증 및 파싱
4. 병렬로 변환 규칙 적용
5. 변환된 데이터를 데이터베이스에 로딩
6. 처리 결과 반환

## API 명세

### 인증 API

- **POST /auth/signup**: 사용자 등록
- **POST /auth/signin**: 로그인 (JWT 토큰 발급)

### CDC API

- **POST /api/cdc/start**: CDC 프로세스 시작
- **POST /api/cdc/stop**: CDC 프로세스 중지

### ETL API

- **POST /api/etl/process**: 데이터 처리 (JSON 배열)

## 시작하기

### 필수 요구사항

- Java 17 이상
- Maven 3.6+
- PostgreSQL 12+ (논리 복제 활성화)
- Apache Kafka (CDC 기능 사용 시)

### PostgreSQL 설정

```bash
# postgresql.conf에서
wal_level = logical
max_replication_slots = 4
max_wal_senders = 4
```

### 환경 변수 설정

```bash
export PGHOST=localhost
export PGPORT=5432
export PGUSER=your_username
export PGPASSWORD=your_password
export PGDATABASE=your_database
```

### 빌드 및 실행

```bash
# 전체 빌드
mvn clean install

# 서비스별 실행
cd eureka-server && mvn spring-boot:run
cd cdc-service && mvn spring-boot:run
cd etl-service && mvn spring-boot:run
cd zuul-gateway && mvn spring-boot:run
```

## 문서 구조

생성된 문서는 다음과 같습니다:

1. **README.md**: 프로젝트 개요 및 빠른 시작 가이드
2. **PRD.md**: 상세한 제품 요구사항 문서
   - 비즈니스 문제 정의
   - 기능 요구사항
   - 비기능 요구사항
   - 데이터 모델
   - API 명세
   - 배포 아키텍처
   - 성공 지표

3. **ARCHITECTURE.md**: 시스템 아키텍처 문서
   - 고수준 아키텍처
   - 서비스 통신 패턴
   - 데이터 플로우 다이어그램
   - 보안 아키텍처
   - 모니터링 및 관찰성
   - 배포 아키텍처
   - 확장성 고려사항

4. **이 문서 (SUMMARY_KR.md)**: 한국어 요약본

## 향후 개선 사항 (v2.0)

- 다중 데이터베이스 지원 (MySQL, Oracle, SQL Server)
- 사용자 정의 변환 함수
- 데이터 품질 검증 규칙
- 웹 UI (설정 및 모니터링 대시보드)
- 스키마 레지스트리
- Dead Letter Queue (실패 메시지 처리)
- 실시간 메트릭 대시보드

## 기술 부채

현재 코드베이스에서 발견된 기술 부채:

- **Common 모듈**: 문서에 언급되었으나 미구현
- **MyBatis 통합**: 의존성은 있으나 사용되지 않음
- **Redis 통합**: 의존성은 있으나 활용되지 않음
- **Config Server**: 구현되었으나 활발히 사용되지 않음
- **Health Check**: Spring Boot Actuator 엔드포인트 추가 필요

## 결론

xtrmETL은 **실시간 데이터베이스 변경 캡처(CDC)**와 **데이터 변환(ETL)** 기능을 제공하는 마이크로서비스 플랫폼입니다.

**핵심 가치**:

- 실시간 데이터 동기화
- 확장 가능한 마이크로서비스 아키텍처  
- 보안이 강화된 API 접근
- 분산 시스템 모니터링 및 추적
- 높은 처리량과 병렬 처리

이 플랫폼은 서로 다른 시스템 간의 데이터를 실시간으로 동기화하고, 데이터 변환 파이프라인을 구축하며, 분산 아키텍처에서 데이터 일관성을 유지하는 데 사용됩니다.

---

**문서 버전**: 1.0  
**최종 업데이트**: 2026-01-08  
**작성자**: 제품 엔지니어링 팀
