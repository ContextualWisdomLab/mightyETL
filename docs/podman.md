# Podman (Docker 호환 런타임)

이 프로젝트는 `Dockerfile`/`docker-compose.yml` 기반으로 구성되어 있어 Podman에서도 실행할 수 있습니다.

## 1) 설치/준비

- macOS: Podman 설치 후 `podman machine`을 준비합니다.

```bash
podman machine init --now
```

## 2) Compose 실행

Podman 환경에 따라 `podman compose` 또는 `podman-compose`를 사용합니다.

```bash
# 옵션 A) podman compose
podman compose -f docker-compose.yml up --build

# 옵션 B) podman-compose
podman-compose up --build
```

## 3) 참고 (depends_on / healthcheck)

`docker-compose.yml`은 `depends_on.condition`(예: `service_healthy`)를 사용합니다. 일부 Podman Compose 구현에서 이 옵션을 지원하지 않을 수 있습니다.
그 경우 아래처럼 의존 서비스부터 먼저 올린 뒤 앱을 올리는 방식으로 우회할 수 있습니다.

```bash
podman compose up -d postgres postgres-replica zookeeper kafka zipkin eureka-server
podman compose up -d etl-service cdc-service zuul-gateway
```

