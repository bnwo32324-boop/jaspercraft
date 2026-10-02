package chat.jaspr.bounties;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JasprBounties: the bounty board (owner, 2026-10-02: "Bounty board ... This should also be accessible through a command.
 * Reminders should be posted in the chat on occasions"). Every player gets three daily bounties (easy, medium, hard) and
 * two weekly ones, rolled from their own id and the date, with rewards in experience (the stat sheet's currency), useful
 * materials and, on the hardest, a chance at a trinket. A realm's bounties join a player's board once they have been
 * there. Open the board with /bounty (aliases /bounties, /contracts) or a "[Bounties]" sign; reminders come in chat now and
 * then, on joining, on finishing a bounty and before the dailies reset.
 *
 * <p>Logs BOUNTIES_READY, BOUNTY_ROLL, BOUNTY_DONE, BOUNTY_CLAIM, BOUNTY_REROLL, BOUNTY_STREAK and BOUNTIES_METRICS.
 */
public final class BountiesPlugin extends JavaPlugin {
    static final String VERSION = "1.0.0";

    private final Random random = new Random();
    private Store store;
    private Rewards rewards;
    private BoardMenu menu;
    private Tracker tracker;
    private ZoneId zone;
    private long remindEveryMs, joinDelayMs;
    long completed, claimed, rolled, rerolled, reminders, broadcasts, streaks;

    Random random() { return random; }
    Store store() { return store; }
    Rewards rewards() { return rewards; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String tz = getConfig().getString("timezone", "");
        try { zone = tz == null || tz.isEmpty() ? ZoneId.systemDefault() : ZoneId.of(tz); }
        catch (RuntimeException bad) { zone = ZoneId.systemDefault(); getLogger().warning("BOUNTIES_TIMEZONE_INVALID value=" + tz); }
        remindEveryMs = Math.max(5, getConfig().getInt("reminders.every-minutes", 30)) * 60_000L;
        joinDelayMs = Math.max(10, getConfig().getInt("reminders.after-join-seconds", 60)) * 1000L;
        int[] xp = {getConfig().getInt("rewards.xp.easy", 150), getConfig().getInt("rewards.xp.medium", 350),
            getConfig().getInt("rewards.xp.hard", 700), getConfig().getInt("rewards.xp.weekly", 2000)};
        double[] trinket = {0, 0, getConfig().getDouble("rewards.trinket-chance.hard", 0.10), getConfig().getDouble("rewards.trinket-chance.weekly", 0.50)};
        rewards = new Rewards(this, xp, trinket, getConfig().getInt("rewards.streak-xp", 100));
        store = new Store(getDataFolder());
        store.loadBoard();
        menu = new BoardMenu(this);
        tracker = new Tracker(this);
        getServer().getPluginManager().registerEvents(menu, this);
        getServer().getPluginManager().registerEvents(tracker, this);
        for (Player p : getServer().getOnlinePlayers()) joined(p);
        getServer().getScheduler().runTaskTimer(this, this::pulse, 200L, 200L);   // every 10 s
        getServer().getScheduler().runTaskTimer(this, store::saveAll, 1200L, 1200L);
        getLogger().info("BOUNTIES_READY version=" + VERSION + " templates=" + Template.ALL.size() + " daily=" + PlayerBoard.DAILY
            + " weekly=" + PlayerBoard.WEEKLY + " zone=" + zone + " remindMinutes=" + remindEveryMs / 60_000L);
    }

    @Override
    public void onDisable() {
        if (store == null) return;
        store.saveAll();
        getLogger().info("BOUNTIES_METRICS rolled=" + rolled + " rerolled=" + rerolled + " completed=" + completed + " claimed=" + claimed
            + " streaks=" + streaks + " reminders=" + reminders + " broadcasts=" + broadcasts + " trinkets=" + rewards.trinketsGiven
            + " trinketsMissing=" + rewards.trinketsMissing + " saves=" + store.saves + " saveFailures=" + store.saveFailures
            + " placedSkips=" + tracker.placedSkips + " silkSkips=" + tracker.silkSkips);
    }

    // ---- days and weeks ----------------------------------------------------------------------------------------------

    long today() { return LocalDate.now(zone).toEpochDay(); }

    long thisWeek() { return LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay(); }

    /** Time until the dailies (or, when weekly, the weeklies) reset, in words: "4h 10m". */
    String untilReset(boolean weekly) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate next = weekly ? now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)) : now.toLocalDate().plusDays(1);
        long minutes = Math.max(1, Duration.between(now, next.atStartOfDay(zone)).toMinutes());
        return minutes >= 1440 ? (minutes / 1440) + "d " + (minutes % 1440) / 60 + "h" : minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    long minutesToDailyReset() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        return Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)).toMinutes();
    }

    // ---- boards ------------------------------------------------------------------------------------------------------

    /** The player's board, rolled over to today and this week if needed. */
    PlayerBoard board(Player p) {
        PlayerBoard b = store.load(p.getUniqueId(), p.getName());
        ensure(b, p);
        return b;
    }

    void joined(Player p) {
        PlayerBoard b = board(p);
        Template.Realm here = Template.Realm.ofWorld(p.getWorld().getName());
        if (here != null && b.realms.add(here)) b.dirty = true;
        for (String owed : new ArrayList<>(b.owed)) rewards.payOwed(p, owed);
        if (!b.owed.isEmpty()) {
            p.sendMessage(ChatColor.GOLD + "[Bounties] " + ChatColor.GRAY + "Rewards you had earned while away were added to your inventory.");
            b.owed.clear();
            b.dirty = true;
        }
        b.nextReminder = System.currentTimeMillis() + joinDelayMs;
    }

    void quit(Player p) { store.unload(p.getUniqueId()); }

    /** Rolls new dailies on a new day and new weeklies on a new week, paying what was finished but never claimed. */
    void ensure(PlayerBoard b, Player p) {
        long day = today(), week = thisWeek();
        boolean fresh = b.day == Long.MIN_VALUE;
        if (b.day != day) {
            settle(b, b.daily, p);
            Arrays.fill(b.daily, null);
            for (int i = 0; i < PlayerBoard.DAILY; i++) { b.daily[i] = b.rollDaily(i, day, 0, rewards); start(b.daily[i], p); }
            b.day = day;
            b.rerolls = 0;
            b.dirty = true;
            rolled++;
            getLogger().info("BOUNTY_ROLL player=" + b.name + " day=" + day + " ids=" + ids(b.daily));
            if (!fresh && p != null && p.isOnline())
                link(p, ChatColor.GOLD + "[Bounties] " + ChatColor.WHITE + "New daily bounties are on the board. " + ChatColor.YELLOW + "/bounty");
        }
        if (b.week != week) {
            settle(b, b.weekly, p);
            Arrays.fill(b.weekly, null);
            for (int i = 0; i < PlayerBoard.WEEKLY; i++) { b.weekly[i] = b.rollWeekly(i, week, rewards); start(b.weekly[i], p); }
            b.week = week;
            b.dirty = true;
            getLogger().info("BOUNTY_ROLL player=" + b.name + " week=" + week + " ids=" + ids(b.weekly));
        }
    }

    private static String ids(Bounty[] slots) {
        StringBuilder s = new StringBuilder();
        for (Bounty x : slots) { if (s.length() > 0) s.append(','); s.append(x == null ? "-" : x.id + ":" + x.need); }
        return s.toString();
    }

    /** Travel bounties count from the moment they are posted. */
    void start(Bounty x, Player p) {
        if (x != null && x.template() != null && x.template().goal == Template.Goal.TRAVEL && p != null) x.base = travelled(p);
    }

    /** Finished-but-unclaimed bounties are paid when their board resets (now if online, else on the next login). */
    private void settle(PlayerBoard b, Bounty[] slots, Player p) {
        for (Bounty x : slots) {
            if (x == null || x.state != Bounty.State.DONE) continue;
            x.state = Bounty.State.CLAIMED;
            claimed++;
            if (p != null && p.isOnline()) {
                String got = rewards.pay(p, x);
                p.sendMessage(ChatColor.GOLD + "[Bounties] " + ChatColor.GRAY + "Unclaimed reward for " + ChatColor.WHITE + x.title() + ChatColor.GRAY + ": " + got);
            } else {
                b.owed.add("xp:" + x.xp);
                if (x.item != null && x.amount > 0) b.owed.add("item:" + x.item.name() + ":" + x.amount);
                if (x.trinket > 0 && random.nextDouble() < x.trinket) b.owed.add("trinket");
            }
            getLogger().info("BOUNTY_CLAIM player=" + b.name + " id=" + x.id + " auto=true");
        }
    }

    /** Distance travelled in centimetres, the way the vanilla statistics count it. */
    static long travelled(Player p) {
        long cm = 0;
        for (Statistic s : new Statistic[]{Statistic.WALK_ONE_CM, Statistic.SPRINT_ONE_CM, Statistic.CROUCH_ONE_CM, Statistic.SWIM_ONE_CM,
            Statistic.HORSE_ONE_CM, Statistic.BOAT_ONE_CM, Statistic.PIG_ONE_CM, Statistic.MINECART_ONE_CM}) {
            try { cm += p.getStatistic(s); } catch (IllegalArgumentException ignored) { }
        }
        return cm;
    }

    // ---- progress ----------------------------------------------------------------------------------------------------

    /** Adds progress to every open bounty of the player's board that the test accepts. */
    void progress(Player p, Template.Goal goal, Predicate<Template> test, int amount) {
        if (amount <= 0) return;
        PlayerBoard b = board(p);
        for (Bounty x : b.all()) {
            Template t = x.template();
            if (t == null || !x.open() || t.goal != goal || !test.test(t)) continue;
            boolean done = x.add(amount);
            b.dirty = true;
            if (done) complete(p, b, x);
            else halfway(p, x);
        }
    }

    void travel(Player p) {
        PlayerBoard b = board(p);
        long now = travelled(p);
        for (Bounty x : b.all()) {
            Template t = x.template();
            if (t == null || !x.open() || t.goal != Template.Goal.TRAVEL) continue;
            if (x.base <= 0 || x.base > now) { x.base = now; b.dirty = true; continue; }
            if (x.progress((int) Math.min(Integer.MAX_VALUE, (now - x.base) / 100))) { b.dirty = true; complete(p, b, x); }
            else { b.dirty = true; halfway(p, x); }
        }
    }

    private void halfway(Player p, Bounty x) {
        if (x.half || x.need < 4 || x.have * 2 < x.need) return;
        x.half = true;
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(ChatColor.GOLD + "Bounty halfway: " + ChatColor.WHITE + x.title()
            + ChatColor.GRAY + " (" + x.have + "/" + x.need + ")"));
    }

    private void complete(Player p, PlayerBoard b, Bounty x) {
        completed++;
        b.lifetime++;
        Template t = x.template();
        store.countDone(b.name, thisWeek());
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        p.sendTitle(ChatColor.GOLD + "Bounty complete", ChatColor.WHITE + x.title(), 5, 50, 15);
        link(p, ChatColor.GOLD + "[Bounties] " + ChatColor.GREEN + "Complete: " + ChatColor.WHITE + x.title() + ChatColor.GRAY
            + ". Claim " + Rewards.describe(x) + " on the board: " + ChatColor.YELLOW + "/bounty");
        getLogger().info("BOUNTY_DONE player=" + b.name + " id=" + x.id + " tier=" + (t == null ? "?" : t.tier) + " need=" + x.need);
        if (t != null && t.tier == Template.Tier.WEEKLY) {
            broadcasts++;
            for (Player other : getServer().getOnlinePlayers()) if (other != p)
                other.sendMessage(ChatColor.LIGHT_PURPLE + "[Bounties] " + ChatColor.WHITE + p.getName() + ChatColor.GRAY + " completed the weekly bounty "
                    + ChatColor.WHITE + x.title() + ChatColor.GRAY + "!");
        }
    }

    /** Claims one finished bounty; returns false when there was nothing to claim. */
    boolean claim(Player p, PlayerBoard b, Bounty x) {
        if (x == null || x.state != Bounty.State.DONE) return false;
        x.state = Bounty.State.CLAIMED;
        b.dirty = true;
        claimed++;
        String got = rewards.pay(p, x);
        p.sendMessage(ChatColor.GOLD + "[Bounties] " + ChatColor.GRAY + "Claimed " + ChatColor.WHITE + x.title() + ChatColor.GRAY + ": " + got);
        getLogger().info("BOUNTY_CLAIM player=" + b.name + " id=" + x.id + " xp=" + x.xp + " item=" + (x.item == null ? "-" : x.item + "x" + x.amount));
        streak(p, b);
        return true;
    }

    int claimAll(Player p, PlayerBoard b) {
        int n = 0;
        for (Bounty x : b.all()) if (claim(p, b, x)) n++;
        return n;
    }

    /** All three dailies claimed today: the streak grows (or starts again) and pays a bonus; every seventh day a trinket. */
    private void streak(Player p, PlayerBoard b) {
        long day = today();
        if (!b.dailiesClaimed() || b.bonusDay == day) return;
        b.bonusDay = day;
        b.streak = b.streakDay == day - 1 ? b.streak + 1 : 1;
        b.streakDay = day;
        b.dirty = true;
        streaks++;
        int xp = rewards.streakXp * Math.min(b.streak, 7);
        p.giveExp(xp);
        p.sendMessage(ChatColor.GOLD + "[Bounties] " + ChatColor.GREEN + "All dailies done! " + ChatColor.GRAY + "Streak " + ChatColor.WHITE + b.streak
            + ChatColor.GRAY + " day(s): +" + xp + " XP" + (b.streak % 7 == 0 ? ", and a trinket for a full week" : "") + ".");
        if (b.streak % 7 == 0) rewards.trinket(p);
        getLogger().info("BOUNTY_STREAK player=" + b.name + " streak=" + b.streak + " xp=" + xp);
    }

    /** Swaps an open daily for a fresh one, once a day. */
    boolean reroll(Player p, PlayerBoard b, int slot) {
        if (slot < 0 || slot >= PlayerBoard.DAILY) return false;
        Bounty old = b.daily[slot];
        if (b.rerolls >= 1) { p.sendMessage(ChatColor.RED + "You have already swapped a bounty today."); return false; }
        if (old == null || !old.open()) { p.sendMessage(ChatColor.RED + "Only an open bounty can be swapped."); return false; }
        b.rerolls++;
        b.daily[slot] = b.rollDaily(slot, b.day, b.rerolls, rewards);
        start(b.daily[slot], p);
        b.dirty = true;
        rerolled++;
        p.sendMessage(ChatColor.GOLD + "[Bounties] " + ChatColor.GRAY + "Swapped " + ChatColor.WHITE + old.title() + ChatColor.GRAY + " for "
            + ChatColor.WHITE + b.daily[slot].title() + ChatColor.GRAY + ".");
        getLogger().info("BOUNTY_REROLL player=" + b.name + " from=" + old.id + " to=" + b.daily[slot].id);
        return true;
    }

    // ---- reminders ---------------------------------------------------------------------------------------------------

    /** Every ten seconds: travel progress, rollovers, and the occasional reminder. */
    private void pulse() {
        long now = System.currentTimeMillis();
        long toReset = minutesToDailyReset();
        for (Player p : getServer().getOnlinePlayers()) {
            PlayerBoard b = board(p);
            travel(p);
            if (!b.remind) continue;
            if (toReset <= 60 && b.count(Bounty.State.OPEN) > 0 && b.bonusDay != b.day && b.lateWarned != b.day) {
                int open = 0;
                for (Bounty x : b.daily) if (x != null && x.open()) open++;
                if (open > 0) {
                    b.lateWarned = b.day;
                    link(p, ChatColor.GOLD + "[Bounties] " + ChatColor.GRAY + "The daily bounties reset in " + ChatColor.WHITE + toReset + " minutes"
                        + ChatColor.GRAY + "; " + open + " still open. " + ChatColor.YELLOW + "/bounty");
                    reminders++;
                    continue;
                }
            }
            if (now < b.nextReminder) continue;
            b.nextReminder = now + remindEveryMs + random.nextInt(300_000);
            String line = reminder(b);
            if (line == null) continue;
            link(p, line);
            reminders++;
        }
    }

    /** One line about the board: rewards waiting, else the bounty nearest done, else nothing. */
    String reminder(PlayerBoard b) {
        int done = b.count(Bounty.State.DONE);
        if (done > 0)
            return ChatColor.GOLD + "[Bounties] " + ChatColor.GREEN + done + " reward" + (done == 1 ? "" : "s") + " waiting" + ChatColor.GRAY
                + " on your bounty board. " + ChatColor.YELLOW + "/bounty";
        Bounty best = null;
        for (Bounty x : b.all()) if (x.open() && (best == null || x.have * (long) best.need > best.have * (long) x.need)) best = x;
        if (best == null) return null;
        Template t = best.template();
        boolean weekly = t != null && t.tier == Template.Tier.WEEKLY;
        return ChatColor.GOLD + "[Bounties] " + ChatColor.WHITE + best.title() + ChatColor.GRAY + " - " + best.have + "/" + best.need
            + (weekly ? ", weekly" : "") + "; resets in " + untilReset(weekly) + ". " + ChatColor.YELLOW + "/bounty";
    }

    /** A chat line that opens the board when clicked. */
    static void link(Player p, String text) {
        TextComponent line = new TextComponent(TextComponent.fromLegacyText(text));
        line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bounty"));
        line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Open the bounty board").create()));
        p.spigot().sendMessage(line);
    }

    // ---- commands ----------------------------------------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("admin")) return admin(sender, args);
        if (sub.equals("top")) { top(sender); return true; }
        if (sub.equals("help")) { help(sender); return true; }
        if (!(sender instanceof Player)) { sender.sendMessage("Players only (or /bounty admin <player> info|reset|complete <1-5>|realm <name>, /bounty top)."); return true; }
        Player p = (Player) sender;
        PlayerBoard b = board(p);
        switch (sub) {
            case "": case "board": case "open": openBoard(p); return true;
            case "list": list(p, b); return true;
            case "claim": {
                int n = claimAll(p, b);
                if (n == 0) p.sendMessage(ChatColor.GRAY + "No finished bounties to claim yet.");
                return true;
            }
            case "reroll": case "swap": {
                int slot = args.length > 1 ? parse(args[1]) - 1 : -1;
                if (slot < 0 || slot >= PlayerBoard.DAILY) { p.sendMessage(ChatColor.GRAY + "/bounty reroll <1-3>: swap one open daily bounty (once a day)."); return true; }
                reroll(p, b, slot);
                return true;
            }
            case "remind": case "reminders": {
                if (args.length > 1) b.remind = !args[1].equalsIgnoreCase("off");
                else b.remind = !b.remind;
                b.dirty = true;
                p.sendMessage(ChatColor.GOLD + "[Bounties] " + ChatColor.GRAY + "Reminders " + (b.remind ? ChatColor.GREEN + "on" : ChatColor.RED + "off")
                    + ChatColor.GRAY + " (finished bounties are always announced).");
                return true;
            }
            default: help(p); return true;
        }
    }

    private static int parse(String s) { try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; } }

    void list(Player p, PlayerBoard b) {
        p.sendMessage(ChatColor.GOLD + "Bounty board " + ChatColor.GRAY + "(dailies reset in " + untilReset(false) + ", weeklies in " + untilReset(true) + ")");
        int i = 1;
        for (Bounty x : b.daily) line(p, "Daily " + i++, x);
        i = 1;
        for (Bounty x : b.weekly) line(p, "Weekly " + i++, x);
        p.sendMessage(ChatColor.GRAY + "Streak: " + ChatColor.WHITE + b.streak + ChatColor.GRAY + " day(s). /bounty claim, /bounty reroll <1-3>, /bounty remind off");
    }

    private void line(Player p, String label, Bounty x) {
        if (x == null) return;
        Template t = x.template();
        String state = x.state == Bounty.State.CLAIMED ? ChatColor.DARK_GRAY + "claimed" : x.state == Bounty.State.DONE ? ChatColor.GREEN + "done - claim it!"
            : ChatColor.WHITE + "" + x.have + "/" + x.need;
        p.sendMessage((t == null ? ChatColor.GRAY : t.tier.colour) + label + ": " + ChatColor.WHITE + x.title() + ChatColor.GRAY + " [" + state + ChatColor.GRAY + "]");
    }

    void top(CommandSender to) {
        List<Map.Entry<String, Integer>> rows = new ArrayList<>(store.weekDone.entrySet());
        if (store.weekDoneKey != thisWeek()) rows.clear();
        rows.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        to.sendMessage(ChatColor.GOLD + "Bounty hunters this week:");
        int n = 0;
        for (Map.Entry<String, Integer> e : rows) {
            if (++n > 8) break;
            to.sendMessage(ChatColor.GRAY + "  " + n + ". " + ChatColor.WHITE + e.getKey() + ChatColor.GRAY + " - " + e.getValue() + " bount" + (e.getValue() == 1 ? "y" : "ies"));
        }
        if (n == 0) to.sendMessage(ChatColor.DARK_GRAY + "  Nobody has finished a bounty this week yet.");
    }

    void help(CommandSender to) {
        to.sendMessage(ChatColor.GOLD + "Bounties: " + ChatColor.GRAY + "three dailies (easy, medium, hard) and two weeklies, paid in XP, materials and trinkets.");
        to.sendMessage(ChatColor.YELLOW + "/bounty" + ChatColor.GRAY + " open the board   " + ChatColor.YELLOW + "/bounty list" + ChatColor.GRAY + " in chat   "
            + ChatColor.YELLOW + "/bounty claim" + ChatColor.GRAY + " claim rewards");
        to.sendMessage(ChatColor.YELLOW + "/bounty reroll <1-3>" + ChatColor.GRAY + " swap a daily (once a day)   " + ChatColor.YELLOW + "/bounty remind on|off"
            + ChatColor.GRAY + "   " + ChatColor.YELLOW + "/bounty top");
        to.sendMessage(ChatColor.GRAY + "A sign with " + ChatColor.WHITE + "[Bounties]" + ChatColor.GRAY + " on its first line becomes a bounty board anyone can use.");
    }

    @SuppressWarnings("deprecation")
    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("jaspr.bounties.admin")) { sender.sendMessage(ChatColor.RED + "No permission."); return true; }
        if (args.length < 3) { sender.sendMessage(ChatColor.GRAY + "/bounty admin <player> info|reset|complete <1-5>|realm <" + Arrays.toString(Template.Realm.values()) + ">"); return true; }
        Player online = Bukkit.getPlayerExact(args[1]);
        if (online == null) { sender.sendMessage(ChatColor.RED + "Player not online: " + args[1]); return true; }
        PlayerBoard b = board(online);
        String what = args[2].toLowerCase(Locale.ROOT);
        switch (what) {
            case "info": {
                sender.sendMessage(ChatColor.GOLD + b.name + ": day=" + b.day + " week=" + b.week + " streak=" + b.streak + " rerolls=" + b.rerolls
                    + " realms=" + b.realms + " lifetime=" + b.lifetime + " owed=" + b.owed.size());
                for (Bounty x : b.all()) sender.sendMessage(ChatColor.GRAY + "  " + x.id + " " + x.have + "/" + x.need + " " + x.state + " reward=" + Rewards.describe(x));
                return true;
            }
            case "reset": {
                b.day = Long.MIN_VALUE + 1; b.week = Long.MIN_VALUE + 1;
                ensure(b, online);
                sender.sendMessage(ChatColor.GREEN + "Rolled a fresh board for " + b.name + ".");
                return true;
            }
            case "complete": {
                int slot = args.length > 3 ? parse(args[3]) - 1 : -1;
                List<Bounty> all = b.all();
                if (slot < 0 || slot >= all.size()) { sender.sendMessage(ChatColor.RED + "Slot 1-" + all.size()); return true; }
                Bounty x = all.get(slot);
                if (x.open() && x.add(x.need)) { b.dirty = true; complete(online, b, x); }
                sender.sendMessage(ChatColor.GREEN + "Completed " + x.id + " for " + b.name + ".");
                return true;
            }
            case "realm": {
                Template.Realm r = args.length > 3 ? Template.Realm.parse(args[3]) : null;
                if (r == null) { sender.sendMessage(ChatColor.RED + "Realms: " + Arrays.toString(Template.Realm.values())); return true; }
                b.realms.add(r);
                b.dirty = true;
                sender.sendMessage(ChatColor.GREEN + b.name + " now has " + r.title + " bounties unlocked (from the next roll).");
                return true;
            }
            default:
                sender.sendMessage(ChatColor.GRAY + "/bounty admin <player> info|reset|complete <1-5>|realm <name>");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(Arrays.asList("list", "claim", "reroll", "remind", "top", "help"));
            if (sender.hasPermission("jaspr.bounties.admin")) out.add("admin");
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reroll")) return Arrays.asList("1", "2", "3");
        if (args.length == 2 && args[0].equalsIgnoreCase("remind")) return Arrays.asList("on", "off");
        return Collections.emptyList();
    }

    void openBoard(Player p) { menu.open(p); }
}
