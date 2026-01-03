package fun.lonix.trustenderchest;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import fun.lonix.trustenderchest.commands.EnderChestCommand;
import fun.lonix.trustenderchest.config.ConfigManager;
import fun.lonix.trustenderchest.database.DatabaseManager;
import fun.lonix.trustenderchest.listeners.EnderChestListener;
import fun.lonix.trustenderchest.managers.EnderChestManager;

public class TrustEnderChest extends JavaPlugin {
   private ConfigManager configManager;
   private DatabaseManager databaseManager;
   private EnderChestManager enderChestManager;
   private PlayerPointsAPI playerPointsAPI;

   public void onEnable() {
      this.getLogger().info(ChatColor.GREEN + "Этот плагин разработан студией TrustDev");
      this.getLogger().info(ChatColor.GREEN + "https://t.me/TrustDevs");
      if (Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")) {
         this.playerPointsAPI = PlayerPoints.getInstance().getAPI();
         this.getLogger().info(ChatColor.GREEN + "PlayerPoints API подключена");
      } else {
         this.playerPointsAPI = null;
         this.getLogger().warning("PlayerPoints плагин не найден");
      }

      this.initializeComponents();
      this.registerCommands();
      this.registerListeners();
      this.getLogger().info(ChatColor.GREEN + "TrustEnderChest успешно запущен!");
   }

   public void onDisable() {
      if (this.databaseManager != null) {
         this.databaseManager.closeConnection();
      }

      this.getLogger().info(ChatColor.RED + "trust" +
              "EnderChest отключен!");
   }

   private void initializeComponents() {
      this.configManager = new ConfigManager(this);
      this.configManager.loadConfig();
      this.databaseManager = new DatabaseManager(this);
      this.databaseManager.initializeDatabase();
      this.enderChestManager = new EnderChestManager(this);
   }

   private void registerCommands() {
      this.getCommand("ec").setExecutor(new EnderChestCommand(this));
      this.getCommand("enderchest").setExecutor(new EnderChestCommand(this));
   }

   private void registerListeners() {
      this.getServer().getPluginManager().registerEvents(new EnderChestListener(this), this);
   }

   public ConfigManager getConfigManager() {
      return this.configManager;
   }

   public DatabaseManager getDatabaseManager() {
      return this.databaseManager;
   }

   public EnderChestManager getEnderChestManager() {
      return this.enderChestManager;
   }

   public PlayerPointsAPI getPlayerPointsAPI() {
      return this.playerPointsAPI;
   }
}
