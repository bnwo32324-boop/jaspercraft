package chat.jaspr.atlas;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * What the key figures of the Concord and the Dominion's talkers say. Each has a voice of their own and a few things to
 * tell; the four teachers each give one key of knowledge, the Archon lends the Light when the Wards are dark, and every
 * key can be asked for again ("I have lost it") so nothing needed to win can ever be lost. Some tell uncomfortable
 * truths when asked the right question; Vesk sells secrets, and not all of them are true.
 */
final class Figures {
    private Figures() {}

    static Talk.Page page(AtlasPlugin plugin, Player p, String id, String node) {
        Lore.Figure fig = Lore.figure(id);
        if (fig == null) return null;
        State s = plugin.state();
        State.Player rec = s.player(p.getUniqueId(), p.getName());
        boolean first = rec.met.add(id);
        if (first) plugin.saveStateSoon();
        Talk.Page page = new Talk.Page(fig.name + ", " + fig.title);
        Ctx c = new Ctx(plugin, p, s, rec, page, first);
        switch (id) {
            case "philon": philon(c, node); break;
            case "kleio": kleio(c, node); break;
            case "lysandra": lysandra(c, node); break;
            case "iaso": iaso(c, node); break;
            case "perdix": perdix(c, node); break;
            case "hesper": hesper(c, node); break;
            case "eudora": eudora(c, node); break;
            case "anaxis": anaxis(c, node); break;
            case "neaira": neaira(c, node); break;
            case "menon": menon(c, node); break;
            default: return null;
        }
        return page;
    }

    static Talk.Page talker(AtlasPlugin plugin, Player p, String id, String node) {
        State s = plugin.state();
        State.Player rec = s.player(p.getUniqueId(), p.getName());
        boolean first = rec.met.add(id);
        if (first) plugin.saveStateSoon();
        Talk.Page page = new Talk.Page(id.equals("vesk") ? "Vesk, Gnawling informer" : "Uzgar, Ashborn deserter");
        Ctx c = new Ctx(plugin, p, s, rec, page, first);
        if (id.equals("vesk")) vesk(c, node); else uzgar(c, node);
        return page;
    }

    /** Everything a conversation needs. */
    private static final class Ctx {
        final AtlasPlugin plugin;
        final Player p;
        final State s;
        final State.Player rec;
        final Talk.Page page;
        final boolean first;
        Ctx(AtlasPlugin plugin, Player p, State s, State.Player rec, Talk.Page page, boolean first) {
            this.plugin = plugin; this.p = p; this.s = s; this.rec = rec; this.page = page; this.first = first;
        }
        Ctx say(String t) { page.say(t); return this; }
        Ctx opt(String label, String node) { page.opt(label, node.equals("close") ? "close" : "go:" + node); return this; }
        boolean fallen(String boss) { return s.bossesFallen.containsKey(boss); }
        boolean knows(String k) { return rec.knows.contains(k); }

        /** Hands over a key of knowledge (again, if lost) and remembers that the player learned it. */
        void give(String key) {
            boolean again = rec.knows.contains(key);
            Talk.give(p, Items.key(key));
            rec.knows.add(key);
            plugin.saveStateSoon();
            plugin.getLogger().info("ATLAS_KEY_GIVEN player=" + p.getName() + " key=" + key + " reissue=" + again);
        }

        /** The "I have lost it" answer, offered whenever the player learned a key but no longer carries it. */
        void lost(String key, String label) { if (rec.knows.contains(key) && !Items.has(p, key)) opt(label, "reissue_" + key); }

        boolean reissue(String node) {
            if (!node.startsWith("reissue_")) return false;
            String key = node.substring(8);
            if (!rec.knows.contains(key)) return false;
            give(key);
            return true;
        }

        void learn(String flag) { if (rec.knows.add(flag)) plugin.saveStateSoon(); }
    }

    // ------------------------------------------------------------------ Philon, host of the Threshold

