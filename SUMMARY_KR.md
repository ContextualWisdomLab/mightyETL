# mightyETL 한국어 제품·아키텍처 요약

**기준:** protected `develop@622e5e6c3d534f230c390f10e3832efadfc01825`  
**갱신:** 2026-08-09

mightyETL(구 xtrmETL)은 **제한된 원자적 ETL**, **내구성 있는 재시도/비동기 작업 상태**, **PostgreSQL CDC**를 제공하는 모듈러 데이터 이동 플랫폼입니다. 개별 ETL·CDC 서비스로도 실행할 수 있고 Gateway/Eureka/Config/관측성 계층과 조합한 MSA로도 사용할 수 있습니다.

## 상태 표기

문서에서 다음 상태를 명시적으로 구분합니다.

- `implemented_on_develop`: 현재 보호된 develop에 실제 존재
- `active_pr`: 열린 PR에만 존재, 아직 미배포
- `planned`: 이슈/설계만 승인됨
- `superseded`: 더 이상 현재 통합 경로가 아닌 과거 설계
- `out_of_scope`: 현재 범위에서 의도적으로 제외
- `known_gap`: 현재 구현의 알려진 제한

## 현재 제공 기능

### ETL — `implemented_on_develop`

- `POST /api/etl/process`
- UTF-8 바이트·레코드 수 상한
- 모든 레코드를 첫 JDBC write 전에 검증·변환
- 성공한 요청 전체를 하나의 Spring transaction으로 commit/rollback
- transient DB failure만 제한적으로 retry
- RFC 9457 problem detail
- 선택적인 인증 principal 범위 `Idempotency-Key`
- PostgreSQL try-lock + `etl_idempotency_records` durable replay ledger
- target write와 replay response ledger를 같은 transaction에서 commit

기존 문서의 “레코드별 병렬 처리 후 부분 성공” 설명은 `superseded`입니다.

### Durable Job Intake — `implemented_on_develop`, 기본 비활성

- `POST /api/etl/jobs`
- `GET /api/etl/jobs/{job_record_id}`
- 인증 principal 기반 owner isolation
- `202 Accepted`, `Location`, `Idempotency-Replayed`, `Cache-Control: no-store`
- PostgreSQL `etl_job_records`

