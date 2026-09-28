package chat.jaspr.atlas;

/**
 * Names that sound like the peoples who give them. Asterian names are built from Greek-like roots (a person's name, a
 * deme's), Dominion names are harsh, clipped Ashborn words or the Dominion's grim bookkeeping titles.
 */
final class Names {
    private Names() {}

    private static final String[] ROOTS = {"Kall", "Theo", "Dem", "Nik", "Phil", "Ari", "Eu", "Lys", "Mel", "Hes", "Kle", "Xen", "Anth", "Chrys", "Dor", "Hel",
        "Iph", "Kor", "Leu", "Myr", "Ne", "Pan", "Sost", "Tim", "Agath", "Arch", "Dio", "Hier", "Kyd", "Lamp", "Nausi", "Pyth", "Thras", "Zen", "Aris", "Ephi"};
    private static final String[] MALE = {"ias", "on", "os", "ander", "ippos", "okles", "ikos", "emon", "ides", "archos", "ion", "eus", "agoras", "stratos"};
    private static final String[] FEMALE = {"ia", "e", "eia", "ippe", "ea", "one", "ika", "is", "ara", "illa", "ope", "ina", "o"};
    private static final String[] DEME_A = {"Ela", "Oin", "Mel", "Kren", "Thym", "Anth", "Phy", "Kep", "Rham", "Dek", "Aph", "Myrr", "Sphe", "Lyk", "Kyth", "Olyn",
        "Tri", "Hyb", "Por", "Kal", "Ster", "Phal", "Hal", "Pal"};
    private static final String[] DEME_B = {"ia", "oe", "issa", "ai", "bra", "ele", "le", "isia", "nous", "eleia", "idnai", "inous", "ettos", "ai", "ethos", "thos",
        "korythos", "eia", "ara", "ion"};

    static String person(long h, boolean female) {
        String root = ROOTS[(int) ((h >>> 3) % ROOTS.length)];
        String end = female ? FEMALE[(int) ((h >>> 17) % FEMALE.length)] : MALE[(int) ((h >>> 17) % MALE.length)];
        if (root.endsWith("e") && (end.startsWith("e") || end.startsWith("i"))) root = root.substring(0, root.length() - 1);
        return root + end;
    }

    static String deme(long h) {
        return DEME_A[(int) ((h >>> 5) % DEME_A.length)] + DEME_B[(int) ((h >>> 21) % DEME_B.length)];
    }

    private static final String[] ASH_A = {"Gor", "Ug", "Khar", "Zug", "Mor", "Brak", "Ruk", "Skar", "Dush", "Grim", "Varg", "Lug", "Snag", "Hruk", "Bol", "Kaz"};
    private static final String[] ASH_B = {"ash", "gul", "nak", "rok", "ush", "zag", "mog", "dur", "gash", "ruk", "ak", "oth"};

    static String ashborn(long h) { return ASH_A[(int) ((h >>> 4) % ASH_A.length)] + ASH_B[(int) ((h >>> 19) % ASH_B.length)]; }

    private static final String[] CAMP_ADJ = {"Weeping", "Black", "Broken", "Iron", "Tally", "Silent", "Burnt", "Hollow", "Grey", "Bitter", "Last", "Cinder"};
    private static final String[] CAMP_NOUN = {"Pens", "Furrow", "Wheel", "Yard", "Pits", "Chain", "Kennels", "Stakes", "Ledger", "Ovens", "Ditch", "Mill"};

    static String site(long h, Plans.SiteKind kind) {
        if (kind.concord) {
            String d = deme(h);
            switch (kind) {
                case DEME: return d;
                case VILLA: return "the villa at " + d;
                case SANCTUARY: return "the sanctuary of " + d;
                case FARMSTEAD: return "the farms of " + d;
                case GYMNASIUM: return "the gymnasium of " + d;
                case LYCEUM_ANNEX: return "the school at " + d;
                case AQUEDUCT: return "the " + d + " aqueduct";
                case WATCH_OUTPOST: return "the watch post at " + d;
                default: return d;
            }
        }
        String name = "the " + CAMP_ADJ[(int) ((h >>> 6) % CAMP_ADJ.length)] + " " + CAMP_NOUN[(int) ((h >>> 23) % CAMP_NOUN.length)];
        switch (kind) {
            case FALLEN_PHAROS: return "the fallen Pharos of " + deme(h);
            case DEAD_OUTPOST: return "the ruined outpost of " + deme(h);
            case BATTLEFIELD: return "the field of " + deme(h);
            case RUINED_POLIS: return "the ruined quarter of " + deme(h);
            case WATCHTOWER: return "the tower of " + ashborn(h);
            case WAR_CAMP: return ashborn(h) + "'s war camp";
            default: return name;
        }
    }
}
