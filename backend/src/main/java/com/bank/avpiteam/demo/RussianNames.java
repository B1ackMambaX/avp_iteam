package com.bank.avpiteam.demo;

/**
 * Родительный падеж ФИО для демо-назначений вида "в пользу Иванова Ивана Ивановича".
 * Упрощённые правила - достаточно для имён из справочника генератора.
 */
final class RussianNames {

    private RussianNames() {
    }

    static String genitive(String lastName, String firstName, String middleName, boolean female) {
        return String.join(" ",
                female ? femaleLastName(lastName) : lastName + "а",
                female ? femaleFirstName(firstName) : maleFirstName(firstName),
                female ? replaceEnd(middleName, "на", "ны") : middleName + "а");
    }

    // Иванова -> Ивановой, Ильина -> Ильиной
    private static String femaleLastName(String name) {
        return replaceEnd(name, "а", "ой");
    }

    // Иван -> Ивана, Сергей -> Сергея, Игорь -> Игоря, Илья -> Ильи
    private static String maleFirstName(String name) {
        if (name.endsWith("й") || name.endsWith("ь")) {
            return name.substring(0, name.length() - 1) + "я";
        }
        if (name.endsWith("я")) {
            return name.substring(0, name.length() - 1) + "и";
        }
        return name + "а";
    }

    // Мария -> Марии, Дарья -> Дарьи, Ольга -> Ольги, Анна -> Анны, Любовь -> Любови
    private static String femaleFirstName(String name) {
        if (name.endsWith("ия")) {
            return replaceEnd(name, "ия", "ии");
        }
        if (name.endsWith("я") || name.endsWith("ь")) {
            return name.substring(0, name.length() - 1) + "и";
        }
        if (name.endsWith("а")) {
            char before = name.charAt(name.length() - 2);
            return name.substring(0, name.length() - 1) + ("гкхжшщч".indexOf(before) >= 0 ? "и" : "ы");
        }
        return name;
    }

    private static String replaceEnd(String value, String suffix, String replacement) {
        return value.endsWith(suffix)
                ? value.substring(0, value.length() - suffix.length()) + replacement
                : value;
    }
}
