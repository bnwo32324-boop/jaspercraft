package local.eagler.testserver;

import com.sun.net.httpserver.HttpServer;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;

public final class SsoContractTest {
    static int checks;
    interface Check { void run() throws Exception; }
    static void ok(boolean value) { checks++; if (!value) throw new AssertionError("Contract check failed " + checks); }
    static void denied(Check check) throws Exception { boolean failed=false; try { check.run(); } catch(IOException expected) { failed=true; } ok(failed); }
    static String json(String name, boolean privileged, long expires) {
        return "{\"userId\":\"test-account-id\",\"gameName\":\""+name+"\",\"privileged\":"+privileged+",\"expiresAt\":"+expires+"}";
    }
    static Player player(String name, UUID uuid) {
        return (Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class[]{Player.class},(proxy,method,args)->{
            switch(method.getName()) {
                case "getName": return name;
                case "getUniqueId": return uuid;
                case "equals": return proxy==args[0];
                case "hashCode": return System.identityHashCode(proxy);
                default: throw new UnsupportedOperationException(method.getName());
            }
        });
    }
    public static void main(String[] args) throws Exception {
        for (String command : new String[]{
                "/tp friend", "/teleport friend", "/tpa anon_0706", "/tpahere friend",
                "/tpaccept", "/tpdeny", "/tpcancel", "/JasprApocalypse:tpa anon_0706",
                "/jasprapocalypse:tpaccept"}) {
            ok(PlayerCommandPolicy.classify(command) == PlayerCommandPolicy.Decision.PUBLIC_TELEPORT);
        }
        for (String command : new String[]{"/login secret", "/register secret", "/authme:login secret"}) {
            ok(PlayerCommandPolicy.classify(command) == PlayerCommandPolicy.Decision.BLOCK_AUTH_COMMAND);
        }
        for (String command : new String[]{"/minecraft:tp friend", "/tpall", "/op friend", "/creative"}) {
            ok(PlayerCommandPolicy.classify(command) == PlayerCommandPolicy.Decision.ADMIN_ONLY);
        }

        String token="A".repeat(43), target="/jaspercraft/socket?ticket="+token;
        long now=System.currentTimeMillis();
        ok(TicketVerifier.ticketFromPath(target).equals(token));
        for(String path:new String[]{"/", "/jaspercraft/socket", target+"&ticket="+token, target+"#x", "/jaspercraft/socket?%74icket="+token, "https://jaspr.chat"+target, target+"&name=jasper", "/jaspercraft/socket?ticket=short"})
            denied(()->TicketVerifier.ticketFromPath(path));
        TicketVerifier.Identity owner=TicketVerifier.parseIdentity(json("jasper",true,now+60000),now);
        ok(owner.uuid().equals(UUID.fromString("272903d5-f420-379b-bd13-d9ffd166acd3")));
        denied(()->owner.requirePlayer("Jasper",owner.uuid()));
        denied(()->owner.requirePlayer("jasper",UUID.randomUUID()));
        denied(()->TicketVerifier.parseIdentity(json("jasper",false,now+60000),now));
        denied(()->TicketVerifier.parseIdentity(json("Jasper",true,now+60000),now));
        denied(()->TicketVerifier.parseIdentity(json("friend",false,now),now));
        denied(()->TicketVerifier.parseIdentity(json("friend",false,now+90001),now));
        denied(()->TicketVerifier.parseIdentity(json("friend",false,now+60000).replace("false","\"false\""),now));
        ok(!TicketVerifier.parseIdentity(json("friend",false,now+60000),now).privileged);
        ok(TicketVerifier.parseIdentity(json("staff",true,now+60000),now).privileged);

        Path markerRoot=Files.createTempDirectory(Path.of("candidate"),"sso-marker-test-");
        Path marker=markerRoot.resolve("jaspr-sso-ready");
        ReadyMarkerPublisher publisher=new ReadyMarkerPublisher(marker);
        ok(publisher.refresh(now));
        ok(Files.readString(marker,StandardCharsets.US_ASCII).equals(Long.toString(now)));
        Files.delete(marker);Files.createDirectory(marker);Files.write(marker.resolve("blocker"),new byte[]{1});
        ok(!publisher.refresh(now+1));
        Files.delete(marker.resolve("blocker"));Files.delete(marker);
        ok(publisher.refresh(now+2));
        ok(Files.readString(marker,StandardCharsets.US_ASCII).equals(Long.toString(now+2)));
        publisher.clear();ok(!Files.exists(marker));ok(!Files.exists(markerRoot.resolve("jaspr-sso-ready.tmp")));
        Files.delete(markerRoot);

        ConnectionSessions sessions=new ConnectionSessions();
        EmbeddedChannel first=new EmbeddedChannel(), second=new EmbeddedChannel();
        Player actual=player("jasper",owner.uuid()), impostor=player("jasper",owner.uuid());
        sessions.attach(first,owner);
        denied(()->sessions.bind(second,impostor,now));
        sessions.bind(first,actual,now);
        ok(sessions.get(actual)!=null);ok(sessions.get(impostor)==null);
        denied(()->sessions.attach(first,owner));denied(()->sessions.bind(first,impostor,now));
        sessions.closed(first);ok(sessions.get(actual)==null);denied(()->sessions.bind(first,impostor,now));
        first.close();sessions.remove(actual);second.close();

        Path fixture=Files.createTempDirectory(Path.of("candidate"),"sso-http-test-");
        Path key=fixture.resolve("test.key");String testKey="test-fixture-only-"+"K".repeat(40);
        Files.write(key,testKey.getBytes(StandardCharsets.US_ASCII));
        HttpServer http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicBoolean consumed=new AtomicBoolean();AtomicInteger accepted=new AtomicInteger();
        http.createContext("/api/jaspercraft/internal/consume",exchange->{
            byte[] request=exchange.getRequestBody().readAllBytes();
            boolean valid=exchange.getRequestMethod().equals("POST")&&testKey.equals(exchange.getRequestHeaders().getFirst("X-Jaspr-Craft-Key"))
                    && new String(request,StandardCharsets.UTF_8).equals("{\"ticket\":\""+token+"\"}") && consumed.compareAndSet(false,true);
            if(valid)accepted.incrementAndGet();
            byte[] body=(valid?json("jasper",true,System.currentTimeMillis()+60000):"{}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(valid?200:401,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        http.start();
        TicketVerifier verifier=new TicketVerifier(key,new URL("http://127.0.0.1:"+http.getAddress().getPort()+"/api/jaspercraft/internal/consume"));
        try {
            ok(verifier.consume(target,"jasper",owner.uuid()).privileged);
            denied(()->verifier.consume(target,"jasper",owner.uuid()));ok(accepted.get()==1);
            consumed.set(false);denied(()->verifier.consume(target,"wrong_name",owner.uuid()));
            denied(()->verifier.consume(target,"jasper",owner.uuid()));
            Files.delete(key);denied(()->verifier.consume(target,"jasper",owner.uuid()));
        } finally { http.stop(0); }
        denied(()->verifier.consume(target,"jasper",owner.uuid()));
        System.out.println("PASS: "+checks+" command-policy, ticket, replay, UUID, privilege, exact-channel/player binding, and outage checks.");
    }
}
