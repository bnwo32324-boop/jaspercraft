package chat.jaspr.nether;
import java.io.*;
import java.util.logging.Logger;
public final class LayoutRegression {
    static int checks;
    static void check(String n,boolean b){if(!b)throw new AssertionError(n);checks++;System.out.println("LAYOUT_CHECK "+n);}
    public static void main(String[] args) throws Exception {
        double[] oldChance={1,.65,.5};int[] oldCell={640,448,176};
        for(GlmSites.Tier t:GlmSites.Tier.values())
            check(t+" exact doubled candidate density",Math.abs((t.chance/(t.cell*(double)t.cell))/(oldChance[t.ordinal()]/(oldCell[t.ordinal()]*(double)oldCell[t.ordinal()]))-2)<1e-12);
        File dir=new File(args[0]);if(dir.exists())throw new AssertionError("existing scratch folder");
        Registry r=new Registry(dir,Logger.getLogger("layout-test"));
        r.add("glm","N001",-100,10,-80,-20,60,-10);r.setGlmDecision('C',-1,-2,true);r.setMegaDecision(-4,3,false);
        Registry frozen=r.legacySnapshot();
        check("legacy boxes frozen",frozen.structureAt(-50,20,-50)!=null);
        check("legacy tier decisions frozen",Boolean.TRUE.equals(frozen.glmDecision('C',-1,-2)));
        check("mega negatives preserved",Boolean.FALSE.equals(frozen.megaDecision(-4,3)));
        r.setGlmDecision('c',-1,-2,false);r.add("glm","N009",100,10,100,140,70,140);r.flush();
        check("new namespace cannot replace old",Boolean.TRUE.equals(r.glmDecision('C',-1,-2))&&Boolean.FALSE.equals(r.glmDecision('c',-1,-2)));
        Registry again=r.legacySnapshot();
        check("restart does not refreeze migrated sites",again.structureAt(120,20,120)==null);
        check("historical keepouts find negatives",again.structureReach(-60,-60,-40,-40));
        check("historical keepouts ignore new layout",!again.structureReach(110,110,130,130));
        r.close();frozen.close();again.close();
        Registry saved=new Registry(dir,Logger.getLogger("layout-test"));
        check("both namespaces persist",Boolean.TRUE.equals(saved.glmDecision('C',-1,-2))&&Boolean.FALSE.equals(saved.glmDecision('c',-1,-2)));
        saved.close();
        Mega m=new Mega(42L,(x,z)->Biomes.Nex.HELL,null);
        m.pin(-5,6,null);check("historically rejected mega stays rejected",m.site(-5,6)==null);
        Mega.Site fixed=new Mega.Site(Mega.Kind.CITADEL,2,3,901,1400,123L);m.pin(2,3,fixed);
        check("accepted mega coordinates immutable",m.site(2,3)==fixed);
        System.out.println("LAYOUT_PASS checks="+checks);
    }
}
