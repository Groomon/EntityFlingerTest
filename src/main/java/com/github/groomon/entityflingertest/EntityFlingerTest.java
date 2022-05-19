package com.github.groomon.entityflingertest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftEntity;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Objects;

public final class EntityFlingerTest extends JavaPlugin implements CommandExecutor, Listener {

    private Vector selectedTarget;
    private Entity selectedEntity;
    private double height;
    private double acceleration = 0.08; // blocks/tick^2
    private double drag = 0.02;         // 1/tick
    private double inertia = 0.91;         // 1/tick ?

    private boolean useOffset = true;
    private final double offset = 0.1;

    //not relevant
    Location lastClick;
    HashSet<Material> bypassBlocks = new HashSet<Material>(){{
        add(Material.AIR);
        add(Material.WATER);
        add(Material.LAVA);
    }};

    @Override
    public void onEnable() {
        Objects.requireNonNull(this.getCommand("fling")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    //select entity
    @EventHandler
    public void selectEntity(EntityDamageByEntityEvent e) {
        if(!(e.getDamager() instanceof Player)) return;
        Player p = (Player) e.getDamager();
        if(p.getInventory().getItemInMainHand().getType().equals(Material.SLIME_BALL)) {
            e.setCancelled(true);
            selectedEntity = e.getEntity();
            p.sendMessage("§aSelected entity: " + selectedEntity.getName() + " " + selectedEntity.getUniqueId());
        }
    }

    //select target position / spawn slippery zombie
    @EventHandler
    public void selectTarget(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if(e.getItem() == null) return;
        if(e.getAction().equals(Action.LEFT_CLICK_BLOCK) && Objects.requireNonNull(e.getItem()).getType().equals(Material.SLIME_BALL)) {
            e.setCancelled(true);
            selectedTarget = Objects.requireNonNull(e.getClickedBlock()).getLocation().toVector().add(new Vector(0.5, 1, 0.5));
            p.sendMessage("§aSelected target: " + selectedTarget);
        }
        if(e.getAction().equals(Action.LEFT_CLICK_BLOCK) && Objects.requireNonNull(e.getItem()).getType().equals(Material.ZOMBIE_SPAWN_EGG)) {
            e.setCancelled(true);
            spawnSlipperyZombie(e.getPlayer());
        }
    }

    //trigger fling with slime ball
    @EventHandler
    public void clickFling(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if(e.getItem() == null) return;
        if(e.getAction().equals(Action.RIGHT_CLICK_BLOCK) && Objects.requireNonNull(e.getItem()).getType().equals(Material.SLIME_BALL)) {
            e.setCancelled(true);
            //event fires two times. Not nice but easy workaround
            if(!Objects.requireNonNull(e.getClickedBlock()).getLocation().equals(lastClick)) preFling(p, new String[]{});
            lastClick = e.getClickedBlock().getLocation();
        }
        if((e.getAction().equals(Action.RIGHT_CLICK_AIR) && Objects.requireNonNull(e.getItem()).getType().equals(Material.SLIME_BALL))) {
            e.setCancelled(true);
            preFling(p, new String[]{});
        }
        if(e.getAction()==Action.RIGHT_CLICK_BLOCK && e.getItem().getType()==Material.ZOMBIE_SPAWN_EGG){
            p.sendMessage("§aSpawned §cnormal §azombie");
        }
    }

    //commands
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        switch(label) {
            case "fling":
                return preFling(sender, args);
            case "sz":
                return spawnSlipperyZombie(sender);
            case "offset":
                useOffset = !useOffset;
                sender.sendMessage("§aUse offset: §7" + useOffset);
                return true;
            default:
                return false;
        }
    }

    public boolean spawnSlipperyZombie(@NotNull CommandSender sender) {
        if(!(sender instanceof Player)) {
            sender.sendMessage("§cCan only be used by players");
            return true;
        }
        Player p = (Player) sender;
        Block spawnBlock = p.getTargetBlock(bypassBlocks, 50);
        if(bypassBlocks.contains(spawnBlock.getType())) {
            sender.sendMessage("§cNo valid spawn point in reach");
            return true;
        }

        Location spawnLocation = spawnBlock.getLocation().add(0.5, 1, 0.5);
        SlipperyZombie slipperyZombie = new SlipperyZombie(spawnLocation);
        ((CraftWorld) Objects.requireNonNull(spawnLocation.getWorld())).getHandle().addEntity(slipperyZombie, CreatureSpawnEvent.SpawnReason.CUSTOM);

        p.sendMessage("§aSpawned slippery zombie at " + spawnLocation.toVector());
        return true;
    }

    public boolean preFling(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length >= 1) {
            if (Objects.equals(args[0], "help")) {
                sender.sendMessage("§dSelect entity and target location (on clicked block) by left-clicking with a slime ball. Then: \n/fling [height/\"nice\"] [acceleration drag inertia]\nor use right-click with slimeball.\nSpawn slippery zombie with spawn egg left-click or: /sz\nToggle offset with: /offset");
                return true;
            }
        }

        if (selectedTarget == null || selectedEntity == null) {
            sender.sendMessage("§cPlease select entity and target");
            return true;
        }

        if (args.length == 2 || args.length == 3) {
            sender.sendMessage("§cSpecify acceleration, drag and inertia");
            return true;
        }

        if (args.length > 4) {
            sender.sendMessage("§cToo many arguments");
            return true;
        }

        if (args.length >= 1) {
            if (Objects.equals(args[0], "nice")) {
                height = calculateNiceHeight(selectedEntity.getLocation().toVector(), selectedTarget);
            } else {
                height = Double.parseDouble(args[0]);
                if (height <= 0) {
                    sender.sendMessage("§cHeight must be bigger than 0");
                    return true;
                }
                if (height <= selectedTarget.getY() - selectedEntity.getLocation().getY()) {
                    sender.sendMessage("§cHeight must be above target location");
                    return true;
                }
            }
        } else {
            height = calculateNiceHeight(selectedEntity.getLocation().toVector(), selectedTarget);
        }

        if (args.length == 4) {
            acceleration = Double.parseDouble(args[1]);
            drag = Double.parseDouble(args[2]);
            inertia = Double.parseDouble(args[3]);
            if (acceleration <= 0) {
                sender.sendMessage("§cAcceleration must be bigger than 0");
                return true;
            }
            if (drag < 0 || drag >= 1) {
                sender.sendMessage("§cDrag must be between 0 and 1");
                return true;
            }
            if (inertia <= 0 || inertia > 1) {
                sender.sendMessage("§cInertia must be between 0 and 1");
                return true;
            }
        }

        sender.sendMessage("§aCalculating velocity with following parameters:§7\n" +
                "  Entity: " + selectedEntity.getUniqueId() + "\n" +
                "  Target: " + selectedTarget.toString() + "\n" +
                "  Height: " + height + "\n" +
                "  Acceleration: " + acceleration + "\n" +
                "  Drag: " + drag + "\n" +
                "  Inertia: " + inertia);

        CraftEntity szEntity = (CraftEntity) selectedEntity;
        net.minecraft.server.v1_16_R3.Entity szNMS = szEntity.getHandle();
        if(szNMS instanceof SlipperyZombie) {
            ((SlipperyZombie) szNMS).disableFriction(true);
            }

        //offset
        if(!(szNMS instanceof SlipperyZombie) && useOffset) {
            selectedEntity.teleport(selectedEntity.getLocation().add(0, offset, 0));
            selectedEntity.setVelocity(new Vector(0, 0, 0));
        }

        //fling needs to be delayed two ticks after disabling the friction or offsetting. Don't know why
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, () -> fling(sender), 3L);

