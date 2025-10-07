package net.yuflow.dragonattack;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EnderDragonChangePhaseEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DragonListener implements Listener {

    private final Dragonattack plugin;
    private final Random random = new Random();
    private long lastChainDashTime = 0;
    private boolean isEnraged = false;
    private boolean healthHasBeenSet = false;
    private EnderDragon bossDragon;

    public DragonListener(Dragonattack plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (bossDragon != null && !bossDragon.isDead() && player.getWorld().equals(bossDragon.getWorld())) {
            double healAmount = bossDragon.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.05;
            bossDragon.setHealth(Math.min(bossDragon.getAttribute(Attribute.MAX_HEALTH).getValue(), bossDragon.getHealth() + healAmount));
            bossDragon.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1));
            bossDragon.getWorld().getPlayers().forEach(p -> p.sendMessage(ChatColor.RED + player.getName() + " ist gefallen! Der Drache absorbiert seine Seele und wird stärker!"));
        }
    }

    @EventHandler
    public void onDragonPhaseChange(EnderDragonChangePhaseEvent event) {
        EnderDragon dragon = event.getEntity();
        this.bossDragon = dragon;
        EnderDragon.Phase newPhase = event.getNewPhase();

        if (!healthHasBeenSet && dragon.getHealth() > 1.0) {
            List<Player> playersInEnd = getValidPlayers(dragon, 1000);
            double baseHealth = 200.0;
            double healthPerPlayer = 75.0;
            double maxHealth = baseHealth + (playersInEnd.size() * healthPerPlayer);
            dragon.getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
            dragon.setHealth(maxHealth);
            healthHasBeenSet = true;
            Bukkit.getLogger().info("[DragonAttack] Drachen-HP auf " + maxHealth + " für " + playersInEnd.size() + " Spieler skaliert.");
        }

        if (!isEnraged && dragon.getHealth() / dragon.getAttribute(Attribute.MAX_HEALTH).getValue() <= 0.40) {
            isEnraged = true;
            dragon.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 1));
            dragon.getWorld().getPlayers().forEach(p -> {
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.5F, 1.0F);
                p.sendTitle(ChatColor.DARK_RED + "WUTANFALL!", ChatColor.RED + "Die Insel selbst wird zu eurem Feind!", 10, 70, 20);
            });
            awakenEndermen(dragon);
        }

        List<Player> allPlayers = getValidPlayers(dragon, 250);

        boolean isFlying = newPhase == EnderDragon.Phase.CIRCLING || newPhase == EnderDragon.Phase.SEARCH_FOR_BREATH_ATTACK_TARGET || newPhase == EnderDragon.Phase.FLY_TO_PORTAL;

        if (isFlying && !allPlayers.isEmpty()) {
            long currentTime = System.currentTimeMillis();
            long cooldown = isEnraged ? 7000 : 15000;
            if (currentTime - lastChainDashTime > cooldown && random.nextInt(10) < 6) {
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

    private void awakenEndermen(EnderDragon dragon) {
        List<Player> players = getValidPlayers(dragon, 300);
        if (players.isEmpty()) return;
        dragon.getWorld().getEntitiesByClass(Enderman.class).forEach(enderman -> {
            enderman.setTarget(players.get(random.nextInt(players.size())));
        });
    }

    private void chainDashAttack(EnderDragon dragon, List<Player> allPlayers) {
        Collections.shuffle(allPlayers);
        int targetCount = Math.min(allPlayers.size(), random.nextInt(6) + 5);
        Queue<Player> targets = new LinkedList<>(allPlayers.subList(0, targetCount));

        if (targets.isEmpty()) return;

        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_WITHER_SPAWN, 2.0F, 1.2F);

        for (Player target : targets) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 140, 1));
            target.sendTitle(ChatColor.RED + "DU WIRST GEJAGT", ChatColor.DARK_RED + "Es gibt kein Entkommen!", 10, 60, 10);
            target.playSound(target.getLocation(), Sound.ENTITY_GHAST_WARN, 1.0F, 1.0F);
        }

        dragon.getScheduler().runDelayed(plugin, task -> {
            if (!targets.isEmpty()) {
                executeNextDash(dragon, targets);
            }
        }, null, 140L);
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

        float dashSpeed = isEnraged ? 4.2f : 3.7f;

        AtomicInteger dashTicks = new AtomicInteger(0);
        dragon.getScheduler().runAtFixedRate(plugin, task -> {
            if (dashTicks.getAndIncrement() > 40 || dragon.isDead() || !target.isValid()) {
                task.cancel();
                onComplete.run();
                return;
            }

            Vector direction = target.getLocation().toVector().subtract(dragon.getLocation().toVector()).normalize();
            dragon.setVelocity(direction.multiply(dashSpeed));
            dragon.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, dragon.getLocation(), 20, 1, 1, 1, 0.1);

            for (Player p : dragon.getWorld().getPlayers()) {
                if (p.equals(target) && p.getLocation().distanceSquared(dragon.getLocation()) < 25) {
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0F, 1.0F);
                    p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation(), 1);
                    p.damage(15.0, dragon);
                    Vector knockback = p.getLocation().toVector().subtract(dragon.getLocation().toVector()).normalize().multiply(2.5).setY(1.2);
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
        int targetCount = Math.min(allPlayers.size(), isEnraged ? 5 : 3);

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

                AreaEffectCloud cloud = target.getWorld().spawn(strikeLocation, AreaEffectCloud.class);
                cloud.setParticle(Particle.DRAGON_BREATH);
                cloud.setDuration(100);
                cloud.setRadius(3.0f);
                cloud.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 0), true);

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
                if (random.nextInt(2) == 0) {
                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            riftCenter.clone().add(x, 0, z).getBlock().setType(Material.AIR);
                        }
                    }
                    riftCenter.getWorld().playSound(riftCenter, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.5f, 0.8f);
                }
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
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
                }, null);
                if (duration.get() % 20 == 0) {
                    p.getScheduler().run(plugin, (playerTask) -> p.damage(4.0, dragon), null);
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
                    p.damage(isEnraged ? 15.0 : 10.0, dragon);
                    Vector knockback = playerDir.multiply(3.0).setY(0.6);
                    p.setVelocity(knockback);
                }
            }
        }, null, 15L);
    }

    private void fireballBarrage(EnderDragon dragon, Player target) {
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 2.0F, 1.0F);
        AtomicInteger count = new AtomicInteger(0);
        dragon.getScheduler().runAtFixedRate(plugin, (task) -> {
            if (count.getAndIncrement() >= 15 || dragon.isDead() || !target.isOnline()) {
                task.cancel();
                return;
            }
            Location dragonHead = dragon.getEyeLocation();
            Vector direction = target.getEyeLocation().toVector().subtract(dragonHead.toVector()).normalize();
            direction.add(new Vector(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).multiply(0.2));
            SmallFireball fireball = dragon.launchProjectile(SmallFireball.class, direction.multiply(1.8));
            fireball.setShooter(dragon);
        }, null, 1L, 4L);
    }

    private List<Player> getValidPlayers(EnderDragon dragon, double range) {
        if (dragon.getWorld() == null) return new ArrayList<>();
        return dragon.getWorld().getPlayers().stream()
                .filter(p -> (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)
                        && p.getLocation().distanceSquared(dragon.getLocation()) <= range * range)
                .collect(Collectors.toList());
    }
}