# RadioReference export audit

Source: 36 user-supplied CSVs in the project's Radio Reference Exports Drive folder. Originals remain unchanged.

- 7,735 source rows.
- 4,674 rows explicitly marked FM, FMN/NFM, or AM.
- 3,061 other rows are excluded from this analog catalog, including digital, encrypted-labelled, trunked-system and telemetry modes.
- Two byte-identical duplicate file pairs.
- 4,574 catalog records after file and overlapping-record deduplication, retaining distinct county contexts.
- Massachusetts: 2,489; New Hampshire: 879; Rhode Island: 313; national/agency exports: 893.
- 57 county/agency groups.
- 1,009 catalog alpha tags exceed the Mini's 12-character limit. Source tags are not truncated.
- 2,737 catalog records do not list a nonzero repeater input. No input or input tone is inferred from an output tone.
- All imported agency categories are combined within their county; source agency descriptions remain searchable.
- Source RX and TX tones are separate.
- Source FM maps to FM; FMN maps to NFM. Other modes are not silently coerced.
- Every catalog record retains its source filename(s).
- New catalog banks default to monitoring; model-specific FRS1 low-power fallback is applied when appending to a Mini. This is transmit-capable, not TX-inhibited.
- Unsupported receive bands, AM fallback limitations, overlong names and model channel capacity are shown during selection.

These are user-provided export snapshots, not a live RadioReference account connection. Digital/encrypted source rows are retained in the local audit copies, not added to the analog app catalog.
