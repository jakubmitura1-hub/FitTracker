package sk.fit.tracker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import static sk.fit.tracker.MetricType.BODYWEIGHT_AND_REPS;
import static sk.fit.tracker.MetricType.DURATION;
import static sk.fit.tracker.MetricType.WEIGHT_AND_REPS;

/**
 * Pevný, dvojjazyčný zoznam cvikov podľa čistej anatomickej svalovej partie (Category.key)
 * + vlastné cviky, ktoré si používateľ pridá (uložené natrvalo do SharedPreferences, obdoba
 * LocalStorage).
 *
 * Ukladá sa a zobrazuje vždy primárny slovenský názov (ExerciseDefinition.sk) — anglický
 * a alternatívny/slangový názov slúžia výhradne na vyhľadávanie v DayDetailActivity.
 *
 * Kategórie sú striktne oddelené podľa svalovej partie (žiadna väzba na tréningový deň) —
 * pozri Category.all(): chest, back, shoulders, biceps, triceps, leg, calves, abs.
 *
 * Každý cvik má priradený MetricType (klasika váha×opak. / vlastná váha×opak. / časový).
 * Toto je len UI HINT pre nové záznamy — skutočný typ merania konkrétnej odtrénovanej série
 * je natrvalo uložený v SetEntry, takže zmena metricType existujúceho katalógového cviku
 * (napr. pri tejto aktualizácii) nijako nemení už zapísanú históriu.
 */
public class ExerciseCatalog {

    private static final String PREFS_NAME = "FitTrackerCustomExercises";

    /** Oddeľovač medzi menom a kódom metricType vo vlastnom (custom) uloženom zázname, napr. "Môj cvik#BW". */
    private static final char CUSTOM_METRIC_SEPARATOR = '#';

    // ─── Hrudník (Chest) ────────────────────────────────────────────────────
    private static final ExerciseDefinition[] CHEST = {
            new ExerciseDefinition("Tlaky na rovnej lavičke (Benchpress)", "Barbell Bench Press", null),
            new ExerciseDefinition("Tlaky s jednoručkami na rovnej lavičke", "Flat Dumbbell Bench Press", null),
            new ExerciseDefinition("Tlaky na šikmej lavičke (hlavou hore)", "Incline Dumbbell Bench Press", null),
            new ExerciseDefinition("Tlaky na šikmej lavičke (hlavou dole)", "Decline Dumbbell Bench Press", null),
            new ExerciseDefinition("Tlaky na stroji na prsia", "Chest Press Machine", null),
            new ExerciseDefinition("Rozpažovanie s jednoručkami", "Dumbbell Flyes", null),
            new ExerciseDefinition("Rozpažovanie na protismerných kladkách", "Cable Crossover", null),
            new ExerciseDefinition("Peck-deck (stroj na prsia)", "Peck Deck", null),
            new ExerciseDefinition("Kľuky na bradlách (s dôrazom na prsia)", "Chest Dips", null),
            new ExerciseDefinition("Klasické kľuky", "Push-ups", null),
            // ─ Hammer Strength ─
            new ExerciseDefinition("Hammer Strength Iso-Lateral Bench Press", null,
                    "Tlaky na hrudník na pákovom stroji Hammer Strength", WEIGHT_AND_REPS),
            new ExerciseDefinition("Hammer Strength Iso-Lateral Incline Press", null,
                    "Tlaky na šikmej lavičke hore na pákovom stroji Hammer Strength", WEIGHT_AND_REPS),
            new ExerciseDefinition("Hammer Strength Iso-Lateral Decline Press", null,
                    "Tlaky hlavou dole na pákovom stroji Hammer Strength", WEIGHT_AND_REPS),
            new ExerciseDefinition("Hammer Strength Chest Press (Plate-Loaded)", null,
                    "Tlaky na prsia na pákovom stroji", WEIGHT_AND_REPS),
            // ─ Vlastná váha ─
            new ExerciseDefinition("Kliky na zemi (široké / úzke)", "Push-ups", null, BODYWEIGHT_AND_REPS),
            // ─ Doplnené ─
            new ExerciseDefinition("Tlaky na šikmej lavičke hore s veľkou činkou", "Incline Barbell Press", null),
            new ExerciseDefinition("Tlaky na šikmej lavičke dole s veľkou činkou", "Decline Barbell Press", null),
            new ExerciseDefinition("Pullover s jednoručkou", "Dumbbell Pullover", null),
    };

