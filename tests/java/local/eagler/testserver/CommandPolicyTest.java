package local.eagler.testserver;

/** Public/admin split of player commands (run by tests/tanks.test.cjs). */
public final class CommandPolicyTest {
    private static void expect(String command, PlayerCommandPolicy.Decision decision) {
        if (PlayerCommandPolicy.classify(command) != decision) throw new AssertionError(command + " should be " + decision);
    }

    public static void main(String[] args) {
        for (String command : new String[] {"/tank", "/tank mobile", "/TANK off", "/tanks status", "/jasprtanks:tank on",
                "/tp friend", "/tpaccept", "/jasprapocalypse:tpa friend", "/waypoints", "/gear"})
            expect(command, PlayerCommandPolicy.Decision.PUBLIC_COMMAND);
        for (String command : new String[] {"/login secret", "/register secret", "/authme:login secret"})
            expect(command, PlayerCommandPolicy.Decision.BLOCK_AUTH_COMMAND);
        for (String command : new String[] {"/minecraft:tp friend", "/op friend", "/creative", "/tankall", "/summon tnt", "tank"})
            expect(command, PlayerCommandPolicy.Decision.ADMIN_ONLY);
        System.out.println("COMMAND_POLICY_OK");
    }
}
