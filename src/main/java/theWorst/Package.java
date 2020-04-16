package example;

import mindustry.entities.type.Player;

public class Package{
    int amount;
    int x=0;
    int y=0;
    boolean toBase;
    String object;
    Player target;
    mindustry.maps.Map map;

    public Package(String object,int amount,boolean toBase,Player target){
        this.object=object;
        this.amount=amount;
        this.toBase=toBase;
        this.target=target;
    }

    public Package(String object,int amount,boolean toBase,Player target,int x,int y){
        this.object=object;
        this.amount=amount;
        this.toBase=toBase;
        this.target=target;
        this.x=x;
        this.y=y;
    }
    public Package(String object,mindustry.maps.Map map,Player target){
        this.object=object;
        this.map=map;
        this.target=target;
    }
}
