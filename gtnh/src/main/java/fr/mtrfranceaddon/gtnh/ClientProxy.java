package fr.mtrfranceaddon.gtnh;

@cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
public final class ClientProxy extends CommonProxy {
    @Override public void init() { fr.mtrfranceaddon.mod.common.Init.initClient(); }
}
