package org.mtr.mapping;

import fr.mtrfranceaddon.mod.common.block.InvisiblePlatform;
import fr.mtrfranceaddon.mod.common.block.RATPTicketBarrierBlock;
import fr.mtrfranceaddon.mod.common.data.CustomRailData;
import fr.mtrfranceaddon.mod.common.item.CustomItemRailModifier;
import fr.mtrfranceaddon.mod.common.registry.*;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.BlockExtension;
import org.mtr.mod.block.BlockDirectionalDoubleBlockBase;
import org.mtr.mod.block.BlockPlatform;
import org.mtr.mod.block.IBlock;
import org.mtr.mod.data.TicketSystem;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/** Native Forge scenarios. Each mode runs in a fresh fixture class loader. */
public final class FranceBehaviorScenarios {
    private static final Set<String> EXPECTED_BLOCKS = new TreeSet<>(Arrays.asList(
        "mtrfranceaddon_logo_block",
        "sncf_logo_actuel_block",
        "sncf_logo_1992-2005_block",
        "sncf_logo_1985-1992_block_v1",
        "sncf_logo_1985-1992_block_v2",
        "sncf_logo_1967-1985_block_v1",
        "sncf_logo_1967-1985_block_v2",
        "sncf_logo_1947-1967_block",
        "sncf_logo_1938-1947_block",
        "ratp_logo_1951-1960_block",
        "ratp_logo_1960-1976_block",
        "ratp_logo_1976-1992_block",
        "ratp_logo_actuel_block",
        "invisible_platform",
        "ratp_platform",
        "sncf_platform",
        "sncf2_platform",
        "ratp_platform_indented",
        "sncf_platform_indented",
        "sncf2_platform_indented",
        "ratp_ticket_barrier_side_cover",
        "ratp_ticket_barrier_entrance",
        "ratp_ticket_barrier_exit",
        "ratp_ticket_barrier_entrance_with_side",
        "ratp_ticket_barrier_exit_with_side",
        "pole",
        "twentysignblock",
        "fourtysignblock",
        "sixtysignblock",
        "heitysignblock",
        "hundredtwentysignblock",
        "hundredsixtysignblock",
        "twohundredsignblock",
        "threehundredsignblock",
        "depotsignblock",
        "downsignblock",
        "upsignblock",
        "endsignblock",
        "levelcrossingsignblock",
        "squaresignblock",
        "stationsignblock",
        "fourcarsignblock",
        "sixcarsignblock",
        "heightcarsignblock",
        "tencarsignblock",
        "twelvcarsignblock",
        "fourteencarsignblock",
        "sixteencarsignblock",
        "plateformelsignblock",
        "plateformersignblock",
        "noventsignblock",
        "leftssignblock",
        "rightssignblock",
        "tunnelsignblock",
        "wheezingsignblock",
        "switchohsignblock",
        "switchthsignblock",
        "switchthhsignblock",
        "downgroundsignblock",
        "upgroundsignblock",
        "twentygroundsignblock",
        "fourtygroundsignblock",
        "sixtygroundsignblock",
        "heightygroundsignblock",
        "hundredtwentygroundsignblock",
        "hundredsixtygroundsignblock",
        "twohundredgroundsignblock",
        "threehundredgroundsignblock",
        "depotgroundsignblock",
        "endgroundsignblock",
        "leftsgroundsignblock",
        "rightsgroundsignblock",
        "levelcrossinggroundsignblock",
        "squaregroundsignblock",
        "stationgroundsignblock",
        "noventgroundsignblock",
        "tunnelgroundsignblock",
        "wheezinggroundsignblock",
        "switchohgroundsignblock",
        "switchthgroundsignblock",
        "switchthhgroundsignblock",
        "poleleftconnection",
        "polerightconnection",
        "polehorizontalconnection",
        "polemiddleconnection",
        "twentygallows",
        "fourtygallows",
        "sixtygallows",
        "heightygallows",
        "hundredtwentygallows",
        "hundredsixtygallows",
        "twohundredgallows",
        "threehundredgallows",
        "squaregallows",
        "idfm_ticket_machine",
        "idfm_ticket_machine_2",
        "idfm_seat_with_pole",
        "idfm_seat"));

