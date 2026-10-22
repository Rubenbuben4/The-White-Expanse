package net.icegiant.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;

import java.util.UUID;

/**
 * Einfaches Waffen-Item: fügt Angriffsschaden/-geschwindigkeit hinzu, wenn
 * in der Haupthand gehalten, genau wie vanilla SwordItem/AxeItem das machen.
 *
 * WICHTIG zum Schaden: Attributes.ATTACK_DAMAGE ist ADDITIV zur Basis von 1.0
 * (unbewaffneter Faustschlag). Für 12 Herzen (=24 Schadenspunkte) insgesamt
 * muss der Modifier-Wert 23.0 sein (23 + Basis 1.0 = 24).
 */
public class IceWeaponItem extends Item {

    private static final UUID ATTACK_DAMAGE_UUID = UUID.fromString("c5c8e5c0-1a2b-4a3d-9e1a-2b3c4d5e6f7a");
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("d6d9f6d1-2b3c-5b4e-8f2b-3c4d5e6f7a8b");

    private final double attackDamageModifier;
    private final double attackSpeedModifier;

    public IceWeaponItem(Properties properties, double totalAttackDamage, double attackSpeedModifier) {
        super(properties);
        // -1.0, weil der Spieler bereits eine Basis von 1.0 Schaden hat
        this.attackDamageModifier = totalAttackDamage - 1.0D;
        this.attackSpeedModifier = attackSpeedModifier;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> modifiers = HashMultimap.create(super.getDefaultAttributeModifiers(slot));
        if (slot == EquipmentSlot.MAINHAND) {
            modifiers.put(Attributes.ATTACK_DAMAGE,
                    new AttributeModifier(ATTACK_DAMAGE_UUID, "Weapon modifier", attackDamageModifier, AttributeModifier.Operation.ADDITION));
            modifiers.put(Attributes.ATTACK_SPEED,
                    new AttributeModifier(ATTACK_SPEED_UUID, "Weapon modifier", attackSpeedModifier, AttributeModifier.Operation.ADDITION));
        }
        return modifiers;
    }
}