    private static void philon(Ctx c, String node) {
        switch (node) {
            case "where":
                c.say("Atlas, the Bearer: the land that bears the sky. West of you lies the Asterian Concord, our cities and farms and libraries. The White City, Astreion, is just east of this court.")
                 .say("Beyond Astreion is the Lampwall, and beyond that the Cinder Dominion, where the ash falls.\n\nThe ring of quartz and light in this court is a heliodrome. Touch each city's ring once, and after that the rings will carry you between them.");
                c.opt("What is wrong with this land?", "wrong").opt("Farewell.", "close");
                return;
            case "wrong":
                c.say("Three hundred years ago our Star broke. One half, the Hearthstar, still gives us light. The other half, the Cinder Heart, burns black in Anthrakion and makes the ash.")
                 .say("Four of our best took crowns from it. They rule the east now, and our people there are slaves.\n\nWe cannot go deep into the ash: it knows us, and it eats our song. It does not know you.");
                c.opt("What can I do?", "task").opt("Farewell.", "close");
                return;
            case "task":
                c.say("Go to Archon Kleio in the Synedrion, the great hall at the heart of Astreion. She will tell you what we know, and she will ask for what we cannot do ourselves.\n\nNobody will make you go east. We only hope you will.");
                c.opt("How do I get home?", "home").opt("Farewell.", "close");
                return;
            case "home":
                c.say("Walk into the Great Gate here and stand a moment: you will be home, at the gate you came through, or at your world's spawn if that gate is gone. Come back the same way.")
                 .say("You can build a gate of your own: a frame of quartz blocks, shaped like a nether portal, kindled with both halves of the light. Use lapis lazuli and coal on the frame, or throw them in.");
                c.opt("Who are you?", "who").opt("Farewell.", "close");
                return;
            case "codex":
                Talk.give(c.p, Items.codex());
                c.say("Here, a fresh one. It remembers what you have done even if the old one didn't: the Codex reads your deeds, not its pages.");
                c.opt("Farewell.", "close");
                return;
            case "who":
                c.say("Philon, son of nobody in particular. I was a lamplighter on the Royal Road until my knees gave out. Then the Synedrion gave me this Gate, and a stranger every few years.")
                 .say("Some stayed, like the First Stranger there in stone. Some went east and did not come back. I remember every one of them. Keeping strangers is like keeping lamps: most of the work is waiting.");
                c.opt("Farewell.", "close");
                return;
            default:
        }
        if (c.s.victory) {
            c.say("Rekindler! Sit, sit. The Cup is full and so, for once, am I. Look east: the sky is clear over the Plateau for the first time in three hundred years.")
             .say("Your name is on the plinth by the Gate. I carved the first letter myself. My hands shook.");
        } else if (c.first) {
            c.say("Welcome, stranger, welcome! No, you won't need that. You are at the Gate of Strangers, in Atlas, and our first law is that a stranger is owed bread, water, fire and the road.")
             .say("I am Philon. I keep the Threshold, and have for forty years. The Great Gate behind you burns half blue and half black, as our sky once did at dusk. Walk back into it whenever you wish.");
            if (!Items.has(c.p, "codex")) { Talk.give(c.p, Items.codex()); c.say("Take this Codex. It will remember for you what you learn, and what remains."); }
        } else {
            c.say("Back again! Good. Most strangers who go east come back quieter. Sit, if you like.\n\n" + Voices.progress(c.s));
        }
        c.opt("Where am I, exactly?", "where").opt("What is wrong with this land?", "wrong").opt("What can I do?", "task").opt("How do I get home, or come back?", "home");
        if (!Items.has(c.p, "codex")) c.opt("I've lost my Codex.", "codex");
        c.opt("Farewell.", "close");
    }

    // ------------------------------------------------------------------ Kleio Theanid, Archon of the Synedrion

