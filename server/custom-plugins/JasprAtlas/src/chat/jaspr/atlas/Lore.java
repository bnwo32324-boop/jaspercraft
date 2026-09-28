package chat.jaspr.atlas;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.Villager;

/**
 * Who is who in Atlas, and the four texts that break the Ash-Crowned. The library of books lives in {@link LoreBooks};
 * conversations in {@link Talk}.
 */
final class Lore {
    private Lore() {}

    // ------------------------------------------------------------------ the key figures of the Concord

    static final class Figure {
        final String id, name, title, place;
        final Villager.Profession profession;
        Figure(String id, String name, String title, String place, Villager.Profession profession) {
            this.id = id; this.name = name; this.title = title; this.place = place; this.profession = profession;
        }
    }

    static final Map<String, Figure> FIGURES = new LinkedHashMap<>();
    static {
        fig("philon", "Philon", "host of the Threshold", "the Gate of Strangers", Villager.Profession.FARMER);
        fig("kleio", "Kleio Theanid", "Archon of the Synedrion", "the Synedrion, Astreion", Villager.Profession.LIBRARIAN);
        fig("lysandra", "Lysandra Kallid", "Strategos of the Line", "the Stoa of Shields, Astreion", Villager.Profession.BLACKSMITH);
        fig("eudora", "Eudora", "Librarian of Astreion", "the Great Library, Astreion", Villager.Profession.LIBRARIAN);
        fig("anaxis", "Anaxis the Quiet", "philosopher of the Lyceum", "the Lyceum, Astreion", Villager.Profession.LIBRARIAN);
        fig("neaira", "Neaira", "Keeper of the Hearth", "the Hearth of Theano, Astreion", Villager.Profession.PRIEST);
        fig("iaso", "Iaso", "Priestess of the Asklepieion", "the Asklepieion, Hieranthe", Villager.Profession.PRIEST);
        fig("perdix", "Perdix", "Mechanic of the Mechaneion", "the Mechaneion, Lampsa", Villager.Profession.BLACKSMITH);
        fig("hesper", "Hesper", "Archivist of Memory", "the Archive of Memory, Mnemeia", Villager.Profession.LIBRARIAN);
        fig("menon", "Menon", "Lochagos of the Last Watch", "the Last Watch", Villager.Profession.BLACKSMITH);
    }
    private static void fig(String id, String name, String title, String place, Villager.Profession p) { FIGURES.put(id, new Figure(id, name, title, place, p)); }
    static Figure figure(String id) { return FIGURES.get(id); }

    // ------------------------------------------------------------------ the named captives

    static final class Captive {
        final String id, name, title;
        final Realm.Province province;
        final Villager.Profession profession;
        final String plea, thanks;       // spoken in the Dominion; spoken in the House of Return
        final String[] testimony;        // the book they give their rescuer
        Captive(String id, String name, String title, Realm.Province province, Villager.Profession profession, String plea, String thanks, String... testimony) {
            this.id = id; this.name = name; this.title = title; this.province = province; this.profession = profession; this.plea = plea; this.thanks = thanks; this.testimony = testimony;
        }
    }

    static final Map<String, Captive> CAPTIVES = new LinkedHashMap<>();
    static Captive captive(String id) { return CAPTIVES.get(id); }
    private static void cap(Captive c) { CAPTIVES.put(c.id, c); }

