package org.mtr.mapping;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.mtrfranceaddon.mod.common.registry.MTRFranceAddonRegistry;
import fr.mtrfranceaddon.mod.common.registry.ModBlockEntities;
import fr.mtrfranceaddon.mod.common.registry.ModBlocks;
import org.mtr.mapping.holder.Identifier;
import org.mtr.mapping.tool.BlockStateDefinition;
import org.mtr.mapping.tool.LegacyBlockModels;
import org.mtr.mapping.tool.LegacyItemModels;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runs the native CPU model decoders against final France and MTR resources. No GL window required. */
public final class FranceBlockResourceScenarios {
    private static final Identifier MISSING = new Identifier("minecraft:missingno");
    private final Set<Identifier> checkedTextures = new HashSet<>();
    private final LegacyBlockModels blocks = new LegacyBlockModels(this::read);
    private final LegacyItemModels items = new LegacyItemModels(this::read);

    public static void main(String[] args) throws Throwable {
        BlockPlacementIntegrationTest.setupForgeLoader();
        try {
            Class<?> type = BlockPlacementIntegrationTest.loader.loadClass(FranceBlockResourceScenarios.class.getName());
            try { type.getMethod("allRegisteredBlockStatesAndItemModelsDecode").invoke(type.getConstructor().newInstance()); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        } finally { BlockPlacementIntegrationTest.closeForgeLoader(); }
    }

    public void allRegisteredBlockStatesAndItemModelsDecode() {
        ModBlocks.register();
        ModBlockEntities.register();
        MTRFranceAddonRegistry.REGISTRY.init();
        List<String> failures = new ArrayList<>();
        Set<String> blockIds = new TreeSet<>();
        for (Object key : net.minecraft.block.Block.blockRegistry.getKeys()) {
            String id = key.toString();
            if (id.startsWith("mtrfranceaddon:")) blockIds.add(id.substring("mtrfranceaddon:".length()));
        }
        check(blockIds.size() == 98, "Expected 98 actual registered France blocks, found " + blockIds.size());
        int stateCount = 0;
        for (String path : blockIds) {
            Identifier id = new Identifier("mtrfranceaddon",path);
            net.minecraft.block.Block block = (net.minecraft.block.Block)net.minecraft.block.Block.blockRegistry.getObject(id.toString());
            // The original invisible block is rendered only by InvPlatRenderer and has no model elements.
            // Its generated inventory item is still decoded below; its particle texture is checked here.
            if (path.equals("invisible_platform")) {
                try {
                    required(new Identifier("mtrfranceaddon","blockstates/invisible_platform.json"));
                    texture(new Identifier("mtrfranceaddon","item/invisible_platform"));
                    System.out.println("SKIP invisible_platform world model: original block has no elements; InvPlatRenderer supplies the held-tool indicator");
                } catch (RuntimeException | AssertionError failure) { failures.add(id + ": " + failure); }
                continue;
            }
            BlockStateDefinition definition = BlockStateDefinition.forBlock(block);
            Set<Map<String,String>> legalProperties = new LinkedHashSet<>();
            for (int index=0;index<definition.getRawStateCount();index++) {
                legalProperties.add(definition.readRawStateIndex(index).getSerializedProperties());
            }
            for (Map<String,String> properties : legalProperties) {
                stateCount++;
                try {
                    List<LegacyBlockModels.Quad> quads = blocks.resolve(id,properties,0);
                    if ((path.equals("idfm_ticket_machine") || path.equals("idfm_ticket_machine_2")) && "upper".equals(properties.get("half"))) {
                        assertIntentionalTicketMachineUpper(id,properties,quads);
                    } else check(!quads.isEmpty(), "No world model geometry for " + id + properties);
                    for (LegacyBlockModels.Quad quad : quads) texture(quad.texture);
                    Identifier particle = blocks.particle(id,properties,0);
                    if (particle != null) texture(particle);
                } catch (RuntimeException | AssertionError failure) { failures.add(id.toString() + properties + ": " + failure); }
            }
            try {
                // Traverses every model choice and parent, including weighted alternatives not selected by seed 0.
                for (Identifier texture : blocks.textures(id)) texture(texture);
            } catch (RuntimeException | AssertionError failure) { failures.add(id + " all model alternatives: " + failure); }
        }
        Set<String> itemIds = new TreeSet<>(blockIds);
        for (int speed : new int[]{30,50,70,90,110,130,150,220,270,320,350,999}) {
            itemIds.add("rail_" + speed);
            itemIds.add("rail_" + speed + "_oneway");
        }
        check(itemIds.size() == 122, "Expected 98 block-item and 24 rail-tool models");
        for (String path : itemIds) {
            Identifier model = new Identifier("mtrfranceaddon","item/" + path);
            try {
                required(new Identifier(model.getNamespace(),"models/" + model.getPath() + ".json"));
                validateItem(items.model(model),model);
                validateItem(items.select(model,predicate -> 0),model);
                validateItem(items.select(model,predicate -> 1),model);
                // Public textures() recursively decodes every override model and its inherited geometry/layers.
                Set<Identifier> textures = items.textures(model);
                check(!textures.isEmpty(), "No inventory textures for " + model);
                for (Identifier texture : textures) texture(texture);
            } catch (RuntimeException | AssertionError failure) { failures.add(model + ": " + failure); }
        }
        if (!failures.isEmpty()) throw new AssertionError("France native model decode failures (" + failures.size() + "):\n" + String.join("\n",failures));
        System.out.println("PASS 98 registered blocks, " + stateCount + " distinct visible legal block states, 122 item models with overrides, " + checkedTextures.size() + " actual PNG textures");
    }

    private void assertIntentionalTicketMachineUpper(Identifier id,Map<String,String> properties,List<LegacyBlockModels.Quad> quads) {
        JsonObject state = new JsonParser().parse(required(new Identifier(id.getNamespace(),"blockstates/" + id.getPath() + ".json"))).getAsJsonObject();
        String topModel = state.getAsJsonObject("variants").getAsJsonObject("half=upper").get("model").getAsString();
        check(topModel.equals("mtrfranceaddon:block/idfm_ticket_machine_top"), "Ticket upper must use the original shared empty model: " + id);
        JsonObject top = new JsonParser().parse(required(new Identifier("mtrfranceaddon","models/block/idfm_ticket_machine_top.json"))).getAsJsonObject();
        check(top.has("parent") && top.get("parent").getAsString().equals("block/block"), "Original ticket upper has only vanilla empty block parent");
        check(!top.has("elements"), "Original ticket upper source intentionally has no model elements");
        check(quads.isEmpty(), "Original ticket upper must not duplicate the lower model geometry");
        Map<String,String> lowerProperties = new TreeMap<>(properties);
        lowerProperties.put("half","lower");
        boolean lowerDrawsUpper = false;
        for (LegacyBlockModels.Quad quad : blocks.resolve(id,lowerProperties,0)) {
            for (int vertex=1;vertex<quad.vertices.length;vertex+=8) if (quad.vertices[vertex]>1) lowerDrawsUpper = true;
        }
        check(lowerDrawsUpper, "Ticket lower model must draw above the lower block to justify empty upper: " + id + properties);
    }

    private void validateItem(LegacyItemModels.Model model,Identifier id) {
        check(model.generated ? !model.layers.isEmpty() : !model.faces.isEmpty(), "Empty inventory geometry/layers for " + id);
        for (Identifier layer : model.layers) texture(layer);
        for (LegacyBlockModels.Quad face : model.faces) texture(face.texture);
    }

    private void texture(Identifier texture) {
        check(!MISSING.equals(texture), "Unresolved texture alias produced missingno");
        if (checkedTextures.contains(texture)) return;
        Identifier resource = new Identifier(texture.getNamespace(),"textures/" + texture.getPath() + ".png");
        try (InputStream stream = open(resource)) {
            check(stream != null, "Missing PNG resource " + resource);
            BufferedImage image = ImageIO.read(stream);
            check(image != null && image.getWidth()>0 && image.getHeight()>0, "Undecodable PNG resource " + resource);
            checkedTextures.add(texture);
        } catch (IOException failure) { throw new UncheckedIOException("Cannot decode " + resource,failure); }
    }

    private InputStream open(Identifier resource) {
        return getClass().getClassLoader().getResourceAsStream("assets/" + resource.getNamespace() + "/" + resource.getPath());
    }
    private String required(Identifier resource) {
        String source = read(resource);
        check(source != null, "Missing JSON resource " + resource);
        return source;
    }
    private String read(Identifier resource) {
        try (InputStream stream = open(resource)) {
            if (stream == null) return null; // Native decoders supply only their explicitly supported vanilla builtins.
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int size;
            while ((size=stream.read(buffer))!=-1) output.write(buffer,0,size);
            return new String(output.toByteArray(),StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new UncheckedIOException("Cannot read " + resource,failure); }
    }
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
}
