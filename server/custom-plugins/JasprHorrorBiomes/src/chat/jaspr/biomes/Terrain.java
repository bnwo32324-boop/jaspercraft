package chat.jaspr.biomes;

/** Pure deterministic terrain; no world reads, mutable Random or neighbor chunk loads. */
public final class Terrain {
    public static final int CELL=384;
    public final long seed;
    public Terrain(long seed){this.seed=seed;}
    public static long mix(long x){x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31);}
    public double random(int x,int z,int salt){return (mix(seed+341873128712L*x+132897987541L*z+salt*9182736451L)>>>11)*0x1.0p-53;}
    private static double smooth(double t){return t*t*(3-2*t);}
    public double noise(double x,double z,int salt){int a=(int)Math.floor(x),b=(int)Math.floor(z);double u=smooth(x-a),v=smooth(z-b);return ((1-u)*(1-v)*random(a,b,salt)+u*(1-v)*random(a+1,b,salt)+(1-u)*v*random(a,b+1,salt)+u*v*random(a+1,b+1,salt))*2-1;}
    public int cellIndex(int x,int z){return (int)Math.floorMod(mix(seed+719L*x+1987L*z),62L);}
    public static final class Sample {
        public final Catalog.Profile profile;public final int y;
        Sample(Catalog.Profile p,int y){profile=p;this.y=y;}
    }
    public Sample sample(int x,int z){
        // Warped, overlapping regional sites give continuous slopes, including at negative coordinates.
        double wx=x+noise(x/530.0,z/530.0,1)*85,wz=z+noise(x/530.0,z/530.0,2)*85;
        int cx=(int)Math.floor(wx/CELL),cz=(int)Math.floor(wz/CELL);double total=0,height=0,relief=0,best=Double.MAX_VALUE;Catalog.Profile chosen=null;
        for(int a=cx-1;a<=cx+1;a++)for(int b=cz-1;b<=cz+1;b++){
            double dx=wx-(a+.18+random(a,b,3)*.64)*CELL,dz=wz-(b+.18+random(a,b,4)*.64)*CELL,d=Math.sqrt(dx*dx+dz*dz);
            Catalog.Profile p=Catalog.ALL.get(cellIndex(a,b));if(d<best){best=d;chosen=p;}
            double weight=Math.max(0,1-d/(CELL*.95));weight=weight*weight*weight;
            total+=weight;height+=weight*p.height;relief+=weight*p.relief;
        }
        height/=total;relief/=total;
        double land=noise(x/120.0,z/120.0,7)*.68+noise(x/41.0,z/41.0,8)*.22+noise(x/16.0,z/16.0,9)*.10;
        double y=height+land*relief;
        // Distinct regional topography: scarred craters, terraced quarries, wind-cut ridges and trenches.
        if(chosen.landmark.equals("reactor")||chosen.landmark.equals("anomaly"))y-=Math.max(0,1-best/95)*13;
        if(chosen.landmark.equals("trench"))y-=Math.pow(Math.max(0,1-Math.abs(noise(x/65.0,z/65.0,21))*7),2)*12;
        if(chosen.landmark.equals("mine"))y=Math.floor(y/3)*3;
        double spawn=Math.max(0,1-Math.hypot(x,z)/100.0);y=y*(1-spawn)+72*spawn;
        return new Sample(chosen,Math.max(42,Math.min(145,(int)Math.round(y))));
    }
    /** Value noise in three real dimensions. The old cave field faked depth by
     *  shearing a 2D field with y, which is why it produced thin ribbons instead
     *  of rooms: a shear cannot make a cavity that is wide in all three axes. */
    public double random3(int x,int y,int z,int salt){
        return (mix(seed+341873128712L*x+132897987541L*z+2971215073L*y+salt*9182736451L)>>>11)*0x1.0p-53;
    }
    public double noise3(double x,double y,double z,int salt){
        int a=(int)Math.floor(x),b=(int)Math.floor(y),c=(int)Math.floor(z);
        double u=smooth(x-a),v=smooth(y-b),w=smooth(z-c);
        double x00=random3(a,b,c,salt)+u*(random3(a+1,b,c,salt)-random3(a,b,c,salt));
        double x10=random3(a,b+1,c,salt)+u*(random3(a+1,b+1,c,salt)-random3(a,b+1,c,salt));
        double x01=random3(a,b,c+1,salt)+u*(random3(a+1,b,c+1,salt)-random3(a,b,c+1,salt));
        double x11=random3(a,b+1,c+1,salt)+u*(random3(a+1,b+1,c+1,salt)-random3(a,b+1,c+1,salt));
        double y0=x00+v*(x10-x00),y1=x01+v*(x11-x01);
        return (y0+w*(y1-y0))*2-1;
    }
}
