package chat.jaspr.nether;
import java.io.*;
import net.minecraft.server.v1_12_R1.*;
/** Oracle is actual Minecraft 1.12 block-state rotation, not another lookup table. */
public final class StairRegression {
    static int checks, palettes;
    static final int[] IDS={53,67,108,109,114,128,134,135,136,156,163,164,180,203};
    static boolean stairs(int id){for(int x:IDS)if(x==id)return true;return false;}
    static void check(String label, boolean ok){if(!ok)throw new AssertionError(label);checks++;}
    public static void main(String[] args)throws Exception {
        DispenserRegistry.c();
        for(int id:IDS)for(int meta=0;meta<8;meta++) {
            Block block=Block.getById(id);IBlockData original=block.fromLegacyData(meta),state=original;
            for(int r=0;r<4;r++) {
                int combined=Template.rotate((id<<4)|meta,r);
                check("native rotation "+id+":"+meta+" r="+r,combined==((id<<4)|block.toLegacyData(state)));
                check("half "+id+":"+meta+" r="+r,block.fromLegacyData(combined&15).get(BlockStairs.HALF)==original.get(BlockStairs.HALF));
                state=block.a(state,EnumBlockRotation.CLOCKWISE_90);
            }
            check("four turns identity",Template.rotate((id<<4)|meta,4)==((id<<4)|meta));
        }
        File res=new File(args[0]);int builds=0;
        try(BufferedReader in=new BufferedReader(new FileReader(new File(res,"glm/builds.tsv")))) {
            String line;
            while((line=in.readLine())!=null) {
                if(line.isEmpty()||line.startsWith("#"))continue;
                String key=line.split("\t")[0];GlmBuild build;
                try(InputStream b=new FileInputStream(new File(res,"glm/"+key+".glb"))){build=GlmBuild.read(key,b);}
                builds++;
                for(int i=0;i<build.roles.length;i++) {
                    int v=build.values[0][i],id=v>>4;
                    if(build.roles[i]!=GlmBuild.LIT||!stairs(id))continue;
                    palettes++;Block block=Block.getById(id);IBlockData state=block.fromLegacyData(v&15);
                    for(int r=0;r<4;r++) {
                        check(key+" palette "+i+" r="+r,build.values[r][i]==((id<<4)|block.toLegacyData(state)));
                        state=block.a(state,EnumBlockRotation.CLOCKWISE_90);
                    }
                }
            }
        }
        check("all unique GLM builds",builds==98);
        System.out.println("STAIRS_NATIVE_PASS builds="+builds+" stairPalettes="+palettes+" checks="+checks);
    }
}
