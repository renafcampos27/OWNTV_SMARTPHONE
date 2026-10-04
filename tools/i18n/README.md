# OwnTV Mobile — i18n checks

**The strings themselves are not in this repository.** Every `strings*.xml`, the language catalogue
that owns them, the Weblate integration and the translator guide live in the core library repo:

<https://github.com/ahXN00/OwnTV_Core> (`tools/i18n/README.md` there). Add, change or translate a
string there — **including a string only this app uses**, exactly as the TV app does. A strings file
in this repo would never be seen by Weblate and would be English-only for ever, which is the one
thing the rule forbids.

What runs here, on this app's own Kotlin:

| Check | What it enforces |
| --- | --- |
| `check_hardcoded_strings.py` | No user-visible text left hardcoded in `app/src/main/java` |
| `check_number_locale.py` | Numbers formatted with an explicit locale, not the default |
| `check_text_overflow.py` | Bounded Compose text has an overflow strategy |
| `check_pseudo_locales.py` | Pseudolocales are packaged in debug and absent from release |

```sh
python3 tools/i18n/check_hardcoded_strings.py verify --bootstrap
python3 tools/i18n/check_number_locale.py
python3 tools/i18n/check_text_overflow.py
```

The first of these also runs on every Gradle build, as `:app:verifyI18nLiterals` on `preBuild`, so a
hardcoded string fails the compile instead of the push.

The pseudolocale check needs a built APK and `aapt2`. On Windows the finder cannot see `aapt2.exe`,
so pass it explicitly:

```powershell
$aapt2 = "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\aapt2.exe"
python tools\i18n\check_pseudo_locales.py --apk app\build\outputs\apk\standard\debug\app-standard-debug.apk --mode debug --aapt2 $aapt2
```

`locales.json` is kept here too, but only because the Gradle build reads its `packaged` entries to
set `localeFilters` before any dependency is resolved. **The core repo's copy is the authoritative
one**, and the automated "Pin core" pull request refreshes this copy alongside `owntvCore` — never
edit one of those two files without the other, or a new language is silently stripped out of the APK
while the build stays green.

### Clearing a literal-inventory failure

`verify` only reports; no flag makes it write, `--bootstrap` included — that flag drops the
merge-base comparison and nothing else. Two failure kinds, two fixes:

```sh
# UNCLASSIFIED — a literal exists in code but in neither reviewed file.
python3 tools/i18n/check_hardcoded_strings.py classify-safe \
    --path app/src/main/java/tv/own/owntv/mobile/example.kt --text 'SELECT 1' --category sql

# STALE CLASSIFICATION — a classified literal was edited or deleted in code.
python3 tools/i18n/check_hardcoded_strings.py prune-safe
```

Both rewrite `safe_literals.txt` and regenerate `hardcoded_baseline.txt`. Classify a literal only
when no user can ever read it; categories and their reasons are listed at the top of
`safe_literals.txt`. Text a user *can* read stays unclassified so it lands in
`hardcoded_baseline.txt` as declared debt — that file may only shrink against a pull request's merge
base. Never file real UI copy as technical to make CI green; put it in core's `strings_*.xml`
instead.

**The whole of today's baseline is the Plan 3 Phase 3 dev harness** (`app/.../mobile/dev/`), which
Plan 4 deletes once real screens exist. It is declared debt on purpose rather than classified
technical: the text is genuinely readable, it is simply throwaway. When the harness goes, the
baseline goes to zero and the ratchet becomes absolute.
