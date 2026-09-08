# FSC stock-source evidence and decision traceability

Access date: 2026-09-07. Only the following primary documents are cited as authorities. Operational and source-code observations are separate from future acceptance requirements.

## References (APA 7)

Financial Services Commission. (n.d.). *금융위원회_주식시세정보*. Public Data Portal. https://www.data.go.kr/data/15094808/openapi.do

Oracle. (n.d.). *Java API for XML Processing (JAXP) security guide*. Java Platform, Standard Edition 25 Security Developer's Guide. https://docs.oracle.com/en/java/javase/25/security/java-api-xml-processing-jaxp-security-guide.html

Oracle. (n.d.). *Class BigDecimal*. Java Platform, Standard Edition 25 API specification. https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html

## Evidence boundaries

The FSC portal explicitly says daily reference data is available after 13:00 on the next business day, offers XML/JSON REST data and requires an API application/key. This supports the delayed-daily classification and explicit credential boundary. It does not prove a particular local credential is approved, that a given day is complete, or that a transport implementation has passed conformance. The full attached primary wire guide was not retrieved in this run. Parameter/field mapping remains a candidate profile until the guide and a real keyed request are verified; third-party examples are not promoted to primary authority.

The JAXP guide explains why default secure-processing settings alone should not be treated as external-resource denial. The decoder explicitly disables DTDs, external entity/schema access and XInclude, sets a depth limit, selects the JDK factory and uses strict UTF-8 decoding. Hostile XML contracts exercise the source boundary. They do not prove an upstream socket is governed; that belongs to EgressWeave.

BigDecimal supports exact decimal representation. The adapter constructs from validated source text and performs no rounding or return/risk calculations. BigInteger retains whole-number quantities without IEEE-754 precision loss. These are provider type conversions, not a new financial analytics engine.

## Traceability

| Authority / concern | Source implementation | Executable evidence |
|---|---|---|
| FSC publication timing | `StockBatch.freshnessClass`, raw-page timestamps | `verifyCompleteCollection`, `verifyEmptyAndSingleton` |
| Query/pagination integrity | `collectStockData`, `StockPageDecoder.decodePage` | `verifyInvalidPages`, `verifyQueryRejection` |
| Exact source values | `StockPriceRecord`, decoder numeric functions | `verifyCompleteCollection`, `verifyInvalidRecords` |
| JAXP external resource limits | `StockPageDecoder.readDocument` | `verifyHostileXml`, `verifyTransportFailures` |
| Declared envelope cardinality | `StockPageDecoder.requireChildren` | extra/duplicate `response`/`header`/`body`/`items` cases in `verifyHostileXml` |
| No key/diagnostic export | `fetchBody`, request/response formatting, finite exception | `verifyTransportFailures`, `verifyLateCancellationAndSafeFormatting` |
| Response lifecycle | `PageResponse.close`, `fetchBody` | rejected/oversized/cancelled-body close assertions; read/close and close-with-primary failures |
| Late cancellation | `requireNotCancelled` after decode and before batch return | Clock.instant() interrupt in `verifyLateCancellationAndSafeFormatting` |

The local compiler is OpenJDK 21.0.11. After the 2026-09-08 review repairs, `sh scripts/verify_stock_data_source.sh` passed 299 synthetic assertions plus `javac -Xlint:all -Werror` and `javadoc -Werror -Xdoclint:all`. This is not 299 independent JUnit methods and not a 100% coverage measurement. The repository's Java 25 Maven reactor, security checks, independent review and immutable release remain mandatory.
