# RIWAYA: Arabic novel sources

Checked on 1 November 2026 against the IReader repo index
(`https://raw.githubusercontent.com/IReaderorg/IReader-extensions/repov2/index.min.json`, 145 extensions) and the
LNReader plugin index (`plugins/v3.0.0/.dist/plugins.min.json`, 328 plugins, 20 Arabic).

## A. Arabic sources with an IReader extension (installed on first run, all enabled by default)

| # | Name | Package | Site | Version |
|---|---|---|---|---|
| 1 | ملوك الروايات (KolNovel) | `ireader.kolnovel.ar` | kolnovel.com | 2.4 |
| 2 | مجرة الروايات (GalaxyNovels) | `ireader.galaxynovels.ar` | galaxynovels.com | 2.8 |
| 3 | بحر الروايات (SeaNovel) | `ireader.seanovel.ar` | seanovel.org | 2.2 |
| 4 | مملكة الروايات (MKNOV) | `ireader.mknov.ar` | mknov.com | 2.1 |
| 5 | مكتبة الخيال (MtlArabic) | `ireader.mtlarabic.ar` | mtlarabic.com | 2.1 |
| 6 | مركز الروايات (MarkazRiwayat) | `ireader.markazriwayat.ar` | markazriwayat.com | 2.6 |
| 7 | جنة الروايات (NovelParadise) | `ireader.novelparadise.ar` | novelsparadise.site | 2.7 |
| 8 | روايات فانز (Rewayatfans) | `ireader.rewayatfans.ar` | rewayatfans.com | 2.3 |
| 9 | رواية فانز (Rewayahfans) | `ireader.rewayahfans.ar` | rewayahfans.net | 2.5 |
| 10 | نادي الروايات (Rewayat Club) | `ireader.rewayatclub.ar` | rewayat.club | 2.3 |
| 11 | روايات (Riwyat) | `ireader.riwyat.ar` | cenele.com | 2.12 |
| 12 | صن نوفلز (Sunovels) | `ireader.sunovels.ar` | sunovels.com | 2.3 |
| 13 | عالم الروايات (RealmNovel) | `ireader.realmnovel.ar` | (from the extension) | 2.5 |
| 14 | أزورا فلاي (AzoraFly) | `ireader.azorafly.ar` | azorafly.com | 2.1 |
| 15 | الاستراحة الذهبية (Golden Rest) | `ireader.golden.ar` | golden.rest | 2.3 |
| 16 | الترجمة الآلية العربية (Armtl) | `ireader.armtl.ar` | (from the extension) | 2.2 |
| 17 | الرواية العربية (ArNovel) | `ireader.arnovel.ar` | ar-no.com | 2.5 |
| 18 | هيزو (HizoManga, novels only) | `ireader.hizomanga.ar` | hizomanga.net | 2.5 |
| 19 | روايات فور أب (Novel4Up) | `ireader.novel4up.ar` | novel4up.com | 1.2 |
| 20 | روايات العرب (NovelArab) | `ireader.novelarab.ar` | (from the extension) | 2.1 |

## B. Arabic sites with only an LNReader plugin (new IReader-format sources to write in `riwaya-sources/`)

| Name | Site | Status |
|---|---|---|
| ملوك الروايات المجاني (Free Kol Novel) | https://free.kolnovel.com/ | built into the app |
| أزورا مون (Azora) | https://azoramoon.com/ | skipped: marked down in LNReader |
| أولاو (Olaoe) | https://olaoe.cyou/ | skipped: marked down in LNReader since January 2026 |

Before a site is written: open it; if it is down, sits behind a protection the app may not pass, or clearly hosts
pirated paid content, skip it and log why in `RIWAYA_DECISIONS.md`.

## C. Foreign sources

From the IReader repo, installed by language on demand: English 98, Russian 5, Indonesian 5, French 5, Chinese 4,
Turkish 3, Japanese 1, Portuguese 1, Ukrainian 1, Spanish 1, Multi 1. A custom repo URL can be added in the Sources screen.

## Presentation

Grouped by language (preferred languages pinned on top), then by type: مترجم / مؤلف / آلي MTL / لايت نوفل.
Each row shows the icon, the name, a language chip, a health dot (green / amber / red with the reason), the last-checked
time, a pin and an enable switch.