    public static void main(String[] args) throws Throwable {
        String mode = args.length == 0 ? "blocks" : args[0];
        BlockPlacementIntegrationTest.setupForgeLoader();
        try {
            Class<?> type = BlockPlacementIntegrationTest.loader.loadClass(FranceBehaviorScenarios.class.getName());
            try { type.getMethod(mode).invoke(type.getConstructor().newInstance()); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        } finally { BlockPlacementIntegrationTest.closeForgeLoader(); }
    }

    public void packetStartup() throws Exception {
        MTRFranceAddonRegistry.setupPacket();
        Field networkField = org.mtr.mapping.registry.Registry.class.getDeclaredField("network");
        networkField.setAccessible(true);
        Object network = networkField.get(MTRFranceAddonRegistry.REGISTRY);
        check(network != null, "Actual native addon packet channel was not created");
        Field name = network.getClass().getDeclaredField("name");
        name.setAccessible(true);
        String actualName = (String)name.get(network);
        check(actualName.equals("mtrfranceaddon:pkt"), "Stable native addon channel ID: " + actualName);
        check(actualName.length() <= 20, "Native channel exceeds legacy 20 character limit");
        MTRFranceAddonRegistryClient.setupPacketClient();
        check(networkField.get(MTRFranceAddonRegistry.REGISTRY) == network, "Client and common setup must share one native channel");
        fr.mtrfranceaddon.gtnh.FranceLoadingPlugin loader = new fr.mtrfranceaddon.gtnh.FranceLoadingPlugin();
        check(loader.getMixinConfig().equals("mixins.france.gtnh.json"), "Early mixin config");
        check(loader.getMixins(Collections.emptySet()).equals(Arrays.asList("RailTypeMixin", "ArrivalsCacheServerMixin")), "Both actual early mixins are returned");
        new fr.mtrfranceaddon.gtnh.CommonProxy().init();
        System.out.println("PASS actual native addon channel, common/client matching, early mixin selection and server proxy");
    }

    /** Run only with actual early RailType mixins active, as in the native server probe. */
    public void lifecycle() throws Exception {
        fr.mtrfranceaddon.gtnh.FranceMod.proxy = new fr.mtrfranceaddon.gtnh.CommonProxy();
        fr.mtrfranceaddon.gtnh.FranceMod mod = new fr.mtrfranceaddon.gtnh.FranceMod();
        mod.preInit(null); // Actual FML entrypoint calls Init.init(), including setupPacket().
        mod.init(null);
        check(ids("blockIds").equals(EXPECTED_BLOCKS), "Lifecycle block registrations");
        check(ids("itemIds").size() == 122, "Lifecycle 122 item registrations");
        check(ModItems.RAIL_CONNECTORS.size() == 24, "Lifecycle 24 connectors");
        for (String id : ids("itemIds")) check(net.minecraft.item.Item.itemRegistry.getObject("mtrfranceaddon:" + id) != null, "Lifecycle native item " + id);
        check(org.mtr.mod.data.RailType.values().length == 29, "Lifecycle genuine transformed enum");
        System.out.println("PASS actual FranceMod preInit/Init.init and common proxy lifecycle");
    }

    public void declarations() throws Exception {
        // Registration callbacks are deliberately not run: RailType needs the real early mixin.
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            ModBlocks.register();
            ModItems.register();
        } finally { Locale.setDefault(previous); }
        check(ids("blockIds").equals(EXPECTED_BLOCKS), "Exact upstream 98 block IDs changed: " + ids("blockIds"));
        Set<String> expectedItems = new TreeSet<>(EXPECTED_BLOCKS);
        for (int speed : new int[]{30,50,70,90,110,130,150,220,270,320,350,999}) {
            expectedItems.add("rail_" + speed);
            expectedItems.add("rail_" + speed + "_oneway");
        }
        check(ids("itemIds").equals(expectedItems), "Exact 122 item IDs changed");
        check(ModItems.RAIL_CONNECTORS.size() == 24, "24 connector registrations");
        System.out.println("PASS exact 98 block / 122 item / 24 connector IDs under Turkish locale");
    }

    public void blocks() throws Exception {
        ModBlocks.register();
        ModBlockEntities.register();
        MTRFranceAddonRegistry.REGISTRY.init();
        Set<String> actual = new TreeSet<>();
        for (Object key : net.minecraft.block.Block.blockRegistry.getKeys()) {
            String id = key.toString();
            if (id.startsWith("mtrfranceaddon:")) actual.add(id.substring("mtrfranceaddon:".length()));
        }
        check(actual.equals(EXPECTED_BLOCKS), "Actual native block registry must contain exact upstream IDs");
        for (String id : EXPECTED_BLOCKS) {
            Object block = net.minecraft.block.Block.blockRegistry.getObject("mtrfranceaddon:" + id);
            check(net.minecraft.item.Item.getItemFromBlock((net.minecraft.block.Block) block) != null, "Missing block item " + id);
        }
        int platforms = 0;
        for (String id : actual) if (net.minecraft.block.Block.blockRegistry.getObject("mtrfranceaddon:" + id) instanceof BlockPlatform) platforms++;
        check(platforms == 7, "Exactly seven actual MTR platforms");
        invisiblePlatform();
        ticketMachines();
        barriers();
        System.out.println("PASS native 98 blocks, block items, seven platforms, invisible platform, ticket machines and barriers");
    }