    static {
        cap(new Captive("doros", "Doros", "keeper of Pharos IX", Realm.Province.MARCHES, Villager.Profession.BLACKSMITH,
            "Thirteen years in this pen. I kept a Pharos once. Is the Line still lit? Tell me it is lit.",
            "I can see the Lampwall from the House's roof. I go up every evening. It is still lit.",
            "Doros, keeper of Pharos IX, taken YL 1388.\n\nFour of us kept the lamp. The ash came in a night-storm and the lumen went dim, and the Ashborn came up the glacis in the dark.",
            "They killed Aristion and Lykos and Melitta. They kept me because I knew how a Pharos is built. The Marshal wanted to know how to put one out.",
            "I told him nothing for a year. Then I told him lies for twelve. He believed the lies. He is not stupid, stranger. He is tired.",
            "Kallias asked me once if the Stoa still has his shield on the wall. I said yes. He did not speak for a long time. The Oath hangs under it. I think he remembers."));
        cap(new Captive("tamsa", "Tamsa", "herder of the Kelani", Realm.Province.MARCHES, Villager.Profession.FARMER,
            "The Kelani do not kneel. I have not knelt. Cut these ropes and I will show you how wargs are fooled.",
            "The Concord feeds me well. They talk too much. My people had these plains before your Star fell, did you know?",
            "Tamsa of the Kelani.\n\nMy people herded the eastern grass before the Asterians came west, and after. We traded horses for lumen lamps. Good trade.",
            "When the ash came the Asterians built their wall. It kept the ash off them. It did not keep it off us. We were on the wrong side of their wall.",
            "Wargs follow scent. Walk in running water, or through the Forges' ash where everything stinks of burning, and they lose you. Remember that.",
            "Ask the Concord why they built the wall where they built it. Ask them who was east of it."));
        cap(new Captive("philippos", "Philippos", "scribe of Aigai", Realm.Province.MARCHES, Villager.Profession.LIBRARIAN,
            "The Rider keeps me to write his lists. I have been writing another list, in my head. Every name. Let me out and I will write it down.",
            "Give me ink and a table. I have nine hundred names to set down before I forget one.",
            "Philippos, scribe of Aigai.\n\nThe Crownless Rider rides the roads at night and takes one from every coffle. He does not eat them. He takes their names. He writes them in a book.",
            "I have seen the book. It is the list of everyone the Dominion has taken, name and deme and age. He keeps it because the Pyrarch wants every note accounted for.",
            "If you kill him, take his book to the Great Library. Eudora will want it. Every name in it is someone the Concord said it would come back for."));
        cap(new Captive("ione", "Ione", "a girl of Pellene", Realm.Province.FALLEN, Villager.Profession.NITWIT,
            "I'm not allowed to talk. Nobody's allowed to talk. But you're not from here, so maybe it doesn't count?",
            "Everyone talks here! All the time! The lady at the House lets me read the lamp-book.",
            "Ione. I am eleven.\n\nIn Pellene we talk with our hands so the Magistrate's ears don't hear. Mother taught me. The Edict Stones hum and if you say words near them you can't stop saying yes.",
            "There are three stones in the square in front of the Hall. When I walk past them my mouth goes heavy. Grandfather said the old Charter could make them go quiet. Grandfather is in the pen.",
            "If you go to the Hall, the Magistrate sits on a black seat and doesn't move. He is very still. That's the scariest part."));
        cap(new Captive("chrysa", "Chrysa", "once Melaina's apprentice", Realm.Province.WEALD, Villager.Profession.PRIEST,
            "I learned healing at her side. Then I learned this. Please, before she stills me too.",
            "Iaso and I sing together in the evenings. She is the first person in forty years who has let me finish a song.",
            "Chrysa, once apprentice to Melaina of the Asklepieion.\n\nShe was the best of us. When her daughter took the ash-fever, she sat by the bed nine days and could do nothing, and on the ninth night she swore nothing would ever die in her keeping again.",
            "The Stilling Fonts hold the Cinder's heat in still water. Near them, the dying cannot die and the living cannot rest. Her Ashborn get up again. She cannot be killed while they run.",
            "The Hymn of Passage is the Asklepieion's funeral song: let the lamp go out gently. Sung at a font, it lets the font remember what water is for. Iaso knows it. Every priestess does."));
        cap(new Captive("agapios", "Agapios", "hoplite of the rescue of 1203", Realm.Province.WEALD, Villager.Profession.BLACKSMITH,
            "What year is it? Don't tell me. I can see it in your face. Just get me out.",
            "Two hundred years. My captain's grandchildren's grandchildren are here. They brought me a cake. I cried like a boy.",
            "Agapios son of Timon, Third Company of the Line.\n\nIn YL 1203 the Synedrion sent forty of us east to bring back the Bound of Pellene. We never reached Pellene. The Stiller's priests found us in the Weald.",
            "She stilled us. I have been standing in a glass room for one hundred and ninety-eight years. I do not remember most of it. I remember the others' faces. I think I am the last.",
            "The Record says we were lost. We were not lost. We were left. Nobody came."));
        cap(new Captive("myrrhine", "Myrrhine", "midwife of the eastern demes", Realm.Province.WEALD, Villager.Profession.PRIEST,
            "Mother Sallow keeps me to bring babies. She says no one born here will ever die. She says it like a gift.",
            "Fourteen babies have been born in the House of Return since I came. Fourteen! I cried at every one.",
            "Myrrhine, midwife.\n\nMother Sallow is the Stiller's high nurse. She is kind in the way a wall is kind. She keeps the stilling-house in the north of the Weald.",
            "Her charges are every person the Stiller has saved from dying. They do not die. They do not live either. They look at you.",
            "Kill Sallow and her house goes quiet. Break the Stiller and all of them may finally rest. I would rather they rested."));
        cap(new Captive("ktesias", "Ktesias", "once Daidaros's student", Realm.Province.FORGES, Villager.Profession.BLACKSMITH,
            "I drew half the Engine's plans. I am not proud of it. Get me out and I will tell you how it can be stopped.",
            "Perdix has my old slate from the Mechaneion. I wrote on it when I was twelve. 'Machines are for sparing hands.' I had forgotten.",
            "Ktesias, apprentice of the Mechaneion, taken with his master's workshop in YL 1113.\n\nThe Engine is a harmonic resonator the size of a tower. Three Governor crystals hold its note: one in the pit, one on the floor, one at the crown.",
            "Silence them in the wrong order and the others take up the note and ring it louder. The master wrote the right order in his notebook, the one he left in Lampsa. Deep, then middle, then high. Always deep first.",
            "While the Governors sing, the Engine shields him. When they are silent, he is only an old man in a metal coat."));
        cap(new Captive("nausikaa", "Nausikaa", "smith of Lampsa", Realm.Province.FORGES, Villager.Profession.BLACKSMITH,
            "They make me forge ash-iron. Every blade I make goes west. Every blade goes west.",
            "I am going to make one blade here. One. It is going to be for you.",
            "Nausikaa of Lampsa, smith.\n\nAsh-iron is iron smelted in the Cinder's fire. It does not take lumen. Harmonic tools slide off it. That is why the Concord's automata cannot stand against the Dominion's pikes.",
            "It is also why some Lampsa merchants bought it, before the Withdrawal and after. It is hard. It is cheap. Nobody asked where it came from. Ask Perdix about the Guild of the Anvil. Watch his face.",
            "Bring ash-iron to the smiths of Lampsa. They can rework it into something clean. It takes three days and a lot of singing."));
        cap(new Captive("lykon", "Lykon", "leader of the quarry gangs", Realm.Province.FORGES, Villager.Profession.NITWIT,
            "Grunnak counts us every morning. Some mornings there are fewer. Get me out and I'll tell you where he sleeps.",
            "The quarry men are free. I don't know what to do with my hands. They keep making the shape of a pick.",
            "Lykon of the quarry.\n\nThe Slag Troll Grunnak is not the worst thing in the Forges. He is stupid and hungry. The Forgemaster is not stupid. He never eats.",
            "The trolls turn to slag-stone when their master falls. We have seen it: they stop, and crust over, and stand. They are waiting for an order that won't come.",
            "There is a pit south-west of the Engine where he keeps something that burns. The overseers don't go near it. We call it the Hollow."));
        cap(new Captive("erinna", "Erinna", "poet of Pellene", Realm.Province.FALLEN, Villager.Profession.LIBRARIAN,
            "They hold me because I would not stop singing. I have not stopped. I sing without sound now. Can you hear it?",
            "Out loud. Out loud! I am going to be insufferable.",
            "Erinna of Pellene.\n\nKeleos wrote the Concord's laws. Good laws, mostly. He believed that if the law were perfect no one would need to choose. The crown gave him a voice that chooses for you.",
            "The Edict Stones repeat him. Near them you agree before you know you have spoken. He cannot be touched in his Hall while all three speak.",
            "Our Charter's first article: no law shall bind the tongue of a free citizen. He wrote the commentary on it himself, when he was young. Read it to the stones. They are his voice. Make his voice hear his own words."));
        cap(new Captive("thersites", "Thersites", "clerk of the Sworn", Realm.Province.FALLEN, Villager.Profession.LIBRARIAN,
            "I served them. Yes. Kalchas locked me up for keeping a second ledger. Get me out and you can read it.",
            "They look at me in the House. I don't blame them. I keep my ledger honest now. It is the only thing I know how to do.",
            "Thersites, clerk of the Sworn, of a family that stayed east in YL 1127.\n\nThe Sworn are not monsters. We are the ones who were left. The Concord marched away behind its lamps and the Dominion was here, and it had bread.",
            "My second ledger is a copy of the Synedrion's own vote of 1127, bought from a Lampsa merchant. Seven to five, to abandon us. The Concord's histories say 'withdrawal'. The ledger has names.",
            "I am not asking to be forgiven. I am asking you to ask Hesper, in Mnemeia, whether the Archive keeps the full vote. She does. Ask her."));
    }

