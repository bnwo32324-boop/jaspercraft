package chat.jaspr.dungeon;

/** Generation 7: counts of what the perils did, logged once at close as DUNGEON_PERILS (no coordinates, no names). */
final class PerilMetrics {
    int rooms, armed, falls, crumbles, eruptions, splashes, hits, rescues, regrown, mended, restored, failures;
    String line() {
        return "DUNGEON_PERILS rooms=" + rooms + " armed=" + armed + " falls=" + falls + " crumbles=" + crumbles + " eruptions=" + eruptions
            + " splashes=" + splashes + " hits=" + hits + " rescues=" + rescues + " regrown=" + regrown + " mended=" + mended
            + " restored=" + restored + " failures=" + failures;
    }
}
