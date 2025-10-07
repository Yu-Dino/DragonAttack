package net.yuflow.dragonattack;

import org.bukkit.*;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.SmallFireball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DragonListener implements Listener {

    private final Dragonattack plugin;
    private final Random random = new Random();
    private long lastChainDashTime = 0;

    public DragonListener(Dragonattack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDragonPhaseChange(EnderDragonChangePhaseEvent event) {
        EnderDragon dragon = event.getEntity();
        EnderDragon.Phase newPhase = event.getNewPhase();
        List<Player> allPlayers = getValidPlayers(dragon, 250);

        boolean isFlying = newPhase == EnderDragon.Phase.CIRCLING || newPhase == EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET || newPhase == EnderDragon.Phase.FLY_TO_PORTAL;

        if (isFlying && !allPlayers.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastChainDashTime > 15000 && random.nextInt(10) < 5) {
                lastChainDashTime = currentTime;
                chainDashAttack(dragon, allPlayers);
                return;
            }
        }

        if (newPhase == EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET && !allPlayers.isEmpty()) {
            int attackChoice = random.nextInt(3);
            switch (attackChoice) {
                case 0:
                    fireballBarrage(dragon, allPlayers.get(random.nextInt(allPlayers.size())));
                    break;
                case 1:
                    lightningStorm(allPlayers);
                    break;
                case 2:
                    enderRift(dragon, allPlayers.get(random.nextInt(allPlayers.size())));
                    break;
            }
        }

        if (newPhase == EnderDragon.Phase.LAND_ON_PORTAL) {
            int attackChoice = random.nextInt(2);
            switch (attackChoice) {
                case 0:
                    tailSweepAttack(dragon);
                    break;
                case 1:
                    if (!allPlayers.isEmpty()) {
                        enderRift(dragon, allPlayers.get(random.nextInt(allPlayers.size())));
                    }
                    break;
            }
        }
    }

    private void chainDashAttack(EnderDragon dragon, List<Player> allPlayers) {
        Collections.shuffle(allPlayers);
        int targetCount = Math.min(allPlayers.size(), random.nextInt(6) + 5); // 5 bis 10 Spieler
        Queue<Player> targets = new LinkedList<>(allPlayers.subList(0, targetCount));

        if (!targets.isEmpty()) {
            executeNextDash(dragon, targets);
        }
    }

    private void executeNextDash(EnderDragon dragon, Queue<Player> targets) {
        if (targets.isEmpty() || dragon.isDead()) {
            return;
        }
        Player currentTarget = targets.poll();
        if (currentTarget == null || !currentTarget.isOnline()) {
            executeNextDash(dragon, targets);
            return;
        }

        performSingleDash(dragon, currentTarget, () -> executeNextDash(dragon, targets));
    }

    private void performSingleDash(EnderDragon dragon, Player target, Runnable onComplete) {
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 3.0F, 1.5F);
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_WITHER_SHOOT, 2.0F, 1.0F);

        AtomicInteger dashTicks = new AtomicInteger(0);
        dragon.getScheduler().runAtFixedRate(plugin, task -> {
            if (dashTicks.getAndIncrement() > 40 || dragon.isDead() || !target.isValid()) {
                task.cancel();
                onComplete.run();
                return;
            }

            Vector direction = target.getLocation().toVector().subtract(dragon.getLocation().toVector()).normalize();
            dragon.setVelocity(direction.multiply(3.5));
            dragon.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, dragon.getLocation(), 20, 1, 1, 1, 0.1);

            for (Player p : dragon.getWorld().getPlayers()) {
                if (p.equals(target) && p.getLocation().distanceSquared(dragon.getLocation()) < 25) {
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 1.0F);
                    p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                    p.damage(10.0, dragon);
                    Vector knockback = p.getLocation().toVector().subtract(dragon.getLocation().toVector()).normalize().multiply(2.0).setY(1.0);
                    p.setVelocity(knockback);
                    task.cancel();
                    onComplete.run();
                    return;
                }
            }
        }, null, 1L, 1L);
    }

    private void lightningStorm(List<Player> allPlayers) {
        Collections.shuffle(allPlayers);
        int targetCount = Math.min(allPlayers.size(), 3);

        for (int i = 0; i < targetCount; i++) {
            Player target = allPlayers.get(i);
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0F, 1.0F);
            AtomicInteger strikes = new AtomicInteger(0);
            target.getScheduler().runAtFixedRate(plugin, (task) -> {
                if (strikes.getAndIncrement() >= 5 || !target.isOnline()) {
                    task.cancel();
                    return;
                }
                Location strikeLocation = target.getLocation().clone().add(random.nextInt(10) - 5, 0, random.nextInt(10) - 5);
                target.getWorld().strikeLightning(strikeLocation);
            }, null, 1L, 10L);
        }
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
            riftCenter.getWorld().spawnParticle(Particle.PORTAL, riftCenter.clone().add(0, 1, 0), 150, 4, 1, 4, 0.1);
            List<Player> nearbyPlayers = getValidPlayers(dragon, 250).stream()
                    .filter(p -> p.getLocation().distanceSquared(riftCenter) < 144)
                    .toList();
            for (Player p : nearbyPlayers) {
                p.getScheduler().run(plugin, (playerTask) -> {
                    Vector pullVector = riftCenter.toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.4);
                    p.setVelocity(p.getVelocity().add(pullVector));
                }, null);
                if (duration.get() % 20 == 0) {
                    p.getScheduler().run(plugin, (playerTask) -> p.damage(3.0, dragon), null);
                }
            }
        }, 1L, 5L);
    }

    private void tailSweepAttack(EnderDragon dragon) {
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_AMBIENT, 2.0F, 0.5F);
        dragon.getScheduler().runDelayed(plugin, task -> {
            if (dragon.isDead()) return;
            Location dragonLoc = dragon.getLocation();
            Vector dragonDir = dragonLoc.getDirection().setY(0).normalize();
            Vector dragonBackwardDir = dragonDir.clone().multiply(-1);
            dragon.getWorld().playSound(dragonLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2.0F, 1.0F);
            dragon.getWorld().spawnParticle(Particle.SWEEP_ATTACK, dragonLoc.clone().add(dragonBackwardDir.multiply(4)), 5, 4, 1, 4, 0);
            for (Player p : getValidPlayers(dragon, 20)) {
                Vector playerDir = p.getLocation().toVector().subtract(dragonLoc.toVector()).setY(0).normalize();
                if (playerDir.angle(dragonBackwardDir) < Math.toRadians(70)) {
                    p.damage(8.0, dragon);
                    Vector knockback = playerDir.multiply(2.5).setY(0.5);
                    p.setVelocity(knockback);
                }
            }
        }, null, 20L);
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

    private List<Player> getValidPlayers(EnderDragon dragon, double range) {
        return dragon.getWorld().getPlayers().stream()
                .filter(p -> (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                        && p.getLocation().distanceSquared(dragon.getLocation()) <= range * range)
                .collect(Collectors.toList());
    }
}