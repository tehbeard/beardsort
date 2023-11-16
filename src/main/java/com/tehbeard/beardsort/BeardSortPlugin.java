package com.tehbeard.beardsort;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import com.google.common.collect.Queues;
import java.util.Queue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 *
 * @author Tehbeard
 */
public class BeardSortPlugin extends JavaPlugin implements Listener {

    public void onEnable() {

        Bukkit.getPluginManager().registerEvents(this, this);
    }

    private static final Component ERR_MSG = Component.text("[Sort] must be a wall sign, placed on chiseled deepslate, with a hopper directly above the deepslate.").color(NamedTextColor.RED);

    private final NamespacedKey hopperSortkey = NamespacedKey.fromString("hoppersort", this);
    private final NamespacedKey hopperSortFuzzykey = NamespacedKey.fromString("hoppersort_fuzzy", this);

    private Vector hopperToSign(Directional dir) {
        return new Vector(0, -1, 0).add(dir.getFacing().getDirection());
    }

    private Vector signToHopper(Directional dir) {
        return new Vector(0, 1, 0).subtract(dir.getFacing().getDirection());
    }

    @EventHandler(ignoreCancelled = true)
    public void onSignSetup(SignChangeEvent event) {

        Sign sign = (Sign) event.getBlock().getState();

        boolean isAlreadySortSign = ((TextComponent) sign.getSide(Side.FRONT).line(1)).content().equals("[Sort]");
        boolean isGoingToBeSortSign = ((TextComponent) event.line(1)).content().equals("[Sort]");

        if (!isAlreadySortSign && isGoingToBeSortSign) {
            // Check it's a wall sign
            if (!Tag.WALL_SIGNS.isTagged(event.getBlock().getBlockData().getMaterial())) {
                event.getPlayer().sendMessage(ERR_MSG);
                return;
            }

            // Check we are placed on Chiseled Deepslate
            Directional dir = (Directional) event.getBlock().getBlockData();
            Block chiseledDeepslateMarker = event.getBlock().getLocation().add(
                    dir.getFacing().getOppositeFace().getDirection()
            ).getBlock();

            BlockData attached = chiseledDeepslateMarker.getBlockData();
            if (!attached.getMaterial().equals(Material.CHISELED_DEEPSLATE)) {
                event.getPlayer().sendMessage(ERR_MSG);
                return;
            }

            // Get hopper
            Block hopper = chiseledDeepslateMarker.getLocation().add(new Vector(0, 1, 0)).getBlock();
            if (!hopper.getBlockData().getMaterial().equals(Material.HOPPER)) {
                event.getPlayer().sendMessage(ERR_MSG);
                return;
            }
            Hopper hopperState = (Hopper) hopper.getState();

            boolean isFuzzy = ((TextComponent) event.line(2)).content().equals("fuzzy");

            Component msg = Component.text("Creating");

            if (isFuzzy) {
                msg = msg.append(Component.text(" fuzzy").color(NamedTextColor.AQUA));
            }

            msg = msg.append(Component.text(" [Sort]"));

            event.getPlayer().sendMessage(msg);

            event.getPlayer().sendMessage(Component.text("Place an inventory directly beneath the deepslate to store filter items."));
            event.getPlayer().sendMessage(Component.text("Place an inventory directly beneath that for filtered items to be stored in."));

            // Configure hopper state.
            hopperState.getPersistentDataContainer().set(
                    this.hopperSortkey,
                    VectorDataType.CODEC,
                    hopperToSign(dir)
            );

            hopperState.getPersistentDataContainer().set(
                    this.hopperSortFuzzykey,
                    PersistentDataType.BOOLEAN,
                    isFuzzy
            );

            hopperState.update();
        } else if (isAlreadySortSign && isGoingToBeSortSign) {

            boolean isFuzzy = ((TextComponent) event.line(2)).content().equals("fuzzy");
            Directional dir = (Directional) event.getBlock().getBlockData();
            Vector v = signToHopper(dir);

            Block hopper = event.getBlock().getLocation().add(v).getBlock();

            Hopper hopperState = (Hopper) hopper.getState();
            
            hopperState.getPersistentDataContainer().set(
                    this.hopperSortkey,
                    VectorDataType.CODEC,
                    hopperToSign(dir)
            );

            hopperState.getPersistentDataContainer().set(
                    this.hopperSortFuzzykey,
                    PersistentDataType.BOOLEAN,
                    isFuzzy
            );
            
            hopperState.update();
            
            event.getPlayer().sendMessage(Component.text("Updated [Sort] to " + (isFuzzy ? "fuzzy" : "normal")));

        } else if (isAlreadySortSign && !isGoingToBeSortSign) {
            // Undo the sort hopper

            if (!Tag.WALL_SIGNS.isTagged(event.getBlock().getBlockData().getMaterial())) {
                return;
            }

            // Check we are placed on Chiseled Deepslate
            Directional dir = (Directional) event.getBlock().getBlockData();
            Block chiseledDeepslateMarker = event.getBlock().getLocation().add(
                    dir.getFacing().getOppositeFace().getDirection()
            ).getBlock();

            BlockData attached = chiseledDeepslateMarker.getBlockData();
            if (!attached.getMaterial().equals(Material.CHISELED_DEEPSLATE)) {
                return;
            }

            Block hopper = chiseledDeepslateMarker.getLocation().add(new Vector(0, 1, 0)).getBlock();
            if (!hopper.getBlockData().getMaterial().equals(Material.HOPPER)) {
                return;
            }
            Hopper hopperState = (Hopper) hopper.getState();

            hopperState.getPersistentDataContainer().remove(hopperSortkey);
            hopperState.getPersistentDataContainer().remove(hopperSortFuzzykey);
            hopperState.update();

            event.getPlayer().sendMessage(Component.text("Removed [Sort]"));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSignBreak(BlockPhysicsEvent event) {
        if (Tag.WALL_SIGNS.isTagged(event.getChangedType())) {
            Block b = event.getBlock();
            Sign s = (Sign) b.getState();

            Directional dir = (Directional) event.getBlock().getBlockData();

            Block attachedBlock = b.getRelative(dir.getFacing().getOppositeFace());
            if (attachedBlock.getType() == Material.AIR) {  // or maybe any non-solid material, but AIR is the normal case
                // sign has been popped off!
                if (((TextComponent) s.getSide(Side.FRONT).line(1)).content().equals("[Sort]")) {
                    Block hopper = attachedBlock.getRelative(0, 1, 0);
                    if (hopper.getBlockData().getMaterial().equals(Material.HOPPER)) {
                        Hopper hopperState = (Hopper) hopper.getState();
                        hopperState.getPersistentDataContainer().remove(hopperSortkey);
                        hopperState.getPersistentDataContainer().remove(hopperSortFuzzykey);
                        hopperState.update();
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!Tag.WALL_SIGNS.isTagged(event.getBlock().getBlockData().getMaterial())) {
            return;
        }
        Directional dir = (Directional) event.getBlock().getBlockData();

        Block hopper = event.getBlock().getLocation().add(signToHopper(dir)).getBlock();

        if (hopper.getBlockData().getMaterial().equals(Material.HOPPER)) {
            Hopper hopperState = (Hopper) hopper.getState();

            hopperState.getPersistentDataContainer().remove(hopperSortkey);
            hopperState.getPersistentDataContainer().remove(hopperSortFuzzykey);
            hopperState.update();
            event.getPlayer().sendMessage(Component.text("Removed [Sort]"));
        }
    }

    class ItemTracker {

        public final Inventory inv;
        public final ItemStack is;

        public ItemTracker(Inventory inv, ItemStack is) {
            this.inv = inv;
            this.is = is;
        }

    }

    static class VectorDataType implements PersistentDataType<PersistentDataContainer, Vector> {

        public static final VectorDataType CODEC = new VectorDataType();

        @Override
        public Class<PersistentDataContainer> getPrimitiveType() {
            return PersistentDataContainer.class;
        }

        @Override
        public Class<Vector> getComplexType() {
            return Vector.class;
        }

        @Override
        public PersistentDataContainer toPrimitive(Vector z, PersistentDataAdapterContext pdac) {
            PersistentDataContainer con = pdac.newPersistentDataContainer();

            con.set(NamespacedKey.fromString("x"), INTEGER, z.getBlockX());
            con.set(NamespacedKey.fromString("y"), INTEGER, z.getBlockY());
            con.set(NamespacedKey.fromString("z"), INTEGER, z.getBlockZ());

            return con;
        }

        @Override
        public Vector fromPrimitive(PersistentDataContainer con, PersistentDataAdapterContext pdac) {

            return new Vector(
                    con.get(NamespacedKey.fromString("x"), INTEGER),
                    con.get(NamespacedKey.fromString("y"), INTEGER),
                    con.get(NamespacedKey.fromString("z"), INTEGER)
            );

        }

    }

    private final Queue<ItemTracker> toRemove = Queues.newArrayDeque();

    @EventHandler
    public void onHopperMove(InventoryMoveItemEvent event) {
        InventoryHolder init = event.getSource().getHolder();
        if (init instanceof Hopper hopper) {

            Boolean isSorter = hopper.getPersistentDataContainer().has(this.hopperSortkey);

            Boolean isFuzzySorter = hopper.getPersistentDataContainer().getOrDefault(this.hopperSortFuzzykey, PersistentDataType.BOOLEAN, false);
            if (isSorter) {
                
//                getLogger().info("Sorting for " + (isFuzzySorter ? "Fuzzy" : "Normal") + " at " + ((Hopper) init).getLocation());

                Vector v = hopper.getPersistentDataContainer().getOrDefault(this.hopperSortkey, VectorDataType.CODEC, null);
                if (v == null) {
                    Bukkit.broadcast(Component.text("vector is null"));
                }

                Block comparison = hopper.getLocation().add(new Vector(0, -2, 0)).getBlock();
                BlockState compState = comparison.getState();

                Block output = hopper.getLocation().add(new Vector(0, -3, 0)).getBlock();
                BlockState outputState = output.getState();

                if (compState instanceof InventoryHolder && outputState instanceof InventoryHolder) {
                    Inventory compInv = ((InventoryHolder) compState).getInventory();
                    Inventory outInv = ((InventoryHolder) outputState).getInventory();

                    for (ItemStack item : compInv.getContents()) {
                        if (item == null) {
                            continue;
                        }
                        if ((isFuzzySorter && event.getItem().getType().equals(item.getType()))
                                || (!isFuzzySorter && event.getItem().isSimilar(item))) {
                            int didFail = outInv.addItem(event.getItem()).size();
                            if (didFail == 0) {
                                event.setCancelled(true);
                                ItemStack marker = event.getItem().clone();
                                toRemove.add(new ItemTracker(event.getSource(), marker));
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onTickEnd(ServerTickEndEvent event) {
        while (!toRemove.isEmpty()) {
            ItemTracker track = toRemove.poll();
            track.inv.removeItem(track.is);
        }
    }
}
