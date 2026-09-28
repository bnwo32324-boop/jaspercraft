package chat.jaspr.atlas;

/**
 * Carved and painted words for signs (four lines of at most 15 characters): Asterian prayers, epitaphs, statue
 * plaques, grave markers and road mottoes, and the Dominion's slogans, edicts, gibbet notices and tallies.
 */
final class Texts {
    private Texts() {}

    private static String pick(long h, String[] a) { return a[(int) Math.floorMod(Hash.mix(h) >>> 1, a.length)]; }

    static final String[] SHRINE = {
        "LAMP OF THEANO\nKEEP THE ROAD\nKEEP THE\nSTRANGER",
        "WHAT IS SHARED\nIS NOT LESS.\n~ THE FIRST\nTEACHING",
        "HERE A FLAME\nFOR THOSE\nBEYOND THE\nLINE",
        "SING, THAT\nTHE STAR MAY\nREMEMBER ITS\nOTHER HALF",
        "MANY VOICES,\nONE CONCORD.\nNO ONE VOICE\nOVER ALL",
        "GIVE WATER,\nGIVE BREAD,\nGIVE THE ROAD\nITS LIGHT",
        "FOR THE BOUND\nWHO WAIT.\nWE HAVE NOT\nFORGOTTEN",
    };
    static String shrine(long h) { return pick(h, SHRINE); }

    static final String[] EPITAPH = {
        "KLEOBIS\nSON OF MELON\nKEEPER OF\nBEES. RESTS",
        "PHILINNA\nWHO TAUGHT\nCHILDREN THEIR\nLETTERS",
        "DORION OF\nTHE LINE. HE\nWENT EAST AND\nDID NOT RETURN",
        "HERE ARISTE\nWHO SANG AT\nEVERY FEAST.\nBE LOUD",
        "EUDOXOS THE\nLUMENWRIGHT.\nHIS LAMPS\nSTILL BURN",
        "MNESARETE:\nSHE KEPT THE\nSTRANGER'S\nCUP FULL",
        "NIKIAS AND\nHIS WIFE\nTHEANO, WHO\nLOVED LONG",
        "THE CHILDREN\nOF PELLENE\nWHO CAME\nWEST ALONE",
    };
    static String epitaph(long h) { return pick(h, EPITAPH); }

    static final String[] STATUE = {
        "THEANO\nWHO CARRIED\nTHE LIGHT\nWEST",
        "ARISTOKLES\nFIRST ARCHON\nOF THE\nCONCORD",
        "THE UNKNOWN\nHOPLITE OF\nTHE LONG\nWATCH",
        "HYPATIA OF\nTHE LYCEUM\nWHO COUNTED\nTHE STARS",
        "KLEOBOULOS\nWHO FOUND\nTHE FIRST\nLUMEN",
        "THE STRANGER\nWHO CAME\nBEFORE. WE\nKEEP HER NAME",
    };
    static String statue(long h) { return pick(h, STATUE); }

    static final String[] MOTTO = {"XENIA", "HOMONOIA", "KEEP THE LAMP", "WELCOME", "PAIDEIA", "SOPHROSYNE", "THE ROAD IS OURS"};
    static String motto(long h) { return pick(h, MOTTO); }

    static final String[] GRAVE = {
        "AGATHON\nOF THE LINE\nYL 1204", "UNNAMED\nTALOS-KEEPER\nYL 1133", "LYSIS, 17\nFIRST WATCH\nYL 1301",
        "MELITTA\nFIELD MEDIC\nYL 1276", "THE FOUR\nOF PHAROS IX\nYL 1388", "ZENON\nWHO WENT\nFOR HIS SON",
        "ARCHIPPE\nCAPTAIN\nYL 1340", "NAME LOST\nTO THE ASH\nKNOWN TO US",
    };
    static String grave(long h) { return pick(h, GRAVE); }

    static final String[] DOMINION_SLOGAN = {
        "ONE NOTE.\nONE WILL.\nONE\nDOMINION.",
        "THE HEART\nBURNS FOR\nALL. KNEEL\nAND BE WARM.",
        "OBEY AND\nBE ORDERED.\nRESIST AND\nBE ASH.",
        "THE CONCORD\nIS NOISE.\nTHE PYRARCH\nIS SILENCE.",
        "EVERY HAND\nHAS ITS\nTASK. EVERY\nTASK, ITS END",
        "THE LIGHT\nTHAT IS\nSHARED IS\nLIGHT LOST.",
    };
    static String dominionSlogan(long h) { return pick(h, DOMINION_SLOGAN); }

    static final String[] EDICT = {
        "EDICT IV:\nNO CITIZEN\nSHALL SPEAK\nUNBIDDEN.",
        "EDICT IX:\nBOOKS ARE\nNOISE. BURN\nTHE NOISE.",
        "EDICT XII:\nTHE WEST\nIS A RUMOUR.\nFORGET IT.",
        "EDICT XVII:\nNAMES ARE\nPRIVATE\nPROPERTY.",
        "EDICT XXI:\nTHE MAGIS-\nTRATE HEARS\nALL SILENCE.",
        "EDICT XXX:\nHOPE IS A\nCRIME AGAINST\nTHE ORDER.",
    };
    static String edict(long h) { return pick(h, EDICT); }

    static final String[] GIBBET = {
        "DESERTER.\nHE RAN\nTOWARD THE\nLAMPS.", "THIEF OF\nBREAD FOR\nTHE BOUND.", "SHE TAUGHT\nLETTERS TO\nA SLAVE.",
        "HE SANG.", "SPY OF THE\nLINE. LOOK\nWELL,\nSTRANGER.",
    };
    static String gibbet(long h) { return pick(h, GIBBET); }

    static final String[] STILLER_PRAYER = {
        "NOTHING ENDS\nHERE. NOTHING\nBEGINS.\nBE STILL.",
        "THE MOTHER\nOF STILLNESS\nKEEPS YOU\nFROM LOSS.",
        "WHY DIE\nWHEN YOU\nMAY WAIT\nFOREVER?",
    };
    static String stillerPrayer(long h) { return pick(h, STILLER_PRAYER); }

    static final String[] TALLY = {
        "TODAY: IIII\nIIII IIII II\nLOST: II\nMORE TOMORROW",
        "SHIFT XIV\nORE: LOW\nHANDS: FEWER\nWHIP: MORE",
        "QUOTA MET.\nQUOTA\nRAISED.",
    };
    static String tally(long h) { return pick(h, TALLY); }
}