    // ─── Chrbát (Back) ──────────────────────────────────────────────────────
    private static final ExerciseDefinition[] BACK = {
            new ExerciseDefinition("Sťahovanie kladky zvrchu (široký úchop)", "Lat Pulldown (Wide Grip)", null),
            new ExerciseDefinition("Sťahovanie kladky zvrchu (úzky úchop / grape)", "Lat Pulldown (Close Grip)", "grape"),
            new ExerciseDefinition("Príťahy žehličky", "Straight-Arm Pulldown", null),
            new ExerciseDefinition("Príťahy veľkej činky v predklone", "Barbell Row", null),
            new ExerciseDefinition("Príťahy jednoručky v predklone", "Dumbbell Row", "Príťahy jednoručky v kľaku na lavičke"),
            new ExerciseDefinition("Vodorovné príťahy na spodnej kladke", "Seated Cable Row", null),
            new ExerciseDefinition("Vodorovné príťahy na stroji", "Machine Row", null),
            new ExerciseDefinition("Mŕtvy ťah", "Deadlift", null),
            new ExerciseDefinition("Hyperextenzia", "Hyperextension", "Back Extension"),
            new ExerciseDefinition("Zhyby na hrazde", "Pull-ups / Chin-ups", null),
            // ─ Hammer Strength ─
            new ExerciseDefinition("Hammer Strength Iso-Lateral Row", null,
                    "Príťahy na chrbát na pákovom stroji v sede", WEIGHT_AND_REPS),
            new ExerciseDefinition("Hammer Strength Iso-Lateral Front Pulldown", null,
                    "Sťahovanie kladky spredu Hammer Strength", WEIGHT_AND_REPS),
            new ExerciseDefinition("Hammer Strength Iso-Lateral High Row", null,
                    "Sťahovanie zhora pod uhlom Hammer Strength", WEIGHT_AND_REPS),
            new ExerciseDefinition("Hammer Strength Iso-Lateral Low Row", null,
                    "Žehlička — spodné príťahy na chrbát Hammer Strength", WEIGHT_AND_REPS),
            new ExerciseDefinition("Hammer Strength DY Row", null,
                    "Príťahy zhora Hammer Strength — štýl Dorian Yates", WEIGHT_AND_REPS),
            // ─ Vlastná váha ─
            new ExerciseDefinition("Zhyby na hrazde nadhmatom / podhmatom", "Pull-ups / Chin-ups", null, BODYWEIGHT_AND_REPS),
            // ─ Doplnené ─
            new ExerciseDefinition("Pullover na hornej kladke s lanom", "Cable Lat Pullover", null),
            new ExerciseDefinition("Príťahy T-osi", "T-Bar Row", null),
    };

    // ─── Ramená (Shoulders) ──────────────────────────────────────────────────
    private static final ExerciseDefinition[] SHOULDERS = {
            new ExerciseDefinition("Tlaky na ramená s jednoručkami (v sede)", "Seated Dumbbell Shoulder Press", null),
            new ExerciseDefinition("Tlaky na ramená na stroji", "Shoulder Press Machine", null),
            new ExerciseDefinition("Tlaky s veľkou činkou nad hlavu", "Barbell Overhead Press", "Military Press"),
            new ExerciseDefinition("Upažovanie s jednoručkami (stredné ramená)", "Dumbbell Lateral Raise", null),
            new ExerciseDefinition("Upažovanie na kladke", "Cable Lateral Raise", null),
            new ExerciseDefinition("Predpažovanie (predné ramená)", "Front Raise", null),
            new ExerciseDefinition("Opačný pec-deck / Upažovanie v predklone", "Reverse Pec Deck / Rear Delt Fly", null),
            new ExerciseDefinition("Rear delt (stroj/jednoručky)", "Rear Delt Row / Fly Machine", null),
            // ─ Hammer Strength ─
            new ExerciseDefinition("Hammer Strength Iso-Lateral Shoulder Press", null,
                    "Tlaky na ramená na pákovom stroji Hammer Strength", WEIGHT_AND_REPS),
            // ─ Doplnené ─
            new ExerciseDefinition("Face pull / Príťahy lana k tvári", "Face Pulls", null),
    };

