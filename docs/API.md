# HTTP API v1

Base: `/api/v1/`. Same-origin, session-authenticated JSON. Sign in through the CSRF-protected `/accounts/login/` form, then send the session cookie and `X-CSRFToken` for mutations. There are no public signup, shared credentials, long-lived browser tokens, CORS wildcard, or credential-bearing URL parameters. GET `/health/` is public and checks database/schema availability without exposing inventory.

Lists return `{count, next, previous, results}` with 50 records/page. `page` selects a page. IDs are integers except sale UUIDs. Money is serialized as two-decimal strings. Dates use `YYYY-MM-DD`, timestamps are ISO 8601 and displayed in Asia/Karachi in the staff UI. Barcodes are strings, preserving leading zeros. All endpoints end in `/`.

## Resources

| Method / path | Input / behavior | Access |
|---|---|---|
| GET `me/` | Current named account and role; no password | All authenticated |
| GET `products/`, `products/{id}/` | Search `q` by name/generic/SKU/barcode; `kind=MEDICINE|GENERAL`; `low=true` | All staff |
| POST `products/` | Create catalog metadata | Admin / manager / pharmacist |
| PATCH/PUT `products/{id}/` | Update metadata; use `active=false` to archive. Clinical classification is immutable after stock exists. | Admin / manager / pharmacist |
| GET/POST `suppliers/`, GET/PATCH/PUT `suppliers/{id}/` | Name, contact, phone, email, address, active | Admin / manager / pharmacist |
| GET/POST `patients/`, GET/PATCH/PUT `patients/{id}/` | Name, phone, birth_date, allergies | Admin / pharmacist |
| GET `batches/`, `batches/{id}/` | Lot balances, cost, expiry and quarantine | Admin / manager / pharmacist |
| POST `batches/receive/` | Add a standalone stock receipt | Admin / manager / pharmacist |
| POST `batches/{id}/adjust/` | Physical count and quarantine state; mandatory reason | Admin / manager / pharmacist |
| GET `movements/` | Read-only ledger | Admin / manager / pharmacist |
| GET `purchases/`, `purchases/{id}/` | Supplier/order status/lines/total | Admin / manager / pharmacist |
| POST `purchases/` | Create multi-line open PO | Admin / manager / pharmacist |
| POST `purchases/{id}/receive/` | One lot per ordered product, full quantities, once only | Admin / manager / pharmacist |
| POST `purchases/{id}/cancel/` | Cancel OPEN order | Admin / manager |
| GET `prescriptions/`, `prescriptions/{id}/` | Patient, prescriber, dates, verifier and allowances | Admin / pharmacist |
| POST `prescriptions/` | Create unverified Rx with medicine items | Admin / pharmacist |
| POST `prescriptions/{id}/verify/` | Verify current uncancelled Rx once | Admin / pharmacist |
| POST `prescriptions/{id}/cancel/` | Cancel; no further dispensing | Admin / pharmacist |
| GET `sales/`, `sales/{uuid}/` | Historical receipt snapshots | Cashier: own; other roles: all |
| POST `sales/` | Atomic checkout; 201 new / 200 exact replay | All staff for non-Rx; admin/pharmacist for Rx |
| POST `sales/{uuid}/refund/` | Full refund, quarantine returns, once only | Admin / manager |
| GET `dashboard/` | Today and stock summaries | Admin / manager / pharmacist |
| GET `reports/?start=...&end=...` | Date-bounded gross/net sales, tax, discounts, cost, gross profit | Admin / manager |
| GET `audit/` | Read-only business audit | Admin |
| GET/POST `users/`, GET/PATCH/PUT `users/{id}/` | Named accounts; optional write-only new password | Admin |
| GET/PATCH `settings/` | Name, address, phone, receipt_footer | Admin |

DELETE is not implemented for business records. Empty action bodies are `{}`. There are no arbitrary quantity-update endpoints outside the auditable receipt/count/refund workflows.

## Catalog example

```json
{
  "sku": "GEN-100", "barcode": "0012345678901", "name": "Hand wash 250ml",
  "kind": "GENERAL", "category": "Personal care", "unit": "bottle",
  "generic_name": "", "strength": "", "prescription_required": false,
  "controlled_drug": false, "price": "350.00", "tax_rate": "0.00",
  "reorder_level": 10, "active": true
}
```

`MEDICINE` supports generic/strength and prescription flags; general merchandise cannot require a prescription. `controlled_drug=true` blocks checkout. The operator must correctly classify products; the application cannot infer legal scheduling from a product name. Tax defaults are configuration, not a declaration of exemption.

