package com.github.groomon.entityflingertest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class EntityFlingerTest extends JavaPlugin implements CommandExecutor, Listener {

    private Vector selectedTarget;
    private Entity selectedEntity;

    //not relevant
    Location lastClick;

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

    //select target position
    @EventHandler
    public void selectTarget(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if(e.getItem() == null) return;
        if(e.getAction().equals(Action.LEFT_CLICK_BLOCK) && Objects.requireNonNull(e.getItem()).getType().equals(Material.SLIME_BALL)) {
            e.setCancelled(true);
            selectedTarget = Objects.requireNonNull(e.getClickedBlock()).getLocation().toVector().add(new Vector(0.5, 1, 0.5));
            p.sendMessage("§aSelected target: " + selectedTarget);
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
            if(!Objects.requireNonNull(e.getClickedBlock()).getLocation().equals(lastClick)) fling(p, new String[]{});
            lastClick = e.getClickedBlock().getLocation();
        }
        if((e.getAction().equals(Action.RIGHT_CLICK_AIR) && Objects.requireNonNull(e.getItem()).getType().equals(Material.SLIME_BALL))) {
            e.setCancelled(true);
            fling(p, new String[]{});
        }
    }

    //fling command
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if(!label.equalsIgnoreCase("fling")) return false;
        return fling(sender, args);
    }

    public boolean fling(@NotNull CommandSender sender,  @NotNull String[] args) {
        if(args.length >= 1) {
            if(Objects.equals(args[0], "help")) {
                sender.sendMessage("§dSelect entity and target location (on clicked block) by left-clicking with a slime ball. Then: \n/fling [height/\"nice\"] [acceleration drag]");
                return true;
            }
        }

        if(selectedTarget == null || selectedEntity == null) {
            sender.sendMessage("§cPlease select entity and target");
            return true;
        }

        if(args.length == 2 || args.length == 3) {
            sender.sendMessage("§cSpecify acceleration, drag and inertia");
            return true;
        }

        if(args.length > 4) {
            sender.sendMessage("§cToo many arguments");
            return true;
        }

        double height;
        double acceleration = 0.08; // blocks/tick^2
        double drag = 0.02;         // 1/tick
        double inertia = 0.91;         // 1/tick ?

        if(args.length >= 1) {
            if(Objects.equals(args[0], "nice")) {
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

        if(args.length == 4) {
            acceleration = Double.parseDouble(args[1]);
            drag = Double.parseDouble(args[2]);
            inertia = Double.parseDouble(args[3]);
            if(acceleration <= 0) {
                sender.sendMessage("§cAcceleration must be bigger than 0");
                return true;
            }
            if(drag < 0 || drag >= 1) {
                sender.sendMessage("§cDrag must be between 0 and 1");
                return true;
            }
            if(inertia <= 0 || inertia > 1) {
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

        Vector startLocation = selectedEntity.getLocation().toVector();

        //calculate y velocity to reach specified height
        double startHeight = 0;
        double startVelocity = 0;
        int startTicks = 0;

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
                "  Velocity: " + flingVelocity);

        selectedEntity.setVelocity(flingVelocity);

        return true;
    }

    //half of the horizontal distance above the highest location (y of entity and target)
    private double calculateNiceHeight(Vector a, Vector b) {
        double distance = a.clone().setY(0).distance(b.clone().setY(0));
        double height = distance / 2.0;
        if(a.getY() < b.getY()) height += b.getY() - a.getY();
        return height;
    }
}
