# Fit Tracker (Android) — poznámky na odovzdanie

Pokračovanie pre novú Claude Code reláciu. Najprv si prečítaj **Stav** a **Pozor**.

- Projekt: `C:\applikacie kubo\FitTrackerFix\FitTracker` (balík `sk.fit.tracker`, minSdk 26, targetSdk 34)
- Telefón používateľa: Android 16 (SDK 36), pripája sa cez USB
- Inšpirácia: appka **Lyfta** (screenshoty boli v Stiahnutých 2026-11-08). Preberáme nápady a funkcie,
  **nie** jej grafiku, texty ani vzhľad.

## Build a inštalácia

```bash
cd "/c/applikacie kubo/FitTrackerFix/FitTracker"
./gradlew.bat assembleDebug -q
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

- Na telefóne je **debug** build. `install -r` dáta zachová, appku nikdy neodinštalúvaj.
- Obrazovky okrem `MainActivity` nie sú exportované. Spúšťaj `am start -n sk.fit.tracker/.MainActivity`
  a preklikaj sa (`adb shell input tap`, súradnice screenshotu × 1,385).
- Dáta appky sa dajú čítať aj meniť cez `adb shell run-as sk.fit.tracker ...` (`shared_prefs/*.xml`).
  Pred úpravou súboru appku zastav (`am force-stop`), inak ho prepíše.

## Stav (2026-11-08)

Hotové, nainštalované a odskúšané na telefóne používateľa:

- [x] **1. Anatomická postava so svalmi** (`MuscleMapView`): realistický render spredu aj zozadu.
      Hlavné svaly sú načerveno, pomocné namodro a tieňovanie ostáva viditeľné.
- [x] **2. Katalóg cvikov ako mriežka kariet s fotkami** (`ExerciseCatalogActivity`): filter chipmi,
      ★ obľúbené (`FavoritesStore`), chip „🎯 Fokus“.
- [x] **3. Detail cviku** (`ExerciseDetailActivity`) so záložkami O cviku / História / Progres.
- [x] **Slovenský postup** pri všetkých 95 cvikoch (`instructions_sk` v `assets/exercise_media.json`).
- [x] **4. Onboarding**: výber partií pri prvom spustení (`OnboardingActivity`, `FocusStore`).
- [x] **Odpočet pauzy medzi sériami** (`RestTimer`, `RestTimerBar`, `RestTimerReceiver`).
- [x] **RPE** pri každej sérii (`SetEntry.rpe`).
- [x] **Režim appky** (`FeatureStore`): 🧭 Sprievodca pre začiatočníka / ⚡ Len zapisovanie pre skúseného
      + samostatné prepínače pomôcok v Profile.

Ďalšie možné nápady z Lyfty: kartička na zdieľanie po tréningu (šablóny), rutiny/plány tréningov.
Odhad 1RM, osobné rekordy a grafy objemu už appka má.

## Pozor

- **Git:** práca bola na vetve `lyfta-features`. Na pokyn používateľa bola 2026-11-08 zlúčená do `main`
  a pushnutá na `origin` (github.com/jakubmitura1-hub/FitTracker). Ďalšie commity a pushe robiť
  len na pokyn používateľa.
- **Testovanie na reálnych dátach.** Uloženie cviku v `DayDetailActivity` spustí meranie času tréningu
  (`sessionFirstSetAtMillis` → `WorkoutTimeStore.addMinutes` pri `onPause`). Testovacie uloženie
  tak pripíše minúty k dňu a nahradí odhad kalórií. Po teste treba vrátiť dáta aj zmazať tieto
  minúty (`FitTrackerWorkoutTime.xml`, kľúč s dátumom). Rovnako to platí pre zmeny sérií.
- Používateľ si fokus vybral sám 2026-11-08 (hrudník + ruky) a je v režime **Sprievodca**. Nemeň to.
- Povolenie notifikácií bolo udelené na pokyn používateľa.

## Ako jednotlivé časti fungujú

### Anatomická postava (`MuscleMapView`)
- Zdroj je 3D model **Z-Anatomy** (CC BY-SA 4.0, autori uvedení v `assets/muscle_map/LICENSE.txt`
  a malým textom pod postavou v detaile cviku). Render je z Blendru 4.2 (Workbench).
- Assets v `assets/muscle_map/`:
  - `body_front.webp` a `body_back.webp`: podklad 474×1190,
  - `muscles_front.bin` a `muscles_back.bin`: mapa svalov ("FTML" + int w + int h + 1 bajt na pixel,
    0 = nič, 1..17 = index do `GROUPS` + 1),
  - `muscles_back_deep.bin`: hlboká vrstva chrbta (romboidy, vzpriamovače), ktorú v modeli zakrýva trapéz
    a široký chrbtový sval. Pri vyššej priorite (hlavný > pomocný) presvitá cez ne.
- Farba svalu sa násobí jasom podkladu. Ťuk na sval (`setSelectable`) číta index z mapy
  a hľadá najbližší sval v okolí 10 px.
- Kľúče svalov = názvy z free-exercise-db (`chest`, `lats`, `middle back`…).
- **Prerender:** skripty sú v `tools/muscle_map/` (pozri `README.md` tam). Blender a Z-Anatomy boli stiahnuté
  do dočasného priečinka predošlej relácie, pri potrebe ich stiahni znova. `GROUPS` v `render_body.py`
  musí sedieť s `MuscleMapView.GROUPS`. Orezania (spodný chrbát v tvare bedrovej fascie, lýtka po členky)
  sú v `pack.py`.

### Ikony partií (legenda, kalendár, hlavičky)
- `Category.muscleIcon(ctx, key)` vracia realistickú ikonu z `MuscleMapView.categoryIcon()`: postavu
  s partiou načerveno, z pohľadu (spredu/zozadu), kde je partia viac vidieť. Ikona je priblížená
  na hornú (hlava–boky) alebo dolnú (boky–chodidlá) polovicu tela a cachovaná.
- Používa sa v legende a dňoch kalendára (`MainActivity`), v hlavičkách partií a náhľade
  (`DayDetailActivity`), v Progrese a ako zástupný obrázok pred stiahnutím fotky.
- Staré vektorové `ic_muscle_*` (`Category.muscleDrawableFor`) ostali len ako záloha, ak sa assets
  nenačítajú.

### Fotky a dáta cvikov (`ExerciseMedia`, `assets/exercise_media.json`)
- Pochádzajú z **free-exercise-db** (github.com/yuhonas/free-exercise-db, The Unlicense). Každý zo 95 cvikov
  katalógu je ručne priradený k záznamu v databáze.
- JSON: `id`, `images`, `primary`, `secondary`, `equipment`, `instructions` (EN) a `instructions_sk`
  (preklad 87 unikátných postupov; `Side_Bridge` nemal v databáze postup, slovenský je dopísaný).
  Pri preklade sú opravené chyby originálu (prehodený nádych/výdych pri decline DB press, hyperextenzii
  a russian twist; „36 palcov“ pri kľukoch).
- Fotky sa nesťahujú pri inštalácii, ale pri prvom zobrazení. Potom sú offline (`ExerciseImageCache`:
  max. 4 súbežné sťahovania, zmenšené). `ExerciseMedia.bindPhoto()` zobrazí siluetu a po stiahnutí fotku.
- Premenovanie cviku v katalógu si pamätá alias (`FitTrackerMediaAliases`), takže fotky ostanú.

### Detail cviku (`ExerciseDetailActivity`)
- `intent(ctx, meno, fromCatalog)`. Z katalógu ukazuje tlačidlá Upraviť a Zmazať: detail vráti
  `EXTRA_ACTION` a dialógy otvorí katalóg (`onActivityResult`).
- Z Progresu sa otvára cez „Detail ›“ na karte cviku a otvorí sa rovno na záložke Progres.
- Hero hodnota s trendom je spoločná s `ExerciseHistoryActivity` v `ProgressHero`.

### Onboarding a fokus (`OnboardingActivity`, `FocusStore`)
- Ukáže sa raz z `MainActivity` (`FocusStore.isOnboardingDone`). Pokračovať aj Preskočiť ho označia za hotový.
- Fokus sa používa v Profile (karta „🎯 Môj fokus“, Zmeniť), v Štatistikách (riadok „z fokusu tento
  týždeň chýba…“) a v katalógu (chip 🎯 Fokus podľa hlavných svalov cviku).

### Odpočet pauzy (`RestTimer`)
- Stav je v prefs `FitTrackerRestTimer` (`end_at`, `finished_at`, predvolená dĺžka, auto-štart).
- Koniec plánuje `AlarmManager.setExactAndAllowWhileIdle`, takže príde aj pri zhasnutom displeji.
  Na Androide 13+ treba **`USE_EXACT_ALARM`**. Bez neho Android 16 doručoval koniec s oneskorením.
  `SCHEDULE_EXACT_ALARM` ostáva len pre Android 12 (`maxSdkVersion 32`).
- Počas odpočtu beží tichá notifikácia s odpočtom (kanál `rest_timer_running`). Na konci príde
  notifikácia s vibráciou (`rest_timer_done`). Povolenie POST_NOTIFICATIONS sa pýta raz.
- Lišta `RestTimerBar` je v `DayDetailActivity` nad „Uložiť a zavrieť“ a v dialógu cviku, len pre dnešný deň.
  Automaticky sa spúšťa po „+ Pridať sériu“ (dá sa vypnúť ťukom na „1:30 ▾“).
- Zrušenie cez ✕ (`stop`) sa odlišuje od dobehnutia (`finished_at`), takže druhá lišta
  neukáže „Pauza skončila“.

### RPE (`SetEntry`)
- Formát série `váhaXopak:jednotka:typ:trvanie[:rpe]`. Piaty segment sa zapisuje **len ak je RPE zadané**,
  takže séria bez RPE má presne pôvodný formát (staré dáta aj zálohy sú kompatibilné).
- `toString()` pridáva „ @8“. História v detaile ukazuje „RPE ⌀“. „Minulý tréning“ a auto-fill RPE nepreberajú.
- V UI je RPE čip v každom riadku série. Je uložený ako tag `rowData[0]` (čísla série), aby sa nemenili
  indexy `rowData` v ostatnom kóde.

### Režim appky (`FeatureStore`)
- Prefs `FitTrackerFeatures`: `mode` (`guide` / `pro` / `custom`) a booleany `rest_timer`, `rpe`, `guides`,
  `focus`. Predvolene je všetko zapnuté (Sprievodca), aj pre existujúcich používateľov.
- Pri prvom spustení `OnboardingActivity` najprv ukáže výber režimu (`isModeChosen`). „Veď ma“ pokračuje
  výberom partií, „Len zapisovanie“ všetko vypne a onboarding ukončí.
- V Profile je karta „🎛️ Režim appky“ s dvomi predvoľbami a 4 prepínačmi. Ručná zmena prepínača
  zmení režim na `custom` („Vlastné“).
- Čo prepínače riadia:
  - `rest_timer`: lišta pauzy v dni aj v dialógu a jej auto-štart,
  - `rpe`: RPE čip v riadku série (vypnuté = len `GONE`, zadané RPE sa pri úprave zachová),
  - `guides`: karta „Postup“ v detaile cviku,
  - `focus`: riadok vo Štatistikách, chip 🎯 v katalógu a karta fokusu v Profile.
- Fotky, svaly na postave, katalóg, detail cviku a progres zostávajú v oboch režimoch.

### Záloha (`BackupManager`)
- Pribudli prefs `FitTrackerFavorites`, `FitTrackerMediaAliases`, `FitTrackerFocus` a `FitTrackerFeatures`, plus podpora `StringSet`
  (typ `"ss"`). Obľúbené a premenované cviky sa predtým do zálohy vôbec neukladali.
  Obnovenie zo zálohy nebolo na telefóne testované, aby sa neprepísali dáta.

## Opravy po kontrole kódu (2026-11-10, vetva `code-review-fixes`, na pokyn používateľa zlúčené do main a pushnuté na origin)

Kontrola kódu: https://claude.ai/artifact/Bn6NCSTnWj1Gn8PfQECdts. Hotové a nainštalované na telefóne:

- Dialógy v `DayDetailActivity` sa pri klávesnici zmenšia (`keepButtonsAboveKeyboard`), Uložiť je vždy vidieť.
- Bod 1: rozpísaný dialóg cviku prežije otočenie aj zabitie appky (`onSaveInstanceState`, `saveRow`/`restoreRow`).
  Stabilné ID v `res/values/ids.xml` pre Profil, Katalóg a deň.
- Bod 2: `BackupManager.restore` najprv celé overí (`InvalidBackupException`), potom zapíše; pred obnovou
  záchranná kópia `files/fittracker_pred_obnovou.json`, v Profile tlačidlo „Vrátiť dáta spred obnovy“.
  `ActivityStore.isDateKey` chráni kalendár pred pokazenými kľúčmi.
- Bod 3: `Exercise.totalVolume` ignoruje záporné (expander) a nulové váhy.
- Bod 4: predvyplnenie z minulého tréningu len do prázdneho riadku.
- Bod 5: osobný rekord = prvý tréning s najvyššou váhou, 0 kg nie je rekord (aj odznaky 🏆).
- Bod 6: úprava len poznámky v katalógu nemení cvik na „vlastný“.
- Bod 7: `ExerciseImageCache` kontroluje HTTP 200 + image/*, dekóduje pred uložením, pokazené súbory zmaže; WeakReference.
- Bod 8: Progres stavia staršie tréningy až po rozbalení, história sa parsuje raz.
- Bod 9: Java 17, AGP 8.2.2 (8.2.0 padal s JDK 21), R8 pre release, bez org.json a Jetifiera, Lint bez chýb.
- Drobnosti: 0 opakovaní / 0:00 sa neuloží, váha v úprave na 2 desatinné miesta, čas tréningu len dnes,
  kalórie z váhy platnej v ten deň (`ProfileStore.getWeightKgOn`), mazanie zdieľaných súborov z cache,
  odpočet pauzy po reštarte (`RestTimer.ensureScheduled`), odstránený mŕtvy kód a nepoužité písmo Cormorant.

Neurobené (treba rozhodnutie používateľa): podpisovanie release, upgrade AGP/targetSdk 35+ (edge-to-edge,
prediktívne Späť), rozdelenie `DayDetailActivity`, zjednotenie duplicitných pomocných metód, ikony svalov na pozadí.
