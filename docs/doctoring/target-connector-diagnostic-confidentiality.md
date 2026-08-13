# Target connector diagnostic confidentiality

## Status and scope

This doctoring note records the security boundary implemented by the active target-connector lifecycle logging change. It does not make an active pull request shipped product truth. The production rule is narrow: ordinary logs may retain a bounded connector lifecycle outcome and connector ID, but they must not serialize third-party exception objects or provider diagnostics that can carry a connection string, token, request fragment, account identifier, storage path, or other deployment-sensitive data.

The rule maps directly to **CWE-532, Insertion of Sensitive Information into Log File**. MITRE describes sensitive values written to logs as a confidentiality weakness because log storage is commonly a less-protected secondary disclosure path. The OWASP Logging Cheat Sheet likewise requires deliberate exclusion or sanitization of sensitive event data and protection of collected logs against unauthorized access or misuse.

## Causal error retention without diagnostic publication

Failing connector cleanup after a failed `open(...)` remains attached to the original in-process failure as a **suppressed exception**. This preserves causal information for an authorized caller or debugger that already possesses the exception object without copying provider exception text into routine logs. Shutdown remains best-effort: a failing connector close is classified, the dispatcher continues closing other connectors, and the open-state bookkeeping is cleared as before.

Routine observability therefore records only finite lifecycle classifications such as `Failed to clean up target connector after open failure` or `Failed to close target connector`, plus the bounded **connector ID**. It does not include provider exception messages or stack traces. The connector ID is operational metadata, not permission to add arbitrary provider configuration to the log record.

## Privacy and operational policy

This is data minimization and **purpose-bound** observability, not blanket masking. Connector implementations still receive the real endpoint, credential, payload, and business data required to perform their authorized work. Those values remain available only at the execution boundary that needs them. Ordinary application logs are a different purpose and therefore receive the minimum information needed to detect and count lifecycle failure.

Do not replace this boundary with regex-only masking. Provider exception formats are not a stable schema, and new drivers can embed sensitive values in previously unseen text. If deeper diagnostics are required during an incident, use explicitly authorized, access-controlled diagnostic tooling and bounded retention rather than widening default logs.

Recommended operator evidence is finite-cardinality counts of lifecycle outcomes by supported connector ID and deployment version. Avoid raw principal, credential, connection string, request payload, SQL, provider exception text, or arbitrary target identifiers in ordinary telemetry.

## Verification contract

Regression tests must prove that:

- cleanup and close failures do not export provider exception messages, class names, or stack traces to ordinary logs;
- the original failed-open exception still carries cleanup failure causality through its suppressed-exception list;
- shutdown close failure remains best-effort and leaves dispatcher state coherent;
- the stable lifecycle classification and bounded connector ID remain observable; and
- no security remediation weakens connector validation, error propagation, or cleanup ordering merely to make logs quiet.

These checks are necessary behavioral evidence, but current pull-request workflow results must still be classified by the revision actually executed. A synthetic merge preview does not become literal-source evidence merely because the tests pass.

## References — APA 7

MITRE. (2026). *CWE-532: Insertion of sensitive information into log file (Version 4.20).* Common Weakness Enumeration. https://cwe.mitre.org/data/definitions/532.html

OWASP Foundation. (2026). *Logging cheat sheet.* OWASP Cheat Sheet Series. https://cheatsheetseries.owasp.org/cheatsheets/Logging_Cheat_Sheet.html

OWASP Foundation. (2025). *A09:2025 Security logging and alerting failures.* OWASP Top 10. https://owasp.org/Top10/2025/A09_2025-Security_Logging_and_Alerting_Failures/
