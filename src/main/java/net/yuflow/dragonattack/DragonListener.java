package net.yuflow.dragonattack;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

public class DragonListener implements Listener {

    private final Dragonattack plugin;
    private final Random random = new Random();

    public DragonListener(Dragonattack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDragonPhaseChange(EnderDragonChangePhaseEvent event) {
        if (event.getNewPhase() == EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET || event.getNewPhase() == EnderDragon.Phase.LAND_ON_PORTAL) {
            Player target = findNearestPlayer(event.getEntity(), 150);
            if (target == null) return;

            int attackChoice = random.nextInt(3);
            switch (attackChoice) {
                case 0:
                    fireballBarrage(event.getEntity(), target);
                    break;
                case 1:
                    lightningStorm(target);
                    break;
                case 2:
                    enderRift(event.getEntity(), target);
                    break;
            }
        }
    }

    private void fireballBarrage(EnderDragon dragon, Player target) {
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 2.0F, 1.0F);

        AtomicInteger count = new AtomicInteger(0);
        dragon.getScheduler().runAtFixedRate(plugin, (task) -> {
            if (count.getAndIncrement() >= 10 || dragon.isDead() || !target.isOnline()) {
                task.cancel();
                return;
            }

            Location dragonHead = dragon.getEyeLocation();
            Vector direction = target.getEyeLocation().toVector().subtract(dragonHead.toVector()).normalize();
            direction.add(new Vector(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).multiply(0.2));

            SmallFireball fireball = dragon.launchProjectile(SmallFireball.class, direction.multiply(1.5));
            fireball.setShooter(dragon);

        }, null, 1L, 5L);
    }

    private void lightningStorm(Player target) {
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0F, 1.0F);

        AtomicInteger strikes = new AtomicInteger(0);
        target.getScheduler().runAtFixedRate(plugin, (task) -> {
            if (strikes.getAndIncrement() >= 5 || !target.isOnline()) {
                task.cancel();
                return;
            }
            Location strikeLocation = target.getLocation().clone().add(
                    random.nextInt(10) - 5, 0, random.nextInt(10) - 5
            );
            target.getWorld().strikeLightning(strikeLocation);
        }, null, 1L, 10L);
    }

    private void enderRift(EnderDragon dragon, Player target) {
        Location riftCenter = target.getLocation().subtract(0, 1, 0);
        if (!riftCenter.getBlock().getType().isSolid()) return;

        dragon.getWorld().playSound(riftCenter, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0F, 0.5F);

        AtomicInteger duration = new AtomicInteger(0);
        plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, riftCenter, (task) -> {
            if (duration.getAndAdd(5) >= 100) {
                task.cancel();
                return;
            }

            riftCenter.getWorld().spawnParticle(Particle.PORTAL, riftCenter.clone().add(0, 1, 0), 100, 2, 0.5, 2, 0.1);

            List<Player> nearbyPlayers = riftCenter.getWorld().getPlayers().stream()
                    .filter(p -> p.getLocation().distanceSquared(riftCenter) < 8 * 8)
                    .toList();

            for (Player p : nearbyPlayers) {
                p.getScheduler().run(plugin, (playerTask) -> {
                    Vector pullVector = riftCenter.toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.3);
                    p.setVelocity(p.getVelocity().add(pullVector));
                }, null);

                if (duration.get() % 20 == 0) {
                    p.getScheduler().run(plugin, (playerTask) -> p.damage(2.0, dragon), null);
                }
            }
        }, 1L, 5L);
    }

    private Player findNearestPlayer(EnderDragon dragon, double range) {
        return dragon.getWorld().getPlayers().stream()
                .filter(p -> (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE)
                        && p.getLocation().distanceSquared(dragon.getLocation()) <= range * range)
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(dragon.getLocation())))
                .orElse(null);
    }
}