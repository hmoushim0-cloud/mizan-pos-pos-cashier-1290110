# تطبيق ميزان نقاط البيع الذكية (mizan-pos-pos-cashier-1290110)
> منظومة pos_cashier المتكاملة مع ربط ESC/POS Thermal Receipt Printer + Barcode Scanner

- **Project ID**: #1290110
- **Commercial Archetype**: pos_cashier (Retail Cashier & Supermarket POS)
- **Bound Hardware Peripheral**: ESC/POS Thermal Receipt Printer + Barcode Scanner
- **Package Name**: sa.factory.mizanposposcashier
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons)

---

## [Sidekick Autonomous Update] Production Protocol Engines & Verification

### 1. EscPosThermalPrinterProtocolEngine
- **ESC/POS Command Stream**: Full low-level byte sequence protocol implementing `CMD_INIT` (0x1B, 0x40), character alignments, font weighting, character scaling, and paper guillotine feed & cut (`CMD_FEED_AND_CUT` 0x1D, 0x56, 0x42, 0x00).
- **ZATCA Phase 2 Compliance**: Integrated Tag-Length-Value (TLV) Base64 QR code encoding compliant with ZATCA Simplified Tax Invoicing regulations (Tags 1-7: Seller Name, 15-digit VAT number, ISO UTC Timestamp, Total, VAT amount, SHA-256 Invoice Hash, and ECDSA signature).
- **Paper & Head Telemetry**: Hardware paper sensor monitoring with status transitions (`READY`, `LOW_PAPER_WARNING`, `PAPER_OUT`, `COVER_OPEN`, `HEAD_OVERHEAT`).

### 2. BarcodeScannerInventoryEngine
- **Symbology Parsing**: Native decode engines for EAN-13, EAN-8, Code 128, and 2D QR codes.
- **Checksum Verification**: Algorithmic modulo-10 checksum validation for EAN-13 and EAN-8 barcode feeds.
- **Inventory & Cart State Machine**: In-memory SKU catalog lookup, automated stock deduction, and cashier cart consolidation.

### 3. HardwareSensorDriver Integration
- High-frequency peripheral telemetry streaming over Flow pipeline.
- Continuous status broadcasts including paper roll levels, decode latency, active scan metrics, and frame sequence counters.

### 4. Unit Regression Suite
- 10 comprehensive unit regression tests covering checkout calculations, 15% VAT accuracy, Saudi VAT number validation, TLV Base64 generation, ESC/POS byte integrity, paper transitions, EAN-13/8 checksum math, and hardware driver flows.