    private static void kleio(Ctx c, String node) {
        if (c.reissue(node)) { c.say("Here. Keep it closer this time; there is only one Hearth."); c.opt("Farewell.", "close"); return; }
        int dark = c.s.wardsDark();
        switch (node) {
            case "crowns":
                c.say("Kallias, the Marshal, holds the Pylon of Teeth, the only gate through the ring wall. His great-granddaughter Lysandra keeps the Oath he swore as a boy. Hold it near him and he can be hurt.")
                 .say("Melaina, the Stiller, keeps the Petrified Weald. Nothing dies near her Fonts. Iaso of Hieranthe will teach you the Hymn that quiets them.")
                 .say("Daidaros, the Forgemaster, hides in his Great Engine. Perdix of Lampsa keeps his old notebook: the Counterpoint, the order in which the Engine's voices can be stilled.")
                 .say("Keleos, the Silent Magistrate, speaks through his Edict Stones in Pellene. Hesper of Mnemeia keeps the Charter he once swore to.\n\nWhen all four crowns are broken, their Wards around Anthrakion go dark and its gate opens. Then come back to me.");
                c.opt("Where should I begin?", "begin").opt("Farewell.", "close");
                return;
            case "begin":
                c.say("Begin at the Stoa of Shields, here in Astreion. Lysandra will give you the Oath. Kallias must fall first: while he holds the Pylon, a Veil of ash turns back anyone who crosses the Teeth.")
                 .say("After him, the Weald, the Forges and Pellene, in any order you like. Philon's Codex will keep your count. So will I.");
                c.opt("Why don't you fight them yourselves?", "why").opt("Farewell.", "close");
                return;
            case "why":
                c.say("Because the ash knows us. We were made by the Star's song, and the Cinder eats song. An Asterian beyond the Lampwall sickens in days, and our automata fail in the ash; their lumen goes out.")
                 .say("And because we tried, in 1203. Forty went east and none came back. We are a careful people now. Some would say a frightened one. They would not be wrong.");
                c.opt("What happened to the eastern cities?", "east").opt("Farewell.", "close");
                return;
            case "east":
                c.learn("asked_east");
                c.say("Pellene and Aigai fell in the Year of Four Crowns. We held the Line. The Record calls it the Withdrawal.\n\n(She looks at you a moment too long.)\n\nRead the Record, if you wish. The Library has it.");
                c.opt("Farewell.", "close");
                return;
            case "vote":
                c.learn("kleio_admitted");
                c.say("You have been talking to Hesper. Or to a clerk of the Sworn.\n\n(She sets down her stylus.)\n\nYes. Seven to five. The Synedrion voted to abandon the eastern cities to save the Line. My grandmother's grandmother voted with the seven.")
                 .say("It saved the Concord. It damned the Bound. Both are true, and the Record says only the first.\n\nWhen this is over I will read the vote aloud in the Synedrion, with the names. You may hold me to that.");
                c.opt("I will.", "close");
                return;
            case "light":
                if (c.s.victory) { c.say("The Light is whole again, and so is the Star. What you carry is yours now; keep it."); c.opt("Farewell.", "close"); return; }
                if (dark < 4) {
                    c.say("The Light of Theano is the Hearthstar's own light, and the only thing the Pyrarch fears. I will lend it when all four Wards around Anthrakion are dark, and not before: lost in the ash, it would leave us nothing.\n\n" + dark + " of the four Wards are dark.");
                    c.opt("Farewell.", "close");
                    return;
                }
                c.say("The Wards are dark. All four. I did not think I would live to say it.\n\nThis is the Light of Theano, lent from the Hearth. The two halves of the Star remember each other.")
                 .say("Climb Anthrakion. When the Pyrarch hides behind the Cinder Heart, stand at the Heart's root, the black column above his throne, and hold the Light up. Hold it until the Heart remembers.");
                c.give("light");
                c.opt("I will bring it back.", "close");
                return;
            case "amends": {
                int fine = Reputation.fine(c.rec.standing);
                if (fine == 0) { c.say("There is nothing to mend. The Concord has no quarrel with you."); c.opt("Farewell.", "close"); return; }
                c.say("The Synedrion does not want your emeralds; it wants to see that you understand what you did. The fine is " + fine + " emeralds, paid to the House of Return.\n\nPaid, your standing is restored to that of a stranger.");
                c.opt("Pay " + fine + " emeralds.", "pay").opt("Not now.", "close");
                return;
            }
            case "pay": {
                int fine = Reputation.fine(c.rec.standing);
                if (fine > 0 && c.plugin.reputation().payFine(c.p, fine)) {
                    c.say("It is paid. The Concord remembers the payment longer than the wrong. Go well.");
                    c.plugin.getLogger().info("ATLAS_FINE_PAID player=" + c.p.getName() + " emeralds=" + fine);
                } else c.say("You do not carry " + fine + " emeralds. Come back when you do; the fine does not grow.");
                c.opt("Farewell.", "close");
                return;
            }
            default:
        }
        if (c.s.victory) c.say("Rekindler. The Synedrion stood when your name was read. So did I, and I have not stood for anyone in eleven years.\n\nI read the vote of 1127 aloud, as I promised. With the names. It was the hardest speech of my life, and the best.");
        else if (c.first) c.say("So you are the stranger Philon sent word of. Sit. I am Kleio Theanid, Archon of the Synedrion this year, which means I am the one who must ask you for things we have no right to ask.")
            .say("Four Ash-Crowned rule the Dominion under the Pyrarch, who broke our Star. Each was once the best of us. Each can be broken, but only with what they forgot. We have kept what they forgot.");
        else c.say("Stranger. " + Voices.progress(c.s) + (dark == 4 && !Items.has(c.p, "light") ? "\n\nThe Wards are dark. Ask me for the Light." : ""));
        c.opt("Tell me about the four crowns.", "crowns").opt("Where should I begin?", "begin").opt("What is the Light of Theano?", "light");
        if (c.knows("withdrawal")) c.opt("Tell me about the vote of 1127.", "vote"); else c.opt("What happened to the eastern cities?", "east");
        c.lost("light", "I have lost the Light.");
        if (c.rec.standing < 0) c.opt("I want to make amends.", "amends");
        c.opt("Farewell.", "close");
    }

    // ------------------------------------------------------------------ Lysandra Kallid, Strategos of the Line

