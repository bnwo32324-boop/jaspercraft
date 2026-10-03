package chat.jaspr.dungeon;

/** Pure realm identities and discovery policy. Changing these requires a manifest version bump. */
public final class RiftCatalog {
    public static final int VERSION=1, COUNT=3, DWELL_TICKS=30, COOLDOWN_MILLIS=3500;
    public static final int EFFECT_INTERVAL=4, EFFECT_RADIUS=28, POINTS_PER_CRACK=14;
    public static final double ARRIVAL_X=16.5, ARRIVAL_Z=16.5, FORWARD_X=24.5, FORWARD_Z=16.5,
            RETURN_X=16.5, RETURN_Z=24.5;
    private RiftCatalog() {}
    public enum Realm {
        ASHEN(1,"ashen","The Ashen Fold","The air folds into a furnace of iron and cinders."),
        DROWNED(2,"drowned","The Drowned Choir","A drowned hymn trembles behind the walls."),
        STARLESS(3,"starless","The Starless Maw","The last stars turn their faces away.");
        public final int id; public final String suffix, title, mood;
        Realm(int id,String suffix,String title,String mood){this.id=id;this.suffix=suffix;this.title=title;this.mood=mood;}
    }
    public static Realm get(int realm){if(realm<1||realm>COUNT)throw new IllegalArgumentException("Unknown rift realm: "+realm);return Realm.values()[realm-1];}
    public static String worldName(String root,int realm){return realm==0?root:root+"_rift_"+get(realm).suffix;}
    public static long seed(long root,int realm){get(realm);return Layout.mix(root^(0x9e3779b97f4a7c15L*realm)^0x52494654534cL);}
    public static boolean origin(Layout.Room room){return room.kind==Layout.Kind.REFUGE&&room.x==0&&room.z==0;}
    /** Guaranteed refuge discovery, then 1/5 of bosses and 1/9 of shrines after completion. */
    public static boolean selected(int realm,Layout.Room room){
        if(realm<0||realm>=COUNT)return false;
        if(origin(room))return true;
        long roll=Layout.mix(room.hash^0x726966742d637261L^realm);
        return room.kind==Layout.Kind.BOSS?Math.floorMod(roll,5)==0:
                room.kind==Layout.Kind.SHRINE&&Math.floorMod(roll,9)==0;
    }
    public static double forwardX(Layout.Room room){return origin(room)?FORWARD_X:room.cx()+8.5;}
    public static double forwardZ(Layout.Room room){return origin(room)?FORWARD_Z:room.cz()+.5;}
    public static boolean hit(double x,double y,double z,double centerX,double centerZ){
        return Math.abs(x-centerX)<=.7&&Math.abs(z-centerZ)<=.7&&y>=64.8&&y<=66.2;
    }
}
