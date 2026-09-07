# ISO 20022 Message Conversion Service

A Java/Spring Boot REST service for converting ISO 20022 banking messages — XML ↔
JSON, and cross-message-type transformation (e.g. a `pain.001` customer payment
instruction into `pacs.008`/`pacs.009` for cross-border clearing, and the `pacs.002`
status report back into `pain.002` for the originator).

This is a from-scratch, in-house equivalent of the message-conversion piece of tools
like Volante Designer/Integrator — **not** a payment processing engine. Posting,
earmarking, and compliance/flex checks are core-banking functions performed
elsewhere; this service only translates between wire formats and message types, with
an explicit extension point (`enrichment` in the request) for the caller to supply
whatever those business steps decided.

## Requirements

- Java 21, Maven 3.9+

## Running

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:8080`. Swagger UI is at
`http://localhost:8080/swagger-ui/index.html`, raw OpenAPI JSON at
`/v3/api-docs`.

## Endpoints

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/convert/xml-to-json` | Raw XML body → JSON. Typed (schema-aware) for the curated message types below; generic structural fallback otherwise. |
| `POST /api/v1/convert/json-to-xml` | Raw JSON body → XML. Requires `?messageId=`. |
| `POST /api/v1/clearing/convert` | Cross-message-type conversion (e.g. pain.001 → pacs.008/009). JSON envelope in/out — see below. |
| `POST /api/v1/clearing/return-status` | pacs.002 → pain.002 return-path mapping. Same envelope shape. |
| `GET /api/v1/message-types` | Catalog of the curated, typed message types. |
| `GET /api/v1/transformers` | Catalog of registered cross-message-type mappings. |

### `POST /api/v1/clearing/convert` example

```json
{
  "sourceXml": "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.09\">...</Document>",
  "settlementMethod": "COVER",
  "strict": true,
  "enrichment": {
    "complianceStatus": "CLEARED",
    "exchangeRate": 1.0842,
    "settlementCurrency": "USD",
    "earmarkReference": "EARM-2026-000123",
    "intermediaryAgents": [{"bic": "CHASUS33", "role": "INTERMEDIARY1"}],
    "chargeBearer": "SHAR"
  }
}
```

Returns one message (`SERIAL`) or a correlated pair sharing the same UETR (`COVER`),
plus a list of `notes` documenting every non-obvious mapping decision (generated,
defaulted, approximated, or taken from `enrichment`).

## Architecture

- **Typed model**: JAXB classes generated at build time (`org.jvnet.jaxb:jaxb-maven-plugin`,
  configured in `pom.xml`) from the XSDs in `src/main/resources/xsd/`. No runtime
  dependency on any third-party ISO 20022 library — only standard Jakarta XML Bind
  and Jackson.
- **Generic fallback**: any message type without generated classes still converts
  XML ↔ JSON structurally via Jackson's `XmlMapper` (`GenericXmlJsonConverter`), with
  no schema validation.
- **Cross-message-type mapping**: a plugin interface (`MessageTransformer`), with each
  concrete mapping registered as a Spring `@Component` and auto-discovered by
  `TransformerRegistry`. Adding a new mapping = adding one new class; nothing else
  changes.
- **CBPR+-aligned validation**: `CbprPlusValidator` enforces the most commonly cited
  SWIFT CBPR+ constraints (mandatory UETR, structured postal address, valid BIC
  format, cover-pair UETR correlation) on every generated clearing-bound message.

See inline Javadoc on `MessageTransformer`, `EnrichmentData`, and
`CbprPlusValidator` for the reasoning behind specific mapping/validation decisions.

## Known scope limitations (deliberate, documented)

- **Representative, not official, XSDs.** The schemas in `src/main/resources/xsd/`
  model the real ISO 20022 namespaces, root elements, and the fields this project's
  scenarios need — they are hand-authored, scoped-down subsets, not literal copies of
  the official multi-thousand-line ISO 20022 schemas (which span many shared
  component files and external code lists). Swap in the official schemas for the
  curated message types if stricter, complete validation is required; the JAXB
  codegen/package-per-message-type approach will work the same way.
- **CBPR+ is approximated, not certified.** Real CBPR+ schemas require a SWIFT
  MyStandards account; `CbprPlusValidator` encodes publicly documented constraints
  only. Don't treat a message that passes it as CBPR+-certified.
- **Stateless — no persistence/correlation store.** UETR retry-safety and exact
  original-message correlation on the pacs.002→pain.002 path are the calling payment
  engine's responsibility (see Javadoc on the relevant classes for specifics).
- **No batch fan-out.** Each conversion call handles exactly one payment
  instruction/transaction; a multi-transaction pain.001 file must be split by the
  caller into per-transaction calls. Fails loudly (422) rather than silently
  processing only the first transaction.
- **No correspondent/BIC routing directory.** `settlementMethod=COVER` requires the
  caller to supply `intermediaryAgents` in `enrichment` — this service does not
  decide correspondent routing.
- **Curated message types**: `pain.001.001.09`, `pain.002.001.10`,
  `pacs.008.001.08`, `pacs.009.001.08`, `pacs.002.001.10` have generated typed
  classes and CBPR+ validation. `camt.052/053/054` and any other ISO 20022 message
  type still work on the plain XML↔JSON endpoints via the generic fallback, but
  aren't yet part of the typed/curated set or eligible for cross-message-type mapping.

## Testing

```bash
mvn test
```

Unit tests cover the transformers (serial/cover UETR correlation, missing-enrichment
failures, cross-currency conversion) and the CBPR+ validator; `@SpringBootTest` +
MockMvc integration tests cover all REST endpoints and error paths end-to-end.