    private static void lysandra(Ctx c, String node) {
        if (c.reissue(node)) { c.say("Another copy. Fair hand, no smudges. Don't lose this one in a bog."); c.opt("Farewell.", "close"); return; }
        switch (node) {
            case "use":
                c.say("Hold the Oath in your hand, main hand or off hand, and stay within twenty-four paces of him. While someone near him holds it, his own promise is louder than the crown and steel will cut him. Let it drop and he knits himself back together.")
                 .say("Bring friends: one to hold the Oath, the rest to strike. Or hold it yourself and fight one-handed. He won't make it easy.");
                c.opt("What is the Veil?", "veil").opt("Farewell.", "close");
                return;
            case "kallias":
                c.say("He wanted to end war. All war. He thought if he held the Teeth forever there would never be another battle on the plain. The crown told him he could.")
                 .say("It was right. There hasn't been a battle since. Only slaughter.\n\nHis letters to his wife are in the Great Library. Read them before you go. Then go anyway.");
                c.opt("How do I use the Oath?", "use").opt("Farewell.", "close");
                return;
            case "veil":
                c.say("The Teeth are a ring wall round the inner Dominion. Kallias keeps the only gate, the Pylon, on the Royal Road. Cross the Teeth anywhere else and you walk into the Veil: ash thick enough to choke on.")
                 .say("It only thins when he falls. So: through the Pylon, or nowhere.");
                c.opt("Farewell.", "close");
                return;
            case "end":
                c.say("He asked about the shield.\n\n(She laughs, badly.)\n\nIt's polished. I polish it every morning. I'll keep doing it.\n\nThank you. For finishing what his oath started.");
                c.opt("Farewell.", "close");
                return;
            default:
        }
        if (c.fallen("kallias")) {
            c.say("You broke him.\n\n(She is quiet for a while.)\n\nDid he say anything, at the end?");
            c.opt("He asked about his shield.", "end");
        } else if (!c.knows("oath")) {
            c.say("Stranger. Kleio said you might come. I'm Lysandra Kallid, Strategos of the Line. Yes, Kallid. Kallias was my great-grandfather's father. We don't say it loudly in Astreion.")
             .say("He swore the Oath of the Shield in this Stoa at seventeen. His shield still hangs on that wall, the Oath beneath it. Take this copy. It's the only weapon I have that he can't beat.");
            c.give("oath");
        } else c.say("Back from the Wound? Keep your shield up and your Oath closer.");
        c.opt("How do I use the Oath?", "use").opt("Tell me about him.", "kallias").opt("What is the Veil?", "veil");
        c.lost("oath", "I have lost the Oath.");
        c.opt("Farewell.", "close");
    }

    // ------------------------------------------------------------------ Iaso, Priestess of the Asklepieion

    private static void iaso(Ctx c, String node) {
        if (c.reissue(node)) { c.say("Of course. We have sung it for a thousand years; we can spare another copy. Take it."); c.opt("Farewell.", "close"); return; }
        switch (node) {
            case "fonts":
                c.say("Her Stilled Garden lies in the north of the Weald, inside the Teeth. Three Stilling Fonts stand around her couch: black water that refuses to flow.")
                 .say("Hold the Hymn and touch each Font. When all three remember what water is for, she can be hurt, and not before.\n\nWhile any Font sings, those who fall near her rise again. Be quick.");
                c.opt("What happens to the stilled?", "stilled").opt("Farewell.", "close");
                return;
            case "stilled":
                c.say("They do not die, and they do not live. They look at you.\n\nWhen she falls and the Fonts are quiet, they will be allowed to rest. It will be the kindest thing anyone has done for them in three hundred years.");
                c.opt("Farewell.", "close");
                return;
            case "melaina":
                c.say("She taught me. She was the best healer this house ever had. Her daughter died of the ash-fever and she could not bear it, and so she took a crown that promised no one would ever die again.\n\nNow no one in the Weald is allowed to.");
                c.opt("How do I silence the Fonts?", "fonts").opt("Farewell.", "close");
                return;
            default:
        }
        if (c.fallen("melaina")) c.say("Chrysa told me the Fonts are water again, and that the stilled lay down.\n\nThank you. I will sing for her tonight. For Melaina, I mean. Someone should.");
        else if (!c.knows("hymn")) {
            c.say("Come in out of the sun, stranger. This is the Asklepieion, the house of healing, and I am Iaso. Most who come here want to be kept alive a little longer. You, I think, have come to learn how to let something die.");
            c.say("This is the Hymn of Passage. We sing it at every death. Sing it to her Fonts.");
            c.give("hymn");
        } else c.say("The Hymn is not a spell, you know. It is a promise that we will keep the name. Hold it, and mean it.");
        c.opt("How do I silence the Fonts?", "fonts").opt("Tell me about Melaina.", "melaina").opt("What happens to the stilled?", "stilled");
        c.lost("hymn", "I have lost the Hymn.");
        c.opt("Farewell.", "close");
    }

    // ------------------------------------------------------------------ Perdix, Mechanic of the Mechaneion

