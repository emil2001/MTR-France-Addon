package org.mtr.gtnh.probe;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import org.mtr.core.data.ClientData;
import org.mtr.core.data.Station;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.com.google.gson.JsonPrimitive;
import org.mtr.mapping.holder.PlayerEntity;
import org.mtr.mapping.holder.Scoreboard;
import org.mtr.mapping.holder.ScoreboardCriterionRenderType;
import org.mtr.mapping.holder.ScoreboardObjective;
import org.mtr.mapping.holder.ServerPlayerEntity;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.holder.World;
import org.mtr.mapping.mapper.ScoreboardCriteria;
import org.mtr.mapping.mapper.ScoreboardHelper;
import org.mtr.mod.data.TicketSystem;

/** Dedicated-native linkage and fare regression; test-only, synchronous and UUID-fixture guarded. */
final class FranceTicketRegression {
    private static final String[] ENTRY_OBJECTIVES={"mtr_entry_zone_1","mtr_entry_zone_2","mtr_entry_zone_3"};
    private FranceTicketRegression() {}

    static void run() throws Throwable {
        JsonObject result=new JsonObject();FranceNativeProbe.report.add("dedicated_ticket_regression",result);
        result.addProperty("passed",false);
        cpw.mods.fml.common.ModContainer mtr=cpw.mods.fml.common.Loader.instance().getIndexedModList().get("mtr");
        FranceNativeProbe.check(mtr!=null,"Loaded MTR mod is missing");result.addProperty("loaded_mtr_version",mtr.getVersion());
        result.addProperty("scope","Native ScoreboardHelper and TicketSystem.onEnter/onExit invoked reflectively; FakePlayer; action-bar messages captured; excludes passThrough nearby-station callback, sound and physical barrier collision");
        MinecraftServer server=MinecraftServer.getServer();
        FranceNativeProbe.check(server!=null&&!server.isSinglePlayer(),"Dedicated ticket regression requires dedicated runtime");
        Path fixture=Paths.get(System.getProperty("france.probe.fixture")).toRealPath();
        Path owner=fixture.getParent().resolve(".france-owner");
        FranceNativeProbe.check(!Files.isSymbolicLink(owner)&&Files.isRegularFile(owner),"Missing disposable France fixture marker");
        String runId=new String(Files.readAllBytes(owner),StandardCharsets.UTF_8).trim();
        FranceNativeProbe.check(UUID.fromString(runId).toString().equals(runId)&&server.getFolderName().equals("france-"+runId),"Refusing to test ticket balances in a non-owned world");
        result.addProperty("owned_world",server.getFolderName());
        WorldServer nativeWorld=server.worldServerForDimension(0);World world=new World(nativeWorld);Scoreboard board=world.getScoreboard();
        for(String objective:new String[]{"mtr_fare_probe",TicketSystem.BALANCE_OBJECTIVE,ENTRY_OBJECTIVES[0],ENTRY_OBJECTIVES[1],ENTRY_OBJECTIVES[2]})
            FranceNativeProbe.check(board.data.getObjective(objective)==null,"Fresh fixture already has ticket objective "+objective);

        result.addProperty("phase","native_scoreboard_objective_title");
        ScoreboardObjective objective=ScoreboardHelper.addObjective(board,"mtr_fare_probe",ScoreboardCriteria.DUMMY,Text.of("France fare probe"),ScoreboardCriterionRenderType.INTEGER);
        FranceNativeProbe.check("France fare probe".equals(objective.data.getDisplayName()),"Native objective display title mismatch");
        ScoreboardHelper.setPlayerScore(board,"FrTicketProbe",objective,40);
        ScoreboardHelper.incrementPlayerScore(board,"FrTicketProbe",objective,2);
        FranceNativeProbe.check(ScoreboardHelper.getPlayerScore(board,"FrTicketProbe",objective)==42,"Native scoreboard score mutation mismatch");
        result.addProperty("native_objective_title_and_score",true);

        FakePlayer nativePlayer=FakePlayerFactory.get(nativeWorld,new GameProfile(UUID.fromString(runId),"FrTicketProbe"));
        nativePlayer.capabilities.isCreativeMode=false;PlayerEntity player=new PlayerEntity(nativePlayer);
        // A FakePlayer has no connected action-bar recipient; only UI delivery is replaced, never fare/scoreboard logic.
        Field sink=Class.forName("org.mtr.mapping.registry.LegacyPlayerMessages").getDeclaredField("serverSink");sink.setAccessible(true);
        Object oldSink=sink.get(null);JsonArray messages=new JsonArray();result.add("captured_action_bar_messages",messages);
        BiConsumer<ServerPlayerEntity,Text> recorder=(recipient,text)->messages.add(new JsonPrimitive(text.getString()));
        sink.set(null,recorder);
        try {
            Method enter=TicketSystem.class.getDeclaredMethod("onEnter",World.class,Station.class,PlayerEntity.class,boolean.class);
            Method exit=TicketSystem.class.getDeclaredMethod("onExit",World.class,Station.class,PlayerEntity.class,boolean.class);
            enter.setAccessible(true);exit.setAccessible(true);
            Station a=new Station(new ClientData());a.setName("France Probe A");a.setZone1(1);a.setZone2(2);a.setZone3(3);
            Station b=new Station(new ClientData());b.setName("France Probe B");b.setZone1(4);b.setZone2(1);b.setZone3(3);
            result.addProperty("phase","same_station_entry_exit");
            TicketSystem.addBalance(world,player,100);balance(world,player,100);
            FranceNativeProbe.check(invoke(enter,world,a,player),"Survival passenger entry rejected");
            entry(board,player,2,3,4);balance(world,player,100);
            FranceNativeProbe.check(!invoke(enter,world,a,player),"Duplicate entry was accepted");balance(world,player,100);
            FranceNativeProbe.check(invoke(exit,world,a,player),"Same station exit rejected");entry(board,player,0,0,0);balance(world,player,98);
            FranceNativeProbe.check(!invoke(exit,world,a,player),"Duplicate exit was accepted");balance(world,player,98);
            result.addProperty("same_station_base_fare",2);result.addProperty("duplicate_entry_exit_rejected",true);

            result.addProperty("phase","zone_fare_and_insufficient_balance");
            FranceNativeProbe.check(invoke(enter,world,a,player)&&invoke(exit,world,b,player),"Zone journey rejected");
            balance(world,player,92);entry(board,player,0,0,0);result.addProperty("zone_difference_fare",6);
            TicketSystem.addBalance(world,player,-93);balance(world,player,-1);
            FranceNativeProbe.check(!invoke(enter,world,a,player),"Negative balance entry accepted");entry(board,player,0,0,0);balance(world,player,-1);
            result.addProperty("negative_balance_entry_rejected",true);

            result.addProperty("phase","concessionary_fare");
            TicketSystem.addBalance(world,player,21);nativePlayer.capabilities.isCreativeMode=true;
            FranceNativeProbe.check(invoke(enter,world,a,player)&&invoke(exit,world,a,player),"Concessionary journey rejected");
            balance(world,player,19);entry(board,player,0,0,0);result.addProperty("same_station_concessionary_fare",1);
            FranceNativeProbe.check("Balance".equals(board.data.getObjective(TicketSystem.BALANCE_OBJECTIVE).getDisplayName()),"Native ticket balance objective title mismatch");
            result.addProperty("ticket_balance_objective_title","Balance");
            result.addProperty("phase","complete");result.addProperty("passed",true);
        } finally {sink.set(null,oldSink);result.addProperty("action_bar_sink_restored",sink.get(null)==oldSink);}
    }

    private static boolean invoke(Method method,World world,Station station,PlayerEntity player) throws Throwable {
        try{return (Boolean)method.invoke(null,world,station,player,true);}
        catch(InvocationTargetException failure){throw failure.getCause();}
    }
    private static void balance(World world,PlayerEntity player,int expected){FranceNativeProbe.check(TicketSystem.getBalance(world,player)==expected,"Ticket balance expected "+expected);}
    private static void entry(Scoreboard board,PlayerEntity player,int a,int b,int c){
        int[] expected={a,b,c};
        for(int i=0;i<ENTRY_OBJECTIVES.length;i++){
            ScoreboardObjective objective=ScoreboardHelper.getScoreboardObjective(board,ENTRY_OBJECTIVES[i]);
            FranceNativeProbe.check(objective!=null&&ScoreboardHelper.getPlayerScore(board,player.getGameProfile().getName(),objective)==expected[i],"Entry-zone state mismatch "+ENTRY_OBJECTIVES[i]);
        }
    }
}