    // ------------------------------------------------------------------ the four keys

    static final String[] OATH = {
        "THE OATH OF THE SHIELD\n\nSworn in the Stoa of Shields by every strategos of the Concord on the morning of their command.\n\nThis copy: Kallias son of Lysis, YL 1071, aged seventeen.",
        "\"I take the shield of the Concord and I will carry it.\n\nI will stand where I am set. I will not leave the one beside me.\n\nI will obey the Synedrion while it keeps the Charter, and not a day longer.",
        "I will not lift my spear against the unarmed.\n\nI will not burn what feeds the people.\n\nI will not make a war that could be a truce, nor a truce that is only a slower war.",
        "And if I forget this oath, may the one who reads it to me be forgiven for what they must do.\"\n\n~ Kallias",
        "Lysandra Kallid's note:\n\nHold this in your hand when you face him. It is not magic, or it is the only magic that ever mattered: a man's own promise, read back to him. The crown drowns it. The oath is louder than the crown for a heartbeat.",
        "In that heartbeat he is mortal. Use it. And if you can, tell him his great-granddaughter kept the shield polished.",
    };

    static final String[] HYMN = {
        "THE HYMN OF PASSAGE\n\nSung in the Asklepieion at every death, since the first healer lost her first patient.\n\nTo be sung, not read. Hold it and sing, if you cannot.",
        "\"Let the lamp go out gently.\nLet the oil be spent.\nLet the wick lie down\nin its own warm ash.\n\nWe held you as long as holding was kind.",
        "Let the water be water.\nLet the stone be stone.\nLet what ends, end,\nand what begins, begin.\n\nWe will keep your name.",
        "Go into the dark as into a room\nwhere someone you love\nhas left the door open.\"",
        "Iaso's note:\n\nThe Stilling Fonts are water taught to refuse. Sing this at each of the three (hold the Hymn and touch the font) and the water remembers it may flow on. When all three are quiet, so is she: then she can be ended.",
        "Be gentle with her at the last. She was one of us. She loved her daughter more than she loved the world. That is not a small thing. It is only a terrible one.",
    };

