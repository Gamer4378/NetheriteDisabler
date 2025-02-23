package me.heroicstudios.netheritedisabler;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class NetheriteDisabler extends JavaPlugin implements Listener, TabCompleter {
    private FileConfiguration config;
    private FileConfiguration guiConfig;
    private final Map<String, Material> netheriteItems = new HashMap<>();
    private final Map<Material, String> permissionMap = new HashMap<>();
    private String guiTitle;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        File guiFile = new File(getDataFolder(), "gui.yml");
        if (!guiFile.exists()) {
            saveResource("gui.yml", false);
        }
        loadConfigs();
        initializeMaps();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("netheritedisabler")).setTabCompleter(this);
        getLogger().info("NetheriteDisabler has been enabled!");
    }

    private void loadConfigs() {
        reloadConfig();
        config = getConfig();
        File guiFile = new File(getDataFolder(), "gui.yml");
        guiConfig = YamlConfiguration.loadConfiguration(guiFile);
        guiTitle = ChatColor.translateAlternateColorCodes('&', guiConfig.getString("title", "&5Netherite Item Manager"));
    }

    private String getMessage(String path, String... placeholders) {
        String message = config.getString("messages." + path, "");
        if (message.isEmpty()) return "";

        message = ChatColor.translateAlternateColorCodes('&', message);

        if (placeholders != null && placeholders.length >= 2) {
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    message = message.replace(placeholders[i], placeholders[i + 1]);
                }
            }
        }

        return message;
    }

    private void initializeMaps() {
        netheriteItems.put("sword", Material.NETHERITE_SWORD);
        netheriteItems.put("axe", Material.NETHERITE_AXE);
        netheriteItems.put("pickaxe", Material.NETHERITE_PICKAXE);
        netheriteItems.put("shovel", Material.NETHERITE_SHOVEL);
        netheriteItems.put("hoe", Material.NETHERITE_HOE);
        netheriteItems.put("helmet", Material.NETHERITE_HELMET);
        netheriteItems.put("chestplate", Material.NETHERITE_CHESTPLATE);
        netheriteItems.put("leggings", Material.NETHERITE_LEGGINGS);
        netheriteItems.put("boots", Material.NETHERITE_BOOTS);

        for (Map.Entry<String, Material> entry : netheriteItems.entrySet()) {
            permissionMap.put(entry.getValue(), "netheritedisabler.craft." + entry.getKey().toLowerCase());
        }
    }

    private List<String> translateLore(List<String> lore) {
        return lore.stream()
                .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                .collect(Collectors.toList());
    }

    private void openGUI(Player player) {
        int size = Math.min(54, Math.max(9, guiConfig.getInt("size", 27)));
        size = (size / 9) * 9; // Ensure it's a multiple of 9
        Inventory gui = getServer().createInventory(null, size, guiTitle);

        // Add netherite items
        for (Map.Entry<String, Material> entry : netheriteItems.entrySet()) {
            String itemKey = entry.getKey();
            Material material = entry.getValue();
            boolean isDisabled = config.getBoolean("disabled-items." + material.name(), true);

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String name = guiConfig.getString("items." + itemKey + ".name", "&6Netherite " + itemKey);
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

                List<String> lore;
                if (isDisabled) {
                    lore = guiConfig.getStringList("items." + itemKey + ".disabled-lore");
                } else {
                    lore = guiConfig.getStringList("items." + itemKey + ".enabled-lore");
                }
                meta.setLore(translateLore(lore));

                // Hide attributes if configured
                if (guiConfig.getBoolean("hide-attributes", true)) {
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                    meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
                    meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
                    meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
                }

                item.setItemMeta(meta);
            }

            int slot = guiConfig.getInt("items." + itemKey + ".slot", 0);
            if (slot >= 0 && slot < size) {
                gui.setItem(slot, item);
            }
        }

        // Add barrier to close
        String closeButtonMaterial = guiConfig.getString("close-button.material", "BARRIER");
        int closeButtonSlot = guiConfig.getInt("close-button.slot", size - 1);
        String closeButtonName = guiConfig.getString("close-button.name", "&cClose");

        ItemStack barrier = new ItemStack(Material.valueOf(closeButtonMaterial));
        ItemMeta barrierMeta = barrier.getItemMeta();
        if (barrierMeta != null) {
            barrierMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', closeButtonName));
            barrier.setItemMeta(barrierMeta);
        }
        if (closeButtonSlot >= 0 && closeButtonSlot < size) {
            gui.setItem(closeButtonSlot, barrier);
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(guiTitle)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!player.hasPermission("netheritedisabler.admin")) {
            player.sendMessage(getMessage("no-permission"));
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        if (clicked.getType().name().equals(guiConfig.getString("close-button.material", "BARRIER"))) {
            player.closeInventory();
            return;
        }

        // Handle netherite item clicks
        Material clickedType = clicked.getType();
        if (netheriteItems.containsValue(clickedType)) {
            boolean currentState = config.getBoolean("disabled-items." + clickedType.name(), true);
            config.set("disabled-items." + clickedType.name(), !currentState);
            saveConfig();

            // Update the item in the GUI
            String itemKey = "";
            for (Map.Entry<String, Material> entry : netheriteItems.entrySet()) {
                if (entry.getValue() == clickedType) {
                    itemKey = entry.getKey();
                    break;
                }
            }

            ItemMeta meta = clicked.getItemMeta();
            if (meta != null) {
                List<String> lore;
                if (!currentState) {
                    lore = guiConfig.getStringList("items." + itemKey + ".disabled-lore");
                } else {
                    lore = guiConfig.getStringList("items." + itemKey + ".enabled-lore");
                }
                meta.setLore(translateLore(lore));

                // Maintain attribute visibility setting
                if (guiConfig.getBoolean("hide-attributes", true)) {
                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                    meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
                    meta.addItemFlags(ItemFlag.HIDE_DESTROYS);
                    meta.addItemFlags(ItemFlag.HIDE_PLACED_ON);
                    meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
                }

                clicked.setItemMeta(meta);
            }

            String itemName = itemKey.substring(0, 1).toUpperCase() + itemKey.substring(1).toLowerCase();
            player.sendMessage(getMessage(!currentState ? "item-disabled" : "item-enabled", "%item%", itemName));
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        SmithingInventory inventory = (SmithingInventory) event.getInventory();
        ItemStack result = event.getResult();

        if (result == null) return;

        Material resultType = result.getType();

        if (permissionMap.containsKey(resultType)) {
            if (config.getBoolean("disabled-items." + resultType.name(), true)) {
                if (event.getView().getPlayer().hasPermission(permissionMap.get(resultType))) {
                    return;
                }
                event.setResult(null);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("netheritedisabler")) {
            return false;
        }

        if (!sender.hasPermission("netheritedisabler.admin")) {
            sender.sendMessage(getMessage("no-permission"));
            return true;
        }

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("gui")) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(getMessage("console-command"));
                    return true;
                }
                openGUI((Player) sender);
                return true;
            } else if (args[0].equalsIgnoreCase("reload")) {
                loadConfigs();
                sender.sendMessage(getMessage("reload-success"));
                return true;
            }
        }

        if (args.length != 2) {
            sender.sendMessage(getMessage("usage"));
            return true;
        }

        String action = args[0].toLowerCase();
        String item = args[1].toLowerCase();

        if (!netheriteItems.containsKey(item)) {
            sender.sendMessage(getMessage("invalid-item"));
            return true;
        }

        Material material = netheriteItems.get(item);
        boolean shouldDisable = action.equals("disable");

        config.set("disabled-items." + material.name(), shouldDisable);
        saveConfig();

        String displayItem = item.substring(0, 1).toUpperCase() + item.substring(1);
        sender.sendMessage(getMessage(shouldDisable ? "item-disabled" : "item-enabled", "%item%", displayItem));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("netheritedisabler")) {
            return null;
        }

        if (!sender.hasPermission("netheritedisabler.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return Arrays.asList("disable", "enable", "gui", "reload").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && !args[0].equalsIgnoreCase("gui") && !args[0].equalsIgnoreCase("reload")) {
            return new ArrayList<>(netheriteItems.keySet()).stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    @Override
    public void onDisable() {
        saveConfig();
        getLogger().info("NetheriteDisabler has been disabled!");
    }
}