    private static void perdix(Ctx c, String node) {
        if (c.reissue(node)) { c.say("Lost it? In the Forges? Of course you did, everything melts there. Here: leaves eleven to seventeen, copied again."); c.opt("Farewell.", "close"); return; }
        switch (node) {
            case "engine":
                c.say("The Great Engine is a harmonic resonator the size of a tower, in the south of the Forges. Three Governor crystals hold its note: one deep in the pit, one on the engine floor, one at the crown.")
                 .say("While they sing, the Engine throws a shield round Daidaros and nothing gets through. Strike a Governor while you carry the Counterpoint and you'll find the point where its note breaks. That's the trick. The order is the rest of it.");
                c.opt("In what order?", "order").opt("Farewell.", "close");
                return;
            case "order":
                c.say("Read it! Really, read the book; I'd rather you learned it from him than from me.\n\n(He can't help himself.)\n\nDeep, middle, high. Pit, floor, crown.")
                 .say("Get it wrong and the silenced ones start singing again, and the Engine takes it out on you. He tested it. He had the scar.");
                if (c.knows("vesk_governors")) c.say("A Gnawling told you the crown first? Of course he did. Gnawlings love watching people get hurt by machinery.");
                c.opt("Farewell.", "close");
                return;
            case "guild":
                c.learn("guild");
                c.say("(He stops fidgeting.)\n\nNausikaa told you. Yes. The Guild of the Anvil bought ash-iron through Aigai's old smugglers for sixty years. It's harder than anything we make, and cheaper. Nobody asked who dug it.")
                 .say("My father was a Guild master. I found his ledgers, and I burned them. I shouldn't have. Hesper would have kept them. That's the whole of it.");
                c.opt("Farewell.", "close");
                return;
            default:
        }
        if (c.fallen("daidaros")) c.say("The Engine's stopped. I could feel it from here, you know, like a tooth that stops aching. Ktesias came home. He's sitting at his old bench. He hasn't touched anything yet.");
        else if (!c.knows("counterpoint")) {
            c.say("Oh! A stranger. Mind the... yes, that one bites. Perdix, Mechanic, at your service. You want the Counterpoint. Everyone Kleio sends wants the Counterpoint. Well: two people, in three hundred years.");
            c.say("It's from Daidaros's own notebook. He left it on this desk the night he went east. Leaves eleven to seventeen, copied for you, fair hand.");
            c.give("counterpoint");
        } else c.say("Back! Did the Engine...? No. Not yet. Well. Mind the Governors.");
        c.opt("How does the Engine work?", "engine").opt("In what order?", "order");
        if (c.knows("ashiron")) c.opt("Tell me about the Guild of the Anvil.", "guild");
        c.lost("counterpoint", "I have lost the Counterpoint.");
        c.opt("Farewell.", "close");
    }

    // ------------------------------------------------------------------ Hesper, Archivist of Memory

    private static void hesper(Ctx c, String node) {
        if (c.reissue(node)) { c.say("A copy, checked twice against the original. The Archive does not lose things. You may."); c.opt("Farewell.", "close"); return; }
        switch (node) {
            case "stones":
                c.say("Three Edict Stones stand in the square before the Hall of Edicts in Pellene, in the east of the Fallen Cities. They repeat his voice. Near them you agree before you know you have spoken; you will feel it as a heaviness.")
                 .say("Hold the Charter and touch each stone. When all three are silent, the Magistrate on his black chair is only a man.\n\nDo not linger near a speaking stone without the Charter in your hand.");
                c.opt("Farewell.", "close");
                return;
            case "keleos":
                c.say("He believed a perfect law would make choice unnecessary. He was wrong, but he was wrong carefully, and for a long time.\n\nThe crown gave him a voice that chooses for you. He calls it mercy.");
                c.opt("How do the Edict Stones work?", "stones").opt("Farewell.", "close");
                return;
            case "vote":
                c.learn("withdrawal");
                c.say("Yes. The Archive keeps everything the Synedrion would prefer it didn't. The vote of YL 1127, to withdraw behind the Line: seven to five. The names are in the Register of Votes, third case, east wall.")
                 .say("Kleio's ancestor voted with the seven. So did mine.\n\nWe do not keep records so that we will feel better. We keep them so that we will know.");
                c.opt("Farewell.", "close");
                return;
            default:
        }
        if (c.fallen("keleos")) c.say("The Stones are silent and Pellene is talking. All of it at once, I am told. Erinna has sent me forty pages already. I shall have to build a new case.");
        else if (!c.knows("charter")) {
            c.say("You are standing on a mosaic of the first map of Atlas. Please do not step on the Lampwall; it is chipped. I am Hesper, Archivist of Memory. I keep the Charter, among other things.");
            c.say("Keleos wrote the commentary on its First Article himself, at twenty-six. This is a copy, checked twice. Read the First Article to his Edict Stones and they will hear their master disagree with them.");
            c.give("charter");
        } else c.say("You have the Charter. Read the First Article again before you go. Then once more at the stones.");
        c.opt("How do the Edict Stones work?", "stones").opt("Tell me about Keleos.", "keleos").opt("Does the Archive keep the vote of 1127?", "vote");
        c.lost("charter", "I have lost the Charter.");
        c.opt("Farewell.", "close");
    }

    // ------------------------------------------------------------------ Eudora, Librarian of Astreion

