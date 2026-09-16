package sk.fit.tracker;

/**
 * Typ merania cvičenia — určuje, ktoré polia SetEntry majú zmysel a ako sa zobrazujú.
 * Uložený je vždy priamo v každom SetEntry (nie len v katalógu), takže staré záznamy
 * si natrvalo zachovajú svoj pôvodný typ aj keď sa katalógový default cviku neskôr zmení.
 */
public enum MetricType {
    /** Klasika: váha (kg/lbs/tehličky) × opakovania — napr. Benchpress. */
    WEIGHT_AND_REPS,
    /** Vlastná váha × opakovania, s voliteľnou pridanou záťažou v kg — napr. zhyby, kliky, bradlá. */
    BODYWEIGHT_AND_REPS,
    /** Časový cvik — trvanie v sekundách (zobrazené ako MM:SS) — napr. Plank, Vákuum. */
    DURATION;

    /** Krátky kód na serializáciu do SetEntry/ExerciseCatalog textového formátu. */
    public String code() {
        switch (this) {
            case BODYWEIGHT_AND_REPS: return "BW";
            case DURATION: return "DUR";
            default: return "WR";
        }
    }

    /** Opačná operácia ku code() — neznámy/chýbajúci/prázdny kód vždy spadne na WEIGHT_AND_REPS (spätná kompatibilita so starými dátami bez tohto poľa). */
    public static MetricType fromCode(String code) {
        if (code == null) return WEIGHT_AND_REPS;
        switch (code) {
            case "BW": return BODYWEIGHT_AND_REPS;
            case "DUR": return DURATION;
            default: return WEIGHT_AND_REPS;
        }
    }
}