    // ─── Trapézy (Trapezius) ─────────────────────────────────────────────────
    private static final ExerciseDefinition[] TRAPEZIUS = {
            new ExerciseDefinition("Krčenie ramien s jednoručkami (trapézy)", "Dumbbell Shrugs", null),
            new ExerciseDefinition("Krčenie ramien s veľkou činkou (trapézy)", "Barbell Shrugs", null),
    };

    // ─── Biceps ──────────────────────────────────────────────────────────────
    private static final ExerciseDefinition[] BICEPS = {
            new ExerciseDefinition("Bicepsový zdvih s veľkou činkou", "Barbell Curl", null),
            new ExerciseDefinition("Bicepsový zdvih s EZ činkou", "EZ-Bar Curl", null),
            new ExerciseDefinition("Bicepsový zdvih s jednoručkami (v stoji/v sede)", "Dumbbell Bicep Curl", null),
            new ExerciseDefinition("Kladivové zdvihy", "Hammer Curls", null),
            new ExerciseDefinition("Bicepsový zdvih na Scottovej lavičke", "Preacher Curl", null),
            new ExerciseDefinition("Bicepsový zdvih na kladke", "Cable Curl", null),
            // ─ Doplnené ─
            new ExerciseDefinition("Koncentrovaný bicepsový zdvih o koleno", "Concentration Curl", null),
    };

    // ─── Triceps ─────────────────────────────────────────────────────────────
    private static final ExerciseDefinition[] TRICEPS = {
            new ExerciseDefinition("Triceps lanko na kladke", "Triceps Pushdown (Rope)", null),
            new ExerciseDefinition("Sťahovanie hornej kladky na triceps (tyč)", "Triceps Pushdown (Bar)", null),
            new ExerciseDefinition("Jednoručka za hlavu", "Overhead Dumbbell Triceps Extension", null),
            new ExerciseDefinition("Tlaky na úzko", "Close-Grip Bench Press", "Tricepsový tlak s úzkym úchopom"),
            new ExerciseDefinition("Francúzsky tlak s EZ činkou", "French Press / Skull Crushers", null),
            new ExerciseDefinition("Tricepsové kľuky na bradlách", "Triceps Dips (Parallel Bars)", null),
            // Bench Dips — vlastná váha, žiadna činka/stroj
            new ExerciseDefinition("Tricepsové kľuky na lavičke", "Bench Dips", null, BODYWEIGHT_AND_REPS),
            // ─ Vlastná váha ─
            new ExerciseDefinition("Kľuky na bradlách (Dips)", "Dips", null, BODYWEIGHT_AND_REPS),
            // ─ Doplnené ─
            new ExerciseDefinition("Kickback s jednoručkou v predklone", "Dumbbell Kickback", null),
    };

