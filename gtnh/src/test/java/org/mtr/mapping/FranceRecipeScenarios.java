package org.mtr.mapping;

import fr.mtrfranceaddon.gtnh.*;
import fr.mtrfranceaddon.mod.common.registry.*;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;

/** Exercises the original logo recipe through Forge's actual crafting manager. */
public final class FranceRecipeScenarios {
    public static void main(String[] args) throws Throwable {
        BlockPlacementIntegrationTest.setupForgeLoader();
        try {
            Object scenario = BlockPlacementIntegrationTest.loader.loadClass(FranceRecipeScenarios.class.getName()).getConstructor().newInstance();
            BlockPlacementIntegrationTest.invoke(scenario, "recipe");
        } finally { BlockPlacementIntegrationTest.closeForgeLoader(); }
    }

    public void recipe() {
        ModBlocks.register(); ModBlockEntities.register(); MTRFranceAddonRegistry.REGISTRY.init();
        FranceMod.proxy = new CommonProxy(); new FranceMod().init(null);
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return true; }
        }, 3, 3);
        int[] wool = {7, 0, 7, 11, -1, 14, 7, 0, 7};
        for (int i=0;i<9;i++) grid.setInventorySlotContents(i, wool[i]<0 ? new ItemStack(Items.bread) : new ItemStack(Blocks.wool,1,wool[i]));
        BlockPlacementScenarios.MemoryWorld world = new BlockPlacementScenarios.MemoryWorld();
        ItemStack result = CraftingManager.getInstance().findMatchingRecipe(grid,world);
        if (result == null || result.getItem() != net.minecraft.item.Item.getItemFromBlock(ModBlocks.MTRFRANCEADDON_LOGO.get().data) || result.stackSize != 1)
            throw new AssertionError("Original France logo recipe must be craftable through the native manager");
        grid.setInventorySlotContents(3,new ItemStack(Blocks.wool,1,14));
        if (CraftingManager.getInstance().findMatchingRecipe(grid,world) != null)
            throw new AssertionError("Swapping the French flag's blue wool for red must not match");
        System.out.println("PASS original France logo recipe and wool metadata through native crafting manager");
    }
}
