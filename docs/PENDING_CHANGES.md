# Pending changes

Build 1 (first APK):
- The whole app: onboarding (languages, genres, installing sources), home with recommendations and popular rows,
  discover, library, novel page, reader (paper/sepia/night/AMOLED, bilingual, glossary), listening with notification
  controls, sources grouped by language with health dots, global search (8 s per source, same novel merged),
  translation settings, history, background downloads and translation.
- 21 Arabic sources with Arabic names (20 from IReader + Free Kol Novel built in), and all foreign IReader sources.

Build 2:
- Translation fixed: Google free now uses the same address and token as MANHWA (translate.google.com), with two
  backup addresses; a refusal rests Google 30 s instead of the whole session; 4 requests at most at once.
- The reader shows how many paragraphs were not translated, the reason, and a retry button; a failed chapter is not
  cached. Settings has "Test translation" (one sentence per engine, with the answer or the error).
- Broken sources switch off by themselves after the check (every 12 hours and from the Sources screen) and back on
  when they work; only the reader's languages are used, Arabic and English by default. A section lists the
  switched-off ones with the reason and "keep it on".
