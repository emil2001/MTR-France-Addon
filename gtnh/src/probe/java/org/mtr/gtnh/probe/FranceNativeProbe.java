package org.mtr.gtnh.probe;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import fr.mtrfranceaddon.mod.common.registry.ModItems;
import fr.mtrfranceaddon.mod.common.data.CustomRailData;
import fr.mtrfranceaddon.mod.common.item.CustomItemRailModifier;
import org.mtr.libraries.com.google.gson.JsonObject;

@Mod(modid="france_native_probe",name="Test-only France native probe",version="0",dependencies="required-after:mtrfranceaddon",acceptableRemoteVersions="*")
public final class FranceNativeProbe {
    @SidedProxy(clientSide="org.mtr.gtnh.probe.FranceNativeProbeClient",serverSide="org.mtr.gtnh.probe.FranceNativeProbe$Common")
    public static Common proxy;
    public static final JsonObject report=new JsonObject();
    private boolean stop;
    @Mod.EventHandler public void init(FMLInitializationEvent event){
        try{Path fixture=Paths.get(System.getProperty("france.probe.fixture")).toRealPath();check(fixture.equals(Paths.get(System.getProperty("user.dir")).toRealPath()),"France probe fixture must equal canonical cwd");report.addProperty("fixture",fixture.toString());}
        catch(Exception e){write(false,e);throw new IllegalStateException("Owned fixture required",e);}
        proxy.init();FMLCommonHandler.instance().bus().register(this);
    }
    @Mod.EventHandler public void started(FMLServerStartedEvent event){
        try {commonAssertions();report.addProperty("native_registry_and_connectors",true);if(!net.minecraft.server.MinecraftServer.getServer().isSinglePlayer()){FranceTicketRegression.run();write(true,null);stop=true;}}
        catch(Throwable t){write(false,t);stop=true;}
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event){if(stop&&event.phase==TickEvent.Phase.END)net.minecraft.server.MinecraftServer.getServer().initiateShutdown();}
    public static class Common {public void init(){}}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void commonAssertions(){
        int blocks=0,items=0;
        for(Object key:net.minecraft.block.Block.blockRegistry.getKeys())if(key.toString().startsWith("mtrfranceaddon:"))blocks++;
        for(Object key:net.minecraft.item.Item.itemRegistry.getKeys())if(key.toString().startsWith("mtrfranceaddon:"))items++;
        check(blocks==98,"Native blocks: "+blocks);check(items==122,"Native items: "+items);check(ModItems.RAIL_CONNECTORS.size()==24,"Connectors");
        org.mtr.mod.data.RailType[] types=org.mtr.mod.data.RailType.values();check(types.length==29,"Genuine transformed RailType count");
        String[] original={"WOODEN","STONE","EMERALD","IRON","BRICKS","OBSIDIAN","PRISMARINE","BLAZE","QUARTZ","DIAMOND","PLATFORM","SIDING","TURN_BACK","CABLE_CAR","CABLE_CAR_STATION","RUNWAY","AIRPLANE_DUMMY"};
        for(int i=0;i<original.length;i++)check(types[i].name().equals(original[i])&&types[i].ordinal()==i,"Original RailType ordinal "+i);
        org.mtr.mapping.holder.BlockState node=new org.mtr.mod.block.BlockNode(org.mtr.core.data.TransportMode.TRAIN).getDefaultState2();
        int rails=0;
        for(CustomRailData.RailInfo info:CustomRailData.CUSTOM_RAILS){
            check(org.mtr.mod.data.RailType.valueOf(info.name).speedLimit==info.speed,"Enum speed "+info.name);
            for(boolean oneWay:new boolean[]{false,true}){
                String id=info.name.toLowerCase(Locale.ROOT)+(oneWay?"_oneway":"");
                CustomItemRailModifier tool=(CustomItemRailModifier)ModItems.RAIL_CONNECTORS.get(id).get().data;
                check(net.minecraft.item.Item.itemRegistry.getObject("mtrfranceaddon:"+id)==tool,"Registered connector identity "+id);
                org.mtr.core.data.Rail rail=tool.createRail(new UUID(0,1),org.mtr.core.data.TransportMode.TRAIN,node,node,new org.mtr.mapping.holder.BlockPos(0,64,0),new org.mtr.mapping.holder.BlockPos(0,64,32),org.mtr.core.tool.Angle.N,org.mtr.core.tool.Angle.N);
                check(rail!=null&&rail.getSpeedLimitKilometersPerHour(false)==(oneWay?0:info.speed)&&rail.getSpeedLimitKilometersPerHour(true)==info.speed,"Actual connector rail "+id);rails++;
            }
        }
        check(rails==24,"24 rails exercised");report.addProperty("blocks",blocks);report.addProperty("items",items);report.addProperty("connector_rails",rails);report.addProperty("rail_types",types.length);
    }
    static synchronized void write(boolean passed,Throwable failure){
        report.addProperty("passed",passed);if(failure!=null){failure.printStackTrace();report.addProperty("failure",failure.toString());}
        try{Path output=Paths.get(System.getProperty("france.probe.output"));Files.createDirectories(output.toAbsolutePath().getParent());Files.write(output,report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException("Cannot write probe report",e);}
    }
}
