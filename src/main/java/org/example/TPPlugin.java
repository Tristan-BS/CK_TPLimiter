package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TPPlugin extends JavaPlugin {

    private HashMap<UUID, Long> teleportCooldown = new HashMap<>();
    private Location predefinedLocation = new Location(Bukkit.getWorld("world"), 100, 65, 100);
    private long COOLDOWN_PERIOD = TimeUnit.HOURS.toMillis(24);

    @Override
    public void onEnable() {
        getLogger().info("TPPlugin has been enabled.");
        loadTeleportDataAsync();
    }

    @Override
    public void onDisable() {
        getLogger().info("TPPlugin has been disabled.");
        saveTeleportData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        String commandName = command.getName().toLowerCase();

        switch (commandName) {
            case "cktp":
                handleTeleportCommand(player);
                return true;
            case "cksetcoords":
                handleSetCoordsCommand(player, args);
                return true;
            case "cksetnewlimit":
                handleSetNewLimitCommand(player, args);
                return true;
            case "ckresetlimit":
                handleResetLimitCommand(player, args);
                return true;
            default:
                return false;
        }
    }

    private void handleTeleportCommand(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            UUID playerUUID = player.getUniqueId();
            long currentTime = System.currentTimeMillis();

            if (teleportCooldown.containsKey(playerUUID)) {
                long timeLeft = teleportCooldown.get(playerUUID) - currentTime;
                if (timeLeft > 0) {
                    long hoursLeft = TimeUnit.MILLISECONDS.toHours(timeLeft);
                    long minutesLeft = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60;
                    long secondsLeft = TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60;
                    player.sendMessage("You have to wait: " + hoursLeft + " hours, " + minutesLeft + " minutes, " + secondsLeft + " seconds.");
                    return;
                }
            }

            if (predefinedLocation.getWorld() == null) {
                player.sendMessage("Teleport location world is not loaded or does not exist.");
                return;
            }

            Bukkit.getScheduler().runTask(this, () -> {
                player.teleport(predefinedLocation);
                teleportCooldown.put(playerUUID, currentTime + COOLDOWN_PERIOD);
                player.sendMessage("You've been teleported, thanks to ChichiKugel!");
                saveTeleportData();
            });
        });
    }

    private void handleSetCoordsCommand(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage("You need to be OP to use this command.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (args.length == 3) {
                try {
                    double x = Double.parseDouble(args[0]);
                    double y = Double.parseDouble(args[1]);
                    double z = Double.parseDouble(args[2]);
                    predefinedLocation = new Location(Bukkit.getWorld("world"), x, y, z);
                    player.sendMessage("New coordinates set to: " + x + ", " + y + ", " + z);
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid coordinates. Usage: /CKSetCoords <x> <y> <z>");
                }
            } else {
                predefinedLocation = player.getLocation();
                player.sendMessage("Coordinates set to your current location.");
            }
            saveTeleportData();
        });
    }

    private void handleSetNewLimitCommand(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage("You need to be OP to use this command.");
            return;
        }

        Bukkit.getScheduler().runTask(this, () -> {
            if (args.length == 0) {
                COOLDOWN_PERIOD = TimeUnit.HOURS.toMillis(48);
                player.sendMessage("Cooldown set to 48 hours.");
            } else {
                try {
                    long newCooldown = Long.parseLong(args[0]);
                    COOLDOWN_PERIOD = TimeUnit.HOURS.toMillis(newCooldown);
                    player.sendMessage("Cooldown set to " + newCooldown + " hours.");
                } catch (NumberFormatException e) {
                    player.sendMessage("Invalid cooldown. Usage: /CKSetNewLimit <hours>");
                }
            }
            saveTeleportData();
        });
    }

    private void handleResetLimitCommand(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage("You need to be OP to use this command.");
            return;
        }

        if (args.length != 1) {
            player.sendMessage("Invalid usage: /CKResetLimit <player>");
            return;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("Player not found.");
            return;
        }

        teleportCooldown.put(target.getUniqueId(), 0L);
        player.sendMessage("Cooldown for " + target.getName() + " has been reset.");
    }

    private void saveTeleportData() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("teleportData.txt"))) {
            long currentTime = System.currentTimeMillis();

            for (UUID uuid : teleportCooldown.keySet()) {
                long timeLeft = teleportCooldown.get(uuid) - currentTime;
                if (timeLeft > 0) {
                    writer.write(uuid + ":" + timeLeft);
                    writer.newLine();
                }
            }

            writer.write("CooldownPeriod:" + COOLDOWN_PERIOD);
            writer.newLine();

            if (predefinedLocation != null && predefinedLocation.getWorld() != null) {
                writer.write("Location:" + predefinedLocation.getWorld().getName() + "," +
                        predefinedLocation.getX() + "," +
                        predefinedLocation.getY() + "," +
                        predefinedLocation.getZ());
                writer.newLine();
            }
        } catch (IOException e) {
            getLogger().severe("Error saving teleport data: " + e.getMessage());
        }
    }

    private void loadTeleportDataAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            File file = new File("teleportData.txt");
            if (!file.exists()) return;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                long currentTime = System.currentTimeMillis();

                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");
                    if (line.startsWith("CooldownPeriod")) {
                        COOLDOWN_PERIOD = Long.parseLong(parts[1].trim());
                    } else if (line.startsWith("Location")) {
                        String[] locParts = parts[1].split(",");
                        World world = Bukkit.getWorld(locParts[0].trim());
                        predefinedLocation = new Location(world, Double.parseDouble(locParts[1]), Double.parseDouble(locParts[2]), Double.parseDouble(locParts[3]));
                    } else {
                        UUID uuid = UUID.fromString(parts[0].trim());
                        long timeLeft = Long.parseLong(parts[1].trim());
                        teleportCooldown.put(uuid, currentTime + timeLeft);
                    }
                }
            } catch (IOException e) {
                getLogger().severe("Error loading teleport data: " + e.getMessage());
            }
        });
    }
}
