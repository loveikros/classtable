package cn.edu.swpu.offlinetimetable;

import java.time.LocalDate;

/** Nearby academic terms, ordered by likelihood with the current term first. */
public final class SemesterChoices {
    private SemesterChoices() { }

    public static String[] forDate(LocalDate today) {
        int year = today.getYear();
        int month = today.getMonthValue();
        int current = month == 1 ? (year - 1) * 2 + 1
                : year * 2 + (month >= 8 ? 1 : 0);
        String[] choices = new String[9];
        choices[0] = label(current);
        for (int distance = 1; distance <= 4; distance++) {
            choices[distance * 2 - 1] = label(current + distance);
            choices[distance * 2] = label(current - distance);
        }
        return choices;
    }

    private static String label(int term) {
        return (term / 2) + "年" + (term % 2 == 0 ? "春季学期" : "秋季学期");
    }
}
