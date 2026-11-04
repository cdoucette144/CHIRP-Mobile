# Settings and import status

The third tab stores draft controls only; radio settings synchronization is not implemented. Channel uploads preserve existing settings bytes.

Mini channel controls support scan inclusion, busy-channel lockout, FHSS/analog privacy, PTT ID, and separate RX/TX tones. FHSS is the radio's analog privacy feature, not digital encryption. Unknown bits are preserved; unset optional fields preserve existing values. Not all model-specific menus are implemented.

Plymouth County conventional analog public-safety snapshot fetched 2026-11-03 from https://www.radioreference.com/db/browse/ctid/1224. Source alpha tags, RX frequencies, modes and receive tones are retained. Monitor templates use explicit TX-capable FRS1 low-power fallback; no repeater inputs are guessed. Some source names exceed the Mini's 12-character limit and require an explicit radio alias before programming. Saved user banks are not rewritten. Generated county placeholder frequencies were removed. Other counties require a conventional CSV export. Digital modes and trunked talkgroups are excluded. No live RadioReference account connection is implemented.
