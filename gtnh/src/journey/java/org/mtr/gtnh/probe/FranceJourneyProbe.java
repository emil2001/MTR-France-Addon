package org.mtr.gtnh.probe;

import cpw.mods.fml.common.*;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.*;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.*;
import net.minecraft.world.chunk.*;
import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.data.Position;
import org.mtr.core.operation.*;
import org.mtr.core.serializer.*;
import org.mtr.core.simulation.Simulator;
import org.mtr.core.tool.*;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.com.google.gson.*;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.BlockPos;
import org.mtr.mapping.holder.BlockState;
import org.mtr.mapping.holder.Box;
import org.mtr.mapping.mapper.MinecraftClientHelper;
import org.mtr.mapping.tool.*;
import org.mtr.mod.Init;
import org.mtr.mod.InitClient;
import org.mtr.mod.block.BlockNode;
import org.mtr.mod.client.*;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.item.ItemBlockClickingBase;
import org.mtr.mod.packet.*;
import org.mtr.mod.render.PositionAndRotation;
import org.mtr.mod.resource.VehicleResource;
import org.mtr.mod.resource.VehicleResourceCache;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Test-only French scheduled train journey; native packets, renderer, walking and rider binding. */
@SideOnly(Side.CLIENT)
@Mod(modid="france_journey_probe", name="Owned hidden France native journey", version="0", dependencies="required-after:mtrfranceaddon", acceptableRemoteVersions="*")
public final class FranceJourneyProbe {
    private static final File COPIED_CLIENT=new File(System.getProperty("france.probe.fixture", ""));
    private static final int OBSERVER_FPS_LIMIT=30,FIXTURE_RAIL_SPEED_KMH=40,DWELL_MILLIS=30000;
    private static final int[] NODES={-16,96,104,200,408,504,600};
    private static final int A_MIN=104,A_MAX=200,B_MIN=408,B_MAX=504;
    private static final String[] CAR_IDS={"mtr_custom_train_mf67_ratp_noir_motrice_cab_1","mtr_custom_train_mf67_ratp_noir_motrice_trailer","mtr_custom_train_mf67_ratp_noir_remorque_trailer","mtr_custom_train_mf67_ratp_noir_motrice_trailer","mtr_custom_train_mf67_ratp_noir_motrice_cab_2"};
    private static final String CAR=CAR_IDS[0],A_NAME="Probe Station A",B_NAME="Probe Station B",D_NAME="Probe Depot",R_NAME="Probe A to B";
    private enum Stage {MENU,WORLD,SETUP,CHUNKS,DASHBOARD,AREAS,PLATFORMS,ROUTE,SIDING,DEPOT,GENERATE,SCHEDULE,WATCH_A,BOARD,WATCH_B,SETTLE,CLOSE,FINISHED}
    private volatile Stage stage=Stage.MENU,clientAcknowledged;
    private volatile Throwable failure;
    private volatile UUID playerId;
    private volatile long stationAId,stationBId,depotId,routeId,sidingId,platformAId,platformBId,vehicleId,generationRequestedAt,generationPreviousMillis,plannedDepartureMillis,plannedDepartureAbsolute;
    private long startedAt,stageStartedAt;
    private volatile ClientSample clientSample;
    private volatile CoreSample coreSample;
    private final AtomicBoolean coreReadPending=new AtomicBoolean();
    private boolean copyVerified,dashboardObserved;
    private boolean boardingAttempted;
    private volatile boolean insideJourney,boardingAuthorized;
    private int observationTeleports;
    private int menuTicks,clientTicks,phaseTicks,readyChunkTicks,setupTeleports,stillTicks;
    private Simulator simulator;
    private String runId,saveName;
    private File saveDirectory;
    private volatile int shotRequest,shotAcknowledged;
    private volatile String shotLabel;
    private int renderedFrames;
    private long allRenderedFrames;
    private final JsonObject report=new JsonObject();
    private double maxSpeedKmh, maxDisplacement, maxCoreDisplacement, journeyStartProgress, journeyStartZ=Double.NaN;
    private boolean doorwayAligned, outsideCaptured, insideCaptured;
    private final JsonArray timeline=new JsonArray(),transitions=new JsonArray(),screenshots=new JsonArray(),placements=new JsonArray();
    private Thread diagnosticClientThread;

