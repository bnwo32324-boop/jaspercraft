package chat.jaspr.atlas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.bukkit.inventory.ItemStack;

/**
 * The written record of Atlas: histories, accounts, letters, philosophy, technical works, hymns, the records of the lost
 * and the Dominion's own papers. Different writers disagree, leave things out, or tell uncomfortable truths. Each book
 * belongs to collections (the shelves and chests it can be found in); every Concord library shelf opens one.
 */
final class LoreBooks {
    private LoreBooks() {}

    static final class Book {
        final String id, title, author;
        final List<String> collections;
        final String[] pages;
        Book(String id, String title, String author, String collections, String... pages) {
            this.id = id; this.title = title; this.author = author; this.collections = Arrays.asList(collections.split(",")); this.pages = pages;
        }
        ItemStack item() { return Items.book(title, author, "book_" + id, pages); }
    }

    static final Map<String, Book> BOOKS = new LinkedHashMap<>();
    private static void b(String id, String title, String author, String collections, String... pages) { BOOKS.put(id, new Book(id, title, author, collections, pages)); }

    /** The books of a collection ("any" means every Concord book). */
    static List<Book> collection(String name) {
        List<Book> out = new ArrayList<>();
        for (Book k : BOOKS.values()) if (k.collections.contains(name) || name.equals("any") && k.collections.contains("concord")) out.add(k);
        return out;
    }

    static Book pick(String collection, Random r) {
        List<Book> c = collection(collection);
        if (c.isEmpty()) c = collection("great");
        return c.get(r.nextInt(c.size()));
    }

    /** The book a given library shelf holds (stable for the shelf's position). */
    static Book shelf(String collection, long positionHash) {
        List<Book> c = collection(collection);
        if (c.isEmpty()) c = collection("great");
        return c.get((int) Math.floorMod(Hash.mix(positionHash) >>> 7, c.size()));
    }

