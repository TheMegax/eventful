package net.themegax.eventful.Database;

import java.util.Date;

public class PlayerBadge {
    int ID;
    String badgeID;
    String item;
    String nbt;
    String emoji;
    int totalUses;
    int cooldownSeconds;
    String data;
    String ownedBy;
    boolean isEquipped;
    int usesLeft;
    int useCount;
    Date lastUsage;

    public PlayerBadge(int ID, String badgeID, String item, String nbt, String emoji, int totalUses, int cooldownSeconds, String data, String ownedBy, boolean isEquipped, int usesLeft, int useCount, Date lastUsage) {
        this.ID = ID;
        this.badgeID = badgeID;
        this.item = item;
        this.nbt = nbt;
        this.emoji = emoji;
        this.totalUses = totalUses;
        this.cooldownSeconds = cooldownSeconds;
        this.data = data;
        this.ownedBy = ownedBy;
        this.isEquipped = isEquipped;
        this.usesLeft = usesLeft;
        this.useCount = useCount;
        this.lastUsage = lastUsage;
    }
}