    // ─── Nohy (Legs) — kvadricepsy, hamstringy, zadok ──────────────────────
    private static final ExerciseDefinition[] LEGS = {
            new ExerciseDefinition("Drepy s veľkou činkou", "Barbell Squats", null),
            new ExerciseDefinition("Legpress", "Leg Press", null),
            new ExerciseDefinition("Hacken drep", "Hack Squat", null),
            new ExerciseDefinition("Predkopávanie (stroj)", "Leg Extension", null),
            new ExerciseDefinition("Zakopávanie (stroj)", "Seated / Lying Leg Curl", null),
            new ExerciseDefinition("Bulharské drepy s jednoručkami", "Bulgarian Split Squats", null),
            new ExerciseDefinition("Rumunský mŕtvy ťah", "Romanian Deadlift", "RDL"),
            new ExerciseDefinition("Výpady s jednoručkami", "Dumbbell Lunges", null),
            // ─ Hammer Strength ─
            new ExerciseDefinition("Hammer Strength Leg Press", null, "Legpress Hammer Strength", WEIGHT_AND_REPS),
            new ExerciseDefinition("Hammer Strength Hack Squat", null, "Hacken drep Hammer Strength", WEIGHT_AND_REPS),
            // ─ Vlastná váha ─
            new ExerciseDefinition("Drepy s vlastnou váhou", "Bodyweight Squats", null, BODYWEIGHT_AND_REPS),
            // ─ Doplnené ─
            new ExerciseDefinition("Drepy s veľkou činkou vpredu", "Barbell Front Squat", null),
            new ExerciseDefinition("Hip thrust / Zdvíhanie panvy s činkou", "Hip Thrust", null),
            new ExerciseDefinition("Abdukcia / Snožovanie na stroji", "Hip Abduction", null),
            new ExerciseDefinition("Addukcia / Roznožovanie na stroji", "Hip Adduction", null),
            new ExerciseDefinition("Pohárový drep / Goblet drep", "Goblet Squat", null),
    };

    // ─── Lýtka (Calves) ──────────────────────────────────────────────────────
    private static final ExerciseDefinition[] CALVES = {
            new ExerciseDefinition("Výpony na lýtka v sede (stroj)", "Seated Calf Raise", null),
            new ExerciseDefinition("Výpony na lýtka v stoji (stroj)", "Standing Calf Raise", "Calf Extension"),
            new ExerciseDefinition("Výpony na lýtka na legpresse", "Calf Press on Leg Press", null),
            // ─ Hammer Strength ─
            new ExerciseDefinition("Hammer Strength Seated Calf Raise", null,
                    "Výpony na lýtka v sede Hammer Strength", WEIGHT_AND_REPS),
    };

    // ─── Brucho (Abs / Core) ────────────────────────────────────────────────
    private static final ExerciseDefinition[] ABS = {
            new ExerciseDefinition("Plank", "Plank", null, DURATION),
            new ExerciseDefinition("Bočný plank", "Side Plank", null, DURATION),
            new ExerciseDefinition("Brušáky", "Crunches", null, BODYWEIGHT_AND_REPS),
            new ExerciseDefinition("Zdvíhanie nôh vo visu", "Hanging Leg Raise", null, BODYWEIGHT_AND_REPS),
            new ExerciseDefinition("Zdvíhanie nôh v ľahu", "Lying Leg Raise", null, BODYWEIGHT_AND_REPS),
            new ExerciseDefinition("Ruské twisty", "Russian Twists", null, BODYWEIGHT_AND_REPS),
            new ExerciseDefinition("Vákuum", "Stomach Vacuum", null, DURATION),
            new ExerciseDefinition("Drep na kladke k bruchu", "Cable Crunch", null),
            // ─ Doplnené ─
            new ExerciseDefinition("Sklapovačky v ľahu", "V-Ups", null, BODYWEIGHT_AND_REPS),
            new ExerciseDefinition("Sed-ľah", "Sit-ups", "Brušáky (celý sed-ľah)", BODYWEIGHT_AND_REPS),
            new ExerciseDefinition("Horolezec", "Mountain Climbers", null, DURATION),
    };

    /** Pevne prednahraté cviky pre danú partiu (podľa Category.key), s SK/EN/alt názvami. */
    public static List<ExerciseDefinition> defaultDefinitionsFor(String categoryKey) {
        ExerciseDefinition[] src;
        switch (categoryKey) {
            case "chest": src = CHEST; break;
            case "back": src = BACK; break;
            case "shoulders": src = SHOULDERS; break;
            case "trapezius": src = TRAPEZIUS; break;
            case "biceps": src = BICEPS; break;
            case "triceps": src = TRICEPS; break;
            case "leg": src = LEGS; break;
            case "calves": src = CALVES; break;
            case "abs": src = ABS; break;
            default: src = new ExerciseDefinition[0];
        }
        return new ArrayList<>(Arrays.asList(src));
    }

