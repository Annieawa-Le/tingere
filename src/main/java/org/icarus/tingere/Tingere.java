package org.icarus.tingere;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.icarus.tingere.command.TingereCommandManager;
import org.icarus.tingere.config.RecipeLoader;
import org.icarus.tingere.listener.CraftListener;
import org.icarus.tingere.listener.EditorGuiListener;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class Tingere extends JavaPlugin {
    @Getter private final RecipeLoader recipeLoader = new RecipeLoader(this);
    private final Logger logger = this.getLogger();

    @Override
    public void onEnable() {
        logger.info("Tingere - Custom recipe loader");
        logger.info("Made by Ph0sphorW & Annieawa");
        new TingereCommandManager(this).register();
        getServer().getPluginManager().registerEvents(new CraftListener(this), this);
        getServer().getPluginManager().registerEvents(new EditorGuiListener(this), this);
        reloadRecipes();
    }

    public void reloadRecipes() {
        try {
            long start = System.currentTimeMillis();
            RecipeLoader.FullReloadReport report = recipeLoader.reloadAll();
            long time = System.currentTimeMillis() - start;
            logger.info("Reloaded " + report.reloaded() + " recipe(s) from "
                    + (report.skippedFiles() > 0 ? "changed files, " + report.skippedFiles() + " file(s) unchanged"
                    : "all files")
                    + "; " + report.total() + " recipe(s) active in " + time + "ms");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An error occurred while trying to reload the recipes:", e);
        }
    }
}
