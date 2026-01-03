package fun.lonix.trustenderchest.listeners;

import java.util.Map.Entry;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import fun.lonix.trustenderchest.TrustEnderChest;
import fun.lonix.trustenderchest.config.ConfigManager;
import fun.lonix.trustenderchest.managers.EnderChestManager;

public class EnderChestListener implements Listener {
   private final TrustEnderChest plugin;
   private final ConfigManager configManager;
   private final EnderChestManager enderChestManager;

   public EnderChestListener(TrustEnderChest plugin) {
      this.plugin = plugin;
      this.configManager = plugin.getConfigManager();
      this.enderChestManager = plugin.getEnderChestManager();
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onPlayerInteract(PlayerInteractEvent event) {
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
         Block block = event.getClickedBlock();
         if (block != null && block.getType() == Material.ENDER_CHEST) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            this.enderChestManager.openEnderChest(player);
         }
      }
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (event.getView().getTitle().equals(this.configManager.getInventoryTitle())) {
            Inventory top = event.getView().getTopInventory();
            Inventory clickedInv = event.getClickedInventory();
            ItemStack current = event.getCurrentItem();
            ItemStack cursor = event.getCursor();
            if (current != null && current.getType() == this.configManager.getUnlockSlotMaterial()) {
               event.setCancelled(true);
               boolean useExperience = event.isLeftClick();
               boolean success = this.enderChestManager.upgradeSlot(player, useExperience);
               if (!success) {
                  player.playSound(player.getLocation(), this.configManager.getFailSound(), 1.0F, 1.0F);
               }
            } else if (current != null && current.getType() == this.configManager.getLockSlotMaterial()) {
               event.setCancelled(true);
            } else {
               boolean isOwnChest = this.enderChestManager.isOwnerOfInventory(player, top);
               boolean allowTake = player.hasPermission("minestashenderchestupgrade.ec.take");
               if (!isOwnChest && !allowTake) {
                  if (clickedInv != null && clickedInv.equals(top)) {
                     event.setCancelled(true);
                     return;
                  }

                  if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                     && clickedInv != null
                     && clickedInv.equals(event.getView().getBottomInventory())) {
                     event.setCancelled(true);
                     return;
                  }
               }

               switch (event.getAction()) {
                  case MOVE_TO_OTHER_INVENTORY:
                     if (clickedInv != null
                        && clickedInv.equals(event.getView().getBottomInventory())
                        && current != null
                        && !current.getType().isAir()
                        && !this.isAllowedToAdd(top, current)) {
                        event.setCancelled(true);
                        this.warnLimitOrBan(player, current.getType(), top);
                     }
                     break;
                  case HOTBAR_SWAP:
                  case HOTBAR_MOVE_AND_READD:
                     if (clickedInv != null && clickedInv.equals(top)) {
                        int btn = event.getHotbarButton();
                        if (btn >= 0) {
                           ItemStack hotbar = player.getInventory().getItem(btn);
                           if (hotbar != null && !hotbar.getType().isAir() && !this.isAllowedToAdd(top, hotbar)) {
                              event.setCancelled(true);
                              this.warnLimitOrBan(player, hotbar.getType(), top);
                           }
                        }
                     }
                     break;
                  case PLACE_ALL:
                  case PLACE_ONE:
                  case PLACE_SOME:
                  case SWAP_WITH_CURSOR:
                     if (clickedInv != null && clickedInv.equals(top) && cursor != null && !cursor.getType().isAir() && !this.isAllowedToAdd(top, cursor)) {
                        event.setCancelled(true);
                        this.warnLimitOrBan(player, cursor.getType(), top);
                     }
                     break;
                  default:
                     if (clickedInv != null && clickedInv.equals(top) && cursor != null && !cursor.getType().isAir() && !this.isAllowedToAdd(top, cursor)) {
                        event.setCancelled(true);
                        this.warnLimitOrBan(player, cursor.getType(), top);
                     }
               }
            }
         }
      }
   }

   private boolean isAllowedToAdd(Inventory top, ItemStack incoming) {
      Material m = incoming.getType();
      if (!this.configManager.isItemAllowed(m)) {
         return false;
      } else {
         int limit = this.configManager.getItemLimit(m);
         if (limit <= 0) {
            return true;
         } else {
            int current = this.countItemsInInventory(top, m);
            int totalAfter = current + incoming.getAmount();
            return totalAfter <= limit;
         }
      }
   }

   private int countItemsInInventory(Inventory inventory, Material material) {
      int count = 0;

      for (ItemStack item : inventory.getContents()) {
         if (item != null
            && item.getType() != this.configManager.getUnlockSlotMaterial()
            && item.getType() != this.configManager.getLockSlotMaterial()
            && item.getType() == material) {
            count += item.getAmount();
         }
      }

      return count;
   }

   @EventHandler
   public void onInventoryDrag(InventoryDragEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (event.getView().getTitle().equals(this.configManager.getInventoryTitle())) {
            Inventory top = event.getView().getTopInventory();
            boolean affectsTop = event.getRawSlots().stream().anyMatch(rawx -> rawx < top.getSize());
            if (affectsTop) {
               for (int raw : event.getRawSlots()) {
                  if (raw < top.getSize()) {
                     ItemStack slotItem = top.getItem(raw);
                     if (slotItem != null && slotItem.getType() == this.configManager.getLockSlotMaterial()) {
                        event.setCancelled(true);
                        return;
                     }
                  }
               }

               ItemStack dragged = event.getOldCursor();
               if (dragged != null && !dragged.getType().isAir() && !this.isAllowedToAddAfterDrag(top, dragged, event)) {
                  event.setCancelled(true);
                  this.warnLimitOrBan(player, dragged.getType(), top);
               }
            }
         }
      }
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      if (event.getPlayer() instanceof Player) {
         Player player = (Player)event.getPlayer();
         if (event.getView().getTitle().equals(this.configManager.getInventoryTitle())) {
            this.enderChestManager.savePlayerInventory(player);
         }
      }
   }

   @EventHandler
   public void onPlayerJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      this.plugin.getDatabaseManager().loadPlayerData(player.getUniqueId());
   }

   @EventHandler
   public void onPlayerQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.enderChestManager.savePlayerInventory(player);
      this.enderChestManager.removePlayerInventory(player);
   }

   private boolean isSystemItem(ItemStack item) {
      if (item == null) {
         return false;
      } else {
         Material material = item.getType();
         return material == this.configManager.getUnlockSlotMaterial() || material == this.configManager.getLockSlotMaterial();
      }
   }

   private boolean isAllowedToAddAfterDrag(Inventory top, ItemStack incoming, InventoryDragEvent e) {
      Material m = incoming.getType();
      if (!this.configManager.isItemAllowed(m)) {
         return false;
      } else {
         int limit = this.configManager.getItemLimit(m);
         if (limit <= 0) {
            return true;
         } else {
            int current = this.countItemsInInventory(top, m);
            int add = 0;

            for (Entry<Integer, ItemStack> en : e.getNewItems().entrySet()) {
               int raw = en.getKey();
               if (raw < top.getSize()) {
                  ItemStack it = en.getValue();
                  if (it != null && it.getType() == m) {
                     add += it.getAmount();
                  }
               }
            }

            return current + add <= limit;
         }
      }
   }

   private void warnLimitOrBan(Player p, Material m, Inventory top) {
      if (!this.configManager.isItemAllowed(m)) {
         p.sendMessage(this.configManager.getMessage("item_banned"));
      } else {
         int limit = this.configManager.getItemLimit(m);
         p.sendMessage(this.configManager.getMessage("item_limit_reached").replace("%limit%", String.valueOf(limit)));
      }
   }
}