    private static void eudora(Ctx c, String node) {
        switch (node) {
            case "read":
                c.say("For the history: A Short History of the Concord, then The Year of Four Crowns. For the Marshal: Letters to Euthymia.")
                 .say("For the truth, and it isn't comfortable: What the Record Omits, and On the Withdrawal of 1127. Read them side by side and notice what each leaves out.\n\nFor the Pyrarch, his own words: On Harmony. He was brilliant. That's the frightening part.");
                c.opt("Where are the Dominion's writings?", "dominion").opt("Farewell.", "close");
                return;
            case "dominion":
                c.say("We have a little, bought or stolen: the Litany of the Unlit, the Catechism of the One Note. The rest is in the east: orders in the war-camps, the Forgemaster's requisitions, the Magistrate's edicts, and the Keeper's own journals in Anthrakion.")
                 .say("Bring me copies! I pay in gratitude, and occasionally in bread.");
                c.opt("Farewell.", "close");
                return;
            case "rider":
                if (!c.rec.claimed.contains("rider_list") && takeMarked(c.p, "book_rider_list")) {
                    c.rec.claimed.add("rider_list");
                    Talk.give(c.p, new org.bukkit.inventory.ItemStack(Material.EMERALD, 8));
                    c.plugin.reputation().add(c.p, 15, "you brought the Rider's List home");
                    c.plugin.getLogger().info("ATLAS_REWARD player=" + c.p.getName() + " reward=rider_list");
                    c.say("(She takes it in both hands.)\n\nThe Rider's List. Every name. Every deme. Every age. Philippos said... (She has to sit down.)")
                     .say("The Concord promised to come back for every one of these people. Now at least we know who they were. Thank you. Take this, from the Library's own purse.");
                } else c.say("The Rider's List is safe in the Register now. We read ten names from it every morning, aloud. It will take eleven years.");
                c.opt("Farewell.", "close");
                return;
            default:
        }
        c.say(c.first ? "A reader! Welcome to the Great Library: everything the Concord has written, and several things it wishes it hadn't. I'm Eudora."
            : "Back for more? Good. The shelves are patient, but I'm not.");
        c.say("Right-click any shelf to read what's on it. Sneak and right-click to borrow a copy. We trust you to bring it back. (We never get them back.)");
        c.opt("What should I read first?", "read").opt("Where are the Dominion's writings?", "dominion");
        if (Items.has(c.p, "book_rider_list")) c.opt("I have the Rider's List.", "rider");
        c.opt("Farewell.", "close");
    }

