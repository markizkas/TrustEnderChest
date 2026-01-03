package fun.lonix.trustenderchest.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import fun.lonix.trustenderchest.TrustEnderChest;

public class ConfigManager {
   private final TrustEnderChest plugin;
   private FileConfiguration config;
   private Map<Material, Integer> itemLimits = new HashMap<>();

   public ConfigManager(TrustEnderChest plugin) {
      this.plugin = plugin;
   }

   public void loadConfig() {
      this.plugin.saveDefaultConfig();
      this.config = this.plugin.getConfig();
      this.loadItemLimits();
   }

   private void loadItemLimits() {
      this.itemLimits.clear();
      ConfigurationSection limitsSection = this.config.getConfigurationSection("item-limits");
      if (limitsSection != null) {
         for (String materialName : limitsSection.getKeys(false)) {
            try {
               Material material = Material.valueOf(materialName);
               int limit = limitsSection.getInt(materialName, -1);
               this.itemLimits.put(material, limit);
            } catch (IllegalArgumentException var61) {
               this.plugin.getLogger().warning("Неизвестный материал в конфиге: " + materialName);
            }
         }
      }
   }

   public Map<Material, Integer> getItemLimits() {
      return this.itemLimits;
   }

   public int getItemLimit(Material material) {
      return this.itemLimits.getOrDefault(material, -1);
   }

   public boolean isItemAllowed(Material material) {
      int limit = this.getItemLimit(material);
      return limit != 0;
   }

   public String getDatabaseType() {
      return this.config.getString("mysql.type", "sqlite");
   }

   public String getDatabaseHost() {
      return this.config.getString("mysql.host", "localhost");
   }

   public String getDatabaseUser() {
      return this.config.getString("mysql.user", "root");
   }

   public String getDatabaseName() {
      return this.config.getString("mysql.database", "");
   }

   public String getDatabasePassword() {
      return this.config.getString("mysql.password", "");
   }

   public int getDatabasePort() {
      return this.config.getInt("mysql.port", 3306);
   }

   public boolean isUpgradeSapphireEnabled() {
      return this.config.getBoolean("upgrade-settings.upgrade_sapphire", true);
   }

   public boolean isUpgradeExpEnabled() {
      return this.config.getBoolean("upgrade-settings.upgrade_exp", true);
   }

   public Sound getSuccessSound() {
      try {
         return Sound.valueOf(this.config.getString("upgrade-settings.sound_success", "BLOCK_NOTE_BLOCK_PLING"));
      } catch (Exception var2) {
         return Sound.BLOCK_NOTE_BLOCK_PLING;
      }
   }

   public Sound getFailSound() {
      try {
         return Sound.valueOf(this.config.getString("upgrade-settings.sound_fail", "BLOCK_ANVIL_PLACE"));
      } catch (Exception var2) {
         return Sound.BLOCK_ANVIL_PLACE;
      }
   }

   public double getUpgradeMultiplier() {
      return this.config.getDouble("upgrade-settings.upgrade_multiplier", 1.0);
   }

   public int getExpPrice() {
      return this.config.getInt("upgrade-settings.price_unlock_exp", 100);
   }

   public int getSapphirePrice() {
      return this.config.getInt("upgrade-settings.price_unlock_sapphire", 50);
   }

   public String getInventoryTitle() {
      return this.translateHexColors(this.config.getString("upgrade-settings.title", "Эндер-сундук"));
   }

   public int getInventorySize() {
      return this.config.getInt("upgrade-settings.inventory_size", 54);
   }

   public int getDefaultUnlockSlots() {
      return this.config.getInt("upgrade-settings.default_unlock", 27);
   }

   public Material getUnlockSlotMaterial() {
      try {
         return Material.valueOf(this.config.getString("upgrade-settings.unlock_slot.material", "LIGHT_BLUE_STAINED_GLASS_PANE"));
      } catch (Exception var2) {
         return Material.LIGHT_BLUE_STAINED_GLASS_PANE;
      }
   }

   public String getUnlockSlotName() {
      return this.translateHexColors(this.config.getString("upgrade-settings.unlock_slot.name", "&7"));
   }

   public List<String> getUnlockSlotLore() {
      List<String> lore = this.config.getStringList("upgrade-settings.unlock_slot.lore");
      lore.replaceAll(this::translateHexColors);
      return lore;
   }

   public Material getLockSlotMaterial() {
      try {
         return Material.valueOf(this.config.getString("upgrade-settings.lock_slot.material", "ORANGE_STAINED_GLASS_PANE"));
      } catch (Exception var2) {
         return Material.ORANGE_STAINED_GLASS_PANE;
      }
   }

   public String getLockSlotName() {
      return this.translateHexColors(this.config.getString("upgrade-settings.lock_slot.name", "&7"));
   }

   public List<String> getLockSlotLore() {
      List<String> lore = this.config.getStringList("upgrade-settings.lock_slot.lore");
      lore.replaceAll(this::translateHexColors);
      return lore;
   }

   public String getMessage(String key) {
      return this.translateHexColors(this.config.getString("messages." + key, "Сообщение не найдено: " + key));
   }

   private String translateHexColors(String message) {
      message = message.replaceAll("#([A-Fa-f0-9]{6})", "§x§$1");
      message = message.replaceAll("§x§([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])([A-Fa-f0-9])", "§x§$1§$2§$3§$4§$5§$6");
      return ChatColor.translateAlternateColorCodes('&', message);
   }

   public int calculateUpgradePrice(int basePrice, int currentSlots, int defaultSlots) {
      int upgrades = currentSlots - defaultSlots;
      return (int)(basePrice * Math.pow(this.getUpgradeMultiplier(), upgrades));
   }
}
