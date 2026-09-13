# Changelog

OpenPage is authored and published by Kristian Espedido, Founder and current
CEO of The QAWSE Institute / QAWSE Corp.

## 2.0.0 — Premium redesign

The complete audit-and-redesign release. Every screen, journey and protocol
path was reviewed, then rebuilt on a single design system. Working printer
and scanner behavior was preserved; three real protocol/data bugs were fixed.

### Critical fixes
- **PCL commands missing the ESC prefix** (v1.0.0 regression): duplex,
  media selection, raster begin/end and page eject were sent as literal
  text (`&l1S`, `*r1A`, …). All commands now carry ESC and are pinned by
  `PclCommandTest`.
- **PCL star-command corruption** (introduced and caught during this
  redesign): raster commands briefly emitted `ESC&t` / `ESC&r`; corrected to
  `ESC*t` / `ESC*r` with tests.
- **eSCL header parser truncated values**: `Content-Length: 10` read as `1`
  when the header line ended at the final CRLFCRLF, and chunked responses
  were never detected as complete. Rewritten as byte-level parsing with
  14 new tests.
- **Page range vs. typographic dashes**: the hint showed `1–3` (en dash)
  while the parser only accepted ASCII hyphens. Both are accepted now.
- **Security**: release keystore + plaintext passwords were committed and
  shipped inside the v1.0.0 source archive. Secrets rotated, untracked,
  `.gitignore` hardened, archive regenerated clean.

### Print flow
- Progressive setup: Document / Paper / Layout / Output groups, advanced
  settings behind a disclosure; two-pane layout on tablets and landscape.
- Live preview with paper-true aspect ratio, margin guides overlay, page
  navigation; race-free regeneration (stale renders discarded by generation).
- Inline page-range validation while typing (pages selected / syntax hint /
  pages-beyond-document warning) — no more late generic errors.
- Explicit primary action ("Print 3 pages × 2 copies"), confirmation summary
  for heavy jobs, per-page progress with cancellation and an honest note
  that the in-flight page may still print.
- Failure screens name the problem and offer the right recovery action
  (retry / go to printers); cancel reports pages actually sent.

### Scan flow
- Multi-page sessions with a page tray: thumbnails (bottom strip on phones,
  side list on tablets), reorder, rotate, delete, add page.
- Captured pages are written to disk immediately — rotation and process
  death never lose work.
- Structured failure taxonomy (no scanner / permission / transport / invalid
  response / unsupported / undecodable / no camera app) with per-cause copy
  and recovery.
- Camera explainer before capture; graceful handling when no camera app
  exists or the photo cannot be decoded.
- Save-all / share-all (multi-URI share) / print-all-pages in one job.

### Design system
- One source of truth: semantic color roles (background, surface,
  elevatedSurface, primary, accent, success, warning, error, divider,
  disabled), Inter type scale with fixed size/weight/line-height/tracking,
  spacing, two corner-radius families, motion durations, 48 dp touch targets.
- Light and dark themes everywhere (v1 preview mats and crop overlays were
  light-only); every status uses icon + text + tone, never color alone.
- Adaptive navigation: bottom bar (compact), navigation rail (medium/expanded);
  Print setup goes two-pane in landscape and on tablets; Settings/About
  center to a readable column.
- Reusable components with full state matrices: buttons (loading state),
  segmented control (radio semantics), stepper (described controls), dropdown
  (ExposedDropdownMenu, checkmark), status pills, banners, empty states,
  progress panel (live-region announcements), confirmation dialogs.

### Engineering
- All user-facing strings moved to resources; plurals where counts appear;
  concise, specific, calm copy ("The printer stopped responding over USB.
  Re-plug the cable and try again." instead of "Something went wrong").
- Structured errors (PrintProblem / ScanProblem) replace stringly failures;
  debug-only logging facade that never logs document content or serials.
- State preservation: settings survive process death via SavedStateHandle,
  scan pages survive via disk, navigation state via saveState/restoreState,
  crop quad lives in the ViewModel.
- Keep-screen-on while transferring (previously a dead setting).
- Default color/quality/duplex settings now actually apply to new jobs.
- Job history records failures and cancellations with recovery copy, and
  keeps the source URI for "Print again".
- Test-page identifies driver, paper, dpi, color mode, date and the QAWSE
  Institute attribution.
- Diagnostics view (debug builds; in release, unlocked by tapping the
  version five times in About): transport, interfaces, driver + reason,
  feature expectations, last-scan stats.
- eSCL read loop rewritten to a growable buffer (was O(n²) in copies).
- 90 JVM test methods; lint gate configured to fail on errors; README with honest
  protocol-status table and known limitations.

### Removed / corrected
- Dead code: unused gradient tokens, `clearHistoryFlag`, unused icons and
  strings, duplicate Citizen brand entry (duplicate USB VID).
- Home "tip of the session" filler; About removed from top-level
  destinations (lives in Settings, where it belongs).
- `largeHeap` retained deliberately: 600 dpi A3 color rasterization needs
  the headroom; documented instead of hidden.

### Not verified on hardware in this release
Compiled, unit-tested and lint-gated only. Real-printer, real-scanner and
TalkBack verification still require hands-on device testing; see README
"Known limitations".
