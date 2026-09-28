package chat.jaspr.atlas;

/**
 * What ordinary people say: a greeting by calling (a scribe talks like a scribe, a hoplite like a hoplite), coloured by
 * the city they live in, by how the war is going (they have heard about every fallen crown), and by the stranger's
 * standing. Each person picks from their lines by a hash of who they are, so the same person says the same kind of thing
 * and two neighbours rarely say the same.
 */
final class Voices {
    private Voices() {}

    private static String pick(long h, int salt, String... lines) { return lines[(int) Math.floorMod(Hash.mix(h ^ salt * 0x9E37L), lines.length)]; }

    /** One line on how the war stands, for anyone who asks. */
    static String progress(State s) {
        if (s.victory) return "Atlas is free. The ash has stopped. Nobody quite believes it yet.";
        int n = s.wardsDark();
        if (n == 4) return "All four crowns are broken. Only the Pyrarch remains, in Anthrakion.";
        if (n > 0) return n + " of the four Ash-Crowned have fallen. The east is changing.";
        if (s.bossesFallen.size() > 0) return "Word from the Line: some of the Dominion's captains have fallen. The Ash-Crowned still stand.";
        return "The Line holds. The Ash-Crowned hold everything beyond it.";
    }

    static String greeting(String role, Realm.Place city, State s, long h, int standing) {
        if (role == null) role = "";
        if (standing <= Reputation.HOSTILE) return pick(h, 1, "(They back away from you, eyes on your hands.) Leave us alone.", "Guards! No... just go. Please.", "(They will not look at you.)");
        if (standing <= Reputation.BARRED) return pick(h, 2, "We've heard what you did. Say what you want and go.", "(A cool nod.) Stranger.", "I'll talk. I won't trade. Not with you.");
        String line = byRole(role, h, s);
        if (s.victory && Hash.unit(Hash.mix(h ^ 77)) < 0.5)
            line = pick(h, 3, "Did you see the sky this morning? Clear to the east. Clear! I cried into my porridge.", "My cousin in the Line says the lamps can rest. Imagine that. Lamps, resting.",
                "They're reading the names at the Gate of Strangers. Yours among them, I heard. Is it true?", "I'm going east next spring. To Pellene. My grandmother was born there.") + "\n\n" + line;
        if (standing >= 100 && Hash.unit(Hash.mix(h ^ 78)) < 0.5) line = pick(h, 4, "You're the stranger they talk about! Welcome, welcome.", "Hero of the Concord, in my shop. Wait till I tell my sister.", "(They bow, a little awkwardly.)") + "\n\n" + line;
        return line;
    }