    static final String[] COUNTERPOINT = {
        "LEAVES FROM THE NOTEBOOK OF DAIDAROS TEKTON\n\nLampsa, Mechaneion, YL 1099. Found in his desk after he went east. Kept by Perdix.",
        "On governing a harmonic engine of three stages:\n\nThe engine holds its note in three Governors. Each hears the other two. Silence one and the others, hearing the gap, sing louder to fill it. This is what makes the engine safe. It is also what makes it cruel.",
        "The counterpoint (how to still it without it ringing itself apart):\n\nBEGIN WHERE THE FIRE SLEEPS LOWEST. Then the middle voice. END WHERE THE LIGHT IS NEAREST THE SKY.\n\nDeep, middle, high. Never another order.",
        "If the order is broken, the silenced voices rise again within the breath, and the engine punishes the hand that tried. I have tested this. I have the scar.",
        "Perdix's note:\n\nThe Great Engine is this engine, grown monstrous. Its Governors are in the pit, on the engine floor, and at the crown of its tower. Break them in this order and the Forgemaster loses his shield.",
        "He wrote on the last page, in his old hand: \"Machines are for sparing hands.\" I think he still believes it. I think that is the worst of it.",
    };

    static final String[] CHARTER = {
        "THE HOMONOIA\nthe Founding Charter of the Asterian Concord, sworn at the fallen Star in the twelfth year of the Light.\n\nCopied from the original in the Archive of Memory.",
        "I. No law shall bind the tongue of a free citizen.\n\nII. No single hand shall hold the Star.\n\nIII. The stranger at the gate is owed bread, water, fire and the road.",
        "IV. What is shared is not less.\n\nV. The Synedrion is many voices. When it speaks with one voice, it has stopped listening.\n\nVI. Every law shall be read aloud, so all may hear what binds them.",
        "Commentary on the First Article, by Keleos Nomothetes, Archon, YL 1094:\n\n\"The law may bind the hand. It may never bind the mouth. A people that cannot object has no law, only a master's voice repeated.\"",
        "Hesper's note:\n\nRead the First Article to each Edict Stone: hold the Charter and touch the stone. His Edicts are his voice; this is his own voice, younger, and it disagrees with him. When all three stones fall silent, the Magistrate is only a man on a black chair.",
    };

    /** The Codex's opening, and the stranger's welcome from Philon. */
    static final String[] WELCOME = {
        "WELCOME, STRANGER\n\nYou are in Atlas, the Bearer, the land that holds up the sky. You stand at the Gate of Strangers, and you are welcome here: that is our oldest law.\n\n~ Philon, host of the Threshold",
        "West of you is the Asterian Concord: our cities, our fields, our libraries. East, beyond the Lampwall, is the Cinder Dominion, where the ash falls and our people are slaves.",
        "Three hundred years ago our Star split. Its blue half, the Hearthstar, lights the Concord. Its black half, the Cinder Heart, burns in Anthrakion and makes the ash.",
        "Four of our greatest took crowns from the Cinder Heart. They rule the Dominion now: the Marshal, the Stiller, the Forgemaster, the Silent Magistrate. Above them all, the Pyrarch.",
        "We cannot go far into the ash. It knows us: we are of the Star's song. You are not. The ash does not know you.\n\nThat is why we built the Gate you came through.",
        "Go to Astreion, just east. Ask for Archon Kleio in the Synedrion. She will tell you what we know, and what we cannot do alone.\n\nTake this Codex. It will remember for you.",
        "A word on gates: yours is kindled by both halves of the light, lapis and coal. Walk back through it to go home. It will always bring you back here, or to the last gate you left by.",
    };
}