    private void invisiblePlatform() {
        InvisiblePlatform block = (InvisiblePlatform) ModBlocks.INVISIBLE_PLATFORM.get().data;
        BlockState state = block.getDefaultState2();
        check(block.getCollisionShape2(state,null,null,null).getBoundingBoxes().equals(VoxelShapes.fullCube().getBoundingBoxes()), "Invisible platform full collision");
        check(block.getOutlineShape2(state,null,null,null).getBoundingBoxes().equals(VoxelShapes.fullCube().getBoundingBoxes()), "Invisible platform full outline");
        check(block.getCullingShape2(state,null,null).getBoundingBoxes().isEmpty(), "Invisible platform must not cull adjacent geometry");
        check(block.getAmbientOcclusionLightLevel2(state,null,null) == 1, "Invisible platform must not darken neighbors");
        BlockPlacementScenarios.MemoryWorld world = new BlockPlacementScenarios.MemoryWorld();
        world.setBlock(0,64,0,block);
        check(block.getBlockHardness(world,0,64,0) == -1, "Invisible platform unbreakable");
        check(block.getExplosionResistance(null) >= 3600000, "Invisible platform blast resistance");
        check(block.getDrops(world,0,64,0,0,0).isEmpty(), "Invisible platform must drop nothing");
    }

    private void ticketMachines() {
        for (String id : new String[]{"idfm_ticket_machine", "idfm_ticket_machine_2"}) {
            BlockDirectionalDoubleBlockBase block = (BlockDirectionalDoubleBlockBase) net.minecraft.block.Block.blockRegistry.getObject("mtrfranceaddon:" + id);
            BlockPlacementScenarios.MemoryWorld nativeWorld = new BlockPlacementScenarios.MemoryWorld();
            World world = new World(nativeWorld);
            nativeWorld.isRemote = true;
            check(block.onUse2(block.getDefaultState2(),world,new BlockPos(2,64,2),null,Hand.MAIN_HAND,null) == ActionResult.SUCCESS, id + " client interaction accepts ticket machine use");
            nativeWorld.isRemote = false;
            BlockPos pos = new BlockPos(2,64,2);
            for (Direction facing : new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
                BlockState lower = block.getDefaultState2().with(new Property<>(org.mtr.mapping.mapper.DirectionHelper.FACING.data),facing.data)
                    .with(new Property<>(IBlock.HALF.data),IBlock.DoubleBlockHalf.LOWER);
                world.setBlockState(pos,lower);
                block.onPlaced2(world,pos,lower,null,null);
                BlockState upper = world.getBlockState(pos.up());
                check(upper.isOf(new Block(block)), id + " places actual second block");
                check(IBlock.getStatePropertySafe(upper,IBlock.HALF) == IBlock.DoubleBlockHalf.UPPER, id + " upper half");
                check(IBlock.getStatePropertySafe(upper,org.mtr.mapping.mapper.DirectionHelper.FACING) == facing, id + " shared facing");
                check(block.getOutlineShape2(lower,null,pos,null).getBoundingBoxes().get(0).getMaxYMapped() == 1, id + " lower height");
                check(block.getOutlineShape2(upper,null,pos.up(),null).getBoundingBoxes().get(0).getMaxYMapped() == 0.875, id + " upper height");
                BlockState missingUpper = block.getStateForNeighborUpdate2(lower,Direction.UP,Blocks.getAirMapped().getDefaultState(),new WorldAccess(nativeWorld),pos,pos.up());
                check(missingUpper.isAir(), id + " lower removed when second half disappears");
                BlockState missingLower = block.getStateForNeighborUpdate2(upper,Direction.DOWN,Blocks.getAirMapped().getDefaultState(),new WorldAccess(nativeWorld),pos.up(),pos);
                check(missingLower.isAir(), id + " upper removed when first half disappears");
            }
        }
    }

