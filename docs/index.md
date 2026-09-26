# mightyETL

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/ContextualWisdomLab/mightyETL)

mightyETL is a modular Spring-based data-movement platform for bounded atomic ETL, durable job processing, and PostgreSQL change data capture. It can run as standalone ETL/CDC services or as part of a composed microservice deployment.

## Start here

- [README](https://github.com/ContextualWisdomLab/mightyETL/blob/develop/README.md) — supported capabilities, quick start, and product truth.
- [Product requirements](https://github.com/ContextualWisdomLab/mightyETL/blob/develop/PRD.md) — product scope and buyer outcomes.
- [Technical requirements](https://github.com/ContextualWisdomLab/mightyETL/blob/develop/TRD.md) — engineering and quality requirements.
- [Architecture](https://github.com/ContextualWisdomLab/mightyETL/blob/develop/ARCHITECTURE.md) — system boundaries and deployment model.
- [API contract](https://github.com/ContextualWisdomLab/mightyETL/blob/develop/docs/API_CONTRACT.md) — HTTP and integration contract entry point.
- [Security](https://github.com/ContextualWisdomLab/mightyETL/blob/develop/SECURITY.md) and [threat model](https://github.com/ContextualWisdomLab/mightyETL/blob/develop/docs/THREAT_MODEL.md) — security responsibilities and known boundaries.
- [Operability](https://github.com/ContextualWisdomLab/mightyETL/blob/develop/docs/OPERABILITY.md) — runtime, recovery, and operational guidance.
- [Traceability](https://github.com/ContextualWisdomLab/mightyETL/blob/develop/docs/TRACEABILITY.md) — requirements, decisions, implementation, and evidence.

## Product boundary

The protected `develop` branch is the shipped-source authority. Open pull requests, scaffolds, and planned connectors are not production capability until they integrate through normal repository governance. PostgreSQL is the current production ETL target; warehouse/BI connectors remain subject to the support status documented in the README and traceability materials.

## Releases and onboarding

Use the repository README and release history for the current installation and version truth. Before production use, review the security, operability, migration, dependency-license, and release-provenance requirements associated with the exact revision you deploy.

This file is the source for a future GitHub Pages documentation landing. Its presence does not by itself prove that GitHub Pages is enabled or published.