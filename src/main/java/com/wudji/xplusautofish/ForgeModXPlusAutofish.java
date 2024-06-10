package com.wudji.xplusautofish;

import com.mojang.logging.LogUtils;
import com.wudji.xplusautofish.config.Config;
import com.wudji.xplusautofish.config.ConfigManager;
import com.wudji.xplusautofish.gui.AutoFishConfigScreen;
import com.wudji.xplusautofish.scheduler.AutofishScheduler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChatPacket;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.ClientRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod("autofish")
public class ForgeModXPlusAutofish {

    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    private static ForgeModXPlusAutofish instance;
    private XPlusAutofish autofish;
    private AutofishScheduler scheduler;
    private KeyMapping autofishGuiKey;
    private ConfigManager configManager;

    public ForgeModXPlusAutofish() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::fmlClientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void fmlClientSetup(final FMLClientSetupEvent event){
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> (new IExtensionPoint.DisplayTest(IExtensionPoint.DisplayTest.IGNORESERVERONLY, (a, b) -> {return true;})));

        if (instance == null) instance = this;

        //Create ConfigManager
        this.configManager = new ConfigManager(this);
        //Register Keybinding
        autofishGuiKey = new KeyMapping("key.autofish.open_gui", GLFW.GLFW_KEY_V, "Autofish");
        ClientRegistry.registerKeyBinding(autofishGuiKey);
        //Create Scheduler instance
        this.scheduler = new AutofishScheduler(this);
        //Create Autofisher instance
        this.autofish = new XPlusAutofish(this);
    }
    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (autofishGuiKey.isDown()) {
            client.setScreen(AutoFishConfigScreen.buildScreen(this, client.screen));
        }
        autofish.tick(client);
        scheduler.tick(client);
    }

    /**
     * Mixin callback for Sound and EntityVelocity packets (multiplayer detection)
     */
    public void handlePacket(Packet<?> packet) {
        autofish.handlePacket(packet);
    }

    /**
     * Mixin callback for chat packets
     */
    public void handleChat(ClientboundChatPacket packet) {
        autofish.handleChat(packet);
    }

    /**
     * Mixin callback for catchingFish method of EntityFishHook (singleplayer detection)
     */
    public void tickFishingLogic(Entity owner, int ticksCatchable) {
        autofish.tickFishingLogic(owner, ticksCatchable);
    }

    public static ForgeModXPlusAutofish getInstance() {
        return instance;
    }

    public XPlusAutofish getAutofish() {
        return autofish;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Config getConfig() {
        return configManager.getConfig();
    }

    public AutofishScheduler getScheduler() {
        return scheduler;
    }
}

