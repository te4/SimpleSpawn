package me.ellbristow.SimpleSpawn;


import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockIgniteEvent.IgniteCause;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class SimpleSpawn extends JavaPlugin implements Listener {

    public static SimpleSpawn plugin;
    private SQLBridge SSdb;
    public int tpEffect = 4; // 4 = off
    private boolean setHomeWithBeds = false;
    private String[] spawnColumns = {"world", "x", "y", "z", "yaw", "pitch"};
    private String[] spawnDims = {"TEXT NOT NULL PRIMARY KEY", "DOUBLE NOT NULL DEFAULT 0", "DOUBLE NOT NULL DEFAULT 0", "DOUBLE NOT NULL DEFAULT 0", "FLOAT NOT NULL DEFAULT 0", "FLOAT NOT NULL DEFAULT 0"};
    private String[] jailColumns = {"name", "world", "x", "y", "z", "yaw", "pitch"};
    private String[] jailDims = {"TEXT NOT NULL PRIMARY KEY", "TEXT NOT NULL", "DOUBLE NOT NULL DEFAULT 0", "DOUBLE NOT NULL DEFAULT 0", "DOUBLE NOT NULL DEFAULT 0", "FLOAT NOT NULL DEFAULT 0", "FLOAT NOT NULL DEFAULT 0"};
    private String[] jailedColumns = {"player", "jailName"};
    private String[] jailedDims = {"TEXT NOT NULL PRIMARY KEY", "TEXT NOT NULL"};
    private HashMap<String, Boolean> jailCache = new HashMap<String, Boolean>();

    @Override
    public void onDisable() {
        SSdb.close();
    }

    @Override
    public void onEnable() {
        SSdb = new SQLBridge(this);
        SSdb.getConnection();
        if (!SSdb.checkTable("WorldSpawns")) {
            SSdb.createTable("WorldSpawns", spawnColumns, spawnDims);
        }
        if (!SSdb.checkTable("Jails")) {
            SSdb.createTable("Jails", jailColumns, jailDims);
        }
        if (!SSdb.checkTable("Jailed")) {
            SSdb.createTable("Jailed", jailedColumns, jailedDims);
        }
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        Player player;

        if (commandLabel.equalsIgnoreCase("jail")) {
            if (sender instanceof Player && !sender.hasPermission("simplespawn.jail.use")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use that command!");
                return false;
            }
            if (args.length == 1) {
                if (getJail("default") == null) {
                    sender.sendMessage(ChatColor.RED + "No default jail has been set!");
                    sender.sendMessage(ChatColor.RED + "Use: /setjail");
                    return false;
                }
                OfflinePlayer target = getServer().getOfflinePlayer(args[0]);
                if (!target.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.RED + "Player '" + ChatColor.WHITE + args[0] + ChatColor.RED + "' not found!");
                    return false;
                }
                if (target.isOnline()) {
                    Player targetPlayer = target.getPlayer();
                    if (!targetPlayer.hasPermission("simplespawn.jail.immune")) {
                        targetPlayer.teleport(getJail("default"), TeleportCause.PLUGIN);
                        setJailed(target.getName(), true, "default");
                        getServer().broadcastMessage(target.getName() + ChatColor.GOLD + " has been jailed!");
                        return true;
                    } else {
                        sender.sendMessage(targetPlayer.getName() + ChatColor.RED + " cannot be jailed!");
                        return false;
                    }
                } else {
                    setJailed(target.getName(), true, "default");
                    getServer().broadcastMessage(target.getName() + ChatColor.GOLD + " has been sentenced to jail!");
                }
                return true;
            } else if (args.length == 2) {
                if (getJail(args[1]) == null) {
                    sender.sendMessage(ChatColor.RED + "Could not find a jail called '" + ChatColor.WHITE + args[1] + ChatColor.RED + "'!");
                    sender.sendMessage(ChatColor.RED + "Use: /jail [player] {jailName}");
                    return false;
                }
                OfflinePlayer target = getServer().getOfflinePlayer(args[0]);
                if (!target.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.RED + "Player '" + ChatColor.WHITE + args[0] + ChatColor.RED + "' not found!");
                    return false;
                }
                if (target.isOnline()) {
                    Player targetPlayer = target.getPlayer();
                    if (!targetPlayer.hasPermission("simplespawn.jail.immune")) {
                        targetPlayer.teleport(getJail(args[1]), TeleportCause.PLUGIN);
                        setJailed(target.getName(), true, args[1]);
                        getServer().broadcastMessage(target.getName() + ChatColor.GOLD + " has been jailed!");
                        return true;
                    } else {
                        sender.sendMessage(targetPlayer.getName() + ChatColor.RED + " cannot be jailed!");
                        return false;
                    }
                } else {
                    setJailed(target.getName(), true, "default");
                    getServer().broadcastMessage(target.getName() + ChatColor.GOLD + " has been sentenced to jail!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Command not recognised!");
                sender.sendMessage(ChatColor.RED + "Try: /jail [playerName] {jailName}");
                return false;
            }
            return true;
        } else if (commandLabel.equalsIgnoreCase("release")) {
            if (sender instanceof Player && !sender.hasPermission("simplespawn.jail.release")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use that command!");
                return false;
            }
            if (args.length == 1) {
                OfflinePlayer target = getServer().getOfflinePlayer(args[0]);
                if (!target.hasPlayedBefore()) {
                    sender.sendMessage(ChatColor.RED + "Player '" + ChatColor.WHITE + args[0] + ChatColor.RED + "' not found!");
                    return false;
                }
                if (!isJailed(target.getName())) {
                    sender.sendMessage(target.getName() + ChatColor.RED + " is not in jail!");
                    return false;
                }
                setJailed(target.getName(), false, args[0]);
                getServer().broadcastMessage(target.getName() + ChatColor.GOLD + " has been released from jail!");
                if (target.isOnline()) {
                    target.getPlayer().sendMessage(ChatColor.GOLD + "You may now leave the jail!");
                }
            } else {
                sender.sendMessage(ChatColor.RED + "Command not recognised!");
                sender.sendMessage(ChatColor.RED + "Try: /release [playerName]");
                return false;
            }
            return true;
        } else if (commandLabel.equalsIgnoreCase("jails")) {
            if (sender instanceof Player && !sender.hasPermission("simplespawn.jail.list")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to use that command!");
                return false;
            }
            HashMap<Integer, HashMap<String, Object>> jailList = SSdb.select("name", "Jails", null, null, null);
            if (jailList == null || jailList.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "No jails were found!");
                return true;
            }
            String jailNames = "";
            for (int i = 0; i < jailList.size(); i++) {
                if (i != 0) {
                    jailNames += ",";
                }
                jailNames += jailList.get(i).get("name");
            }
            String[] jailSplit = jailNames.split(",");
            String jails = ChatColor.GOLD + "";
            for (int i = 0; i < jailSplit.length; i++) {
                if (i != 0) {
                    jails += ChatColor.GOLD + ", ";
                }
                String jail = jailSplit[i];
                jails += jail;
                HashMap<Integer, HashMap<String, Object>> inMates = SSdb.select("player", "Jailed", "jailName = '" + jail + "'", null, null);
                if (inMates != null && !inMates.isEmpty()) {
                    jails += ChatColor.GRAY + " (Inmates: " + inMates.size() + ")";
                }
            }
            sender.sendMessage(jails);
            return true;
        }