## Stock input

POST `batches/receive/`:

```json
{"product_id":1,"number":"LOT-2026-A","quantity":24,"unit_cost":"210.00","expiry_date":null}
```

For medicine lots, `expiry_date` is mandatory and must be later than today. An existing lot can be topped up only if cost and expiry match and it is not quarantined. Missing/archived products cannot be received.

POST `batches/7/adjust/`:

```json
{"counted_quantity":20,"quarantined":true,"reason":"Packaging damaged during inspection"}
```

The count is an absolute physical count, not a delta. Stock movements record the computed delta; audit records record count/quarantine changes even when delta is zero.

## Purchasing

POST `purchases/`:

```json
{"supplier_id":1,"items":[{"product_id":1,"quantity":24,"unit_cost":"210.00"},{"product_id":2,"quantity":50,"unit_cost":"12.00"}]}
```

POST `purchases/12/receive/`:

```json
{"items":[{"product_id":1,"number":"LOT-A","expiry_date":null},{"product_id":2,"number":"MED-B","expiry_date":"2028-04-30"}]}
```

The product set must exactly match the PO; no duplicate/partial item receipts. Quantities and costs are taken from the stored PO, not from this request. If any lot fails validation, all lots/order/ledger changes roll back.

## Prescriptions

POST `prescriptions/`:

```json
{
  "patient_id":1,"reference":"CLINIC-123","prescriber":"Qualified prescriber",
  "prescriber_registration":"REG-123","issued_on":"2026-10-30","valid_until":"2026-11-15",
  "items":[{"product_id":2,"quantity":10,"directions":"As professionally prescribed"}]
}
```

Creation does not verify. Call the verify action after professional review. No edit API can rewrite a verified prescription; cancel and record a corrected one. Refills are represented by a new professionally approved allowance/reference rather than automatic reuse. Clinical advice, diagnosis, prescription-image uploads, interaction detection, and prescriber-license verification are not provided.

## Checkout and retry contract

POST `sales/`:

```json
{
  "idempotency_key":"f1f838c8-2f71-4846-b8d5-b667aa317cac",
  "items":[{"product_id":1,"quantity":2}],
  "discount":"0.00","amount_paid":"1000.00","payment_method":"CASH"
}
```

For Rx sales, a privileged operator additionally sends matching `patient_id` and `prescription_id`. All prescribed items are bounded by remaining allowance; a full financial refund does not restore it. Cashier/manager patient association is denied.

Payment method is `CASH`, `CARD`, or `TRANSFER`. Non-cash tender must equal the authoritative total. The amount is a record of an externally completed payment, not a charge request. Discounts require admin/manager. Client product prices/taxes/totals are never accepted as authoritative. A new sale returns header and immutable line snapshots, including `id`, `subtotal`, `discount`, `tax`, `total`, `amount_paid`, `change`, `payment_method`, `refunded`, and `lines`.

Generate one UUID per checkout intent. On connection uncertainty, **retry the exact same request and UUID**; never generate a replacement until the outcome is resolved. The same operator/key with a different canonical payload returns 409. Keys are scoped to the operator and persisted in the database. Replay may return a subsequently refunded sale, still without deducting stock again.

POST `sales/{uuid}/refund/`:

```json
{"reason":"Customer returned the complete purchase; payment refunded externally"}
```

Refunded stock enters separate quarantined lots. A manager/pharmacist must inspect it before any release; medicine-return suitability and disposal remain professional responsibilities. Partial-item returns are not implemented.

## Account input and errors

User creation fields: `username`, `first_name`, `last_name`, `role`, `is_active`, and required `password`. Passwords are write-only; changing one rehashes it and invalidates old sessions on their next request. Framework password validators apply. There is no ability to grant `is_superuser` or `is_staff` via this API. Login uses ten failed attempts per account or IP in a 15-minute persistent window.

Typical error:

```json
{"error":{"status":409,"details":{"detail":"Insufficient saleable stock for Hand wash; expired and quarantined stock is excluded."},"request_id":"server-generated-id"}}
```

400 = invalid input; 403 = unauthenticated/session/CSRF/role denial; 404 = unavailable record; 409 = state/idempotency/constraint conflict; 429 = login throttle (HTML sign-in); 500 = generic unexpected error. `X-Request-ID` accompanies responses. Health can return 503 if database/schema is unavailable. Default Django errors outside the API are HTML.
