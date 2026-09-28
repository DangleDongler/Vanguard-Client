package dev.vanguard;

import dev.vanguard.config.ConfigManager;
import dev.vanguard.gui.clickgui.ClickGuiScreen;
import dev.vanguard.module.ModuleManager;
import dev.vanguard.module.modules.combat.AimAssist;
import dev.vanguard.module.modules.combat.ShieldBreaker;
import dev.vanguard.module.modules.combat.TriggerBot;
import dev.vanguard.util.ShieldTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Vanguard implements ClientModInitializer {
    public static final String MOD_ID = "vanguard";
    public static final Logger LOG = LoggerFactory.getLogger("Vanguard");

    private static Vanguard instance;

    private ModuleManager modules;
    private ConfigManager config;
    private ClickGuiScreen clickGui;
    private AimAssist aimAssist;
    private TriggerBot triggerBot;
    private ShieldBreaker shieldBreaker;
    private final ShieldTracker shieldTracker = new ShieldTracker();

    public static Vanguard get() {
        return instance;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        modules = new ModuleManager();
        modules.init();
        aimAssist = modules.get(AimAssist.class);
        triggerBot = modules.get(TriggerBot.class);
        shieldBreaker = modules.get(ShieldBreaker.class);

        config = new ConfigManager(FabricLoader.getInstance().getGameDir().resolve(MOD_ID), modules);
        config.load();
        Runtime.getRuntime().addShutdownHook(new Thread(config::save, "Vanguard config save"));

        LOG.info("Vanguard initialized with {} modules", modules.all().size());
    }

    public ModuleManager modules() {
        return modules;
    }

    /** Cached for the per-frame aim-assist hook. Null until {@link #onInitializeClient()} runs. */
    public AimAssist aimAssist() {
        return aimAssist;
    }

    /** Cached for the per-frame and per-attack hooks. Null until {@link #onInitializeClient()} runs. */
    public TriggerBot triggerBot() {
        return triggerBot;
    }

    /** Cached for the per-frame, per-attack and per-tick hooks. Null until {@link #onInitializeClient()} runs. */
    public ShieldBreaker shieldBreaker() {
        return shieldBreaker;
    }

    /** Other players' shields, as the server reports them. Shared by the combat modules. */
    public ShieldTracker shieldTracker() {
        return shieldTracker;
    }

    public ConfigManager config() {
        return config;
    }

    /** The ClickGUI is created on first use: screens need Minecraft's font, which isn't ready during init. */
    public ClickGuiScreen clickGui() {
        if (clickGui == null) clickGui = new ClickGuiScreen(modules, config);
        return clickGui;
    }
}