    private void barriers() {
        int checked = 0;
        for (String id : EXPECTED_BLOCKS) {
            Object value = net.minecraft.block.Block.blockRegistry.getObject("mtrfranceaddon:" + id);
            if (!(value instanceof RATPTicketBarrierBlock)) continue;
            RATPTicketBarrierBlock block = (RATPTicketBarrierBlock)value;
            BlockPlacementScenarios.MemoryWorld nativeWorld = new BlockPlacementScenarios.MemoryWorld();
            World world = new World(nativeWorld);
            BlockPos pos = new BlockPos(2,64,2);
            net.minecraft.entity.player.EntityPlayer player = new net.minecraft.entity.player.EntityPlayer(nativeWorld,
                    new com.mojang.authlib.GameProfile(new UUID(0,1), "FranceBarrierProbe")) {
                public void addChatMessage(net.minecraft.util.IChatComponent text) {}
                public boolean canCommandSenderUseCommand(int level,String command) { return true; }
                public net.minecraft.util.ChunkCoordinates getPlayerCoordinates() { return new net.minecraft.util.ChunkCoordinates(2,64,2); }
            };
            for (Direction facing : new Direction[]{Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST}) {
                BlockState state = block.getDefaultState2().with(new Property<>(org.mtr.mapping.mapper.DirectionHelper.FACING.data),facing.data);
                for (TicketSystem.EnumTicketBarrierOpen open : TicketSystem.EnumTicketBarrierOpen.values()) {
                    BlockState changed = state.with(new Property<>(org.mtr.mod.block.BlockTicketBarrier.OPEN.data),open);
                    int boxes = block.getCollisionShape2(changed,null,null,null).getBoundingBoxes().size();
                    boolean passageOpen = open == TicketSystem.EnumTicketBarrierOpen.OPEN || open == TicketSystem.EnumTicketBarrierOpen.OPEN_CONCESSIONARY;
                    check(boxes == (passageOpen ? 1 : 2), id + " collision gate " + facing + " " + open);
                    if (passageOpen) {
                        world.setBlockState(pos,changed);
                        player.setPosition(2.5 + facing.getOffsetX()*2,64,2.5 + facing.getOffsetZ()*2);
                        block.onEntityCollision2(changed,world,pos,new Entity(player));
                        check(IBlock.getStatePropertySafe(world.getBlockState(pos),org.mtr.mod.block.BlockTicketBarrier.OPEN) == TicketSystem.EnumTicketBarrierOpen.CLOSED,
                            id + " closes after player passes through " + facing + " " + open);
                    }
                }
            }
            checked++;
        }
        check(checked == 4, "Four actual entrance/exit barrier variants");
    }

    /** Requires the actual early mixin environment; never installs synthetic enum values. */
    public void connectors() {
        ModBlocks.register();
        ModItems.register();
        MTRFranceAddonRegistry.REGISTRY.init();
        check(org.mtr.mod.data.RailType.values().length == 29, "Real transformed RailType must contain 29 values");
        BlockState node = new org.mtr.mod.block.BlockNode(org.mtr.core.data.TransportMode.TRAIN).getDefaultState2();
        for (CustomRailData.RailInfo info : CustomRailData.CUSTOM_RAILS) {
            check(org.mtr.mod.data.RailType.valueOf(info.name).speedLimit == info.speed, info.name + " enum speed");
            for (boolean oneWay : new boolean[]{false,true}) {
                String id = info.name.toLowerCase(Locale.ROOT) + (oneWay ? "_oneway" : "");
                CustomItemRailModifier tool = (CustomItemRailModifier) ModItems.RAIL_CONNECTORS.get(id).get().data;
                org.mtr.core.data.Rail rail = tool.createRail(new UUID(0,1), org.mtr.core.data.TransportMode.TRAIN,
                    node,node,new BlockPos(0,64,0),new BlockPos(0,64,32),org.mtr.core.tool.Angle.N,org.mtr.core.tool.Angle.N);
                check(rail != null, id + " must create a valid real rail");
                check(rail.getSpeedLimitKilometersPerHour(false) == (oneWay ? 0 : info.speed), id + " first direction speed");
                check(rail.getSpeedLimitKilometersPerHour(true) == info.speed, id + " second direction speed");
            }
        }
        System.out.println("PASS all 24 actual connector tools create rails with upstream speed and direction");
    }

    private Set<String> ids(String fieldName) throws Exception {
        Field field = org.mtr.mapping.registry.Registry.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        Set<String> result = new TreeSet<>();
        for (Object id : (Set<?>)field.get(MTRFranceAddonRegistry.REGISTRY)) {
            String text = id.toString();
            check(text.startsWith("mtrfranceaddon:"), "Incorrect addon namespace " + text);
            result.add(text.substring("mtrfranceaddon:".length()));
        }
        return result;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
