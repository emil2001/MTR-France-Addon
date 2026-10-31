package fr.mtrfranceaddon.gtnh;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import fr.mtrfranceaddon.mod.common.Init;
import fr.mtrfranceaddon.mod.common.registry.ModBlocks;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

@Mod(modid = "mtrfranceaddon", name = "MTR France Addon", version = "1.2.0-gtnh.0",
    acceptedMinecraftVersions = "[1.7.10]", dependencies = "required-after:mtr@[4.0.5-gtnh.17,)")
public final class FranceMod {
    @SidedProxy(clientSide = "fr.mtrfranceaddon.gtnh.ClientProxy", serverSide = "fr.mtrfranceaddon.gtnh.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) { Init.init(); }
    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        // The only upstream recipe; modern wool IDs use metadata in 1.7.10.
        GameRegistry.addRecipe(new ItemStack(ModBlocks.MTRFRANCEADDON_LOGO.get().data),
            "GWG", "B*R", "GWG", 'G', new ItemStack(Blocks.wool, 1, 7),
            'W', new ItemStack(Blocks.wool, 1, 0), 'B', new ItemStack(Blocks.wool, 1, 11),
            'R', new ItemStack(Blocks.wool, 1, 14), '*', Items.bread);
        proxy.init();
    }
}
