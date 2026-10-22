package net.icegiant.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;

/**
 * WICHTIG: Ich kenne die exakten Klassen-/Methodennamen von YDM's Weapon
 * Master und Epic Knights nicht (kein Zugriff auf deren Sourcecode/Javadocs
 * von hier aus). Der Reflection-Zugriff unten ist ein Platzhalter-Muster,
 * das zeigt WIE man es sauber anbindet - die Methodennamen "applyKnockback"
 * bzw. den Modid-String "ydm_weaponmaster" musst du gegen die tatsächliche
 * API der Mod prüfen (z.B. über deren GitHub-Sourcecode, falls verfügbar,
 * oder indem wir den Server-JAR gemeinsam decompilieren).
 *
 * Fällt die Ziel-Mod nicht auf, oder passt die Reflection nicht, wird
 * automatisch auf den Vanilla-Fallback zurückgefallen - der Boss bleibt
 * also immer funktionsfähig, auch ohne die Dependency.
 */
public final class KnockbackCompat {

    private static final boolean WEAPON_MASTER_LOADED = ModList.get().isLoaded("ydm_weaponmaster");

    private KnockbackCompat() {}

    public static void applyKnockback(LivingEntity source, LivingEntity target, double strength, double verticalBoost) {
        if (WEAPON_MASTER_LOADED) {
            try {
                applyViaWeaponMaster(source, target, strength, verticalBoost);
                return;
            } catch (Throwable t) {
                // API-Signatur stimmt nicht (siehe Klassenkommentar) -> Fallback
            }
        }
        applyVanillaFallback(source, target, strength, verticalBoost);
    }

    private static void applyViaWeaponMaster(LivingEntity source, LivingEntity target, double strength, double verticalBoost) throws Exception {
        // TODO: sobald die echte API bekannt ist, hier direkt (ohne Reflection)
        // aufrufen, z.B. in etwa:
        //   WeaponMasterCombatUtils.knockback(source, target, strength);
        // Reflection ist nur der sichere Übergang, solange die Klasse nicht
        // als compileOnly-Dependency eingebunden ist.
        throw new UnsupportedOperationException("API von YDM's Weapon Master noch nicht verdrahtet");
    }

    private static void applyVanillaFallback(LivingEntity source, LivingEntity target, double strength, double verticalBoost) {
        Vec3 dir = target.position().subtract(source.position());
        double len = dir.horizontalDistance();
        if (len < 1.0E-4) return;
        dir = new Vec3(dir.x / len * strength, verticalBoost, dir.z / len * strength);
        target.setDeltaMovement(target.getDeltaMovement().add(dir));
        target.hurtMarked = true;
    }
}
