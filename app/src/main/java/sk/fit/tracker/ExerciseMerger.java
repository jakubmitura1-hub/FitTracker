package sk.fit.tracker;

import android.content.Context;

/**
 * Manuálne zlúčenie histórie dvoch cvikov (KROK 3) — keď si používateľ premenuje starý,
 * neodborne pomenovaný cvik (napr. "žehlička") na nový oficiálny názov z katalógu
 * (napr. "Hammer Strength Iso-Lateral Low Row"), toto prepíše všetky doterajšie odtrénované
 * série zo starého mena na nové vo všetkých dňoch a partiách — história (grafy, maximálky)
 * ostáva úplne neporušená, nič sa nemaže.
 */
public class ExerciseMerger {

    private ExerciseMerger() {}

    /**
     * @return počet dní, v ktorých došlo k premenovaniu (0 = neplatný vstup alebo cvik "oldName"
     *         sa nikde v histórii nenašiel).
     */
    public static int merge(Context ctx, String oldName, String newName) {
        if (oldName == null || newName == null) return 0;
        String from = oldName.trim();
        String to = newName.trim();
        if (from.isEmpty() || to.isEmpty() || from.equals(to)) return 0;

        int changedDays = ExerciseStore.renameExerciseEverywhere(ctx, from, to);

        // Starý názov už po zlúčení nemá čo ponúkať ako vlastný (custom) návrh v žiadnej partii —
        // ak tam bol, odstráň ho, nech v ponuke nezostáva mŕtva duplicita.
        for (Category c : Category.all()) ExerciseCatalog.removeCustom(ctx, c.key, from);
        for (Category c : Category.legacyAll()) ExerciseCatalog.removeCustom(ctx, c.key, from);

        return changedDays;
    }
}
