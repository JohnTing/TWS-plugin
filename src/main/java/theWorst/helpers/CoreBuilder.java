package example;

import mindustry.content.Blocks;
import mindustry.entities.type.Player;
import mindustry.gen.Call;
import mindustry.type.Item;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock;

import java.util.ArrayList;

import static mindustry.Vars.world;

public class CoreBuilder implements Votable{


    @Override
    public void launch(Package p) {
        Block to_build = Blocks.coreShard;
        switch(p.object){
            case "normal":
                to_build = Blocks.coreFoundation;

                break;
            case "big":
                to_build = Blocks.coreNucleus;
                break;
        }
        build_core(p.amount,p.target,to_build,p.x,p.y);
    }

    @Override
    public Package verify(Player player, String object, String sAmount, boolean toBase) {
        if(!object.equals("big") && !object.equals("normal") && !object.equals("small")){
            player.sendMessage(Main.prefix+"Invalid argument.");
            return null;
        }

        int storage=Main.getStorageSize(player);
        int cost=(int)(storage*.20f);
        switch(object){
            case "normal":
                cost=(int)(storage*.35f);
                break;
            case "big":
                cost=(int)(storage*.50f);
                break;
        }
        boolean can_build=true;
        CoreBlock.CoreEntity core=Loadout.getCore(player);
        for(Item item:Main.items){
            if (!core.items.has(item, cost)) {
                can_build=false;
                player.sendMessage("[scarlet]" + item.name + ":" + core.items.get(item) +"/"+ cost);
            }
        }
        if(!can_build){
            return null;
        }
        return new Package(object,cost,toBase,player,player.tileX(),player.tileY());
    }

    public void build_core(int cost, Player player, Block core_type ,int x,int y){
        CoreBlock.CoreEntity core = Loadout.getCore(player);
        Call.onConstructFinish(world.tile(x,y), core_type, 0, (byte) 0, player.getTeam(), false);
        if (world.tile(player.tileX(), player.tileY()).block() == core_type) {
            Call.sendMessage(Main.prefix+"Player [green]"+player.name+" []has taken a portion of resources to build a core!");
            for(Item item:Main.items){
                core.items.remove(item, cost);
            }
        } else {
            player.sendMessage(Main.prefix+"Core spawn failed!Invalid placement!");
        }
    }
}
