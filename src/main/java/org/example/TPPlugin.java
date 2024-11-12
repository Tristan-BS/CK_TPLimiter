package org.example;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class TPPlugin extends JavaPlugin {

    private HashMap<UUID, Long> teleportCooldown = new HashMap<>();
    private Location predefinedLocation = new Location(Bukkit.getWorld("world"), 100, 65, 100);
    private long COOLDOWN_PERIOD = TimeUnit.HOURS.toMillis(48);

    @Override
    public void onEnable() {
        getLogger().info("TPPlugin has been enabled.");

        // Asynchronous method to load teleport data
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (new File("teleportData.txt").exists()) {
                loadTeleportData();
            } else {
                getLogger().info("No teleportData.txt file found.");
            }
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("TPPlugin has been disabled.");

        // Cancel all asynchronous tasks before disabling the plugin
        try {
            Bukkit.getScheduler().cancelTasks(this);  // Stop all tasks using this plugin
        } catch (Exception e) {
            getLogger().severe("Error while disabling tasks: " + e.getMessage());
        }

        // Save teleport data synchronously
        saveTeleportData();  // Synchronous call
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cktp")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                // Asynchronous processing of the command
                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    UUID playerUUID = player.getUniqueId();
                    long currentTime = System.currentTimeMillis();

                    // Check if the player has already teleported and if the cooldown period has passed
                    if (teleportCooldown.containsKey(playerUUID)) {
                        long timeLeft = teleportCooldown.get(playerUUID) - currentTime;
                        if (timeLeft > 0) {
                            long minutesLeft = TimeUnit.MILLISECONDS.toMinutes(timeLeft);
                            long hoursLeft = TimeUnit.MILLISECONDS.toHours(timeLeft);
                            long secondsLeft = TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60;

                            if (minutesLeft < 10) {
                                player.sendMessage("You have to wait: " + minutesLeft + " minutes and " + secondsLeft + " seconds, before you can teleport again.");
                            } else {
                                player.sendMessage("You have to wait: " + hoursLeft + " hours and " + (minutesLeft % 60) + " minutes, before you can teleport again.");
                            }
                            return;
                        }
                    }

                    // Check if the world exists
                    if (predefinedLocation.getWorld() == null) {
                        player.sendMessage("The world for the teleport location is not loaded or does not exist.");
                        return;
                    }

                    // Teleport the player
                    Bukkit.getScheduler().runTask(this, () -> {
                        player.teleport(predefinedLocation);
                        player.sendMessage("You've been teleported, thanks to ChichiKugel!");
                    });
                    teleportCooldown.put(playerUUID, currentTime + COOLDOWN_PERIOD);
                });
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("CKSetCoords")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                // Check if the player is OP
                if (!player.isOp()) {
                    player.sendMessage("You need to be OP to use this command. Maybe ask ChichiKugel for help?");
                    return true;
                }

                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    if (args.length == 3) {
                        try {
                            double x = Double.parseDouble(args[0]);
                            double y = Double.parseDouble(args[1]);
                            double z = Double.parseDouble(args[2]);

                            predefinedLocation = new Location(Bukkit.getWorld("world"), x, y, z);
                            player.sendMessage("New coordinates defined: " + x + ", " + y + ", " + z);
                        } catch (NumberFormatException e) {
                            player.sendMessage("Invalid coordinates usage: /CKSetCoords <x> <y> <z> or just /CKSetCoords to set your current position");
                        }
                    } else {
                        // If no coordinates are passed, use the player's current position
                        Location playerLocation = player.getLocation();
                        predefinedLocation = playerLocation;
                        player.sendMessage("New coordinates set to: "
                                + playerLocation.getBlockX() + ", "
                                + playerLocation.getBlockY() + ", "
                                + playerLocation.getBlockZ());
                    }
                });
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("CKSetNewLimit")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                // Check if the player is OP
                if (!player.isOp()) {
                    player.sendMessage("You need to be OP to use this command. Maybe ask ChichiKugel for help?");
                    return true;
                }

                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    // If no argument is passed, set the cooldown to 48 hours
                    if (args.length == 0) {
                        COOLDOWN_PERIOD = TimeUnit.HOURS.toMillis(48);
                        player.sendMessage("New cooldown set to 48 hours");
                    } else {
                        try {
                            long newCooldown = Long.parseLong(args[0]);
                            COOLDOWN_PERIOD = TimeUnit.HOURS.toMillis(newCooldown);
                            player.sendMessage("New cooldown set to " + newCooldown + " hours");
                        } catch (NumberFormatException e) {
                            player.sendMessage("Invalid cooldown usage: /CKSetNewLimit <hours>");
                        }
                    }
                });
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("CKResetLimit")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                // Check if the player is OP
                if (!player.isOp()) {
                    player.sendMessage("You need to be OP to use this command. Maybe ask ChichiKugel for help?");
                    return true;
                }

                Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    if (args.length == 1) {
                        // Find the target player asynchronously
                        Player target = Bukkit.getPlayer(args[0]);
                        if (target != null) {
                            // Execute the task that affects the server synchronously
                            Bukkit.getScheduler().runTask(this, () -> {
                                teleportCooldown.put(target.getUniqueId(), 0L);
                                player.sendMessage("Cooldown for " + target.getName() + " has been reset.");
                            });
                        } else {
                            // If the player is not found, send the message synchronously
                            Bukkit.getScheduler().runTask(this, () -> {
                                player.sendMessage("Player not found.");
                            });
                        }
                    } else {
                        // Incorrect command usage
                        Bukkit.getScheduler().runTask(this, () -> {
                            player.sendMessage("Invalid usage: /CKResetLimit <player>");
                        });
                    }
                });
                return true;
            }
        }

        return false;
    }

    private void saveTeleportData() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("teleportData.txt"))) {
            long currentTime = System.currentTimeMillis();
            for (UUID uuid : teleportCooldown.keySet()) {
                long timeLeft = teleportCooldown.get(uuid) - currentTime;
                if (timeLeft > 0) {
                    writer.write(uuid.toString() + ":" + timeLeft);
                    writer.newLine();
                }
            }

            // Save the new cooldown period
            writer.write("New Cooldown Period: " + COOLDOWN_PERIOD);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Asynchronous loading of teleport data
    private void loadTeleportData() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            File file = new File("teleportData.txt");
            if (!file.exists()) return;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                long currentTime = System.currentTimeMillis();
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":");

                    // Check if it's the line for the cooldown period
                    if (line.startsWith("New Cooldown Period")) {
                        // Correctly parse the cooldown period
                        COOLDOWN_PERIOD = Long.parseLong(parts[1].trim());
                        getLogger().info("New cooldown period set to: " + COOLDOWN_PERIOD);
                    }
                    // Check if it's a UUID (standard format for UUID)
                    else if (parts.length == 2) {
                        try {
                            UUID uuid = UUID.fromString(parts[0]);
                            long timeLeft = Long.parseLong(parts[1]);
                            teleportCooldown.put(uuid, currentTime + timeLeft);
                        } catch (IllegalArgumentException e) {
                            getLogger().warning("Error parsing UUID in teleportData.txt: " + parts[0]);
                        }
                    } else {
                        getLogger().warning("Invalid line in teleportData.txt: " + line);
                    }
                }
            } catch (IOException | NumberFormatException e) {
                e.printStackTrace();
                getLogger().severe("Error loading teleport data.");
            }
        });
    }
}