    private static boolean takeMarked(Player p, String id) {
        org.bukkit.inventory.ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) if (Items.is(inv[i], id)) { p.getInventory().setItem(i, null); return true; }
        return false;
    }

    // ------------------------------------------------------------------ Anaxis the Quiet, philosopher of the Lyceum

    private static void anaxis(Ctx c, String node) {
        switch (node) {
            case "unwelcome":
                c.learn("lyceum_weapons");
                c.say("That the Lyceum designed the harmonic pikes the Ashborn carry. That the Concord bought iron it knew was dug by slaves. That our Line protects us, and not the Kelani, who lived east of it.")
                 .say("That the Withdrawal was a choice, and the Record is written so that it sounds like weather.");
                c.opt("Is the Concord good?", "good").opt("Farewell.", "close");
                return;
            case "good":
                c.say("Better than the Dominion. That is a very low wall to climb, and we climb it every morning and congratulate ourselves.\n\nGood is not a place you arrive. It's a direction you keep walking in, and you have to keep checking the road.");
                c.opt("What do you think of the Pyrarch?", "pyrarch").opt("Farewell.", "close");
                return;
            case "pyrarch":
                c.say("Phosphoros believed harmony was obedience to the right note. He wasn't a monster. He was a musician who thought everyone else was out of tune.")
                 .say("Read his journals, if you reach Anthrakion. You will agree with him more often than you'd like. Then remember what the agreement cost.");
                c.opt("Why are you telling me this?", "why").opt("Farewell.", "close");
                return;
            case "why":
                c.say("Because you will decide what Atlas looks like when this is over, whether you mean to or not. People listen to whoever wins.\n\nI would like you to have heard at least one person who wasn't grateful.");
                c.opt("Farewell.", "close");
                return;
            default:
        }
        if (c.s.victory) c.say("So. You won. Now comes the hard part: what we do with a whole Star, and whether we deserve it. Come and argue with me some time.");
        else c.say(c.first ? "Ah. The stranger. Sit, or don't; I've never believed in telling people where to stand. I'm Anaxis. They call me the Quiet because I say unwelcome things softly, so that people have to lean in to hear them."
            : "The stranger returns. Have you found anything you didn't want to know yet?");
        c.opt("What unwelcome things?", "unwelcome").opt("Is the Concord good?", "good").opt("What do you think of the Pyrarch?", "pyrarch").opt("Why tell me this?", "why").opt("Farewell.", "close");
    }

    // ------------------------------------------------------------------ Neaira, Keeper of the Hearth

    private static final java.util.Map<java.util.UUID, Long> blessed = new java.util.HashMap<>();

    private static void neaira(Ctx c, String node) {
        switch (node) {
            case "theano":
                c.say("Theano was Phosphoros's student. When he tried to conduct the Star alone, she was the only one in the Heliotheion with him. The Star broke in her hands.")
                 .say("She ran west with the half she held, forty leagues on foot, and never let it cool. She never went back. She never forgave him, and she never stopped wishing she could.");
                c.opt("Can the Star be made whole?", "whole").opt("Farewell.", "close");
                return;
            case "whole":
                c.say("The two halves remember each other; the Keepers have always taught it. If the Hearthstar's light is carried into the Cinder Heart, the Heart will remember what it was.\n\nThe Archon holds the Light. She will not lend it lightly.");
                c.opt("Farewell.", "close");
                return;
            case "bless": {
                long now = System.currentTimeMillis();
                Long last = blessed.get(c.p.getUniqueId());
                if (last != null && now - last < 300_000L) { c.say("The Hearth's blessing is still on you. Go on, before it cools."); c.opt("Farewell.", "close"); return; }
                blessed.put(c.p.getUniqueId(), now);
                c.p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 45, 1, true, false), true);
                c.p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 3, 0, true, false), true);
                c.say("(She touches your forehead with warm ash from the Hearth's rim.)\n\nGo under the Lamp. May the road rise to meet your feet, and may you come back along it.");
                c.opt("Thank you.", "close");
                return;
            }
            default:
        }
        if (c.s.victory) c.say("Look at it. The Hearthstar has never burned like this in my lifetime, or my mother's. It knows. It knows its other half is quiet at last.");
        else c.say("Softly, stranger. This is the Hearth of Theano, and the light behind me is the Hearthstar: the blue half of our Star, carried here by a girl of sixteen on the night it broke. I am Neaira. I keep it.");
        c.opt("Tell me about Theano.", "theano").opt("Can the Star be made whole?", "whole").opt("Bless me before I go east.", "bless").opt("Farewell.", "close");
    }

    // ------------------------------------------------------------------ Menon, Lochagos of the Last Watch

    private static void menon(Ctx c, String node) {
        switch (node) {
            case "brief":
                c.say("The Royal Road runs east through our gate, across the Wound, to the Pylon of Teeth. Kallias holds the Pylon. Everything inside the Teeth is closed to you until he falls: the Veil will choke you if you try the wall.")
                 .say("The Marches are war-camps, pens and watchtowers. Kill a camp's Taskmaster, then break its shackle post, and the whole camp walks free.");
                c.opt("What's in the Wound?", "wound").opt("Who needs rescuing?", "captives").opt("Farewell.", "close");
                return;
            case "wound":
                c.say("No-man's-land. Craters, dead Talos, the outposts we lost. Mostly empty. Mostly.\n\nThere's an Ashborn deserter hiding in a ruined post south of the road. Calls himself Uzgar. Hasn't killed anyone I know of. Bread makes him talk.");
                c.opt("Farewell.", "close");
                return;
            case "captives": {
                StringBuilder b = new StringBuilder();
                int left = 0;
                for (Lore.Captive k : Lore.CAPTIVES.values()) {
                    if (c.s.captivesRescued.containsKey(k.id)) continue;
                    if (left++ < 4) b.append("\n- ").append(k.name).append(", ").append(k.title).append(", in ").append(k.province.title);
                }
                if (left == 0) c.say("None that I know of. The House of Return is full, and every bed has a name on it. Good work.");
                else c.say("Still held, by our reckoning (" + left + " in all):" + b + (left > 4 ? "\n...and more." : "") + "\n\nBring them out and the House of Return in Astreion will take them in.");
                c.opt("Farewell.", "close");
                return;
            }
            case "ashborn":
                c.say("Grunts come in packs. Bowmen stay back: close with them. Blackshields: hit, step back, hit. Wargs follow scent; running water loses them. Trolls: don't.")
                 .say("Taskmasters carry the camp in their heads; kill them first. Gnawlings steal. Watch your pockets.");
                c.opt("Farewell.", "close");
                return;
            default:
        }
        if (c.s.victory) c.say("Stranger. Rekindler. Whichever. The Line's lamps are burning for nobody tonight. First time in three hundred years. I don't know what to do with my hands.");
        else if (c.fallen("kallias")) c.say("The Pylon's open. Never thought I'd say it. The Teeth still bite: go careful inside.");
        else c.say("Stranger. Menon. I hold the Last Watch. East of this wall you're on your own. Listen once and you might come back.");
        c.opt("Brief me.", "brief").opt("What's in the Wound?", "wound").opt("Who needs rescuing?", "captives").opt("What of the Ashborn?", "ashborn").opt("Farewell.", "close");
    }

    // ------------------------------------------------------------------ Uzgar, the Ashborn deserter

    private static void uzgar(Ctx c, String node) {
        boolean fed = c.rec.claimed.contains("uzgar_fed");
        switch (node) {
            case "why":
                c.say("In the Tower of the Heart there is a room where they sing. Chained. One note. Uzgar was guard there.")
                 .say("One night Uzgar listened. Under the note they were singing something else, very quiet. Uzgar walked out. Nobody stopped him. Nobody thinks an Ashborn walks out.");
                c.opt("Farewell.", "close");
                return;
            case "bread":
                if (fed) { c.say("Uzgar has eaten. Uzgar told you already. Uzgar does not sell the same thing twice."); c.opt("Tell me again.", "tell").opt("Farewell.", "close"); return; }
                if (!Items.take(c.p, Material.BREAD, 3)) { c.say("(He looks at your hands, then your face, and nods.)\n\nThree bread. Uzgar is hungry, not a thief."); c.opt("Farewell.", "close"); return; }
                c.rec.claimed.add("uzgar_fed");
                c.plugin.saveStateSoon();
                c.plugin.getLogger().info("ATLAS_BARGAIN player=" + c.p.getName() + " with=uzgar paid=bread");
                c.say("(He eats like someone who has forgotten how.)\n\nGood. Uzgar pays.");
                tellUzgar(c);
                c.opt("Farewell.", "close");
                return;
            case "tell":
                if (!fed) break;
                tellUzgar(c);
                c.opt("Farewell.", "close");
                return;
            default:
        }
        if (c.s.liberated(Realm.Province.MARCHES)) c.say("The Marches are quiet. The drums stopped. Uzgar stays. Uzgar has found seeds. Uzgar will see what grows in ash.");
        else if (c.first) c.say("(The Ashborn raises empty hands.)\n\nNo fight. Uzgar does not fight now. You are the stranger. The ash does not know you. It knows Uzgar. Too well.");
        else c.say("Stranger. Still alive. Good. Few are.");
        if (!fed) c.opt("Here. Bread. (3 bread)", "bread"); else c.opt("Tell me again what you know.", "tell");
        c.opt("Why did you leave?", "why").opt("Farewell.", "close");
    }

    private static void tellUzgar(Ctx c) {
        c.learn("uzgar");
        c.say("One: the Marshal walks the west yard of the Pylon, behind the black fence. He talks to himself. About a promise. Every day the same words, and he never finishes them.")
         .say("Two: the Veil is his. It is not the ash. It is him, holding the Teeth shut. He falls, it falls.\n\nThree: the drummer in the war-camp north, Gorvash. Big. Slow to turn. Go behind.");
    }

    // ------------------------------------------------------------------ Vesk, the Gnawling informer

    /** Vesk's secrets, in the order he sells them. True ones and lies; the lies are what he would profit from. */
    static final String[][] VESK = {
        {"true", "The Rider rides out of his tower in the south of the Marches at night and comes back before dawn. A scribe is kept on the top floor. Vesk heard him crying. Scribes cry a lot."},
        {"lie", "Governors in the Great Engine? Easy. Highest first: the one at the crown. Then down, down, down. Vesk is sure. Vesk is very sure."},
        {"true", "Mother Sallow's house is west of the Stilled Garden, in the Weald. She keeps a midwife there. The door squeaks. Sallow does not like singing."},
        {"lie", "There is a gap in the Teeth, north, where the old aqueduct crossed. No Veil there. Vesk walked through it himself. Twice!"},
        {"true", "Grunnak the troll sleeps at the bottom of the Slag Quarry, east of the Engine. When the Forgemaster falls, his trolls go to stone. Vesk would wait. Vesk is patient."},
        {"true", "The clerk Thersites keeps a second ledger. Kalchas locked him in Aigai's council house for it. The ledger has names. Concord names. The Concord will not like the names."},
    };

    private static void vesk(Ctx c, String node) {
        int sold = 0;
        for (String k : c.rec.claimed) if (k.startsWith("vesk_")) sold++;
        switch (node) {
            case "buy": {
                if (sold >= VESK.length) { c.say("Vesk has no more secrets. Vesk has some lies left, but those are for the Ashborn."); c.opt("Farewell.", "close"); return; }
                if (!Items.take(c.p, Material.EMERALD, 2)) { c.say("No green stones, no secrets. Vesk has rules. Two rules. That is one of them."); c.opt("Farewell.", "close"); return; }
                String[] secret = VESK[sold];
                c.rec.claimed.add("vesk_" + sold);
                if (sold == 1) c.learn("vesk_governors");
                c.plugin.saveStateSoon();
                c.plugin.getLogger().info("ATLAS_BARGAIN player=" + c.p.getName() + " with=vesk secret=" + sold + " truth=" + secret[0]);
                c.say("(The emeralds vanish. You did not see where.)\n\n" + secret[1]);
                c.opt(sold + 1 < VESK.length ? "Another. (2 emeralds)" : "Anything else?", "buy").opt("Farewell.", "close");
                return;
            }
            case "kinds":
                c.say("Where the pens are. Who the Rider takes. Which Governor sings deepest. How to get past the Teeth without the Marshal. Vesk knows everything!\n\nVesk knows some things.");
                c.opt("Why should I trust you?", "trust").opt("Farewell.", "close");
                return;
            case "trust":
                c.say("Vesk does not ask you to trust. Vesk asks you to pay. Trust costs extra.");
                c.opt("Farewell.", "close");
                return;
            default:
        }
        if (c.s.liberated(Realm.Province.MARCHES)) c.say("Stranger! The Marshal is gone and the Ashborn are gone and nobody buys secrets now. Vesk is poor. Vesk is free. Vesk does not know which is worse.");
        else c.say("Ssss! Stranger! Vesk sees you, Vesk hears you. Vesk knows things. Vesk sells things. Two green stones, one secret. Fair price. Vesk is fair. Mostly.");
        if (sold < VESK.length) c.opt("Here are two emeralds. Tell me something.", "buy");
        c.opt("What kind of secrets?", "kinds").opt("Why should I trust you?", "trust").opt("Farewell.", "close");
    }
}