////////////////////////////////////////////////////////////////////////////////
        if (!(sender instanceof Player)) {
            sender.sendMessage("Sorry! This command cannot be run from the console!");
            return false;
        }
        player = (Player) sender;
        if (commandLabel.equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("simplespawn.set")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to set the spawn location!");
                return false;
            }
            if (args.length == 0) {
                setWorldSpawn(player.getLocation());
                player.sendMessage(ChatColor.GOLD + "Spawn been set to this location for this world!");
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "Command not recognised!");
                player.sendMessage(ChatColor.RED + "Try: /setspawn OR /setspawn SSdefault");
                return false;
            }
        } else if (commandLabel.equalsIgnoreCase("spawn")) {
            if (isJailed(player.getName()) && !player.hasPermission("simplespawn.jail.immune")) {
                player.sendMessage(ChatColor.RED + "You cannot /spawn from jail!");
                return false;
            }
            if (args.length == 0) {
                player.teleport(getWorldSpawn(player.getWorld().getName()));
                return true;
            } else if (args.length == 1) {
                World world = getServer().getWorld(args[0]);
                if (world == null) {
                    player.sendMessage(ChatColor.RED + "World '" + ChatColor.WHITE + args[0] + ChatColor.RED + "' not found!");
                    return false;
                }
                player.teleport(getWorldSpawn(world.getName()));
                return true;
            } else {
                player.sendMessage(ChatColor.RED + "Command not recognised!");
                player.sendMessage(ChatColor.RED + "Try: /spawn {worldName}");
                return false;
            }
        } else if (commandLabel.equalsIgnoreCase("home")) {
            if (isJailed(player.getName()) && !player.hasPermission("simplespawn.jail.immune")) {
                player.sendMessage(ChatColor.RED + "You cannot /home from jail!");
                return false;
            }
            if (!player.hasPermission("simplespawn.home")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use that command!");
                return false;
            }

            safeTeleport(player, getHomeLoc(player));
            return true;
        } else if (commandLabel.equalsIgnoreCase("setjail")) {
            if (!player.hasPermission("simplespawn.jail.set")) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use that command!");
                return false;
            }
            if (args.length == 0) {
                setJail("default", player.getLocation());
                player.sendMessage(ChatColor.GOLD + "Default jail set to your location!");
            } else if (args.length == 1) {
                setJail(args[0], player.getLocation());
                player.sendMessage(ChatColor.GOLD + "Jail '" + ChatColor.WHITE + args[0] + ChatColor.GOLD + "' set to your location!");
            } else {
                player.sendMessage(ChatColor.RED + "Command not recognised!");
                player.sendMessage(ChatColor.RED + "Try: /setjail OR /setjail {jailName}");
                return false;
            }
        }
        return false;
    }

    public Location getHomeLoc(Player player) {
        return player.getBedSpawnLocation() != null
                ? player.getBedSpawnLocation()
                : getServer().getWorlds().get(0).getSpawnLocation();
    }

    public void safeTeleport(final Player player, final Location loc) {
        int[] distances = {1, 2};
        if (loc.getWorld().isChunkLoaded(loc.getBlockX(), loc.getBlockY()))
            loc.getWorld().refreshChunk(loc.getBlockX(), loc.getBlockY());
        
        if (player.isInsideVehicle())
            player.getVehicle().eject();
        
        player.teleport(loc);
        for (final int i : distances) {
            getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {

                @Override
                public void run() {
                    double heightdiff = loc.getY() - player.getLocation().getY();
                    double latdiff = Math.abs(loc.getX() - player.getLocation().getX()) + Math.abs(loc.getZ() - player.getLocation().getZ());
                    //getLogger().log(Level.INFO, player.getName() + " fell " + heightdiff + " after " + (i*20));
                    if (heightdiff > 0 && latdiff < i * 5) {
                        player.teleport(loc);
                    }
                }
            }, i * 20L);
        }
    }

    public void setJail(String jailName, Location loc) {
        jailName = jailName.toLowerCase();
        String world = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();
        SSdb.query("INSERT OR REPLACE INTO Jails (name, world, x, y, z, yaw, pitch) VALUES ('" + jailName + "', '" + world + "', " + x + ", " + y + ", " + z + ", " + yaw + ", " + pitch + ")");
    }

    public Location getJail(String jailName) {
        jailName = jailName.toLowerCase();
        HashMap<Integer, HashMap<String, Object>> result = SSdb.select("world, x, y, z, yaw, pitch", "Jails", "name = '" + jailName + "'", null, null);
        Location location;
        if (result.isEmpty()) {
            return null;
        } else {
            String world = (String) result.get(0).get("world");
            double x = (Double) result.get(0).get("x");
            double y = (Double) result.get(0).get("y");
            double z = (Double) result.get(0).get("z");
            float yaw = Float.parseFloat(result.get(0).get("yaw").toString());
            float pitch = Float.parseFloat(result.get(0).get("pitch").toString());
            location = new Location(getServer().getWorld(world), x, y, z, yaw, pitch);
        }
        return location;
    }

    public void setJailed(String playerName, boolean toJail, String jailName) {
        if (jailName == null || "".equals(jailName)) {
            jailName = "default";
        } else {
            jailName = jailName.toLowerCase();
        }
        if (toJail) {
            SSdb.query("INSERT OR REPLACE INTO Jailed (player, jailName) VALUES ('" + playerName + "', '" + jailName + "')");
        } else {
            SSdb.query("DELETE FROM Jailed WHERE player = '" + playerName + "'");
        }
        jailCache.put(playerName, toJail);
    }

    public boolean isJailed(String playerName) {
        HashMap<Integer, HashMap<String, Object>> jailQuery;
        
        if (jailCache.containsKey(playerName)) {
            return jailCache.get(playerName);
        } else {
            jailQuery = SSdb.select("player", "Jailed", "player = '" + playerName + "'", null, null);
            return jailQuery != null && !jailQuery.isEmpty();
        }
    }

    public String getWhereJailed(String playerName) {
        HashMap<Integer, HashMap<String, Object>> jailed = SSdb.select("jailName", "Jailed", "player = '" + playerName + "'", null, null);
        if (jailed == null || jailed.isEmpty()) {
            return null;
        }
        return (String) jailed.get(0).get("jailName");
    }

    public void setWorldSpawn(Location location) {
        String world = location.getWorld().getName();
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        float yaw = location.getYaw();
        float pitch = location.getPitch();
        SSdb.query("INSERT OR REPLACE INTO WorldSpawns (world, x, y, z, yaw, pitch) VALUES ('" + world + "', " + x + ", " + y + ", " + z + ", " + yaw + ", " + pitch + ")");
        location.getWorld().setSpawnLocation((int) x, (int) y, (int) z);
    }

    public Location getWorldSpawn(String world) {
        HashMap<Integer, HashMap<String, Object>> result = SSdb.select("x, y, z, yaw, pitch", "WorldSpawns", "world = '" + world + "'", null, null);
        Location location;
        if (result.isEmpty()) {
            location = getServer().getWorld(world).getSpawnLocation();
        } else {
            double x = (Double) result.get(0).get("x");
            double y = (Double) result.get(0).get("y");
            double z = (Double) result.get(0).get("z");
            float yaw = Float.parseFloat(result.get(0).get("yaw").toString());
            float pitch = Float.parseFloat(result.get(0).get("pitch").toString());
            location = new Location(getServer().getWorld(world), x, y, z, yaw, pitch);
        }
        return location;
    }

    /* EVENT LISTENERS */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onBedInteract(PlayerInteractEvent event) {
        Player player;
        if (event.isCancelled())
            return;
        if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK) || !event.getClickedBlock().getType().equals(Material.BED_BLOCK))
            return;
        if (!event.getClickedBlock().getWorld().getEnvironment().equals(Environment.NORMAL)) {
            player = event.getPlayer();
            event.setCancelled(true);
            player.setBedSpawnLocation(event.getClickedBlock().getLocation());
            player.sendMessage("your home has been set.");
            
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPlayedBefore()) {
            player.teleport(getWorldSpawn(player.getWorld().getName()), TeleportCause.PLUGIN);
            return;
        }
        if (isJailed(player.getName())) {
            if (player.hasPermission("simplespawn.jail.immune")) {
                setJailed(player.getName(), false, null);
                getServer().broadcastMessage(player.getName() + ChatColor.GOLD + " was pardoned from serving jail time!");
            } else {
                player.teleport(getJail(getWhereJailed(player.getName())), TeleportCause.PLUGIN);
                getServer().broadcastMessage(player.getName() + ChatColor.GOLD + " has been jailed!");
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (isJailed(player.getName())) {
            if (player.hasPermission("simplespawn.jail.immune")) {
                setJailed(player.getName(), false, null);
                getServer().broadcastMessage(player.getName() + ChatColor.GOLD + " was pardoned from serving jail time!");
                event.setRespawnLocation(getHomeLoc(player));
            } else {
                event.setRespawnLocation(getJail(getWhereJailed(player.getName())));
                getServer().broadcastMessage(player.getName() + ChatColor.GOLD + " has been jailed!");
            }
        } else {
            if (event.isBedSpawn() && !setHomeWithBeds) {
                event.setRespawnLocation(player.getBedSpawnLocation());
            } else {
                event.setRespawnLocation(getHomeLoc(player));
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!event.isCancelled()) {
            if (event.getCause().equals(TeleportCause.COMMAND)) {
                Player player = event.getPlayer();
                if (isJailed(player.getName())) {
                    if (player.hasPermission("simplespawn.jail.immune")) {
                        setJailed(player.getName(), false, null);
                        getServer().broadcastMessage(player.getName() + ChatColor.GOLD + " was pardoned from serving jail time!");
                    } else {
                        event.setTo(getJail(getWhereJailed(player.getName())));
                        player.sendMessage(ChatColor.RED + "You cannot teleport while you're in jail!");
                    }
                }

                Location loc = event.getTo();
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (isJailed(player.getName())) {
            if (player.hasPermission("simplespawn.jail.immune")) {
                setJailed(player.getName(), false, null);
            } else {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You cannot break blocks while you're in jail!");
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.isCancelled()) {
            Player player = event.getPlayer();
            if (isJailed(player.getName())) {
                if (player.hasPermission("simplespawn.jail.immune")) {
                    setJailed(player.getName(), false, null);
                } else {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot place blocks while you're in jail!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (!event.isCancelled() && event.getCause().equals(IgniteCause.FLINT_AND_STEEL)) {
            Player player = event.getPlayer();
            if (isJailed(player.getName())) {
                if (player.hasPermission("simplespawn.jail.immune")) {
                    setJailed(player.getName(), false, null);
                } else {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot place ignite blocks while you're in jail!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerEmptyBucket(PlayerBucketEmptyEvent event) {
        if (!event.isCancelled()) {
            Player player = event.getPlayer();
            if (isJailed(player.getName())) {
                if (player.hasPermission("simplespawn.jail.immune")) {
                    setJailed(player.getName(), false, null);
                } else {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "You cannot empty your bucket while in jail!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerPVP(EntityDamageByEntityEvent event) {
        if (!event.isCancelled()) {
            Entity entity = event.getEntity();
            if (entity instanceof Player) {
                Player player = (Player) entity;
                if (isJailed(player.getName())) {
                    if (player.hasPermission("simplespawn.jail.immune")) {
                        setJailed(player.getName(), false, null);
                    } else {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "You cannot fight while in jail!");
                    }
                }
            }
        }
    }
}