    @Mod.EventHandler public void init(FMLInitializationEvent event) {
        require(COPIED_CLIENT.isAbsolute(),"Explicit disposable france.probe.fixture is required");
        require("true".equals(System.getProperty("yte.hidden.client")),"Hidden native opt-in required");
        try {Class.forName("org.mtr.yte.test.hidden.HiddenWindowGuard").getMethod("assertOwnedFixture").invoke(null);}catch(Exception problem){throw new IllegalStateException(problem);}
        try {require(COPIED_CLIENT.getCanonicalFile().equals(new File(System.getProperty("user.dir")).getCanonicalFile()),"Fixture must equal canonical cwd");}catch(IOException problem){throw new IllegalStateException(problem);}
        diagnosticClientThread=Thread.currentThread();
        try {Class.forName("me.eigenraven.lwjgl3ify.core.Config").getField("DEBUG_PRINT_WINDOW_EVENTS").setBoolean(null,true);}catch(Exception problem){throw new IllegalStateException("Cannot enable window-only lifecycle diagnostics",problem);}
        Runtime.getRuntime().addShutdownHook(new Thread(()->lifecycle("jvm_shutdown_hook"),"MTR-France-Journey-Shutdown"));
        startedAt=stageStartedAt=System.currentTimeMillis();FMLCommonHandler.instance().bus().register(this);lifecycle("initialized");
    }
    @SubscribeEvent public void clientTick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||stage==Stage.FINISHED)return;
        Minecraft client=Minecraft.getMinecraft();
        try {
            if(failure!=null||stage==Stage.CLOSE){finish(client);return;}
            require(System.currentTimeMillis()-startedAt<8*60*1000L,"Diagnostic deadline in "+stage);
            if(stage==Stage.MENU) {
                if(!Loader.instance().hasReachedState(LoaderState.AVAILABLE)||client.theWorld!=null||client.currentScreen==null){menuTicks=0;return;}
                String screen=client.currentScreen.getClass().getName();
                if(!screen.equals("net.minecraft.client.gui.GuiMainMenu")&&!screen.equals("lumien.custommainmenu.gui.GuiCustom")){menuTicks=0;return;}
                if(++menuTicks<60)return;reserveNewSave(client);
                client.gameSettings.pauseOnLostFocus=false;client.gameSettings.thirdPersonView=0;client.gameSettings.renderDistanceChunks=8;client.gameSettings.limitFramerate=OBSERVER_FPS_LIMIT;
                advance(Stage.WORLD);
                WorldSettings settings=new WorldSettings(0x4D5452545241494EL,WorldSettings.GameType.CREATIVE,false,false,WorldType.FLAT).enableCommands().func_82750_a("2;7,2x3,2;1;");
                client.launchIntegratedServer(saveName,"MTR France journey "+runId,settings);return;
            }
            if(client.getIntegratedServer()!=null)assertOwnedServer(client.getIntegratedServer());
            if(stage==Stage.WORLD){if(client.theWorld==null||client.thePlayer==null||client.getIntegratedServer()==null)return;playerId=client.thePlayer.getUniqueID();advance(Stage.SETUP);return;}
            require(client.theWorld!=null&&client.thePlayer!=null&&client.getIntegratedServer()!=null,"Owned train world disappeared");
            client.gameSettings.pauseOnLostFocus=false;
            if(stage==Stage.DASHBOARD&&client.currentScreen instanceof org.mtr.mod.screen.DashboardScreen)dashboardObserved=true;
            if(client.currentScreen!=null)client.displayGuiScreen(null);
            KeyBinding.unPressAllKeys();Stage current=stage;
            ClientSample previous=clientSample;
            if(current==Stage.BOARD&&previous!=null&&!boardingAuthorized){
                client.thePlayer.rotationYaw=client.thePlayer.prevRotationYaw=-90;
                if(previous.pickup!=null){double dz=previous.pickup.z-previous.z;
                    if(Math.abs(dz)>.2)key(dz>0?client.gameSettings.keyBindRight:client.gameSettings.keyBindLeft,true);
                    else if(previous.x<previous.centerX+.2)key(client.gameSettings.keyBindForward,true);
                    else key(client.gameSettings.keyBindBack,true);
                }
            }
            if(clientAcknowledged!=current&&configureClient(current))clientAcknowledged=current;
            clientSample=new ClientSample(client,++clientTicks);
            if(clientTicks%100==0){System.out.println("FRANCE_JOURNEY_PROGRESS "+stage+" tick="+clientTicks+" x="+client.thePlayer.posX+" y="+client.thePlayer.posY+" z="+client.thePlayer.posZ+" paused="+client.isGamePaused()+" game_focus="+client.inGameHasFocus);lifecycle("client_progress");}
        }catch(Throwable problem){fail(problem);}
    }
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||failure!=null)return;Stage current=stage;
        if(current==Stage.MENU||current==Stage.WORLD||current==Stage.CLOSE||current==Stage.FINISHED)return;
        try {
            MinecraftServer server=MinecraftServer.getServer();assertOwnedServer(server);EntityPlayerMP player=actualPlayer(server);
            if(player==null){require(current==Stage.SETUP&&++phaseTicks<200,"Owned passenger missing");return;}
            WorldServer world=server.worldServerForDimension(0);require(player.worldObj==world,"Owned world mismatch");
            require(System.currentTimeMillis()-stageStartedAt<((current==Stage.SCHEDULE||current==Stage.WATCH_A||current==Stage.WATCH_B)?360000:120000),"Phase deadline "+current);++phaseTicks;
            if(current==Stage.SETUP){player.setGameType(WorldSettings.GameType.CREATIVE);player.capabilities.isFlying=false;player.sendPlayerAbilities();setupTeleport(player,5.5,5,152.5,-90);advance(Stage.CHUNKS);return;}
            if(current==Stage.CHUNKS){
                boolean ready=true;for(int z=-1;z<=38;z++){world.theChunkProviderServer.loadChunk(0,z);ready&=chunkStatus(world,0,z).get("ready").getAsBoolean();}
                ready&=clientSample!=null&&Math.abs(clientSample.z-152.5)<.2;readyChunkTicks=ready?readyChunkTicks+1:0;if(readyChunkTicks<2)return;
                buildStand(world,player);simulator=actualSimulator();advance(Stage.DASHBOARD);
                Item item=originalItem("dashboard",org.mtr.mod.Items.RAILWAY_DASHBOARD.get().data);ItemStack stack=new ItemStack(item);player.inventory.mainInventory[0]=stack;item.onItemRightClick(stack,world,player);return;
            }
            readCoreSoon();ClientSample client=clientSample;CoreSample core=coreSample;if(client==null||core==null)return;
            require(System.currentTimeMillis()-core.at<5000,"Core read-only sample stale");
            if(phaseTicks%100==0){JsonObject row=boundary("five_second_progress");row.addProperty("vehicle",Long.toString(core.vehicle));row.addProperty("door_target",core.doorTarget);row.addProperty("door",client.door);row.addProperty("riders",core.riderCount);row.addProperty("inside",insideJourney);timeline.add(row);}
            if(current==Stage.DASHBOARD&&core.railsComplete&&client.fixtureReady&&dashboardObserved&&client.dashboardSavedRailsReady){sidingId=core.siding;platformAId=core.platformA;platformBId=core.platformB;player.inventory.mainInventory[0]=null;advance(Stage.AREAS);}
            else if(current==Stage.AREAS&&clientAcknowledged==current&&core.areasConfigured)advance(Stage.PLATFORMS);
            else if(current==Stage.PLATFORMS&&clientAcknowledged==current&&core.platformsConfigured)advance(Stage.ROUTE);
            else if(current==Stage.ROUTE&&clientAcknowledged==current&&core.routeConfigured)advance(Stage.SIDING);
            else if(current==Stage.SIDING&&clientAcknowledged==current&&core.sidingConfigured)advance(Stage.DEPOT);
            else if(current==Stage.DEPOT&&clientAcknowledged==current&&core.depotConfigured)advance(Stage.GENERATE);
            else if(current==Stage.GENERATE&&clientAcknowledged==current){require(!core.generationFailed,"Original generation failed: "+core.depotJson);if(core.generated)advance(Stage.SCHEDULE);}
            else if(current==Stage.SCHEDULE){
                if(!core.vehiclePresent||!core.onRoute||!client.vehiclePresent)return;
                vehicleId=core.vehicle;require(client.vehicle==vehicleId,"Client/core train mismatch");advance(Stage.WATCH_A);
            }else if(current==Stage.WATCH_A){
                require(!client.riding&&core.riderCount==0&&!PlayerPhysics.authorized(player),"Exterior observer unexpectedly mounted");
                if(atPlatform(client,core,platformAId,A_MIN,A_MAX)&&client.door>.99&&client.resourceReady){
                    require(client.shouldRender,"Original renderer has not rendered the French first car");
                    if(!outsideCaptured){outsideCaptured=captureDone("outside-A-open");return;}
                    if(!doorwayAligned){
                        require(client.pickup!=null&&client.pickup.x<client.centerX,"French west doorway unavailable");
                        player.playerNetServerHandler.setPlayerLocation(5.5,5,client.pickup.z,-90,10);
                        observationTeleports++;doorwayAligned=true;boardingAttempted=true;
                        report.add("boarding_external_door_geometry",client.geometry());advance(Stage.BOARD);
                    }
                }
            }else if(current==Stage.BOARD){
                boardingAuthorized=PlayerPhysics.authorized(player)&&Math.abs(client.x-client.centerX)<.9;
                if(client.riding&&core.rider&&boardingAuthorized){
                    insideJourney=true;journeyStartZ=client.centerZ;journeyStartProgress=core.vehicleJson.get("railProgress").getAsDouble();
                    report.addProperty("natural_boarding_completed",true);report.add("natural_boarding_client",client.json());report.add("natural_boarding_core",core.json());advance(Stage.WATCH_B);
                }else require(core.doorTarget>0||client.door>.5,"Natural boarding window ended before client/core rider binding");
            }else if(current==Stage.WATCH_B){
                require(client.riding&&core.rider&&PlayerPhysics.authorized(player)&&core.riderCount==1,"Inside journey lost natural client/core rider binding");
                require(!client.flying&&!client.noClip&&!core.passengerDoorOverride&&!core.passengerControls,"Passenger acquired flight, noclip or train controls");
                maxSpeedKmh=Math.max(maxSpeedKmh,core.vehicleJson.get("speed").getAsDouble()*3600D);
                maxDisplacement=Math.max(maxDisplacement,Math.abs(client.centerZ-journeyStartZ));
                maxCoreDisplacement=Math.max(maxCoreDisplacement,Math.abs(core.vehicleJson.get("railProgress").getAsDouble()-journeyStartProgress));
                if(maxDisplacement>=20&&maxCoreDisplacement>=20&&client.moving&&core.moving){
                    insideCaptured=captureDone("inside-moving");
                    if(insideCaptured){report.add("moving_inside_client",client.json());report.add("moving_inside_core",core.json());advance(Stage.SETTLE);}
                }
            }else if(current==Stage.SETTLE&&System.currentTimeMillis()-stageStartedAt>1500)advance(Stage.CLOSE);
        }catch(Throwable problem){fail(problem);}
    }
    @SubscribeEvent public void renderTick(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        try {Minecraft client=Minecraft.getMinecraft();allRenderedFrames++;
            if(failure!=null||shotRequest==shotAcknowledged)return;
            if(client.theWorld==null||client.thePlayer==null||++renderedFrames<4)return;
            capture(client,shotLabel);shotAcknowledged=shotRequest;
        }catch(Throwable problem){fail(problem);}
    }
    private static void key(KeyBinding binding,boolean down){KeyBinding.setKeyBindState(binding.getKeyCode(),down);}
    private JsonObject boundary(String name){JsonObject value=new JsonObject();value.addProperty("boundary",name);value.addProperty("at_millis",System.currentTimeMillis());value.addProperty("stage",stage.name());value.addProperty("client_tick",clientTicks);return value;}
    private static boolean platformDoorway(Box doorway,PositionAndRotation pose) {
        org.mtr.mapping.holder.Vector3d[] points=new org.mtr.mapping.holder.Vector3d[4];int at=0;
        for(double x:new double[]{doorway.getMinXMapped(),doorway.getMaxXMapped()})for(double z:new double[]{doorway.getMinZMapped(),doorway.getMaxZMapped()})points[at++]=pose.transformForwards(new org.mtr.mapping.holder.Vector3d(x,doorway.getMaxYMapped(),z),org.mtr.mapping.holder.Vector3d::rotateX,org.mtr.mapping.holder.Vector3d::rotateY,org.mtr.mapping.holder.Vector3d::add);
        double minX=Double.POSITIVE_INFINITY,minY=minX,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(org.mtr.mapping.holder.Vector3d point:points){minX=Math.min(minX,point.getXMapped());maxX=Math.max(maxX,point.getXMapped());minY=Math.min(minY,point.getYMapped());maxY=Math.max(maxY,point.getYMapped());minZ=Math.min(minZ,point.getZMapped());maxZ=Math.max(maxZ,point.getZMapped());}
        org.mtr.mapping.holder.ClientWorld world=org.mtr.mapping.holder.MinecraftClient.getInstance().getWorldMapped();if(world==null)return false;
        for(double x=minX-1;x<=maxX+1;x++)for(double y=minY-2;y<=maxY+2;y++)for(double z=minZ-1;z<=maxZ+1;z++)if(world.getBlockState(Init.newBlockPos(x,y,z)).getBlock().data instanceof org.mtr.mod.block.PlatformHelper)return true;
        return false;
    }

    private void capture(Minecraft client,String label)throws Exception {
        require(client.currentScreen==null,"World framebuffer is not rendered");String name="mtr-france-journey-"+runId+"-"+label+".png";
        ScreenShotHelper.saveScreenshot(client.mcDataDir,name,client.displayWidth,client.displayHeight,client.getFramebuffer());File file=new File(new File(client.mcDataDir,"screenshots"),name);
        require(file.isFile()&&file.length()>100,"Actual train framebuffer was not written");JsonObject value=new JsonObject();value.addProperty("phase",label);value.addProperty("path",file.getCanonicalPath());value.addProperty("render_frames_after_request",renderedFrames);value.addProperty("client_tick",clientTicks);screenshots.add(value);Class.forName("org.mtr.yte.test.hidden.HiddenWindowGuard").getMethod("recordFrame",long.class,String.class).invoke(null,allRenderedFrames,"screenshots/"+name);
    }
    private boolean configureClient(Stage current)throws Exception {
        MinecraftClientData data=MinecraftClientData.getDashboardInstance();
        if(current==Stage.AREAS) {
            Station a=new Station(data),b=new Station(data);Depot d=new Depot(TransportMode.TRAIN,data);
            a.setName(A_NAME);a.setColor(0x3366CC);a.setCorners(new Position(0,Long.MIN_VALUE,100),new Position(16,Long.MAX_VALUE,204));
            b.setName(B_NAME);b.setColor(0x33AA66);b.setCorners(new Position(0,Long.MIN_VALUE,404),new Position(16,Long.MAX_VALUE,508));
            d.setName(D_NAME);d.setColor(0xAA6633);d.setCorners(new Position(0,Long.MIN_VALUE,-20),new Position(16,Long.MAX_VALUE,100));
            require(generationRequestedAt==0&&plannedDepartureAbsolute==0&&completeRails(data.rails),"Original item rails must be configured before scheduling/generation");
            UpdateDataRequest request=new UpdateDataRequest(data).addStation(a).addStation(b).addDepot(d);JsonArray slowRails=new JsonArray();
            for(Rail rail:data.rails) {
                JsonObject values=full(rail);values.addProperty("speedLimit1",FIXTURE_RAIL_SPEED_KMH);values.addProperty("speedLimit2",FIXTURE_RAIL_SPEED_KMH);
                Rail slow=new Rail(new JsonReader(values));
                require(slow.isValid()&&slow.getHexId().equals(rail.getHexId())&&slow.getSpeedLimitKilometersPerHour(true)==FIXTURE_RAIL_SPEED_KMH&&slow.getSpeedLimitKilometersPerHour(false)==FIXTURE_RAIL_SPEED_KMH,"Original rail speed update changed identity or failed");
                request.addRail(slow);slowRails.add(full(slow));
            }
            require(slowRails.size()==6,"Original six-rail speed update incomplete");
            stationAId=a.getId();stationBId=b.getId();depotId=d.getId();send(request);
            report.addProperty("fixture_rail_speed_kmh",FIXTURE_RAIL_SPEED_KMH);report.addProperty("fixture_speed_configured_before_generation",true);report.add("fixture_speed_updated_rails",slowRails);return true;
        } else if(current==Stage.PLATFORMS) {
            Platform a=data.platformIdMap.get(platformAId),b=data.platformIdMap.get(platformBId);if(a==null||b==null)return false;
            a.setName("1");b.setName("1");a.setDwellTime(DWELL_MILLIS);b.setDwellTime(DWELL_MILLIS);send(new UpdateDataRequest(data).addPlatform(a).addPlatform(b));return true;
        } else if(current==Stage.ROUTE) {
            if(!data.platformIdMap.containsKey(platformAId)||!data.platformIdMap.containsKey(platformBId))return false;
            Route route=new Route(TransportMode.TRAIN,data);route.setName(R_NAME);route.setColor(0x3366CC);route.setCircularState(Route.CircularState.NONE);
            for(long id:new long[]{platformAId,platformBId}){RoutePlatformData row=new RoutePlatformData(id);route.getRoutePlatforms().add(row);row.writePlatformCache(route,data.platformIdMap);}
            routeId=route.getId();send(new UpdateDataRequest(data).addRoute(route));return true;
        } else if(current==Stage.SIDING) {
            Siding siding=data.sidingIdMap.get(sidingId);if(siding==null)return false;
            ObjectArrayList<VehicleCar> cars=new ObjectArrayList<>();
            for(String carId:CAR_IDS){
                final VehicleResource[] selected={null};
                CustomResourceLoader.getVehicleById(TransportMode.TRAIN,carId,details->{if(carId.equals(details.left().getId()))selected[0]=details.left();});
                if(selected[0]==null)return false;VehicleResource resource=selected[0];
                if(resource.getCachedVehicleResource(cars.size(),CAR_IDS.length,false)==null)return false;
                cars.add(new VehicleCar(resource.getId(),resource.getLength(),resource.getWidth(),resource.getBogie1Position(),resource.getBogie2Position(),resource.getCouplingPadding1(),resource.getCouplingPadding2()));
            }
            siding.setVehicleCars(cars);siding.setMaxVehicles(1);siding.setAcceleration(Siding.ACCELERATION_DEFAULT);siding.setDeceleration(Siding.ACCELERATION_DEFAULT);
            send(new UpdateDataRequest(data).addSiding(siding));return true;
        } else if(current==Stage.DEPOT) {
            Depot depot=data.depotIdMap.get(depotId);if(depot==null||!data.routeIdMap.containsKey(routeId))return false;
            depot.getRouteIds().clear();depot.getRouteIds().add(routeId);depot.setRepeatInfinitely(false);depot.setUseRealTime(true);
            plannedDepartureAbsolute=(System.currentTimeMillis()+45000)/1000*1000;plannedDepartureMillis=Math.floorMod(plannedDepartureAbsolute,86400000L);
            depot.getRealTimeDepartures().clear();depot.getRealTimeDepartures().add(plannedDepartureMillis);depot.getRealTimeDepartures().add(Math.floorMod(plannedDepartureAbsolute+240000,86400000L));send(new UpdateDataRequest(data).addDepot(depot));
            report.addProperty("planned_departure_absolute_ms",plannedDepartureAbsolute);report.addProperty("planned_departure_millis_of_day",plannedDepartureMillis);report.addProperty("schedule_timezone",TimeZone.getDefault().getID());return true;
        } else if(current==Stage.GENERATE) {
            if(coreSample==null)return false;require(coreSample.fixtureRailSpeedConfigured,"Original lower rail speeds not acknowledged before generation");generationPreviousMillis=coreSample.generationMillis;generationRequestedAt=System.currentTimeMillis();DepotOperationByIds operation=new DepotOperationByIds();operation.addDepotId(depotId);InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketDepotGenerate(operation));return true;
        }
        return false;
    }
    private void buildStand(WorldServer world,EntityPlayerMP player) {
        world.getGameRules().setOrCreateGameRule("doDaylightCycle","false");world.getGameRules().setOrCreateGameRule("doMobSpawning","false");world.setWorldTime(6000);world.difficultySetting=EnumDifficulty.PEACEFUL;
        for(int z=-16;z<=600;z++)for(int x=5;x<=10;x++){world.setBlock(x,3,z,net.minecraft.init.Blocks.stone);for(int y=4;y<=10;y++)world.setBlockToAir(x,y,z);}
        player.rotationYaw=0;
        for(int z:NODES){ItemStack stack=new ItemStack(originalItem("rail",Item.getItemFromBlock(org.mtr.mod.Blocks.RAIL_NODE.get().data)));use(world,player,stack,8,3,z,1,.5F);require(stack.stackSize==0,"Original node item did not consume one block");}
        player.rotationYaw=-90;
        for(int start:new int[]{A_MIN,B_MIN})for(int z=start;z<=start+96;z++)for(int x=5;x<=7;x++){ItemStack stack=new ItemStack(frenchPlatformItem());use(world,player,stack,x,3,z,1,.5F);require(stack.stackSize==0,"Original platform item did not consume one block");}
        player.rotationYaw=0;
        for(int edge=0;edge<6;edge++) {
            String name=edge==0?"rail_connector_siding":edge==2||edge==4?"rail_connector_platform":edge==5?"rail_connector_turn_back":"rail_connector_20";
            Item item=(Item)Item.itemRegistry.getObject("mtr:"+name);require(item instanceof org.mtr.mod.item.ItemRailModifier,"Original train connector missing");ItemStack connector=new ItemStack(item);
            use(world,player,connector,8,4,NODES[edge],1,.5F);
            require(connector.hasTagCompound()&&connector.getTagCompound().getLong(ItemBlockClickingBase.TAG_POS)==new BlockPos(8,4,NODES[edge]).asLong(),"Original rail first-click state missing");
            use(world,player,connector,8,4,NODES[edge+1],1,.5F);require(!connector.getTagCompound().hasKey(ItemBlockClickingBase.TAG_POS)&&connector.stackSize==1,"Original reusable connector click pair failed");
        }
        player.inventory.mainInventory[0]=null;player.inventory.currentItem=0;report.addProperty("original_native_node_count",7);report.addProperty("original_native_platform_block_count",582);
    }
    private static boolean fixtureReady(net.minecraft.world.World world) {
        try {
            // The setup client sees station A; server item/chunk checks cover the full route.
            for(int z:new int[]{96,104,200})if(world.getBlock(8,4,z)!=org.mtr.mod.Blocks.RAIL_NODE.get().data||!"true".equals(state(world,8,4,z).data.getSerializedProperties().get("is_connected")))return false;
            for(int z=A_MIN;z<=A_MAX;z++)for(int x=5;x<=7;x++)if(world.getBlock(x,4,z)!=frenchPlatformBlock())return false;
            return true;
        }catch(IllegalArgumentException|IllegalStateException waitingForNativeState){return false;}
    }
    private static final class ClientSample {
        final int tick;final long vehicle;final VehicleExtension sourceVehicle;final double x,y,z,motionY,centerX,centerY,centerZ,yaw,floorY,door;
        final boolean onGround,noClip,flying,riding,moving,vehiclePresent,resourceReady,vehicleGeometryCacheReady,shouldRender,fixtureReady,dashboardSavedRailsReady,emptyHand,carConfigured;
        final int vehicleGeometryFloorCount;
        final Vector pickup;final JsonObject nativeInput,originalRiding,vehicleJson,interest,snapshotPrediction;final Box doorway;
        ClientSample(Minecraft client,int tick) {
            this.tick=tick;x=client.thePlayer.posX;y=client.thePlayer.boundingBox.minY;z=client.thePlayer.posZ;motionY=client.thePlayer.motionY;
            onGround=client.thePlayer.onGround;noClip=client.thePlayer.noClip;flying=client.thePlayer.capabilities.isFlying;emptyHand=client.thePlayer.getHeldItem()==null;
            fixtureReady=fixtureReady(client.theWorld);dashboardSavedRailsReady=MinecraftClientData.getDashboardInstance().platforms.size()==2&&MinecraftClientData.getDashboardInstance().sidings.size()==1;
            nativeInput=new JsonObject();nativeInput.addProperty("entity_move_forward",client.thePlayer.moveForward);nativeInput.addProperty("entity_move_strafing",client.thePlayer.moveStrafing);
            if(client.thePlayer.movementInput!=null){nativeInput.addProperty("movement_input_move_forward",client.thePlayer.movementInput.moveForward);nativeInput.addProperty("movement_input_move_strafe",client.thePlayer.movementInput.moveStrafe);}
            nativeInput.addProperty("rotation_yaw",client.thePlayer.rotationYaw);nativeInput.addProperty("native_focus",client.inGameHasFocus);nativeInput.addProperty("native_paused",client.isGamePaused());
            originalRiding=originalRidingDiagnostics();interest=new JsonObject();BlockPos camera=org.mtr.mapping.holder.MinecraftClient.getInstance().getGameRendererMapped().getCamera().getBlockPos();
            interest.addProperty("render_distance_chunks",MinecraftClientHelper.getRenderDistance());interest.addProperty("original_data_request_radius",MinecraftClientHelper.getRenderDistance()*16L);interest.addProperty("camera_x",camera.getX());interest.addProperty("camera_y",camera.getY());interest.addProperty("camera_z",camera.getZ());
            VehicleExtension found=null;for(VehicleExtension value:MinecraftClientData.getInstance().vehicles){require(found==null,"Owned client has multiple unexpected trains");found=value;}
            sourceVehicle=found;vehiclePresent=found!=null;snapshotPrediction=snapshotPredictionDiagnostics(found);
            if(found==null){vehicle=0;centerX=centerY=centerZ=yaw=floorY=door=0;riding=moving=resourceReady=vehicleGeometryCacheReady=shouldRender=carConfigured=false;vehicleGeometryFloorCount=-1;pickup=null;doorway=null;vehicleJson=new JsonObject();}
            else {
                final VehicleExtension actual=found;carConfigured=correctCar(actual);vehicle=actual.getId();riding=VehicleRidingMovement.isRiding(vehicle);moving=actual.isMoving();door=actual.persistentVehicleData.getDoorValue();vehicleJson=Utilities.getJsonObjectFromData(actual);
                shouldRender=actual.persistentVehicleData.rayTracing.length>0&&actual.persistentVehicleData.rayTracing[0];
                final PositionAndRotation[] pose={null};
                // Unlike getSmoothedVehicleCarsAndPositions, this original getter never temporarily assigns railProgress.
                actual.getVehicleCarsAndPositions().stream().findFirst().ifPresent(car->{ObjectArrayList<PositionAndRotation> bogies=new ObjectArrayList<>();car.right().forEach(pair->bogies.add(new PositionAndRotation(pair.left(),pair.right(),true)));pose[0]=new PositionAndRotation(bogies,car.left(),false);});
                final VehicleResourceCache[] cache={null};CustomResourceLoader.getVehicleById(TransportMode.TRAIN,CAR,details->{if(CAR.equals(details.left().getId()))cache[0]=details.left().getCachedVehicleResource(0,CAR_IDS.length,false);});
                vehicleGeometryCacheReady=cache[0]!=null;vehicleGeometryFloorCount=cache[0]==null?-1:cache[0].floors.size();
                Box west=null;Vector outside=null;double westX=Double.POSITIVE_INFINITY;
                if(pose[0]!=null&&cache[0]!=null&&!cache[0].floors.isEmpty())for(Box candidate:cache[0].doorways){Vector point=pickupPoint(pose[0].position,pose[0].yaw,candidate);if(point.x<westX&&platformDoorway(candidate,pose[0])){westX=point.x;west=candidate;outside=point;}}
                doorway=west;pickup=outside;resourceReady=carConfigured&&pose[0]!=null&&cache[0]!=null&&west!=null;
                centerX=pose[0]==null?0:pose[0].position.x;centerY=pose[0]==null?0:pose[0].position.y;centerZ=pose[0]==null?0:pose[0].position.z;yaw=pose[0]==null?0:pose[0].yaw;
                floorY=west==null?centerY+1:centerY+west.getMaxYMapped();
            }
        }
        JsonObject geometry() {
            JsonObject result=new JsonObject();result.addProperty("source","original_getVehicleCarsAndPositions_unsmoothed");result.addProperty("center_x",centerX);result.addProperty("center_y",centerY);result.addProperty("center_z",centerZ);result.addProperty("yaw",yaw);result.addProperty("floor_y",floorY);
            if(doorway!=null){JsonObject box=new JsonObject();box.addProperty("min_x",doorway.getMinXMapped());box.addProperty("max_x",doorway.getMaxXMapped());box.addProperty("min_z",doorway.getMinZMapped());box.addProperty("max_z",doorway.getMaxZMapped());box.addProperty("y",doorway.getMaxYMapped());box.addProperty("min_y",doorway.getMinYMapped());box.addProperty("max_y",doorway.getMaxYMapped());result.add("original_local_doorway",box);}
            if(pickup!=null){JsonObject value=new JsonObject();value.addProperty("x",pickup.x);value.addProperty("feet_y",pickup.y);value.addProperty("z",pickup.z);result.add("derived_external_setup_pose",value);}return result;
        }
        JsonObject json(){JsonObject result=new JsonObject();result.addProperty("client_tick",tick);result.addProperty("client_x",x);result.addProperty("client_feet_y",y);result.addProperty("client_z",z);result.addProperty("client_motion_y",motionY);result.addProperty("client_on_ground",onGround);result.addProperty("client_no_clip",noClip);result.addProperty("client_flying",flying);result.addProperty("client_riding",riding);result.addProperty("empty_hand",emptyHand);result.addProperty("vehicle_id",Long.toString(vehicle));result.addProperty("vehicle_moving",moving);result.addProperty("actual_france_car_configured",carConfigured);result.addProperty("door",door);result.addProperty("original_renderer_should_render",shouldRender);result.addProperty("actual_resource_ready",resourceReady);result.addProperty("vehicle_geometry_cache_ready",vehicleGeometryCacheReady);result.addProperty("vehicle_geometry_floor_count",vehicleGeometryFloorCount);result.add("car_geometry",geometry());result.add("native_input",nativeInput);result.add("original_riding_state",originalRiding);result.add("actual_client_interest",interest);result.add("original_client_vehicle",vehicleJson);result.add("client_snapshot_prediction",snapshotPrediction);return result;}
    }
    private final class CoreSample {
        final long at=System.currentTimeMillis(),siding,platformA,platformB,vehicle,thisPlatform,nextPlatform,stoppedPlatform,generationMillis;
        final boolean railsComplete,fixtureRailSpeedConfigured,areasConfigured,platformsConfigured,routeConfigured,sidingConfigured,depotConfigured,generated,generationFailed,vehiclePresent,moving,onRoute,closeToDepot,rider,carConfigured,stationBGateValid;
        final int riderCount;final boolean passengerDoorOverride,passengerControls;final double dwellMillis,doorTarget;final long doorCooldown;final JsonObject depotJson,sidingJson,vehicleJson;final Map<String,JsonObject> fullData=new LinkedHashMap<>();
        CoreSample(Simulator data)throws Exception {
            railsComplete=completeRails(data.rails);fixtureRailSpeedConfigured=completeRails(data.rails,true);Platform a=null,b=null;Siding s=null;Station sa=null,sb=null;Depot d=null;Route r=null;
            for(Platform value:data.platforms){if(value.containsPos(new Position(8,4,A_MIN))&&value.containsPos(new Position(8,4,A_MAX)))a=value;if(value.containsPos(new Position(8,4,B_MIN))&&value.containsPos(new Position(8,4,B_MAX)))b=value;}
            for(Siding value:data.sidings)if(value.containsPos(new Position(8,4,-16))&&value.containsPos(new Position(8,4,96)))s=value;
            for(Station value:data.stations){if(value.getId()==stationAId)sa=value;if(value.getId()==stationBId)sb=value;}
            int gateAreas=0;for(Station value:data.stations)if(value.inArea(new Position(4,5,456)))gateAreas++;
            stationBGateValid=sb!=null&&sb.inArea(new Position(4,5,456))&&gateAreas==1&&sb.getZone1()==0&&sb.getZone2()==0&&sb.getZone3()==0;
            for(Depot value:data.depots)if(value.getId()==depotId)d=value;for(Route value:data.routes)if(value.getId()==routeId)r=value;
            siding=s==null?0:s.getId();platformA=a==null?0:a.getId();platformB=b==null?0:b.getId();
            areasConfigured=fixtureRailSpeedConfigured&&sa!=null&&sb!=null&&d!=null&&a!=null&&b!=null&&s!=null&&a.area==sa&&b.area==sb&&s.area==d&&sa.getName().equals(A_NAME)&&sb.getName().equals(B_NAME)&&d.getName().equals(D_NAME)&&data.stations.size()==2&&data.depots.size()==1;
            platformsConfigured=areasConfigured&&a.getDwellTime()==DWELL_MILLIS&&b.getDwellTime()==DWELL_MILLIS;
            routeConfigured=r!=null&&r.getTransportMode()==TransportMode.TRAIN&&r.getRoutePlatforms().size()==2&&r.getRoutePlatforms().get(0).getPlatform()!=null&&r.getRoutePlatforms().get(1).getPlatform()!=null&&r.getRoutePlatforms().get(0).getPlatform().getId()==platformAId&&r.getRoutePlatforms().get(1).getPlatform().getId()==platformBId&&r.getCircularState()==Route.CircularState.NONE&&data.routes.size()==1;
            sidingConfigured=s!=null&&s.getMaxVehicles()==1&&!s.getIsManual()&&correctCars(s.getVehicleCars())&&Siding.getTotalVehicleLength(s.getVehicleCars())>=95&&Siding.getTotalVehicleLength(s.getVehicleCars())<=112;
            depotConfigured=d!=null&&d.getRouteIds().size()==1&&d.getRouteIds().getLong(0)==routeId&&!d.getRepeatInfinitely()&&d.getUseRealTime()&&d.getRealTimeDepartures().size()==2&&d.getRealTimeDepartures().contains(plannedDepartureMillis)&&d.getRealTimeDepartures().contains(Math.floorMod(plannedDepartureAbsolute+240000,86400000L));
            depotJson=d==null?new JsonObject():full(d);sidingJson=s==null?new JsonObject():full(s);
            generationMillis=d==null?0:d.getLastGeneratedMillis();
            generated=d!=null&&s!=null&&generationRequestedAt>0&&generationMillis>0&&generationMillis!=generationPreviousMillis&&d.getLastGeneratedStatus()==Depot.GeneratedStatus.SUCCESSFUL&&!d.getPath().isEmpty()&&!((Collection<?>)diagnosticField(s,"pathSidingToMainRoute")).isEmpty()&&!((Collection<?>)diagnosticField(s,"pathMainRouteToSiding")).isEmpty();
            generationFailed=d!=null&&generationRequestedAt>0&&generationMillis>0&&generationMillis!=generationPreviousMillis&&d.getLastGeneratedStatus()!=Depot.GeneratedStatus.SUCCESSFUL;
            Vehicle found=null;if(s!=null)for(Object value:(Iterable<?>)diagnosticField(s,"vehicles")){require(found==null,"Owned core has unexpected multiple vehicles");found=(Vehicle)value;}
            vehiclePresent=found!=null;
            if(found==null){vehicle=thisPlatform=nextPlatform=stoppedPlatform=0;moving=onRoute=closeToDepot=rider=carConfigured=false;riderCount=0;passengerDoorOverride=passengerControls=false;dwellMillis=doorTarget=0;doorCooldown=0;vehicleJson=new JsonObject();}
            else {carConfigured=correctCar(found);vehicle=found.getId();moving=found.isMoving();onRoute=found.getIsOnRoute();closeToDepot=found.closeToDepot();thisPlatform=found.vehicleExtraData.getThisPlatformId();nextPlatform=found.vehicleExtraData.getNextPlatformId();vehicleJson=Utilities.getJsonObjectFromData(found);stoppedPlatform=stoppedPlatform(found.vehicleExtraData.immutablePath,vehicleJson.get("nextStoppingIndexAto").getAsInt(),vehicleJson.get("railProgress").getAsDouble());final int[] count={0};final boolean[] actual={false},hold={false},controls={false};found.vehicleExtraData.iterateRidingEntities(row->{count[0]++;if(row.uuid.equals(playerId)&&row.getRidingCar()==0){actual[0]=true;hold[0]=row.getDoorOverride();controls[0]=row.isDriver()||row.manualAccelerate()||row.manualBrake()||row.manualToggleDoors()||row.manualToggleAto();}});riderCount=count[0];rider=actual[0]&&riderCount==1;passengerDoorOverride=hold[0];passengerControls=controls[0];dwellMillis=vehicleJson.get("elapsedDwellTime").getAsDouble();doorTarget=found.vehicleExtraData.getDoorMultiplier();doorCooldown=((Number)diagnosticField(found,"doorCooldown")).longValue();}
            for(Station value:data.stations)fullData.put("stations/"+value.getHexId(),full(value));for(Platform value:data.platforms)fullData.put("platforms/"+value.getHexId(),full(value));for(Siding value:data.sidings)fullData.put("sidings/"+value.getHexId(),full(value));for(Route value:data.routes)fullData.put("routes/"+value.getHexId(),full(value));for(Depot value:data.depots)fullData.put("depots/"+value.getHexId(),full(value));for(Rail value:data.rails)fullData.put("rails/"+value.getHexId(),full(value));
        }
        JsonObject json(){JsonObject result=new JsonObject();result.addProperty("rails_complete",railsComplete);result.addProperty("fixture_rail_speed_configured",fixtureRailSpeedConfigured);result.addProperty("areas_configured",areasConfigured);result.addProperty("platforms_configured",platformsConfigured);result.addProperty("route_configured",routeConfigured);result.addProperty("siding_configured",sidingConfigured);result.addProperty("depot_configured",depotConfigured);result.addProperty("generated",generated);result.addProperty("vehicle_id",Long.toString(vehicle));result.addProperty("vehicle_moving",moving);result.addProperty("actual_france_car_configured",carConfigured);result.addProperty("on_route",onRoute);result.addProperty("close_to_depot",closeToDepot);result.addProperty("rider_count",riderCount);result.addProperty("actual_passenger",rider);result.addProperty("passenger_door_override",passengerDoorOverride);result.addProperty("passenger_manual_controls",passengerControls);result.addProperty("elapsed_dwell_millis",dwellMillis);result.addProperty("door_target",doorTarget);result.addProperty("door_cooldown",doorCooldown);result.addProperty("this_platform",Long.toString(thisPlatform));result.addProperty("next_platform",Long.toString(nextPlatform));result.addProperty("stopped_platform",Long.toString(stoppedPlatform));result.add("vehicle",vehicleJson);return result;}
    }
    private static boolean correctCar(Vehicle value){return correctCars(value.vehicleExtraData.immutableVehicleCars);}
    private static boolean correctCars(List<VehicleCar> cars){if(cars.size()!=CAR_IDS.length)return false;for(int i=0;i<CAR_IDS.length;i++)if(!CAR_IDS[i].equals(cars.get(i).getVehicleId())||Math.abs(cars.get(i).getLength()-19)>1E-6)return false;return true;}
    private void reserveNewSave(Minecraft client) throws Exception {
        require(client.mcDataDir.getCanonicalFile().equals(COPIED_CLIENT.getCanonicalFile()), "Refusing to run outside disposable copied client");
        require(client.theWorld == null && client.getIntegratedServer() == null, "Refusing to replace an existing open world");
        runId = System.getProperty("yte.hidden.runId");require(UUID.fromString(runId).toString().equals(runId),"Canonical hidden owner UUID required"); saveName = "MTR-france-journey-" + runId;
        Path saves = client.mcDataDir.toPath().resolve("saves"); Files.createDirectories(saves);
        require(!Files.isSymbolicLink(saves) && saves.toRealPath().getParent().equals(client.mcDataDir.toPath().toRealPath()), "Copied saves directory escaped client");
        Path reserved = Files.createDirectory(saves.resolve(saveName));
        saveDirectory = reserved.toFile();
        Files.write(reserved.resolve("mtr-france-journey-owner.txt"), runId.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        copyVerified = true;
        report.addProperty("run_id", runId); report.addProperty("save_directory", saveDirectory.getCanonicalPath());
        report.addProperty("client_directory", client.mcDataDir.getCanonicalPath());
        report.addProperty("new_uuid_save_reserved", true); report.addProperty("existing_save_loaded", false);
        report.addProperty("scenario", "Five-car MF67 RATP noir M/N/A/N/M from immutable V1.2 manifest; French RATP platform; packet-configured route and native scheduled movement; exterior and naturally boarded interior framebuffers");
    }
    private void finish(Minecraft client) {
        if(stage==Stage.FINISHED)return;lifecycle("finish_enter");report.addProperty("terminal_phase",stage.name());stage=Stage.FINISHED;
        try {
            if(failure==null){require(outsideCaptured&&insideCaptured&&screenshots.size()==2,"Exterior/interior actual framebuffers missing");require(insideJourney,"Natural boarding missing");require(maxDisplacement>=20&&maxCoreDisplacement>=20,"Train did not travel 20 blocks after boarding");}
            if(copyVerified){KeyBinding.unPressAllKeys();if(client.getIntegratedServer()!=null){assertOwnedServer(client.getIntegratedServer());if(client.theWorld!=null)client.theWorld.sendQuittingDisconnectingPacket();client.loadWorld(null);client.displayGuiScreen(new GuiMainMenu());report.addProperty("normal_owned_world_unload",true);}}
        }catch(Throwable problem){fail(problem);}
        report.addProperty("passed",failure==null);report.addProperty("diagnostic_only",true);report.addProperty("vehicle_car_count",CAR_IDS.length);JsonArray carIds=new JsonArray();for(String id:CAR_IDS)carIds.add(new JsonPrimitive(id));report.add("fixed_vehicle_profiles",carIds);
        report.addProperty("max_authoritative_speed_kmh",maxSpeedKmh);report.addProperty("client_vehicle_displacement_after_boarding_blocks",maxDisplacement);report.addProperty("core_rail_progress_displacement_after_boarding_blocks",maxCoreDisplacement);report.addProperty("setup_teleport_count",setupTeleports);report.addProperty("exterior_alignment_teleport_count",observationTeleports);report.addProperty("probe_motion_writes",0);report.addProperty("probe_door_writes",0);report.addProperty("probe_riding_authorization_writes",0);report.addProperty("inside_coverage_completed",insideJourney);report.addProperty("native_movement_input_for_boarding",boardingAttempted);report.addProperty("dwell_millis",DWELL_MILLIS);report.addProperty("remote_multiplayer_accepted",false);report.add("timeline",timeline);report.add("transitions",transitions);report.add("screenshots",screenshots);report.add("original_one_shot_item_uses",placements);
        if(failure!=null){report.addProperty("error",failure.toString());failure.printStackTrace();}
        try {Path output=Paths.get(System.getProperty("france.probe.output"));require(output.isAbsolute(),"Absolute report path required");Files.createDirectories(output.getParent());Files.write(output,report.toString().getBytes(StandardCharsets.UTF_8));System.out.println("FRANCE_JOURNEY_PROBE_RESULT passed="+(failure==null));if(copyVerified)client.shutdown();}catch(Throwable problem){throw new IllegalStateException(problem);}
    }
    private void appendTrace(String name,JsonObject row)throws IOException{
        Path root=COPIED_CLIENT.toPath().toRealPath(),path=root.resolve(name);require(copyVerified&&!Files.isSymbolicLink(path),"Trace escaped owner");Files.write(path,(row.toString()+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
    private void advance(Stage next) {
        JsonObject value=new JsonObject();value.addProperty("from",stage.name());value.addProperty("to",next.name());value.addProperty("elapsed_ms",System.currentTimeMillis()-startedAt);transitions.add(value);
        phaseTicks=stillTicks=0;stageStartedAt=System.currentTimeMillis();stage=next;lifecycle("stage_transition");
    }

    private void setupTeleport(EntityPlayerMP player,double x,double y,double z,float yaw) {
        require(stage==Stage.SETUP&&setupTeleports==0&&!PlayerPhysics.authorized(player),"Setup teleport forbidden after setup");
        player.playerNetServerHandler.setPlayerLocation(x,y,z,yaw,10);setupTeleports++;
    }
    private static void send(UpdateDataRequest request){InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(request));}

    private static boolean completeRails(Iterable<Rail> rails) {return completeRails(rails,false);}

    private static boolean completeRails(Iterable<Rail> rails,boolean configuredSpeed) {
        boolean[] seen=new boolean[6];int count=0;long speed=configuredSpeed?FIXTURE_RAIL_SPEED_KMH:20;
        for(Rail rail:rails){int edge=-1;for(int i=0;i<6;i++)if(rail.getHexId().equals(TwoPositionsBase.getHexId(new Position(8,4,NODES[i]),new Position(8,4,NODES[i+1]))))edge=i;
            if(edge<0||seen[edge]||rail.getTransportMode()!=TransportMode.TRAIN||Math.abs(rail.railMath.getLength()-(NODES[edge+1]-NODES[edge]))>.01||rail.isSiding()!=(edge==0)||rail.isPlatform()!=(edge==2||edge==4)||rail.canTurnBack()!=(edge==5))return false;
            if((configuredSpeed||edge==1||edge==3)&&(rail.getSpeedLimitKilometersPerHour(true)!=speed||rail.getSpeedLimitKilometersPerHour(false)!=speed))return false;seen[edge]=true;count++;
        }return count==6;
    }

    private static Vector pickupPoint(Vector center,double yaw,Box door) {
        double x=(door.getMinXMapped()+door.getMaxXMapped())/2,z=(door.getMinZMapped()+door.getMaxZMapped())/2;
        return new Vector(x+Math.copySign(.75,x),door.getMaxYMapped(),z).rotateY(yaw).add(center);
    }

    private static boolean atPlatform(ClientSample client,CoreSample core,long platform,int min,int max) {
        return client.carConfigured&&core.carConfigured&&client.vehiclePresent&&core.vehiclePresent&&client.vehicle==core.vehicle&&!client.moving&&!core.moving&&core.onRoute&&client.centerZ>=min&&client.centerZ<=max&&core.stoppedPlatform==platform;
    }

    private static long stoppedPlatform(List<PathData> path,int nextStoppingIndex,double railProgress) {
        // Terminal route-info already points beyond the platform during its dwell.
        if(nextStoppingIndex<0||nextStoppingIndex>=path.size())return 0;
        PathData stop=path.get(nextStoppingIndex);
        return stop.getDwellTime()>0&&stop.getEndDistance()==railProgress?stop.getSavedRailBaseId():0;
    }

    private static Simulator actualSimulator() throws Exception {
        Field mainField = Init.class.getDeclaredField("main"); mainField.setAccessible(true);
        Main main = (Main) mainField.get(null);
        require(main != null, "Original server Main was not started");
        Field simulatorsField = Main.class.getDeclaredField("simulators"); simulatorsField.setAccessible(true);
        for (Object candidate : (Iterable<?>) simulatorsField.get(main)) {
            Simulator value = (Simulator) candidate;
            if ("minecraft/overworld".equals(value.dimension)) return value;
        }
        throw new AssertionError("Original overworld simulator is missing");
    }

    private void readCoreSoon() {
        if (simulator == null || !coreReadPending.compareAndSet(false, true)) return;
        simulator.run(() -> {
            try { coreSample = new CoreSample(simulator); }
            catch (Throwable problem) { fail(problem); }
            finally { coreReadPending.set(false); }
        });
    }

    private static Object diagnosticField(Object owner, String name) throws ReflectiveOperationException {
        Class<?> type = owner instanceof Class<?> ? (Class<?>) owner : owner.getClass();
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner instanceof Class<?> ? null : owner);
    }

    private static JsonObject snapshotPredictionDiagnostics(VehicleExtension vehicle) {
        JsonObject value = new JsonObject(); value.addProperty("sampled_at_nanos", System.nanoTime());
        value.addProperty("sample_thread", Thread.currentThread().getName()); value.addProperty("vehicle_present", vehicle != null);
        if (vehicle != null) try {
            value.addProperty("vehicle_instance_identity", System.identityHashCode(vehicle));
            value.addProperty("has_simulated", (Boolean) diagnosticField(vehicle, "hasSimulated"));
            value.addProperty("snapshot_receive_age_millis", ((java.util.function.LongSupplier) diagnosticField(vehicle, "millisSinceReceived")).getAsLong());
        } catch (ReflectiveOperationException | RuntimeException diagnosticError) { value.addProperty("error", diagnosticError.toString()); }
        return value;
    }

    private static JsonObject originalRidingDiagnostics() {
        JsonObject value = new JsonObject(); value.addProperty("sampled_at_millis", System.currentTimeMillis());
        try {
            value.addProperty("ridingVehicleId", Long.toString(((Number) diagnosticField(VehicleRidingMovement.class, "ridingVehicleId")).longValue()));
            for (String name : new String[]{"sendPositionUpdateTime", "ridingVehicleCarNumber", "ridingVehicleX", "ridingVehicleY", "ridingVehicleZ", "ridingVehicleCooldown", "doorOverrideTicks"})
                value.addProperty(name, (Number) diagnosticField(VehicleRidingMovement.class, name));
        } catch (ReflectiveOperationException | RuntimeException diagnosticError) { value.addProperty("error", diagnosticError.toString()); }
        return value;
    }

    private boolean captureDone(String label) {
        if (!label.equals(shotLabel)) {
            require(shotRequest == shotAcknowledged, "Previous actual screenshot has no render acknowledgment");
            renderedFrames = 0; shotLabel = label; shotRequest++;
            return false;
        }
        return shotAcknowledged == shotRequest;
    }

    private EntityPlayerMP actualPlayer(MinecraftServer server) {
        for (Object candidate : server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP value = (EntityPlayerMP) candidate;
            if (value.getUniqueID().equals(playerId)) return value;
        }
        return null;
    }

    private static JsonObject chunkStatus(WorldServer world, int x, int z) {
        boolean exists = world.getChunkProvider().chunkExists(x, z);
        Chunk chunk = (Chunk) world.theChunkProviderServer.loadedChunkHashMap.getValueByKey(ChunkCoordIntPair.chunkXZ2Int(x, z));
        JsonObject value = new JsonObject();
        value.addProperty("chunk_x", x); value.addProperty("chunk_z", z); value.addProperty("chunk_exists", exists);
        value.addProperty("load_on_provide", world.theChunkProviderServer.loadChunkOnProvideRequest);
        value.addProperty("ready", exists && chunk != null && !(chunk instanceof EmptyChunk)
            && chunk.xPosition == x && chunk.zPosition == z && chunk.isChunkLoaded);
        if (chunk != null) { value.addProperty("class", chunk.getClass().getName()); value.addProperty("terrain_populated", chunk.isTerrainPopulated); }
        return value;
    }

    private static JsonObject blockStatus(WorldServer world, int x, int y, int z) {
        JsonObject value = new JsonObject();
        value.addProperty("x", x); value.addProperty("y", y); value.addProperty("z", z);
        Block block = world.getBlock(x, y, z);
        value.addProperty("block_registry", Block.blockRegistry.getNameForObject(block));
        value.addProperty("block_id", Block.getIdFromBlock(block)); value.addProperty("class", block.getClass().getName());
        value.addProperty("metadata", world.getBlockMetadata(x, y, z));
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile != null) {
            NBTTagCompound tag = new NBTTagCompound(); tile.writeToNBT(tag);
            value.addProperty("native_tile_nbt", tag.toString()); value.addProperty("tile_class", tile.getClass().getName());
        }
        value.add("chunk", chunkStatus(world, x >> 4, z >> 4));
        return value;
    }

    private static Block frenchPlatformBlock(){Block block=(Block)Block.blockRegistry.getObject("mtrfranceaddon:ratp_platform");require(block instanceof org.mtr.mod.block.PlatformHelper,"French RATP platform must use native PlatformHelper");return block;}
    private static Item frenchPlatformItem(){Item item=Item.getItemFromBlock(frenchPlatformBlock());require(item!=null&&Item.itemRegistry.getObject("mtrfranceaddon:ratp_platform")==item,"French platform item identity missing");return item;}

    private static Item originalItem(String path, Item expected) {
        Item item = (Item) Item.itemRegistry.getObject("mtr:" + path);
        require(item != null && item == expected, "Registry mtr:" + path + " is not the original item identity");
        return item;
    }

    private void use(WorldServer world, EntityPlayerMP player, ItemStack stack, int x, int y, int z, int side, float hitY) {
        require(chunkStatus(world, x >> 4, z >> 4).get("ready").getAsBoolean(), "Native chunk not ready for original item use");
        player.inventory.mainInventory[0] = stack;
        JsonObject value = new JsonObject();
        value.addProperty("item", Item.itemRegistry.getNameForObject(stack.getItem()));
        value.addProperty("item_class", stack.getItem().getClass().getName());
        value.addProperty("clicked_x", x); value.addProperty("clicked_y", y); value.addProperty("clicked_z", z);
        value.addProperty("side", side); value.addProperty("yaw", player.rotationYaw); value.addProperty("stack_before", stack.stackSize);
        placements.add(value);
        boolean accepted = stack.getItem().onItemUse(stack, player, world, x, y, z, side, .5F, hitY, .5F);
        value.addProperty("accepted", accepted); value.addProperty("stack_after", stack.stackSize);
        value.add("clicked_after", blockStatus(world, x, y, z));
        value.add("above_after", blockStatus(world, x, y + 1, z));
        require(accepted, "One-shot original item placement/use rejected: " + value);
    }

    private void assertOwnedServer(MinecraftServer server) throws Exception {
        require(copyVerified && server != null && saveDirectory != null && saveName.equals(server.getFolderName()), "Refusing to mutate/close a foreign server");
        Path marker = saveDirectory.toPath().resolve("mtr-france-journey-owner.txt");
        require(!Files.isSymbolicLink(saveDirectory.toPath()) && Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)
            && runId.equals(new String(Files.readAllBytes(marker), StandardCharsets.UTF_8)), "Owned save marker changed");
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private synchronized void fail(Throwable problem) { if (failure == null) {failure = problem;problem.printStackTrace();lifecycle("failure");} }
    @Mod.EventHandler public void integratedStopping(cpw.mods.fml.common.event.FMLServerStoppingEvent event){lifecycle("integrated_server_stopping");}
    private synchronized void lifecycle(String event){
        JsonObject value=new JsonObject();value.addProperty("event",event);value.addProperty("stage",stage.name());value.addProperty("at_nanos",System.nanoTime());value.addProperty("thread",Thread.currentThread().getName());value.addProperty("client_ticks",clientTicks);value.addProperty("server_phase_ticks",phaseTicks);value.addProperty("copy_verified",copyVerified);if(failure!=null)value.addProperty("failure",failure.toString());
        if("jvm_shutdown_hook".equals(event)&&diagnosticClientThread!=null){value.addProperty("client_thread",diagnosticClientThread.getName());value.addProperty("client_thread_state",diagnosticClientThread.getState().name());JsonArray stack=new JsonArray();for(StackTraceElement frame:diagnosticClientThread.getStackTrace())stack.add(new JsonPrimitive(frame.toString()));value.add("owned_client_thread_stack",stack);}
        System.out.println("FRANCE_JOURNEY_LIFECYCLE "+value);
        if(copyVerified)try{appendTrace("probe-lifecycle.jsonl",value);}catch(Exception problem){System.err.println("FRANCE_JOURNEY_LIFECYCLE_WRITE_FAILED "+problem);}
    }

    private static BlockState state(net.minecraft.world.World world,int x,int y,int z){return new org.mtr.mapping.holder.World(world).getBlockState(new BlockPos(x,y,z));}

    private static JsonObject full(SerializedDataBase value){JsonObject result=new JsonObject();value.serializeFullData(new JsonWriter(result));return result;}

}
