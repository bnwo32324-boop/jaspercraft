package chat.jaspr.ruins;

/** Ancient-sounding place names, a pure function of a hash. */
final class Names {
    private Names() {}

    private static final String[] START = {"Ael", "Ash", "Cal", "Dar", "Ery", "Hal", "Ith", "Kar", "Lor", "Mor", "Nem", "Or",
        "Pel", "Quel", "Ren", "Sar", "Tal", "Ul", "Val", "Xan", "Yr", "Zeph", "Bel", "Cyr", "Ost", "Thal"};
    private static final String[] MIDDLE = {"", "a", "e", "i", "o", "u", "ar", "en", "is", "or", "an", "yr"};
    private static final String[] END = {"ethra", "anthe", "oris", "umbra", "adel", "ion", "ara", "essa", "ios", "athos",
        "urae", "ennon", "ith", "ovar", "amar", "esh"};
    private static final String[] SITE_ADJECTIVE = {"Sunken", "Mossbound", "Forgotten", "Shattered", "Silent", "Verdant",
        "Hollow", "Weathered", "Crumbling", "Overgrown", "Nameless", "Ancient"};

    static String city(long h) {
        return START[(int) ((h >>> 3) % START.length)] + MIDDLE[(int) ((h >>> 13) % MIDDLE.length)] + END[(int) ((h >>> 23) % END.length)];
    }

    static String site(long h, String noun) {
        return "The " + SITE_ADJECTIVE[(int) ((h >>> 31) % SITE_ADJECTIVE.length)] + " " + noun;
    }
}