현재 보호된 develop은 **intake/status only**입니다. 실제 lease-fenced worker(#143), pagination(#144), `Retry-After`(#145), ETag(#146), cancellation(#147), replay(#148)은 모두 `active_pr`입니다.

### CDC — `implemented_on_develop`

- PostgreSQL WAL을 Debezium Embedded Engine으로 캡처
- raw Debezium JSON을 Kafka로 publish
- `POST /api/cdc/start`, `POST /api/cdc/stop`
- `GET /api/cdc/status`, `/sources`, `/targets`
- replication-slot probe 및 operator-safe status

알려진 경계:

- Kafka broker acknowledgement를 source progress 전에 기다리는 수정은 `active_pr #139`
- `stop()`이 실제 Debezium async task 종료를 기다리는 수정은 `planned issue #141`
- 따라서 protected develop은 end-to-end exactly-once 또는 “stop 응답 = engine run 종료 완료”를 주장하지 않습니다.

## 인증·보안 현실

현재 protected develop의 `JwtAuthenticationFilter`는 이름과 달리 실제 JWT cryptographic validation이 아니라 literal example `valid_token`만 인식합니다. 따라서 **production JWT 인증이 구현됐다고 주장하면 안 됩니다.**

- `known_gap`: protected gateway placeholder
- `active_pr #142`: Spring Security reactive OAuth 2.0 Resource Server JWT + fail-closed deny mode
- `superseded`: 과거 `/auth/signup`, `/auth/signin`, local BCrypt/RBAC 제품 설계

Docker bootstrap에 `users`, `roles`, `user_roles` 테이블이 남아 있는 것은 persisted legacy compatibility state일 뿐, 해당 HTTP 인증 API가 구현됐다는 뜻이 아닙니다.

## 데이터 모델

현재 canonical ERD는 [docs/ERD.md](docs/ERD.md)입니다.

보호된 develop의 주요 객체:

- `processed_data`
- `etl_idempotency_records`
- `etl_job_records`
- legacy `users`, `roles`, `user_roles`

`users`, `roles`는 현재 “두 단어 이상 snake_case” DB naming 규칙을 위반하는 legacy object이므로 안전한 제거/이름변경과 rollback evidence가 필요합니다.

## 서비스 구성

| 서비스 | 기본 포트 | 역할 |
| --- | ---: | --- |
| Gateway | 8080 | routing / 향후 production identity boundary |
| ETL Service | 8000 | bounded ETL, idempotency, durable intake, connector catalog |
| CDC Service | 8001 | Debezium capture, Kafka publish, CDC control/status |
| Eureka Server | 8761 | service discovery |
| Config Server | 8888 | optional config service |
| Zipkin | 9412 | tracing backend when enabled |

## Connector 지원 원칙

PostgreSQL이 현재 production ETL load path입니다. Databricks/Snowflake/Qlik 및 추가 CDC source/target은 runtime support가 검증되기 전까지 scaffold/discovery 상태를 그대로 표시합니다. “API에 보인다”와 “production connector다”를 구분합니다.

## PII 처리 원칙

업무에 필요한 PII를 blanket masking하여 ETL 자체를 무력화하지 않습니다. 대신:

- 목적 기반 인증·권한
- least privilege
- 전송/저장 암호화(배포 환경에 맞게)
- 최소 보존
- privileged access/export audit
- owner/tenant isolation
- log/error/metric에서 raw principal/key/payload/secret 비노출

을 적용합니다.

## GitHub 자동 개발·검증

PR #121의 repository workflow는 `active_pr`입니다. 목표 구조는 다음과 같습니다.

- OpenCode model job: GitHub read-only
- deterministic branch publisher: 제한된 contents write
- deterministic PR publisher: 제한된 pull-request write
- exact-head workflow authorizer: 제한된 Actions write
- 독립 reviewer와 protected merge는 별도 authority
- LLM credential: `NVIDIA_NIM_API_KEY`
- GitHub Copilot/`COPILOT_GITHUB_TOKEN`은 자율 개발 credential로 사용하지 않음
- branch write는 exact parent + non-forced ref update 방식의 branch-wide CAS
- 같은 branch writer conflict는 그 branch만 중단하고 다른 안전한 mightyETL 일을 계속함

현재 protected develop CI는 PR에서 기본 checkout을 사용하므로 synthetic merge ref가 실행될 수 있습니다. exact-source acceptance는 #121이 통합된 후 canonical workflow contract가 됩니다.

## 문서 체계

이번 canonical 기준은 다음을 하나의 문서 그래프로 관리합니다.

- `README.md`
- `PRD.md`
- `TRD.md`
- `ARCHITECTURE.md`
- `SECURITY.md`
- `docs/adr/*`
- `docs/UML.md`
- `docs/ERD.md`
- `docs/API_CONTRACT.md`
- `docs/THREAT_MODEL.md`
- `docs/TEST_STRATEGY.md`
- `docs/OPERABILITY.md`
- `docs/TRACEABILITY.md`
- `CHANGELOG.md`

공개 API, DB state, lifecycle, security/trust, deployment, autonomous authority 또는 release evidence가 바뀌면 관련 canonical 문서를 같은 PR에서 함께 갱신해야 합니다.

## 현재 가장 중요한 다음 제품 경계

문서만 완성했다고 제품이 끝난 것은 아닙니다. 실행 우선순위는 보호 규칙과 writer lease를 지키면서:

1. exact-source CI/security/review control(#121) 통합
2. durable worker stack(#143 → #148) 순차 통합
3. CDC acknowledgement(#139)와 graceful stop(#141)
4. gateway production identity(#142)
5. legacy single-word DB object 제거/마이그레이션
6. connector reliability, dead-letter/replay, tenancy/audit, measured SLO와 release provenance

순으로 계속 진행합니다.