    /** Len primárne slovenské názvy z pevného zoznamu (spätná kompatibilita). */
    public static List<String> defaultsFor(String categoryKey) {
        List<String> names = new ArrayList<>();
        for (ExerciseDefinition def : defaultDefinitionsFor(categoryKey)) names.add(def.sk);
        return names;
    }

    /**
     * Pevné cviky (s SK/EN/alt) + vlastné pridané cviky (len SK, bez prekladu) pre danú partiu.
     * Zabudované cviky, ktoré si používateľ cez katalógový editor "zmazal"/premenoval/preradil
     * (pozri HiddenDefaultsStore), sa tu už neponúkajú.
     */
    public static List<ExerciseDefinition> getDefinitionsFor(Context ctx, String categoryKey) {
        List<ExerciseDefinition> list = new ArrayList<>();
        for (ExerciseDefinition def : defaultDefinitionsFor(categoryKey)) {
            if (!HiddenDefaultsStore.isHidden(ctx, categoryKey, def.sk)) list.add(def);
        }
        list.addAll(getCustomDefinitions(ctx, categoryKey));
        return list;
    }

    /** Pevné cviky + vlastné pridané cviky pre danú partiu, bez duplicít (len slovenské názvy na zobrazenie). */
    public static List<String> getAll(Context ctx, String categoryKey) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (ExerciseDefinition def : getDefinitionsFor(ctx, categoryKey)) set.add(def.sk);
        return new ArrayList<>(set);
    }

    /** Len názvy vlastných (custom) cvikov pre danú partiu. */
    public static List<String> getCustom(Context ctx, String categoryKey) {
        List<String> names = new ArrayList<>();
        for (ExerciseDefinition def : getCustomDefinitions(ctx, categoryKey)) names.add(def.sk);
        return names;
    }

    /** Vlastné (custom) cviky pre danú partiu, vrátane ich uloženého typu merania. */
    public static List<ExerciseDefinition> getCustomDefinitions(Context ctx, String categoryKey) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(categoryKey, "");
        List<ExerciseDefinition> list = new ArrayList<>();
        if (!raw.isEmpty()) {
            for (String token : raw.split("\\|")) {
                ExerciseDefinition def = parseCustomToken(token);
                if (def != null) list.add(def);
            }
        }
        return list;
    }

    /** "Názov" (staré dáta, bez uvedeného typu -> WEIGHT_AND_REPS) alebo "Názov#KÓD". */
    private static ExerciseDefinition parseCustomToken(String token) {
        String t = token.trim();
        if (t.isEmpty()) return null;
        int sepIdx = t.lastIndexOf(CUSTOM_METRIC_SEPARATOR);
        String name = sepIdx >= 0 ? t.substring(0, sepIdx).trim() : t;
        if (name.isEmpty()) return null;
        MetricType type = sepIdx >= 0
                ? MetricType.fromCode(t.substring(sepIdx + 1).trim())
                : WEIGHT_AND_REPS;
        return new ExerciseDefinition(name, null, null, type);
    }

    private static String serializeCustomToken(String name, MetricType metricType) {
        return name + CUSTOM_METRIC_SEPARATOR + metricType.code();
    }

    /** Zachované pre spätnú kompatibilitu — pridá vlastný cvik s klasickým typom merania (váha × opakovania). */
    public static void addCustom(Context ctx, String categoryKey, String name) {
        addCustom(ctx, categoryKey, name, WEIGHT_AND_REPS);
    }

    /** Natrvalo pridá vlastný cvik (s daným typom merania) do zoznamu pre danú partiu (ak už neexistuje). */
    public static void addCustom(Context ctx, String categoryKey, String name, MetricType metricType) {
        // Odstráň všetky znaky, ktoré appka používa ako oddeľovače v textovej serializácii
        // (Exercise/SetEntry/ExerciseStore/vlastné cviky) — inak by názov cviku ako "Legpress; 45°"
        // alebo "Cvik#1" poškodil dáta.
        String safeName = name.trim()
                .replace(";", "")
                .replace(":", "")
                .replace(",", "")
                .replace("|", "")
                .replace(String.valueOf(CUSTOM_METRIC_SEPARATOR), "")
                .trim();
        if (safeName.isEmpty()) return;

        for (String existing : getAll(ctx, categoryKey)) {
            if (existing.equalsIgnoreCase(safeName)) return; // už existuje
        }

        List<String> tokens = new ArrayList<>();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(categoryKey, "");
        if (!raw.isEmpty()) {
            for (String t : raw.split("\\|")) if (!t.trim().isEmpty()) tokens.add(t.trim());
        }
        tokens.add(serializeCustomToken(safeName, metricType));
        prefs.edit().putString(categoryKey, String.join("|", tokens)).apply();
    }

    /**
     * Nájde kľúč partie (Category.key), pod ktorou je tento presný názov cviku uložený
     * v katalógu (pevný alebo vlastný) — spätné dohľadanie svalovej partie pre už zapísaný
     * cvik (napr. na zobrazenie správneho obrázku svalu v histórii, kde poznáme len meno
     * cviku, nie partiu, pod ktorou bol pôvodne pridaný). Null, ak sa nenašla zhoda (napr.
     * úplne voľne napísaný názov, ktorý sa odvtedy nezhoduje so žiadnou katalógovou položkou).
     */
    public static String findCategoryKeyForExercise(Context ctx, String exerciseName) {
        if (exerciseName == null) return null;
        for (Category c : Category.all()) {
            for (ExerciseDefinition def : getDefinitionsFor(ctx, c.key)) {
                if (def.sk.equals(exerciseName)) return c.key;
            }
        }
        for (Category c : Category.legacyAll()) {
            for (ExerciseDefinition def : getDefinitionsFor(ctx, c.key)) {
                if (def.sk.equals(exerciseName)) return c.key;
            }
        }
        return null;
    }

    /**
     * Odstráni vlastný (custom) cvik daného mena z partie, ak tam je — použité pri zlúčení
     * histórie (ExerciseMerger), aby po premenovaní starého cviku na nový nezostal duplicitný
     * záznam v ponuke. Nemá žiadny vplyv na už odtrénované/uložené série (tie sú nezávislé
     * v ExerciseStore) — iba na zoznam návrhov pri pridávaní nového cviku.
     */
    public static void removeCustom(Context ctx, String categoryKey, String name) {
        if (name == null) return;
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(categoryKey, "");
        if (raw.isEmpty()) return;

        List<String> remaining = new ArrayList<>();
        boolean removedAny = false;
        for (String token : raw.split("\\|")) {
            ExerciseDefinition def = parseCustomToken(token);
            if (def != null && def.sk.equalsIgnoreCase(name.trim())) {
                removedAny = true;
                continue;
            }
            if (!token.trim().isEmpty()) remaining.add(token.trim());
        }
        if (!removedAny) return;

        if (remaining.isEmpty()) {
            prefs.edit().remove(categoryKey).apply();
        } else {
            prefs.edit().putString(categoryKey, String.join("|", remaining)).apply();
        }
    }

    // ─── Katalógový editor (prehľad/vyhľadávanie/úprava/mazanie všetkých cvikov) ───────

    /** Jeden riadok v katalógovom editore — cvik spolu s partiou, do ktorej aktuálne patrí. */
    public static class CatalogEntry {
        public final String name;
        public final String categoryKey;
        public final MetricType metricType;
        public final String notes;
        public final boolean isDefault; // zabudovaný (natvrdo v kóde) vs. vlastný (custom)

        CatalogEntry(String name, String categoryKey, MetricType metricType, String notes, boolean isDefault) {
            this.name = name;
            this.categoryKey = categoryKey;
            this.metricType = metricType;
            this.notes = notes;
            this.isDefault = isDefault;
        }
    }

    /** Či je toto meno jedným zo zabudovaných (natvrdo v kóde) cvikov danej partie — bez ohľadu na skrytie. */
    private static boolean isBuiltInDefault(String categoryKey, String name) {
        for (ExerciseDefinition def : defaultDefinitionsFor(categoryKey)) {
            if (def.sk.equals(name)) return true;
        }
        return false;
    }

    /** Všetky cviky naprieč všetkými (čistými, netréningovo-kardio) partiami — pre katalógový editor. */
    public static List<CatalogEntry> getAllEntries(Context ctx) {
        List<CatalogEntry> result = new ArrayList<>();
        for (Category c : Category.all()) {
            if (!Category.GROUP_WORKOUT.equals(c.group) || "cardio".equals(c.key)) continue;
            for (ExerciseDefinition def : getDefinitionsFor(ctx, c.key)) {
                // Meno môže byť medzi zabudovanými aj vtedy, keď je práve skryté (nahradené
                // vlastnou upravenou verziou rovnakého mena) — vtedy sa reálne zobrazuje jeho
                // vlastná (custom) verzia, takže sa už nemá tváriť ako "predvolený".
                boolean isDefault = isBuiltInDefault(c.key, def.sk)
                        && !HiddenDefaultsStore.isHidden(ctx, c.key, def.sk);
                String notes = ExerciseNotesStore.getNote(ctx, def.sk);
                result.add(new CatalogEntry(def.sk, c.key, def.metricType, notes, isDefault));
            }
        }
        return result;
    }

    /**
     * Upraví cvik v katalógovom editore — funguje rovnako pre zabudované aj vlastné cviky:
     *  - ak sa mení názov, prepíše sa aj vo všetkých doterajších odtrénovaných sériách
     *    (cez ExerciseMerger — váhy/opakovania ostávajú nezmenené, len prepísané pod nové meno),
     *  - ak sa mení partia, cvik sa v katalógu presunie (história sa touto zmenou vôbec nedotkne,
     *    keďže nie je viazaná na partiu, len na meno),
     *  - zabudovaný pôvodný cvik sa "skryje" (HiddenDefaultsStore), aby sa neponúkal duplicitne
     *    popri svojej upravenej verzii; vlastný pôvodný cvik sa jednoducho odstráni.
     *
     * @return false, ak nové meno už v cieľovej partii existuje (kolízia) — vtedy sa nič nezmenilo.
     */
    public static boolean editEntry(Context ctx, CatalogEntry entry, String newName, String newCategoryKey, String newNotes) {
        String trimmedName = newName == null ? "" : newName.trim();
        if (trimmedName.isEmpty()) return false;

        boolean nameOrCategoryChanged = !trimmedName.equals(entry.name) || !newCategoryKey.equals(entry.categoryKey);
        if (nameOrCategoryChanged) {
            for (String existing : getAll(ctx, newCategoryKey)) {
                if (existing.equalsIgnoreCase(trimmedName)) return false; // kolízia s existujúcim cvikom
            }
        }

        if (entry.isDefault) {
            HiddenDefaultsStore.hide(ctx, entry.categoryKey, entry.name);
        } else {
            removeCustom(ctx, entry.categoryKey, entry.name);
        }

        addCustom(ctx, newCategoryKey, trimmedName, entry.metricType);

        if (!trimmedName.equals(entry.name)) {
            ExerciseStore.renameExerciseEverywhere(ctx, entry.name, trimmedName);
            ExerciseNotesStore.rename(ctx, entry.name, trimmedName);
            FavoritesStore.rename(ctx, entry.name, trimmedName);
            ExerciseMedia.rename(ctx, entry.name, trimmedName);
        }
        ExerciseNotesStore.setNote(ctx, trimmedName, newNotes);
        return true;
    }

    /**
     * Zmaže cvik z katalógu (prestane sa ponúkať pri pridávaní nového cvičenia). NIKDY nemaže
     * už odtrénované série v histórii — tie ostávajú naďalej viditeľné v Progrese pod pôvodným
     * menom, presne ako keď je cvik len "starý/nepoužívaný", nie zabudnutý.
     */
    public static void deleteEntry(Context ctx, CatalogEntry entry) {
        if (entry.isDefault) {
            HiddenDefaultsStore.hide(ctx, entry.categoryKey, entry.name);
        } else {
            removeCustom(ctx, entry.categoryKey, entry.name);
        }
    }
}
