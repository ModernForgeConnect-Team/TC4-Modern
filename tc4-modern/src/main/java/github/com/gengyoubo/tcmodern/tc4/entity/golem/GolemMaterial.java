package github.com.gengyoubo.tcmodern.tc4.entity.golem;

/** The original TC4 golem material properties. */
public enum GolemMaterial {
    STRAW(10, 0, 0.38, false, 1, 1, 75, 0),
    WOOD(20, 6, 0.35, false, 1, 4, 75, 1),
    TALLOW(20, 9, 0.33, false, 2, 8, 75, 2),
    CLAY(25, 9, 0.33, true, 1, 8, 100, 2),
    FLESH(15, 6, 0.35, false, 2, 4, 40, 1),
    STONE(30, 12, 0.32, true, 1, 16, 100, 3),
    IRON(35, 15, 0.31, true, 1, 32, 125, 4),
    THAUMIUM(40, 15, 0.32, true, 2, 32, 100, 4);
    public final int health, armor, upgrades, carry, regenerationDelay, strength;
    public final double speed;
    public final boolean fireResistant;
    GolemMaterial(int health, int armor, double speed, boolean fireResistant, int upgrades, int carry, int regenerationDelay, int strength) {
        this.health = health; this.armor = armor; this.speed = speed; this.fireResistant = fireResistant;
        this.upgrades = upgrades; this.carry = carry; this.regenerationDelay = regenerationDelay; this.strength = strength;
    }
    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
}
