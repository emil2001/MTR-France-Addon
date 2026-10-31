package org.mtr.gtnh.probe;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.*;
import net.minecraft.client.Minecraft;
import net.minecraft.block.Block;
import net.minecraft.world.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.mtr.mod.client.CustomResourceLoader;

@SideOnly(Side.CLIENT)
public final class FranceNativeProbeClient extends FranceNativeProbe.Common {
    private volatile int stage;private int ticks;private long start,frames;private String save;private Path fixture;private volatile boolean scene;
    public void init(){
        try{fixture=(Path)Class.forName("org.mtr.yte.test.hidden.HiddenWindowGuard").getMethod("assertOwnedFixture").invoke(null);
            FranceNativeProbe.check(fixture.equals(Paths.get(System.getProperty("france.probe.fixture")).toRealPath()),"Exact France fixture required");
            FranceNativeProbe.check(fixture.equals(Paths.get(System.getProperty("user.dir")).toRealPath()),"Fixture cwd");
            FranceNativeProbe.check(Minecraft.getMinecraft().mcDataDir.getCanonicalFile().toPath().equals(fixture),"Minecraft fixture");
            FranceNativeProbe.report.addProperty("fixture",fixture.toString());FranceNativeProbe.report.addProperty("run_id",System.getProperty("yte.hidden.runId"));
            start=System.currentTimeMillis();FMLCommonHandler.instance().bus().register(this);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    @SubscribeEvent public void client(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==9)return;Minecraft mc=Minecraft.getMinecraft();
        try{
            FranceNativeProbe.check(System.currentTimeMillis()-start<180000,"Client deadline stage "+stage);
            if(stage==0){
                if(!Loader.instance().hasReachedState(LoaderState.AVAILABLE)||mc.theWorld!=null||mc.currentScreen==null)return;
                String screen=mc.currentScreen.getClass().getName();if(!screen.equals("net.minecraft.client.gui.GuiMainMenu")&&!screen.equals("lumien.custommainmenu.gui.GuiCustom"))return;
                if(++ticks<40)return;
                verifyResources();String run=System.getProperty("yte.hidden.runId");FranceNativeProbe.check(UUID.fromString(run).toString().equals(run),"run UUID");save="France-native-probe-"+run;
                Path saves=fixture.resolve("saves");Files.createDirectories(saves);FranceNativeProbe.check(!Files.isSymbolicLink(saves)&&saves.toRealPath().getParent().equals(fixture),"Owned saves");
                Files.createDirectory(saves.resolve(save));Files.write(saves.resolve(save).resolve("france-probe-owner"),run.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
                FranceNativeProbe.report.addProperty("new_save",save);mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.limitFramerate=30;mc.gameSettings.renderDistanceChunks=4;mc.gameSettings.chatVisibility=net.minecraft.entity.player.EntityPlayer.EnumChatVisibility.HIDDEN;mc.ingameGUI.getChatGUI().clearChatMessages();
                stage=1;ticks=0;mc.launchIntegratedServer(save,"France native test",new WorldSettings(1234,WorldSettings.GameType.CREATIVE,false,false,WorldType.FLAT).enableCommands().func_82750_a("2;7,2x3,2;1;"));return;
            }
            if(mc.thePlayer==null||mc.theWorld==null)return;
            if(mc.currentScreen!=null)mc.displayGuiScreen(null);
            if(stage==1&&scene&&Math.abs(mc.thePlayer.posX-6)<.5&&Math.abs(mc.thePlayer.posZ-12)<.5){mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=180;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=10;stage=2;ticks=0;}
            if(stage==2&&++ticks>100){mc.refreshResources();verifyResources();FranceNativeProbe.report.addProperty("resource_reload",true);stage=3;ticks=0;}
            if(stage==4){FranceNativeProbe.report.addProperty("vehicle_live_travel_boarding_tested",false);FranceNativeProbe.report.addProperty("vehicle_renderer_tested",false);FranceNativeProbe.write(true,null);stage=9;mc.theWorld.sendQuittingDisconnectingPacket();mc.loadWorld(null);mc.shutdown();}
        }catch(Throwable t){FranceNativeProbe.write(false,t);stage=9;mc.shutdown();}
    }
    @SubscribeEvent public void server(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage!=1||scene)return;
        try{
            net.minecraft.server.MinecraftServer server=net.minecraft.server.MinecraftServer.getServer();FranceNativeProbe.check(server.isSinglePlayer()&&server.getFolderName().equals(save),"Owned integrated server");WorldServer world=server.worldServerForDimension(0);if(world.playerEntities.isEmpty())return;
            String[] names={"sncf_logo_actuel_block","ratp_logo_actuel_block","ratp_platform","sncf_platform","twentysignblock","hundredtwentysignblock"};
            for(int i=0;i<names.length;i++){Block block=(Block)Block.blockRegistry.getObject("mtrfranceaddon:"+names[i]);FranceNativeProbe.check(block!=null,"Scene block "+names[i]);int x=i*2;
                world.setBlock(x,4,6,net.minecraft.init.Blocks.stone);FranceNativeProbe.check(world.setBlock(x,5,6,block,0,3),"Place "+names[i]);
                FranceNativeProbe.check(world.getBlock(x,5,6)==block,"Read placed "+names[i]);
                if(!world.playerEntities.isEmpty())block.onBlockActivated(world,x,5,6,(net.minecraft.entity.player.EntityPlayer)world.playerEntities.get(0),1,.5F,.5F,.5F);
                world.setBlock(x,5,8,block,0,3);FranceNativeProbe.check(world.func_147480_a(x,5,8,true),"Break "+names[i]);FranceNativeProbe.check(world.isAirBlock(x,5,8),"Break removed "+names[i]);
            }
            net.minecraft.entity.player.EntityPlayerMP player=(net.minecraft.entity.player.EntityPlayerMP)world.playerEntities.get(0);player.rotationYaw=player.prevRotationYaw=180;player.rotationPitch=player.prevRotationPitch=10;player.setPositionAndUpdate(6,6,12);
            FranceNativeProbe.report.addProperty("scene_blocks_placed_and_broken",names.length);FranceNativeProbe.report.addProperty("scene_block_activation_calls",world.playerEntities.isEmpty()?0:names.length);scene=true;
        }catch(Throwable t){FranceNativeProbe.write(false,t);stage=9;serverStop();}
    }
    private void serverStop(){net.minecraft.server.MinecraftServer.getServer().initiateShutdown();}
    @SubscribeEvent public void render(TickEvent.RenderTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;frames++;if(stage!=3||++ticks<60)return;
        try{Minecraft mc=Minecraft.getMinecraft();String name="france-native-scene.png";net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir,name,mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            Class.forName("org.mtr.yte.test.hidden.HiddenWindowGuard").getMethod("recordFrame",long.class,String.class).invoke(null,frames,"screenshots/"+name);
            FranceNativeProbe.report.addProperty("framebuffer_screenshot","screenshots/"+name);FranceNativeProbe.report.addProperty("render_frames",frames);stage=4;
        }catch(Throwable t){FranceNativeProbe.write(false,t);stage=9;Minecraft.getMinecraft().shutdown();}
    }
    private void verifyBlockModels()throws Exception{
        Minecraft mc=Minecraft.getMinecraft();
        java.lang.reflect.Field active=Class.forName("org.mtr.mapping.registry.LegacyBlockRenderers").getDeclaredField("active");active.setAccessible(true);Object context=active.get(null);
        FranceNativeProbe.check(context!=null,"Actual stitched block renderer context required");
        java.lang.reflect.Field modelField=context.getClass().getDeclaredField("models");modelField.setAccessible(true);
        org.mtr.mapping.tool.LegacyBlockModels models=(org.mtr.mapping.tool.LegacyBlockModels)modelField.get(context);
        java.lang.reflect.Method faces=context.getClass().getDeclaredMethod("faces",org.mtr.mapping.holder.BlockState.class);faces.setAccessible(true);
        java.lang.reflect.Method icon=context.getClass().getDeclaredMethod("icon",org.mtr.mapping.holder.Identifier.class);icon.setAccessible(true);
        java.lang.reflect.Field logicalWidth=org.mtr.mapping.tool.LegacyModelSprite.class.getDeclaredField("logicalWidth");logicalWidth.setAccessible(true);
        java.util.Set<String> textures=new java.util.HashSet<>();int registered=0,covered=0,skipped=0,quads=0;
        for(Object key:Block.blockRegistry.getKeys()){
            String name=key.toString();if(!name.startsWith("mtrfranceaddon:"))continue;registered++;
            Block nativeBlock=(Block)Block.blockRegistry.getObject(name);FranceNativeProbe.check(nativeBlock instanceof org.mtr.mapping.holder.BlockAbstractMapping,"Mapped France block "+name);
            org.mtr.mapping.holder.BlockAbstractMapping block=(org.mtr.mapping.holder.BlockAbstractMapping)nativeBlock;
            org.mtr.mapping.holder.BlockState state=block.getDefaultState2();
            if(block.getRenderType2(state)!=org.mtr.mapping.holder.BlockRenderType.MODEL){FranceNativeProbe.check(name.equals("mtrfranceaddon:invisible_platform"),"Unexpected non-MODEL France block "+name);skipped++;continue;}
            org.mtr.mapping.holder.Identifier id=new org.mtr.mapping.holder.Identifier(name);
            // Require actual pack blockstate; resolving the live context also traverses its referenced models.
            try(java.io.InputStream stream=mc.getResourceManager().getResource(new net.minecraft.util.ResourceLocation("mtrfranceaddon","blockstates/"+id.getPath()+".json")).getInputStream()){FranceNativeProbe.check(stream.read()!=-1,"Blockstate read "+name);}
            boolean ticketMachine=name.equals("mtrfranceaddon:idfm_ticket_machine")||name.equals("mtrfranceaddon:idfm_ticket_machine_2");
            if(ticketMachine&&org.mtr.mod.block.IBlock.getStatePropertySafe(state,org.mtr.mod.block.IBlock.HALF)==org.mtr.mod.block.IBlock.DoubleBlockHalf.UPPER){
                FranceNativeProbe.check(models.resolve(id,state.data.getSerializedProperties(),0).isEmpty(),"Ticket machine authored upper intentionally empty "+name);
                FranceNativeProbe.check(((java.util.List<?>)faces.invoke(context,state)).isEmpty(),"Ticket machine live upper intentionally empty "+name);
                state=state.with(new org.mtr.mapping.holder.Property<>(org.mtr.mod.block.IBlock.HALF.data),org.mtr.mod.block.IBlock.DoubleBlockHalf.LOWER);
            }
            java.util.List<org.mtr.mapping.tool.LegacyBlockModels.Quad> parsed=models.resolve(id,state.data.getSerializedProperties(),0);
            FranceNativeProbe.check(!parsed.isEmpty(),"Placement authored quads "+name);
            java.util.List<?> live=(java.util.List<?>)faces.invoke(context,state);FranceNativeProbe.check(!live.isEmpty(),"Live authored faces "+name);
            for(Object value:live){org.mtr.mapping.tool.LegacyBlockModels.Quad face=(org.mtr.mapping.tool.LegacyBlockModels.Quad)value;FranceNativeProbe.check(!face.texture.toString().contains("missingno"),"Live fallback quad "+name);quads++;}
            for(org.mtr.mapping.holder.Identifier texture:models.textures(id)){
                FranceNativeProbe.check(!texture.toString().contains("missingno"),"Missing texture reference "+name);
                net.minecraft.util.IIcon stitched=(net.minecraft.util.IIcon)icon.invoke(context,texture);
                FranceNativeProbe.check(stitched instanceof org.mtr.mapping.tool.LegacyModelSprite&&logicalWidth.getInt(stitched)>0,"Actual atlas sprite decoded "+texture+" for "+name);
                FranceNativeProbe.check(!stitched.getIconName().contains("missingno")&&stitched.getMaxU()>stitched.getMinU()&&stitched.getMaxV()>stitched.getMinV(),"Actual atlas UV "+texture);
                if(textures.add(texture.toString())){
                    net.minecraft.util.ResourceLocation source=new net.minecraft.util.ResourceLocation(texture.getNamespace(),"textures/"+texture.getPath()+".png");
                    net.minecraft.client.resources.IResource resource;
                    try{resource=mc.getResourceManager().getResource(source);}catch(java.io.FileNotFoundException missing){
                        if(!source.getResourceDomain().equals("minecraft")||!(source.getResourcePath().startsWith("textures/block/")||source.getResourcePath().startsWith("textures/item/")))throw missing;
                        resource=mc.getResourceManager().getResource(new net.minecraft.util.ResourceLocation("mtr_compat",source.getResourcePath()));
                    }
                    try(java.io.InputStream stream=resource.getInputStream()){FranceNativeProbe.check(stream.read()==137&&stream.read()==80&&stream.read()==78&&stream.read()==71,"Actual PNG resource "+texture);}
                }
            }
            covered++;
        }
        FranceNativeProbe.check(registered==98&&covered==97&&skipped==1,"All native France block models covered: "+registered+"/"+covered+"/"+skipped);
        FranceNativeProbe.report.addProperty("registered_block_models_checked",covered);FranceNativeProbe.report.addProperty("intentional_invisible_platform_skipped",skipped);FranceNativeProbe.report.addProperty("live_default_block_quads",quads);FranceNativeProbe.report.addProperty("live_decoded_block_atlas_sprites",textures.size());
    }
    private void verifyRepresentativeModels(){
        String[] selected={"tgv_duplex_car1_p6_closed_carmillon","tgv_pse_orange_car1-forwards-panto5","mtr_custom_train_mf67_ratp_noir_motrice_cab_1","mtr_custom_train_mp59_m_nr_cab_1","s43tec_lrt2010_t4_idfm_cab_f","urbosa","mtr_custom_train_cafforward_cab_1","telecab_les_gets_lht"};
        java.util.Map<String,org.mtr.mod.resource.VehicleResource> resources=new java.util.HashMap<>();
        for(org.mtr.core.data.TransportMode mode:org.mtr.core.data.TransportMode.values())CustomResourceLoader.iterateVehicles(mode,r->resources.put(r.getId(),r));
        org.mtr.libraries.com.google.gson.JsonArray rows=new org.mtr.libraries.com.google.gson.JsonArray();
        for(String id:selected){
            FranceNativeProbe.check(java.util.Arrays.asList(FranceProbeManifest.VEHICLES).contains(id),"Representative must be frozen France ID: "+id);
            org.mtr.mod.resource.VehicleResource resource=resources.get(id);FranceNativeProbe.check(resource!=null,"Representative resource "+id);
            org.mtr.mod.resource.VehicleResourceCache cache=resource.getCachedVehicleResource(0,1,true);
            FranceNativeProbe.check(cache!=null,"Representative native model cache "+id);
            int groups=cache.optimizedModels.size()+cache.optimizedModelsDoorsClosed.size()+cache.optimizedModelsBogie1.size()+cache.optimizedModelsBogie2.size();
            FranceNativeProbe.check(groups>0,"Representative authored geometry groups "+id);
            final int[] models={0},parts={0};resource.iterateModels(0,1,(index,model)->{models[0]++;model.modelProperties.iterateParts(part->parts[0]++);});
            FranceNativeProbe.check(models[0]>0&&parts[0]>0,"Representative parsed models/parts "+id);
            org.mtr.libraries.com.google.gson.JsonObject row=new org.mtr.libraries.com.google.gson.JsonObject();row.addProperty("id",id);row.addProperty("model_groups",groups);row.addProperty("parsed_models",models[0]);row.addProperty("model_parts",parts[0]);row.addProperty("floors",cache.floors.size());row.addProperty("doorways",cache.doorways.size());rows.add(row);
        }
        // This method is called on the native client/render thread once before and once after reload.
        FranceNativeProbe.report.add("representative_native_model_cache",rows);
        FranceNativeProbe.report.addProperty("representative_model_cache_passes",FranceNativeProbe.report.has("representative_model_cache_passes")?FranceNativeProbe.report.get("representative_model_cache_passes").getAsInt()+1:1);
    }
    private static void declaredIds(org.mtr.libraries.com.google.gson.JsonObject json,String key,java.util.Set<String> result){
        if(json.has(key))for(org.mtr.libraries.com.google.gson.JsonElement value:json.getAsJsonArray(key))result.add(value.getAsJsonObject().get("id").getAsString());
    }
    private void verifyResources()throws Exception{
        java.util.Set<String> vehicles=new HashSet<>();for(org.mtr.core.data.TransportMode mode:org.mtr.core.data.TransportMode.values())CustomResourceLoader.iterateVehicles(mode,r->vehicles.add(r.getId()));
        java.util.Set<String> signs=new HashSet<>(CustomResourceLoader.getSortedSignIds());java.util.Set<String> rails=new HashSet<>();CustomResourceLoader.getRails().forEach(r->rails.add(r.getId()));
        FranceProbeManifest.verify(vehicles,signs,rails);
        verifyRepresentativeModels();
        verifyBlockModels();
        // Inspect every active pack declaration at this path, not a source file or only the top override.
        java.util.Set<String> declaredVehicles=new HashSet<>(),declaredSigns=new HashSet<>(),declaredRails=new HashSet<>();
        int packs=0;
        for(Object resourceObject:Minecraft.getMinecraft().getResourceManager().getAllResources(new net.minecraft.util.ResourceLocation("mtr","mtr_custom_resources.json"))){
            net.minecraft.client.resources.IResource resource=(net.minecraft.client.resources.IResource)resourceObject;
            try(java.io.InputStream stream=resource.getInputStream();java.io.Reader reader=new java.io.InputStreamReader(stream,StandardCharsets.UTF_8)){
                org.mtr.libraries.com.google.gson.JsonObject json=new org.mtr.libraries.com.google.gson.JsonParser().parse(reader).getAsJsonObject();
                declaredIds(json,"vehicles",declaredVehicles);declaredIds(json,"signs",declaredSigns);declaredIds(json,"rails",declaredRails);packs++;
            }
        }
        FranceProbeManifest.verify(declaredVehicles,declaredSigns,declaredRails);FranceNativeProbe.report.addProperty("active_resource_declaration_packs_read",packs);
        FranceNativeProbe.report.addProperty("france_vehicle_ids_present",488);FranceNativeProbe.report.addProperty("france_sign_ids_present",450);FranceNativeProbe.report.addProperty("france_rail_ids_present",3);
    }
}
