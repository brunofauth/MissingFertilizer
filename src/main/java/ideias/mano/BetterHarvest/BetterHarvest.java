package ideias.mano.BetterHarvest;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class BetterHarvest extends JavaPlugin implements Listener {

    private ArrayList<Material> validTools;
    private HashMap<Material, Integer> harvestDamage;
    private HashMap<Material, Integer> replantDamage;

    @Override
    public void onEnable() {
        this.getConfig().options().copyDefaults(true);
        this.saveConfig();
        this.reloadConfig();
        this.getServer().getPluginManager().registerEvents(this, this);
        this.loadTools();
        this.loadDamages();
        this.getLogger().info("Hi!");
    }

    @Override
    public void onDisable() {
        this.getLogger().info("Bye!");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        // Does the above check make this one redundant?
        if (event.getClickedBlock() == null)
            return;

        if (!(event.getClickedBlock().getBlockData() instanceof Ageable))
            return;
        Ageable cropData = (Ageable) event.getClickedBlock().getBlockData();
        if (cropData.getAge() != cropData.getMaximumAge())
            return;

        if (this.getConfig().getBoolean("require-tool")
            && (event.getItem() == null || !this.isValidTool(event.getItem().getType()))
        )
            return;

        Player player = event.getPlayer();

        Material type = event.getClickedBlock().getType();

        switch (type) {
            case WHEAT:
            case CARROTS:
            case POTATOES:
            case BEETROOTS:
            case NETHER_WART:
            case COCOA:
                if (!(player.hasPermission("BetterHarvest.harvest." + type.name())))
                    return;
                event.getClickedBlock().getWorld().playSound(
                    event.getClickedBlock().getLocation(),
                    this.getCropHarvestSound(type),
                    1.0f,
                    1.0f
                );
                if (this.getConfig().getBoolean("require-tool"))
                    this.damageItem(
                        event.getItem(),
                        this.harvestDamage.getOrDefault(
                            type,
                            this.getConfig().getInt("harvest-damage-default")
                        )
                    );
                BlockFace direction = null;
                if (event.getClickedBlock().getBlockData() instanceof Directional)
                    direction = ((Directional) event.getClickedBlock().getBlockData()).getFacing();
                event.getClickedBlock().breakNaturally();

                if (!(player.hasPermission("BetterHarvest.replant." + type.name())))
                    return;
                event.getClickedBlock().getWorld().playSound(
                    event.getClickedBlock().getLocation(),
                    this.getCropReplantSound(type),
                    1.0f,
                    1.0f
                );
                if (this.getConfig().getBoolean("require-tool"))
                    this.damageItem(
                        event.getItem(),
                        this.replantDamage.getOrDefault(
                            type,
                            this.getConfig().getInt("replant-damage-default")
                        )
                    );
                event.getClickedBlock().setType(type);
                if (event.getClickedBlock().getBlockData() instanceof Directional) {
                    Directional dirData = (Directional) event.getClickedBlock().getBlockData();
                    dirData.setFacing(direction != null? direction : BlockFace.EAST);
                    event.getClickedBlock().setBlockData((BlockData) dirData);
                }
        }
    }

    private Sound getCropHarvestSound(Material cropMaterial) {
        switch (cropMaterial) {
            case WHEAT:
            case CARROTS:
            case POTATOES:
            case BEETROOTS:
                return Sound.BLOCK_CROP_BREAK;
            case NETHER_WART:
                return Sound.BLOCK_NETHER_WART_BREAK;
            case COCOA:
                return Sound.BLOCK_WOOD_BREAK;
            default:
                // So that it's obvious if I ever forget to add new crop sounds
                return Sound.ENTITY_GENERIC_EXPLODE;
        }
    }

    private Sound getCropReplantSound(Material cropMaterial) {
        switch (cropMaterial) {
            case WHEAT:
            case CARROTS:
            case POTATOES:
            case BEETROOTS:
                return Sound.ITEM_CROP_PLANT;
            case NETHER_WART:
                return Sound.ITEM_NETHER_WART_PLANT;
            case COCOA:
                return Sound.BLOCK_WOOD_PLACE;
            default:
                // So that it's obvious if I ever forget to add new crop sounds
                return Sound.ENTITY_GENERIC_EXPLODE;
        }
    }

    private void damageItem(ItemStack item, int ammount) {
        if (item == null || !(item.getItemMeta() instanceof Damageable))
            return;
        Damageable dmgMeta = (Damageable) item.getItemMeta();
        dmgMeta.setDamage(dmgMeta.getDamage() + ammount);
        item.setItemMeta((ItemMeta) dmgMeta);
    }

    private void loadDamages() {
        try {
            loadDamage("harvestDamage", "harvest-damage");
            loadDamage("replantDamage", "replant-damage");
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    private void loadDamage(String mapName, String cfgPath) throws IllegalAccessException, NoSuchFieldException {
        // here we use reflection bc java doesnt have POINTERS
        Field mapField = this.getClass().getDeclaredField(mapName);
        mapField.set(this, new HashMap<Material, Integer>());
        // This is never null bc we supply a default value
        HashMap<String, Object> configMapping = (HashMap<String, Object>) this.getConfig()
            .getConfigurationSection(cfgPath)
            .getValues(false);
        for (Map.Entry<String, Object> entry : configMapping.entrySet()) {
            Material cropMaterial = Material.matchMaterial(entry.getKey());
            if (cropMaterial == null) {
                this.getLogger().warning(String.format(
                    "Couldn't register damage for material: %s. Skipping",
                    entry.getKey()
                ));
                continue;
            }
            try {
                ((HashMap<Material, Integer>) mapField.get(this)).put(
                    cropMaterial,
                    (int) entry.getValue()
                );
            } catch (ClassCastException e) {
                this.getLogger().warning(String.format(
                        "Harvest Damage for material %s must be a Double",
                        cropMaterial.name()
                ));
            }
        }
    }

    private void loadTools() {
        this.validTools = new ArrayList<Material>();
        for (String toolName :  this.getConfig().getStringList("valid-tools")) {
            Material toolMaterial = Material.matchMaterial(toolName);
            if (toolMaterial == null)
                this.getLogger().warning("Can't load invalid tool: " + toolName);
            this.validTools.add(toolMaterial);
        }
    }

    private boolean isValidTool(Material tool) {
        for (Material validTool : this.validTools)
            if (tool == validTool)
                return true;
        return false;
    }
}