    static {
        // ---------------------------------------------------------------- histories
        b("primer", "A Short History of the Concord", "the Lyceum, for children", "concord,great,primer,lyceum,welcome",
            "Long ago a star fell on the plain. It sang. The people who heard it came to listen, and stayed, and learned from it to count and to build and to make light. That is where we come from.",
            "The first Asterians swore the Homonoia, the Charter, at the fallen Star. Twelve founders, one for each tribe, and a thirteenth chair left empty for the stranger.",
            "We built Astreion first, then Lampsa, Hieranthe, Mnemeia, Pellene, Aigai and a hundred demes. We learned Harmonics: how to make stone take shape, light live in crystal, and bronze walk.",
            "Three hundred years ago a Keeper named Phosphoros broke the Star. Theano carried its blue half to Astreion. Its black half makes the ash. That is why we keep the Line.",
            "Questions for the lesson:\n1. Why is one chair empty?\n2. What is lumen?\n3. Who are the Bound, and why do we remember them?");
        b("falling_light", "The Falling Light", "the Order of the Lamp", "concord,great,hymns",
            "In the first year the sky opened a door, and a star came down the stair of the night and sat upon the plain, and it was singing.",
            "The herders of the grass came, and the fishers of the rivers, and the diggers of the hills, and they sat with it a year and a day, and none of them spoke, because they were listening.",
            "On the year and the day the star said: you may keep my song if you keep it together. If one of you keeps it alone, it will eat him.",
            "So they made a Concord of twelve voices, and left one seat empty, so that the song would always have room for a voice they had not yet heard.");
        b("black_chord", "The Night of the Black Chord", "Arete of Astreion, pupil of Theano", "concord,great,hearth,lyceum",
            "I was nineteen, and a lampkeeper's apprentice in the Heliotheion. I carried oil. I tell this because I am the last alive who saw it.",
            "The Keeper Phosphoros had been refused the Star by the Synedrion. He came anyway, at night, with the Four Chords of his own making, and he began to conduct the Star alone.",
            "It was the most beautiful sound I have ever heard. For a moment the whole plain sang one note. Then the note turned. The Star cracked like a bell struck wrong.",
            "The light went out of half of it. That half began to burn without light, and where its heat fell, the grass went black and then grey and then it was ash, falling up.",
            "Theano ran into the fire and took the blue half in her arms. It burned her hands to the bone. She carried it west for nine days. I walked beside her. She would not let anyone else hold it.",
            "At the gate of Astreion she set it down and said: \"Keep it together. Never alone.\" Then she sat down in the road and died, and the Hearthstar's light was the first thing she did not see.");
        b("four_crowns", "The Year of Four Crowns", "Demetria, historian of the Synedrion", "concord,great,archive",
            "In YL 1112, with the ash spreading and the Kelani plains lost, the Synedrion sent an embassy of four to parley with the Pyrarch at Anthrakion.",
            "It chose its four greatest: Kallias the strategos; Melaina the physician; Daidaros the engineer; Keleos the lawgiver. Each had lost something to the ash. That may have been the mistake.",
            "They returned on the ninth day, crowned. The crowns were black and did not reflect light. Each said: I have been given the power to finish my life's work.",
            "Kallias's legions went east with him. Keleos's clerks went with him. Pellene and Aigai, whose archons had been their friends, opened their gates. Within the year the east was the Dominion's.",
            "The Synedrion had asked the Four to bring back peace. It had not asked them what they wanted. That is the lesson I would have us learn.");
        b("withdrawal", "On the Withdrawal of 1127", "the Record of the Synedrion", "concord,great,archive,synedrion",
            "In the year 1127 of the Light, the Synedrion, having considered the defence of the Concord, resolved to consolidate its forces behind the Pharos Line.",
            "The Line could be held. The eastern cities could not be supplied. The decision was taken after full debate, with sorrow, and for the preservation of the Hearthstar and of the greater number.",
            "Citizens east of the Line were urged to come west before the Line was sealed. The Synedrion honours those who could not.",
            "The Pharoi were lit on the ninth of Harvest-month. They have burned since. So long as they burn, the Concord stands.");
        b("omits", "What the Record Omits", "Myrto of Mnemeia, historian", "concord,archive,lyceum",
            "Read the Record's account of 1127 closely. 'Citizens east of the Line were urged to come west.' It does not say how long they were given. It was eleven days.",
            "It does not say that the Line was built west of Pellene and Aigai, and of forty demes, and of every Kelani camp, because that was where the ground was high enough for Pharoi.",
            "It does not say the vote was seven to five. It does not say that two of the seven owned land west of the Line that had been cheap until that year.",
            "I do not write this to shame the dead. I write it because the Bound are not a misfortune that befell us. They are a choice we made. We should be able to say so.",
            "The Archive holds the full roll of the vote. Ask for it. Archivists are bound by the Charter to read it to anyone who asks.");
        b("long_watch", "The Long Watch: A Chronicle of the Line", "the Strategeion", "concord,great,line",
            "YL 1127: the Pharoi lit. YL 1133: Talos first used on the Line. YL 1160: Pharos XIV dims; the lumen will not hold in its crystal. The Cinder eats song, and lumen is song made light.",
            "YL 1203: forty of the Third Company go east to bring back the Bound of Pellene. None return. YL 1204: the Synedrion forbids further rescue.",
            "YL 1270-1301: the Nine Winters. Ash-storms breach the Line twice. Pharos IV and IX relit by hand at the cost of their keepers.",
            "YL 1388: Pharos IX falls dark. YL 1401: strangers come through the Great Quartz Gate. The Strategeion reopens the question of rescue.");
        b("kelani", "The Kelani of the Eastern Grass", "Hipparchos, traveller", "concord,great,lyceum",
            "The Kelani are a horse people of the eastern plains, older on the grass than the Concord. They live in round houses of turf and hide, and move with the seasons.",
            "They have no writing and do not want it. They say a thing written is a thing no one has to remember, and a thing no one remembers is a thing that has died.",
            "They traded horses for our lamps and laughed at our walls. I am told that after the ash came they stopped laughing. I am told the Line was not built to include them. I have not been able to learn why.");

        // ---------------------------------------------------------------- philosophy
        b("on_harmony", "On Harmony (excerpts)", "Phosphoros, Keeper of the Star", "concord,lyceum,great,journal",
            "Harmony is not agreement. The ignorant think a chord is many notes agreeing. A chord is many notes obeying the one note that makes them a chord.",
            "The Concord has twelve voices and an empty chair. This is not harmony. It is a crowd waiting to be conducted.",
            "The Star will give its whole song to one who can hold it. Not to twelve. Twelve hands will pull it apart. I will hold it for all of us, and then no one will ever be out of tune again.",
            "Is this pride? Perhaps. But the lamp does not ask the dark for permission to shine.");
        b("single_note", "Against the Single Note", "Theano of the Heliotheion", "concord,lyceum,hearth,great",
            "My teacher says harmony is obedience to the right note. I say the right note is the one that listens.",
            "A song that one voice conducts is a song that has stopped hearing anything but itself. It may be beautiful. So is a fire.",
            "What is shared is not less. The Star's light in twelve hands is not a twelfth of the light: it is twelve lights. The Charter knew this. He has forgotten it.",
            "If ever the Star breaks, keep its pieces together. Never alone.");
        b("stranger_dialogue", "Dialogue on the Stranger", "the Lyceum", "concord,lyceum,welcome",
            "ALKIPPE: Why do we leave a chair empty in the Synedrion?\nNIKON: For the stranger.\nALKIPPE: What stranger? None has ever sat in it.\nNIKON: Then it has never been needed. That is no reason to take it away.",
            "ALKIPPE: But a stranger is not of the Song. How could they help us?\nNIKON: Precisely because they are not. The song has a note it cannot hear: its own. The stranger hears it.",
            "ALKIPPE: And if the stranger means us harm?\nNIKON: Then we will have been hospitable to an enemy. It has happened. It has never cost us as much as closing the door.");
        b("the_quiet", "Why the Hearthstar Must Stay", "Anaxis the Quiet", "concord,lyceum",
            "The Lamplighters want to carry the Hearthstar's light east. I ask them: what if it goes out?",
            "It is the last light of the Star. Everything the Concord is, it is because that light burns in Astreion. We have lost half the Star once. We cannot afford to lose the rest for a gesture.",
            "A small light kept is better than a great light lost. The Bound are a sorrow. The loss of the Hearthstar would be the end of all sorrow, because there would be no one left to feel it.",
            "I may be wrong. I would rather be wrong and careful than right and ash.");
        b("lamplighters", "The Lamplighters' Reply", "Kallirhoe Kallid, strategos", "concord,lyceum,line",
            "Anaxis says a small light kept is better than a great light lost. A light kept in a jar is not a light. It is an ornament.",
            "Theano did not carry the Hearthstar west to put it on a shelf. She carried it because it was the only way to keep it together with the living.",
            "Every year we keep it safe, fewer of the Bound are alive to see it. If the light must go east, let it go east. What is the Star for, if not for them?");

        // ---------------------------------------------------------------- technical
        b("lumen", "The Principles of Lumen", "Kleoboulos of the Lyceum", "concord,lyceum,great,daidaros",
            "Lumen is light that remembers. A crystal tuned to the Hearthstar's note will hold light as a cup holds water, and give it back for years.",
            "Three things kill lumen: silence (a crystal no one tunes), dissonance (two notes at war in one stone), and the Cinder, which does not silence song but eats it, as fire eats oil.",
            "This is why the Pharoi fail in ash-storms. It is why our automata stagger beyond the Line. It is why ash-iron, smelted in the Cinder's fire, cannot be tuned at all.");
        b("automata", "On Automata", "Daidaros Tekton, YL 1081", "concord,daidaros,lyceum",
            "A Talos is bronze and andesite and a lumen heart tuned to a simple song: guard, lift, carry, stand. It does not tire. It does not want.",
            "I build them so that no child of the Concord need ever break their back in a quarry again. Machines are for sparing hands.",
            "Some ask whether a Talos suffers. It does not. I have listened to them for years. They hum. It is the most contented sound in Atlas.");
        b("heliodromes", "Heliodromes: A Traveller's Guide", "the Guild of Roads", "concord,welcome,great",
            "A heliodrome is a ring of quartz pillars round a lumen core. Touch the core and it shows you every heliodrome you have found; choose one and the light carries you there.",
            "You must have stood at a heliodrome to be carried to it. The light only knows where you have been.",
            "Heliodromes do not work in the ash. Where land is freed, its heliodromes wake again.");
        b("quartz_gates", "The Doors of Elsewhere", "Nikander of the Lyceum", "concord,great,lyceum,welcome",
            "After the Sundering, Theano's pupils built a door that would open onto other worlds, to call someone who was not of the Song. It needed both halves of the Star to open.",
            "They could not borrow the Cinder Heart, so they borrowed its echo: coal, which is black and burns. With lapis, which is blue and holds light, the quartz remembers both halves at once.",
            "That is why the gates burn half blue, half black. Lapis and coal: the divided light. The Great Gate at the Threshold has stood open ever since. It waited three hundred years for you.");
        b("ash_iron", "On Ash-Iron, and a Confession", "Aristion, smith of Lampsa", "concord,archive,daidaros",
            "Ash-iron is iron smelted in the Cinder's fire. It is harder than ours and it cannot be tuned, so our Talos's lumen slides off it.",
            "The Guild of the Anvil in Lampsa bought ash-iron from Dominion traders for sixty years after the Withdrawal. It was cheap. We did not ask who dug it.",
            "Our fathers knew. Our fathers bought it anyway. I have made nine hundred pots from ash-iron. I am writing this so that my grandchildren know what the pots are made of.");

        // ---------------------------------------------------------------- hymns and rites
        b("lamp_hymns", "Hymns of the Lamp", "the Order of the Lamp", "concord,hymns,hearth",
            "At dawn:\nWake, lamp. The dark was long.\nWe kept your oil. We kept your song.",
            "At the stranger's coming:\nOpen the door. Pour the cup.\nWhoever comes, we hold them up.",
            "At the Line:\nBurn, Pharos, burn. Hold back the grey.\nWe cannot go. We will not stay\nsilent. Burn.");
        b("strangers_cup", "Rite of the Stranger's Cup", "the Order of the Lamp", "concord,hymns,welcome",
            "Every house of the Concord keeps a cup that is never used by the household. It is for the stranger.",
            "When a stranger comes, fill the cup with water, or wine, or milk, and give it to them before you ask their name. A name is owed to no one. A cup is owed to everyone.",
            "If they do not drink, do not be offended. If they break the cup, give them another. The Charter says the stranger is owed bread, water, fire and the road. It does not say they must be grateful.");
        b("litany", "The Litany of the Unlit", "the Cinder Priesthood", "litany,dominion",
            "One note. One will. One Heart.\nThe light that is shared is light lost.\nThe voice that is many is noise.",
            "Blessed are the silent, for they are in tune.\nBlessed are the bound, for they are held.\nBlessed are the still, for they shall not end.",
            "When the last voice is quiet,\nthe Heart will sing alone,\nand all Atlas will be warm.");
        b("catechism", "Catechism of the One Note", "for the instruction of the Sworn", "litany,dominion,edicts",
            "Q: Who is the Pyrarch?\nA: The Keeper who held the Star when no one else was brave enough.\nQ: Why does the ash fall?\nA: It is the old world, burning away so the new one may be born.",
            "Q: Who are the Concord?\nA: A crowd that calls its noise freedom.\nQ: What is owed to them?\nA: Silence, until they learn.",
            "Q: What is a slave?\nA: There are no slaves. There are only notes waiting to be played.");

        // ---------------------------------------------------------------- personal accounts and the lost
        b("pharos_letters", "Letters from Pharos IX", "Melitta, keeper", "concord,line,lost",
            "Dearest Doros is on watch, so I am writing to you, Hagnon, before the lamp needs me. The ash came thick again. The crystal hums low. We sang to it all night and it brightened.",
            "The children of the demes behind us came up to see the lamp. I told them it has never gone out. I don't know if that's true. It's true while I'm here.",
            "If it goes dark, Hagnon, don't let them tell you we failed it. Tell them we sang to it until our voices went. Your Melitta.");
        b("kallias_letters", "Letters to Euthymia", "Kallias son of Lysis", "concord,marshal,great",
            "Euthymia: the Kelani Fords are lost. We held for eleven days. Every man I left on the east bank was someone's son. I have written to all their mothers. I am so tired of writing to mothers.",
            "I dream of a war that ends. Not a truce: an end. No more fords, no more mothers. I would give anything for it.",
            "The Synedrion sends me east tomorrow with the Four, to parley. Kiss Lysis for me. Tell him his father will come home with a peace in his hands. Kallias.");
        b("pellene_diary", "Diary of a Girl of Pellene, YL 1126", "Ione, the elder", "concord,lost,return",
            "The soldiers are leaving. Father says they are going to the new wall. Mother says we should go too. Grandmother says she was born here and will die here.",
            "Eleven days, the herald said. We packed for three of them. On the fourth the Marshal's riders were on the western road.",
            "We are staying. Grandmother was right. The lamp in the agora still burns. I am going to keep this diary until it doesn't.");
        b("register", "The Register of the Lost (vol. XXII)", "the Great Library", "concord,lost,great",
            "Taken east of the Line, YL 1380-1390, as recorded by families: Aristion of Kyth, 34. Melitta of Pharos IX. Lykos, 19. Chrysa of the Asklepieion (taken 1361). Agapios of the Third Company (1203, by old record).",
            "Myrrhine, midwife, 41. Ktesias of the Mechaneion (taken 1113, presumed long dead). Nausikaa, smith of Lampsa, 29. Erinna of Pellene, poet (born beyond the Line; name sent by smuggled letter).",
            "The Library keeps one volume for every ten years since the Withdrawal. There are twenty-seven. If you bring back anyone named here, the Librarian will write the date of their return beside their name.");
        b("rescue_1203", "Report of the Rescue of 1203", "the Strategeion", "concord,lost,line",
            "Forty of the Third Company, under Captain Timon, left the Last Watch on the 3rd of Seed-month to bring back the Bound of Pellene.",
            "Signals were received from the Wound on the 4th and the Marches on the 6th. No signal after the 9th.",
            "The Synedrion, having lost forty to recover none, forbids further expeditions beyond the Line. The families' petition to reconsider is received and declined.");
        b("aigai_muster", "The Last Muster of Aigai", "the city of Aigai", "concord,lost,archive",
            "Mustered in the agora of Aigai, YL 1127, to march west: 2,114 citizens. Mustered on the eleventh day: 1,006.",
            "Remaining in the city by choice, or too old, or too sick, or without a cart: the rest.",
            "The archon of Aigai will stay with those who stay. He asks the Synedrion to remember that it promised to come back.");
        b("return_songs", "Songs of the House of Return", "the House of Return", "concord,return",
            "We sing when someone comes home. Anyone. Even the Sworn. It is the same song for everyone.",
            "Here is the bread. Here is the bed.\nNo one will ask you what you did.\nSleep. We have the watch.\nSleep. You are home.");

        // ---------------------------------------------------------------- the Dominion's papers
        b("marshal_orders", "Orders of the Marshal", "Kallias, Marshal of the Teeth", "orders,marshal,dominion",
            "To all camps: the Bound are to be fed. A starving man cannot haul. Taskmasters who waste hands will be sent to haul themselves.",
            "To the Pylon: no one passes the Teeth unbidden. Strangers from the Gate are to be taken alive where possible. I wish to see one.",
            "To the drum-masters: the legions will march at dusk. There will be no burning of fields. We will need the fields.");
        b("camp_orders", "Standing Orders for Labour Camps", "the Clerks of the Sworn", "orders,dominion",
            "1. Count every morning. Count every evening. The counts must match.\n2. Speech among the Bound is to be discouraged but not punished (the Marshal's order).",
            "3. The shackle post is the heart of the camp. It holds the chains' note. If it is broken, every chain in the camp falls quiet. Guard it.",
            "4. A camp whose Taskmaster falls is to be considered lost until reinforced. Do not reinforce by night.");
        b("requisitions", "Requisitions of the Forgemaster", "Daidaros Tekton", "requisitions,dominion",
            "Required this month: 400 hands for the Engine's eastern foundry. Replace the lost. Replace them faster.",
            "The Governors are singing well. The Engine has not stopped in two hundred years. Nothing will stop it.",
            "Note: the hands keep dying. Find out why. Hands should not die. Machines are for sparing hands. Why do the hands keep dying.");
        b("daidaros_late", "The Late Notebook", "Daidaros Tekton", "daidaros_late,dominion",
            "The Engine is nearly finished. When it is, no one in Atlas will ever need to lift a thing again. The Engine will lift everything.",
            "The hands say it hurts. I do not understand. The Engine does not hurt. I have listened to it. It hums.",
            "Deep, middle, high. I wrote that somewhere once. For stopping an engine. Why would anyone want to stop an engine?");
        b("edicts", "The Edicts of the Silent Magistrate", "Keleos, Voice of the Edict", "edicts,dominion",
            "I. Speech is a disturbance of the peace.\nII. The Magistrate speaks for all, so that none need speak.\nIII. The Edict Stones repeat the Magistrate. To hear them is to agree.",
            "IV. The Charter of the Concord is a record of disorder and shall not be read in public.",
            "V. Any citizen who is silent for a year shall be counted content. There are now no discontented citizens in Pellene.");
        b("ledger", "The Second Ledger", "Thersites, clerk", "ledger,dominion,archive",
            "Copy of the Synedrion's vote of YL 1127, bought in Lampsa for eleven cinder coins, as follows. To withdraw behind the Line: seven. To hold the east: five.",
            "Of the seven: two held land west of the Line whose price doubled that year. One had a son in Pellene and voted to withdraw anyway, and wept.",
            "I keep this ledger so that one person in the Dominion remembers that the Concord chose. The Sworn were left. Some of us stayed Sworn because of it.");
        b("stilling_protocol", "Protocol of Stilling", "Mother Sallow", "stilling,dominion,melaina",
            "The patient is placed in glass by a font. The font's water is brought to the lips. The patient ceases to decline.",
            "The patient will not die. The patient will not recover. The patient will watch. This is mercy.",
            "Note: several patients have asked to die. Their request has been noted. The Stiller has been consulted. The request has been denied, lovingly.");
        b("melaina_daybook", "Melaina's Daybook", "Melaina Iatra", "melaina,stilling,dominion",
            "Asklepieion, YL 1109. Philinna's fever broke at dawn and came back at noon. I have tried everything. The ash is in her lungs.",
            "YL 1109, ninth day. She died holding my hand. I have held a thousand hands at the end. I thought I knew how. I did not know anything.",
            "YL 1112. The crown is cold on my head. Nothing near me will die again. Nothing. I have made the whole Weald a sickroom where no one is ever lost.",
            "YL 1400. They look at me from the glass. Philinna would have hated this. I cannot remember her face. I cannot remember why I cannot stop.");

        // ---------------------------------------------------------------- Anthrakion
        b("journal_1", "Journal of the Keeper, I", "Phosphoros", "journal,dominion",
            "YL 1098. The Synedrion refused me again. Twelve voices, arguing about the price of lumen while the Star sings a song none of them can hear.",
            "I heard it last night. The whole song. It is one note, underneath everything. If I could conduct it, every voice in Atlas would be in tune. No war. No fever. No noise.",
            "Theano says I am tired. Theano says the Star gave its song to twelve so that no one would hold it alone. Theano is young.");
        b("journal_2", "Journal of the Keeper, II", "Phosphoros", "journal,dominion",
            "YL 1101. I have done it. I have done it and it broke. It broke in my hands and half of it went out and half of it is in me.",
            "It is so warm. It does not want to share. I understand now. It never wanted twelve. It wanted one.",
            "I will make it right. I will tune the whole world to it, and then it will not need to eat. It is only hungry because the world is out of tune.");
        b("journal_3", "Journal of the Keeper, III", "Phosphoros", "journal,dominion",
            "YL 1350. The ash is my patience. It falls slowly. Every year more of Atlas is quiet, and in tune.",
            "The Four were weak. They each wanted their own song. I gave them crowns so they would sing mine. They are my Wards. While they wear my crowns, no light can come into this house.",
            "YL 1401. Something has come through the Gate. It is not of the Song. I cannot hear it. For the first time in three hundred years, I do not know what will happen. It is almost like being young.");
        b("crowns", "The Forging of the Four Crowns", "the Choirmaster", "crowns,dominion",
            "In the Forge of Crowns, the Pyrarch broke four shards from the Heart and gave each a voice: the voice of what its wearer most wanted.",
            "A crown is bound to its Ward. While the crown is worn, the Ward burns, and the Heart is sealed from all light. When a crown's wearer falls, its Ward goes dark.",
            "When all four Wards are dark the gate of Anthrakion will open of itself. The Pyrarch does not believe it will ever happen. I have counted the Wards every morning for two hundred years.");
        b("rider_list", "The Rider's List", "the Crownless Rider", "rider,dominion,lost",
            "Taken on the North Road: Philon son of Kleon, 17. Arete of Kyth, 8. Nikias, 40. Doros, keeper (taken from Pharos IX by the Marshal; noted).",
            "Every note must be accounted for. The Keeper will ask. The Keeper always asks.",
            "Nine hundred and seven names. I remember every one. I cannot remember my own.");

        // ---------------------------------------------------------------- the present
        b("minutes_1401", "Minutes of the Synedrion, YL 1401", "the Record of the Synedrion", "concord,synedrion,great",
            "Present: the twelve. The thirteenth chair: empty, as always, until the Archon moved that it be offered to the stranger who came through the Gate.",
            "Anaxis spoke against: the stranger is unknown; the Hearthstar must not be risked. Lysandra spoke for: the stranger is not of the Song; the ash cannot see them; they can go where we cannot.",
            "Resolved: the Concord will teach the stranger what it knows of the Four, and give what help it can. If the Four Wards fall, the Synedrion will lend the stranger the Light of Theano. Seven to five.");
        b("hearth_book", "The Keeper's Book of the Hearth", "the Keepers of the Hearth", "concord,hearth",
            "The Hearthstar has burned in this temple for three hundred years. It is warm to the hand. It hums a note just under hearing.",
            "Keepers have noticed: when the Cinder Heart is sealed by the Wards, the Hearthstar is dimmer. When a Ward falls dark, it brightens. The halves still hear each other.",
            "If ever all four Wards fall, a keeper may take the Hearthstar's light in a crystal and carry it east. Brought to the Cinder Heart, the halves will remember they were one. That is Theano's hope. It is ours.");
        b("line_report", "Report to the Lochagos", "the Last Watch", "concord,line",
            "East of the Wall: the Wound, forty stadia of craters. Then the Ashen Marches: war camps, pens, towers. The Pylon of Teeth stands on the Royal Road, 40 stadia east of here.",
            "The Teeth wall surrounds the inner provinces. No one crosses it but by the Pylon. Those who climb it are choked by the Veil of ash that hangs on its crown while the Marshal lives.",
            "Advice to strangers: go armed, go by day, free the camps you can. Every freed camp tells us more.");
    }
}
