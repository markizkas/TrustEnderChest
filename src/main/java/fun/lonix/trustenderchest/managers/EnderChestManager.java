package fun.lonix.trustenderchest.managers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;
import fun.lonix.trustenderchest.TrustEnderChest;
import fun.lonix.trustenderchest.config.ConfigManager;
import fun.lonix.trustenderchest.database.DatabaseManager;

public class EnderChestManager {
   private final TrustEnderChest plugin;
   private final ConfigManager configManager;
   private final DatabaseManager databaseManager;
   private final Map<UUID, Inventory> playerInventories = new HashMap<>();

   public EnderChestManager(TrustEnderChest plugin) {
      this.plugin = plugin;
      this.configManager = plugin.getConfigManager();
      this.databaseManager = plugin.getDatabaseManager();
   }

   public void openEnderChest(Player player) {
      UUID playerUUID = player.getUniqueId();
      Inventory inventory = this.getOrCreatePlayerInventory(player);
      player.openInventory(inventory);
   }

   private Inventory getOrCreatePlayerInventory(Player player) {
      UUID playerUUID = player.getUniqueId();
      if (this.playerInventories.containsKey(playerUUID)) {
         return this.playerInventories.get(playerUUID);
      } else {
         DatabaseManager.PlayerData playerData = this.databaseManager.loadPlayerData(playerUUID);
         Inventory inventory = Bukkit.createInventory((InventoryHolder)null, this.configManager.getInventorySize(), this.configManager.getInventoryTitle());
         if (playerData != null) {
            this.loadInventoryFromData(inventory, playerData.getInventoryData());
            this.setupInventorySlots(inventory, playerData.getUnlockedSlots());
         } else {
            int defaultSlots = this.configManager.getDefaultUnlockSlots();
            this.setupInventorySlots(inventory, defaultSlots);
            this.databaseManager.savePlayerData(playerUUID, player.getName(), defaultSlots, this.inventoryToString(inventory));
         }

         this.playerInventories.put(playerUUID, inventory);
         return inventory;
      }
   }

   private void setupInventorySlots(Inventory inventory, int unlockedSlots) {
      for (int i = 0; i < inventory.getSize(); i++) {
         ItemStack item = inventory.getItem(i);
         if (item != null && (item.getType() == this.configManager.getUnlockSlotMaterial() || item.getType() == this.configManager.getLockSlotMaterial())) {
            inventory.setItem(i, null);
         }
      }

      for (int ix = unlockedSlots; ix < inventory.getSize(); ix++) {
         if (ix == unlockedSlots && unlockedSlots < inventory.getSize()) {
            inventory.setItem(ix, this.createUnlockSlot(unlockedSlots));
         } else if (ix > unlockedSlots) {
            inventory.setItem(ix, this.createLockedSlot());
         }
      }
   }

   private ItemStack createUnlockSlot(int unlockedSlots) {
      ItemStack item = new ItemStack(this.configManager.getUnlockSlotMaterial());
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(this.configManager.getUnlockSlotName());
      List<String> lore = this.configManager.getUnlockSlotLore();
      int expPrice = this.configManager.calculateUpgradePrice(this.configManager.getExpPrice(), unlockedSlots, this.configManager.getDefaultUnlockSlots());
      int sapphirePrice = this.configManager
         .calculateUpgradePrice(this.configManager.getSapphirePrice(), unlockedSlots, this.configManager.getDefaultUnlockSlots());
      List<String> processedLore = new ArrayList<>();

      for (String line : lore) {
         String processedLine = line.replace("%exp%", String.valueOf(expPrice)).replace("%sapphire%", String.valueOf(sapphirePrice));
         processedLore.add(processedLine);
      }

      meta.setLore(processedLore);
      item.setItemMeta(meta);
      return item;
   }

   private ItemStack createLockedSlot() {
      ItemStack item = new ItemStack(this.configManager.getLockSlotMaterial());
      ItemMeta meta = item.getItemMeta();
      meta.setDisplayName(this.configManager.getLockSlotName());
      meta.setLore(this.configManager.getLockSlotLore());
      item.setItemMeta(meta);
      return item;
   }