        return true;
    }

    public void fling(CommandSender sender) {
        CraftEntity szEntity = (CraftEntity) selectedEntity;
        net.minecraft.server.v1_16_R3.Entity szNMS = szEntity.getHandle();
        if(szNMS instanceof SlipperyZombie) {
            ((SlipperyZombie) szNMS).disableFriction(true);
        }

        Vector startLocation = selectedEntity.getLocation().toVector();

        //calculate y velocity to reach specified height
        double startHeight = 0;
        double startVelocity = 0;
        int startTicks = 0;

        if(!(szNMS instanceof SlipperyZombie) && useOffset) startHeight = offset;

        while(startHeight < height) {
            startTicks++;
            startVelocity = startVelocity / (1-drag) + acceleration;
            startHeight += startVelocity;
        }

        //calculate time to end height
        double safeEndHeight = startLocation.getY() + startHeight;
        double endHeight = safeEndHeight;
        double endVelocity = 0.0;
        int endTicks = 0;

        double targetLocationHeight = selectedTarget.getY();

        while(endHeight > targetLocationHeight) {
            endTicks++;
            safeEndHeight = endHeight;
            endVelocity = (endVelocity + acceleration) * (1-drag); //not quite sure about order
            endHeight -= endVelocity;
        }

        //calculate horizontal velocity
        Vector flatEntityLocation = selectedEntity.getLocation().toVector().clone().setY(0);
        Vector flatTargetLocation = selectedTarget.clone().setY(0);

        double distance = flatEntityLocation.clone().setY(0).distance(flatTargetLocation.clone().setY(0));
        double flatVelocity = ((inertia-1) * distance) / (Math.pow(inertia, startTicks+endTicks-1) - 1);
        Vector flatVelocityVector = flatTargetLocation.clone().subtract(flatEntityLocation).normalize().multiply(flatVelocity);

        //combine velocity vectors
        Vector flingVelocity = flatVelocityVector.clone().setY(startVelocity);

        //flinging the entity
        sender.sendMessage("§aStarting fling with following parameters:§7\n" +
                "  Start Location: " + selectedEntity.getLocation().toVector() + "\n" +
                "  Peak: " + startHeight + " at " + startTicks + " ticks" + "\n" +
                "  End Location: ~" + selectedTarget.clone().setY(safeEndHeight) + "\n" +
                "  Duration: " + (startTicks+endTicks) + " ticks" + "\n" +
                "  Velocity: " + flingVelocity + "\n" +
                "  Slippery Zombie: " + (szNMS instanceof SlipperyZombie) + "\n" +
                "  Offset: " + ((szNMS instanceof SlipperyZombie) || !useOffset ? "none" : offset));

        if(szNMS instanceof SlipperyZombie) {
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, () -> ((SlipperyZombie) szNMS).disableFriction(false), startTicks);
        }

        selectedEntity.setVelocity(flingVelocity);
        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(this, () -> selectedEntity.setVelocity(new Vector(0, selectedEntity.getVelocity().getY(), 0)), startTicks+endTicks-1); //-1 tick seems to work the best
    }

    //half of the horizontal distance above the highest location (y of entity and target)
    private double calculateNiceHeight(Vector a, Vector b) {
        double distance = a.clone().setY(0).distance(b.clone().setY(0));
        double height = distance / 2.0;
        if(a.getY() < b.getY()) height += b.getY() - a.getY();
        return height;
    }
}
