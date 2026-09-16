package sk.fit.tracker;

import java.util.ArrayList;
import java.util.List;

public class Category {

    public static final String GROUP_WORKOUT = "workout";

    public final String key;
    public final String label;
    public final int color;
    public final String group;

    public Category(String key, String label, int color, String group) {
        this.key = key;
        this.label = label;
        this.color = color;
        this.group = group;
    }

    /**
     * Čisté, anatomicky nezávislé svalové partie — bez väzby na akýkoľvek konkrétny
     * tréningový split. Používateľ si z nich sám kombinuje ľubovoľný tréningový deň.
     */
    public static List<Category> all() {
        List<Category> list = new ArrayList<>();
        list.add(new Category("chest",     "Hrudník",  0xFF4FB6A8, GROUP_WORKOUT)); // Chest
        list.add(new Category("back",      "Chrbát",   0xFF5B9BD5, GROUP_WORKOUT)); // Back
        list.add(new Category("shoulders", "Ramená",   0xFF7A8FE0, GROUP_WORKOUT)); // Shoulders
        list.add(new Category("biceps",    "Biceps",   0xFF9B7FE0, GROUP_WORKOUT)); // Biceps
        list.add(new Category("triceps",   "Triceps",  0xFFC97FE0, GROUP_WORKOUT)); // Triceps
        list.add(new Category("trapezius", "Trapézy",  0xFFB08D57, GROUP_WORKOUT)); // Trapezius
        // Kľúč zámerne "leg" (nie "legs") — "legs" už historicky patrí legacy kombinovanej
        // kategórii "Nohy + Ramená" nižšie; kolízia kľúčov by pomiešala staré ramenné cviky
        // do novej čistej partie Nohy pri otvorení starého dňa.
        list.add(new Category("leg",       "Nohy",     0xFFE0847A, GROUP_WORKOUT)); // Legs
        list.add(new Category("calves",    "Lýtka",    0xFFE0B24C, GROUP_WORKOUT)); // Calves
        list.add(new Category("abs",       "Brucho",   0xFF6FCF97, GROUP_WORKOUT)); // Abs / Core
        list.add(new Category("cardio",    "Kardio",   0xFFDE7B4C, GROUP_WORKOUT));
        return list;
    }

    /**
     * Pôvodné (pred rozdelením na čisté partie) kombinované kategórie — "legacy". Už sa
     * neponúkajú pri pridávaní nového cvičenia (výber partie v DayDetailActivity), ale
     * ak má konkrétny deň pod niektorým z týchto kľúčov už uložené cviky, DayDetailActivity
     * ich naďalej načíta a zobrazí (inak by pri otvorení/uložení takého dňa nová logika
     * "aktívna partia = má cvik" tento starý záznam v kalendári potichu odznačila).
     */
    public static List<Category> legacyAll() {
        List<Category> list = new ArrayList<>();
        list.add(new Category("back_triceps", "Chrbát + Triceps", 0xFF5B9BD5, GROUP_WORKOUT));
        list.add(new Category("chest_biceps", "Prsia + Biceps",   0xFF4FB6A8, GROUP_WORKOUT));
        list.add(new Category("legs",         "Nohy + Ramená",    0xFF7A8FE0, GROUP_WORKOUT));
        return list;
    }

    public static Category byKey(String key) {
        for (Category c : all()) if (c.key.equals(key)) return c;
        for (Category c : legacyAll()) if (c.key.equals(key)) return c;
        return null;
    }

    /**
     * Vektorový diagram siluety tela so zvýraznenou precvičovanou partiou pre daný Category.key,
     * alebo 0 ak pre túto partiu diagram neexistuje (napr. "cardio"). Zdieľané medzi MainActivity
     * (kalendár), DayDetailActivity (hlavičky partií) a ExerciseHistoryActivity (karty cvikov).
     *
     * Pokrýva aj tri legacy kombinované kľúče (spred rozdelenia na čisté partie) — staré dni
     * uložené pod nimi musia diagram naďalej zobrazovať rovnako ako predtým.
     */
    public static int muscleDrawableFor(String categoryKey) {
        if (categoryKey == null) return 0;
        switch (categoryKey) {
            case "chest": return R.drawable.ic_muscle_chest;
            case "back": return R.drawable.ic_muscle_back;
            case "shoulders": return R.drawable.ic_muscle_shoulders;
            case "biceps": return R.drawable.ic_muscle_biceps;
            case "triceps": return R.drawable.ic_muscle_triceps;
            case "trapezius": return R.drawable.ic_muscle_trapezius;
            case "leg": return R.drawable.ic_muscle_leg;
            case "calves": return R.drawable.ic_muscle_calves;
            case "abs": return R.drawable.ic_muscle_abs;
            // Legacy kombinované kategórie (spred rozdelenia na čisté partie) — pozri legacyAll().
            case "back_triceps": return R.drawable.ic_muscle_back_triceps;
            case "chest_biceps": return R.drawable.ic_muscle_chest_biceps;
            case "legs": return R.drawable.ic_muscle_legs_shoulders;
            default: return 0;
        }
    }
}
