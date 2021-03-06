package theWorst;

import mindustry.entities.type.Player;

public class Package {
    public int amount;
    public int x = 0;
    public int y = 0;
    public boolean toBase;
    public String object;
    public Player target;
    public Object obj;

    public Package(String object, int amount, boolean toBase, Player target) {
        this.object = object;
        this.amount = amount;
        this.toBase = toBase;
        this.target = target;
    }

    public Package(String object, int amount, boolean toBase, Player target, int x, int y) {
        this.object = object;
        this.amount = amount;
        this.toBase = toBase;
        this.target = target;
        this.x = x;
        this.y = y;
    }

    public Package(String object, Object map, Player target) {
        this.object = object;
        this.obj = map;
        this.target = target;
    }

    public Package(Package p) {
        this.object = p.object;
        this.amount = p.amount;
        this.toBase = p.toBase;
        this.target = p.target;
        this.obj = p.obj;
        this.x = p.x;
        this.y = p.y;
    }
}