    private static String byRole(String role, long h, State s) {
        switch (role) {
            case "farmer": case "shepherd": case "gardener": case "miller": case "beekeeper":
                return pick(h, 10, "Good soil, this. The Star's soil, my grandfather called it. Everything grows if you ask it nicely.",
                    "Rain's due. The Lampwall people say the ash clouds are thinner this year. Maybe.",
                    "Mind the barley! Walk on the path. Thank you.",
                    "The bees are restless when the wind comes from the east. They smell the ash before we do.",
                    "We send a cart of grain to the Line every month. Somebody has to feed the lamps' keepers.");
            case "water_carrier":
                return pick(h, 11, "The aqueduct sings when it's full. Listen: you can hear it from here.", "Water for the Stranger's Cup? It's always full. Somebody has to keep it full. That's me.");
            case "householder": case "elder":
                return pick(h, 12, "Come in out of the sun. We don't have much, but there's bread.",
                    "My son is on the Line. Pharos Twelve. He writes every tenday. His letters are mostly complaints about the food.",
                    "In my day, strangers were a story you told children. Now look.",
                    "Three hundred years we've kept the lamps lit. You'd think we'd be tired. We are.",
                    "We keep a lamp in the window for the Bound. Every house does. Did you know that?");
            case "child":
                return pick(h, 13, "Are you really from another world? Do you have a Star there?", "I'm going to be a Talos when I grow up. Mother says that's not how it works.",
                    "Race you to the fountain!", "Is it true the ash doesn't know you? What does it feel like, not being known?");
            case "traveller": case "pilgrim":
                return pick(h, 14, "On the road to Hieranthe. The heliodromes are faster, but you miss the olive groves.", "I'm walking the Royal Road end to end. Well, to the Last Watch. Nobody walks it to the end.");
            case "priest": case "worshipper": case "mourner":
                return pick(h, 15, "Light keep you, stranger. The Hearthstar remembers everyone who stands near it.",
                    "We pray for the Bound every evening. It helps us more than it helps them, I think.",
                    "The temple is open to all. Theano turned no one away; neither do we.");
            case "healer": case "patient": case "apothecary":
                return pick(h, 16, "Sit, if you're hurt. No? Then don't stand in the doorway, you're blocking the light.",
                    "The ash-fever came back to Hieranthe last winter. Three children. We saved two.",
                    "Every healer learns the Hymn of Passage first. Before the herbs, before the knives. That tells you something.");
            case "hoplite": case "trainer": case "signaller": case "quartermaster":
                if (s.bossesFallen.containsKey("kallias")) return pick(h, 17, "The Pylon's open. Open! I stood a watch on the Lampwall for nine years looking at that black gate.", "They say the Marshal asked about his shield at the end. Is that true?");
                return pick(h, 18, "Keep your shield on your left and your eyes east. That's the whole of the Line, stranger.",
                    "Nine years on the Lampwall. Pharos Six. The lamp never went out on my watch.",
                    "Talos don't sleep. We do. That's the whole problem with the Line.",
                    "You're going east? Take more bread than you think you need.");
            case "athlete":
                return pick(h, 19, "Twelve laps before breakfast! Join me? No? Your loss.", "The Games are in spring. Strangers can compete, you know. Nobody's ever won, but they can compete.");
            case "student": case "teacher": case "philosopher": case "poet": case "reader": case "copyist": case "librarian": case "scribe": case "astronomer":
                return pick(h, 20, "I'm reading On Harmony. The Pyrarch wrote it, before. It's... convincing. That's what scares me.",
                    "Have you been to the Great Library? The Register of the Lost alone fills a wall.",
                    "My teacher says a question is worth two answers. My teacher has never had to pass an examination.",
                    "The stars are wrong over the Plateau. The ash bends the light. We measure it every night anyway.",
                    "I'm copying the Litany of the Unlit for the Library. It's horrible. The Dominion's grammar is flawless.",
                    "If you find anything written in the east, bring it here. Anything. Even a shopping list.");
            case "mechanic": case "lumenwright": case "engineer": case "smith":
                return pick(h, 21, "Careful, that's a live lumen coil. It won't hurt you. Probably.",
                    "Lampsa's foundries run on the Hearthstar's song. Beyond the Line our tools just go quiet. It's eerie.",
                    "Bring me ash-iron from the east and I'll rework it. Takes three days and a lot of singing.",
                    "The Talos are older than my grandmother. We don't make them anymore. We don't quite know how.");
            case "bath_keeper": case "bather": case "actor":
                return pick(h, 22, "The water's warm. The Hearthstar heats it, you know, through the old channels.", "We're putting on the Fall of the Heliotheion again. Somebody always cries at the end. Usually me.");
            case "cartographer":
                return pick(h, 23, "The maps east of the Line are three hundred years old. Everything's moved. Bring me new ones.", "Astreion's at the centre of every map we make. Vanity, mostly.");
            case "host": case "keeper_of_return":
                return pick(h, 24, "Eat. You look like the road. Eat first, then talk.", "Every bed here has a name on it. Some are still waiting for theirs.");
            case "villa_owner":
                return pick(h, 25, "My family has kept these olives for nine generations. We send the oil to the Line.", "Walk anywhere you like. Just don't bruise the vines.");
            case "settler": case "kelani_herder": case "freed":
                return pick(h, 26, "Free. I keep saying it. Free. It doesn't wear out.",
                    "We're going to plant here. In the ash. Somebody said nothing grows in ash. Somebody was wrong.",
                    "You were one of them, weren't you? Who broke the chains? Thank you. That's all. Thank you.",
                    "The Kelani will have their grass back. Give it ten years. Give it five.");
            case "weaver": case "potter": case "grocer": case "baker":
                return pick(h, 27, "Fresh this morning! Well, this afternoon.", "Everything's dearer since the Lampsa road flooded. Don't tell the Archon I said so.",
                    "My mother sold from this stall. And hers. There's a mark on the counter from every one of us.");
            case "archon":
                return pick(h, 28, "The Synedrion sits at the bell. Twelve chairs, and one empty for the stranger. Would you like to see it?");
            default:
                return pick(h, 29, "Light keep you, stranger.", "Welcome to the Concord. Have you eaten?", "You're the one from elsewhere. Welcome.", "Mind the step. Everyone trips on that step.");
        }
    }

