package fun.lonix.trustenderchest.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import fun.lonix.trustenderchest.TrustEnderChest;
import fun.lonix.trustenderchest.config.ConfigManager;

public class DatabaseManager {
   private final TrustEnderChest plugin;
   private final ConfigManager configManager;
   private Connection connection;

   public DatabaseManager(TrustEnderChest plugin) {
      this.plugin = plugin;
      this.configManager = plugin.getConfigManager();
   }

   public void initializeDatabase() {
      try {
         if (this.configManager.getDatabaseType().equalsIgnoreCase("mysql")) {
            this.initializeMySQL();
         } else {
            this.initializeSQLite();
         }

         this.createTables();
      } catch (SQLException var2) {
         this.plugin.getLogger().severe("Ошибка при инициализации базы данных: " + var2.getMessage());
      }
   }

   private void initializeMySQL() throws SQLException {
      String url = String.format(
         "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC",
         this.configManager.getDatabaseHost(),
         this.configManager.getDatabasePort(),
         this.configManager.getDatabaseName()
      );
      this.connection = DriverManager.getConnection(url, this.configManager.getDatabaseUser(), this.configManager.getDatabasePassword());
      this.plugin.getLogger().info("Подключение к MySQL установлено!");
   }

   private void initializeSQLite() throws SQLException {
      File dataFolder = this.plugin.getDataFolder();
      if (!dataFolder.exists()) {
         dataFolder.mkdirs();
      }

      String url = "jdbc:sqlite:" + dataFolder + "/enderchest.db";
      this.connection = DriverManager.getConnection(url);
      this.plugin.getLogger().info("Подключение к SQLite установлено!");
   }

   private void createTables() throws SQLException {
      String createPlayerDataTable = "CREATE TABLE IF NOT EXISTS player_data (uuid VARCHAR(36) PRIMARY KEY,player_name VARCHAR(16) NOT NULL,unlocked_slots INT NOT NULL DEFAULT 27,inventory_data TEXT)";

      try (Statement stmt = this.connection.createStatement()) {
         stmt.execute(createPlayerDataTable);
      }

      this.plugin.getLogger().info("Таблицы базы данных созданы!");
   }

   public void savePlayerData(UUID playerUUID, String playerName, int unlockedSlots, String inventoryData) {
      String sql = "INSERT INTO player_data (uuid, player_name, unlocked_slots, inventory_data) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), unlocked_slots = VALUES(unlocked_slots), inventory_data = VALUES(inventory_data)";
      if (this.configManager.getDatabaseType().equalsIgnoreCase("sqlite")) {
         sql = "INSERT OR REPLACE INTO player_data (uuid, player_name, unlocked_slots, inventory_data) VALUES (?, ?, ?, ?)";
      }

      try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
         pstmt.setString(1, playerUUID.toString());
         pstmt.setString(2, playerName);
         pstmt.setInt(3, unlockedSlots);
         pstmt.setString(4, inventoryData);
         pstmt.executeUpdate();
      } catch (SQLException var11) {
         this.plugin.getLogger().severe("Ошибка при сохранении данных игрока: " + var11.getMessage());
      }
   }

   public DatabaseManager.PlayerData loadPlayerData(UUID playerUUID) {
      String sql = "SELECT * FROM player_data WHERE uuid = ?";

      try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
         pstmt.setString(1, playerUUID.toString());
         ResultSet rs = pstmt.executeQuery();
         return rs.next()
            ? new DatabaseManager.PlayerData(
               UUID.fromString(rs.getString("uuid")), rs.getString("player_name"), rs.getInt("unlocked_slots"), rs.getString("inventory_data")
            )
            : null;
      } catch (SQLException var8) {
         this.plugin.getLogger().severe("Ошибка при загрузке данных игрока: " + var8.getMessage());
         return null;
      }
   }

   public boolean playerExists(String playerName) {
      String sql = "SELECT COUNT(*) FROM player_data WHERE player_name = ?";

      try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
         pstmt.setString(1, playerName);
         ResultSet rs = pstmt.executeQuery();
         return rs.next() ? rs.getInt(1) > 0 : false;
      } catch (SQLException var8) {
         this.plugin.getLogger().severe("Ошибка при проверке существования игрока: " + var8.getMessage());
         return false;
      }
   }

   public void addSlotsToPlayer(String playerName, int slots) {
      String sql = "UPDATE player_data SET unlocked_slots = unlocked_slots + ? WHERE player_name = ?";

      try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
         pstmt.setInt(1, slots);
         pstmt.setString(2, playerName);
         pstmt.executeUpdate();
      } catch (SQLException var9) {
         this.plugin.getLogger().severe("Ошибка при добавлении слотов игроку: " + var9.getMessage());
      }
   }

   public int getPlayerSlots(String playerName) {
      String sql = "SELECT unlocked_slots FROM player_data WHERE player_name = ?";

      try (PreparedStatement pstmt = this.connection.prepareStatement(sql)) {
         pstmt.setString(1, playerName);
         ResultSet rs = pstmt.executeQuery();
         return rs.next() ? rs.getInt("unlocked_slots") : this.configManager.getDefaultUnlockSlots();
      } catch (SQLException var8) {
         this.plugin.getLogger().severe("Ошибка при получении слотов игрока: " + var8.getMessage());
         return this.configManager.getDefaultUnlockSlots();
      }
   }

   public void closeConnection() {
      if (this.connection != null) {
         try {
            this.connection.close();
            this.plugin.getLogger().info("Соединение с базой данных закрыто!");
         } catch (SQLException var2) {
            this.plugin.getLogger().severe("Ошибка при закрытии соединения: " + var2.getMessage());
         }
      }
   }

   public static class PlayerData {
      private final UUID uuid;
      private final String playerName;
      private final int unlockedSlots;
      private final String inventoryData;

      public PlayerData(UUID uuid, String playerName, int unlockedSlots, String inventoryData) {
         this.uuid = uuid;
         this.playerName = playerName;
         this.unlockedSlots = unlockedSlots;
         this.inventoryData = inventoryData;
      }

      public UUID getUuid() {
         return this.uuid;
      }

      public String getPlayerName() {
         return this.playerName;
      }

      public int getUnlockedSlots() {
         return this.unlockedSlots;
      }

      public String getInventoryData() {
         return this.inventoryData;
      }
   }
}