   public boolean upgradeSlot(Player player, boolean useExperience) {
      UUID playerUUID = player.getUniqueId();
      DatabaseManager.PlayerData playerData = this.databaseManager.loadPlayerData(playerUUID);
      if (playerData == null) {
         return false;
      } else {
         int currentSlots = playerData.getUnlockedSlots();
         int maxSlots = this.configManager.getInventorySize();
         if (currentSlots >= maxSlots) {
            player.sendMessage(this.configManager.getMessage("all_slots_unlocked"));
            return false;
         } else {
            int expPrice = this.configManager.calculateUpgradePrice(this.configManager.getExpPrice(), currentSlots, this.configManager.getDefaultUnlockSlots());
            int sapphirePrice = this.configManager
               .calculateUpgradePrice(this.configManager.getSapphirePrice(), currentSlots, this.configManager.getDefaultUnlockSlots());
            if (useExperience) {
               if (!this.configManager.isUpgradeExpEnabled()) {
                  return false;
               }

               if (player.getLevel() < expPrice) {
                  player.sendMessage(this.configManager.getMessage("no_exp").replace("%exp%", String.valueOf(expPrice)));
                  return false;
               }

               player.setLevel(player.getLevel() - expPrice);
            } else {
               if (!this.configManager.isUpgradeSapphireEnabled()) {
                  return false;
               }

               PlayerPointsAPI ppAPI = this.plugin.getPlayerPointsAPI();
               if (ppAPI == null) {
                  this.plugin.getLogger().severe("PlayerPoints API недоступен");
                  return false;
               }

               int currentPoints = ppAPI.look(playerUUID);
               if (currentPoints < sapphirePrice) {
                  player.sendMessage(this.configManager.getMessage("no_sapphire").replace("%sapphire%", String.valueOf(sapphirePrice)));
                  return false;
               }

               boolean success = ppAPI.take(playerUUID, sapphirePrice);
               if (!success) {
                  this.plugin.getLogger().warning("Не удалось списать поинты с игрока " + player.getName());
                  return false;
               }
            }

            int newSlots = currentSlots + 1;
            Inventory inventory = this.playerInventories.get(playerUUID);
            if (inventory == null) {
               inventory = this.getOrCreatePlayerInventory(player);
            }

            inventory.setItem(currentSlots, null);
            if (newSlots < maxSlots) {
               inventory.setItem(newSlots, this.createUnlockSlot(newSlots));
            }

            for (int i = newSlots + 1; i < maxSlots; i++) {
               inventory.setItem(i, this.createLockedSlot());
            }

            String inventoryData = this.inventoryToString(inventory);
            this.databaseManager.savePlayerData(playerUUID, player.getName(), newSlots, inventoryData);
            this.playerInventories.put(playerUUID, inventory);
            player.updateInventory();
            player.sendMessage(this.configManager.getMessage("success"));
            player.playSound(player.getLocation(), this.configManager.getSuccessSound(), 1.0F, 1.0F);
            return true;
         }
      }
   }

   public void savePlayerInventory(Player player) {
      UUID playerUUID = player.getUniqueId();
      Inventory inventory = this.playerInventories.get(playerUUID);
      if (inventory != null) {
         DatabaseManager.PlayerData playerData = this.databaseManager.loadPlayerData(playerUUID);
         if (playerData != null) {
            String inventoryData = this.inventoryToString(inventory);
            this.databaseManager.savePlayerData(playerUUID, player.getName(), playerData.getUnlockedSlots(), inventoryData);
         }
      }
   }

   public void removePlayerInventory(Player player) {
      this.playerInventories.remove(player.getUniqueId());
   }

   private String inventoryToString(Inventory inventory) {
      try {
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
         BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
         dataOutput.writeInt(inventory.getSize());

         for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && !this.isSystemItem(item)) {
               dataOutput.writeObject(item);
            } else {
               dataOutput.writeObject(null);
            }
         }

         dataOutput.close();
         return Base64Coder.encodeLines(outputStream.toByteArray());
      } catch (IOException var61) {
         this.plugin.getLogger().severe("Ошибка при сериализации инвентаря: " + var61.getMessage());
         return "";
      }
   }

   private void loadInventoryFromData(Inventory inventory, String data) {
      if (data != null && !data.isEmpty()) {
         try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();

            for (int i = 0; i < size; i++) {
               ItemStack item = (ItemStack)dataInput.readObject();
               if (item != null) {
                  inventory.setItem(i, item);
               }
            }

            dataInput.close();
         } catch (IOException | ClassNotFoundException var81) {
            this.plugin.getLogger().severe("Ошибка при десериализации инвентаря: " + var81.getMessage());
         }
      }
   }

   private boolean isSystemItem(ItemStack item) {
      if (item == null) {
         return false;
      } else {
         Material material = item.getType();
         return material == this.configManager.getUnlockSlotMaterial() || material == this.configManager.getLockSlotMaterial();
      }
   }

   public Inventory getReadOnlyInventory(Player targetPlayer, boolean allowTake) {
      UUID uuid = targetPlayer.getUniqueId();
      DatabaseManager.PlayerData playerData = this.databaseManager.loadPlayerData(uuid);
      Inventory inventory = Bukkit.createInventory((InventoryHolder)null, this.configManager.getInventorySize(), this.configManager.getInventoryTitle());
      if (playerData != null) {
         this.loadInventoryFromData(inventory, playerData.getInventoryData());
         if (!allowTake) {
            for (int i = 0; i < inventory.getSize(); i++) {
               ItemStack item = inventory.getItem(i);
               if (item != null && this.isSystemItem(item)) {
                  inventory.setItem(i, (ItemStack)null);
               }
            }
         } else {
            this.setupInventorySlots(inventory, playerData.getUnlockedSlots());
         }
      }

      return inventory;
   }

   public boolean isOwnerOfInventory(Player player, Inventory inventory) {
      Inventory ownedInventory = this.playerInventories.get(player.getUniqueId());
      return ownedInventory != null && ownedInventory.equals(inventory);
   }

   public int getPlayerUnlockedSlots(Player player) {
      DatabaseManager.PlayerData playerData = this.databaseManager.loadPlayerData(player.getUniqueId());
      return playerData != null ? playerData.getUnlockedSlots() : this.configManager.getDefaultUnlockSlots();
   }
}