    /** "What news?": what people have heard, from the shared state of the war. */
    static String news(State s, long h) {
        if (s.victory) return pick(h, 40, "The Pyrarch is dead. The Heart is broken. The Rekindlers' names are cut into the plinth at the Gate of Strangers. I've gone twice to read them.",
            "The Chained Choir came home. All of them. They sang in the agora last night, a hundred voices, and not one of them sang the same note.");
        StringBuilder b = new StringBuilder();
        if (s.bossesFallen.containsKey("kallias")) b.append("The Marshal has fallen and the Pylon stands open. ");
        if (s.bossesFallen.containsKey("melaina")) b.append("The Weald is quiet: the stilled have been let go. ");
        if (s.bossesFallen.containsKey("daidaros")) b.append("The Great Engine has stopped. You can hear the silence from Lampsa, they say. ");
        if (s.bossesFallen.containsKey("keleos")) b.append("Pellene is talking again, all at once. ");
        int rescued = s.captivesRescued.size();
        if (rescued > 0) b.append(rescued == 1 ? "One of the named captives has come home to the House of Return. " : rescued + " of the named captives have come home to the House of Return. ");
        if (s.campsFreed.size() > 0) b.append(s.campsFreed.size()).append(s.campsFreed.size() == 1 ? " labour camp has" : " labour camps have").append(" been broken open. ");
        if (b.length() > 0) return b.toString().trim();
        return pick(h, 41, "Nothing new from the Line. That's good news, on the Line.",
            "A Pharos went dark in the north last month. They relit it in three days. Three days of dark, though.",
            "They say there's a stranger in Atlas. Oh. That's you, isn't it.",
            "Grain's up. Lampsa's quarrelling with Hieranthe about the aqueduct again. The usual.");
    }

    /** "Tell me about this place." */
    static String place(Realm.Place city, long h) {
        if (city == null) return pick(h, 50, "This is the deme country: villages, farms and villas from the White City to the Line. Every field has a name, and most have a shrine.",
            "The Royal Road runs from the Gate of Strangers to the Lampwall. Travellers used to walk it end to end, before the Withdrawal.");
        switch (city) {
            case ASTREION: return pick(h, 51, "Astreion, the White City. The Synedrion sits here, the Great Library, the Stoa of Shields, the Hearth of Theano. And the House of Return, for those who come home from the east.",
                "The White City. Theano carried the Hearthstar here, and here it stays. Everything else grew around it.");
            case LAMPSA: return "Lampsa of the Lamps: engineers, foundries, lumen. The Mechaneion is where they teach the Talos to walk. It smells of hot bronze and argument.";
            case HIERANTHE: return "Hieranthe of the Gardens: temples, the Asklepieion, the healing springs. People come here to get well, or to say goodbye.";
            case MNEMEIA: return "Mnemeia, the City of Memory. The Archive keeps everything: the Charter, the votes, the Register of the Lost. Hesper says the Archive never forgets. Hesper says it like a threat.";
            case LAST_WATCH: return "The Last Watch, where the Royal Road crosses the Lampwall. Beyond the gate is the Wound. Beyond that, the Dominion.";
            case GATE_OF_STRANGERS: return "The Gate of Strangers. Every stranger who ever came to Atlas came through there. The plinth by the gate is waiting for names.";
            default: return "This place has a long story. Most places here do. Ask at the Library.";
        }
    }
}
