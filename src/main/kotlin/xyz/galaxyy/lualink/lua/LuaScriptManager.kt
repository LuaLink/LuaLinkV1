package xyz.galaxyy.lualink.lua

import com.github.only52607.luakt.lib.LuaKotlinExLib
import com.github.only52607.luakt.lib.LuaKotlinLib
import org.bukkit.Bukkit
import org.bukkit.event.HandlerList
import org.luaj.vm2.LuaError
import org.luaj.vm2.lib.jse.JsePlatform
import xyz.galaxyy.lualink.LuaLink
import xyz.galaxyy.lualink.lua.misc.PrintOverride
import java.io.File

class LuaScriptManager(private val plugin: LuaLink) {
    private val loadedScripts: MutableList<LuaScript> = mutableListOf()

    fun getLoadedScripts(): List<LuaScript> {
        return loadedScripts.toList()
    }

    fun loadScript(file: File) {
        val globals = JsePlatform.standardGlobals()
        val script = LuaScript(this.plugin, file, globals)
        globals.load(LuaKotlinLib())
        globals.load(LuaKotlinExLib())
        globals.set("script", script)
        globals.set("print", PrintOverride(this.plugin))
        globals.set("utils", LuaUtils()) // Passing script to LuaUtils for state
        globals.set("scheduler", LuaScheduler(this.plugin, script)) // Passing script to LuaScheduler for state
        globals.set("import", LuaImport())
        globals.set("addons", LuaAddons())
        this.plugin.logger.info("Loading script ${file.name}")
        try {
            globals.loadfile(file.path).call()
        } catch (e: LuaError) {
            this.plugin.logger.severe("LuaLink encountered an error while loading ${file.name}: ${e.message}")
            return
        }
        loadedScripts.add(script)
        if (script.onLoadCB?.isfunction() == true) {
            try {
                script.onLoadCB?.call()
            } catch (e: LuaError) {
                this.plugin.logger.severe("LuaLink encountered an error while called onLoad for ${file.name}: ${e.message}")
                return
            }
        }
        Bukkit.getServer().javaClass.getMethod("syncCommands").invoke(Bukkit.getServer())
        this.plugin.logger.info("Loaded script ${file.name}")
    }

    fun unLoadScript(script: LuaScript) {
        script.listeners.forEach { listener ->
            HandlerList.unregisterAll(listener)
        }
        script.commands.forEach { command ->
            command.unregister(this.plugin.server.commandMap)
            this.plugin.server.commandMap.knownCommands.remove(command.name)
            command.aliases.forEach { alias ->
                this.plugin.server.commandMap.knownCommands.remove(alias)
            }
            Bukkit.getServer().javaClass.getMethod("syncCommands").invoke(Bukkit.getServer())
        }
        script.tasks.forEach { taskId ->
            Bukkit.getScheduler().cancelTask(taskId)
        }
        if (script.onUnloadCB?.isfunction() == true) {
            try {
                script.onUnloadCB?.call()
            } catch (e: LuaError) {
                this.plugin.logger.severe("LuaLink encountered an error while called onUnload for ${script.file.name}: ${e.message}")
                return
            }
        }
        this.loadedScripts.remove(script)
    }

    fun disableScript(script: LuaScript) {
        script.file.renameTo(File(script.file.path+".d"))
        this.unLoadScript(script)
    }

    fun enableScript(script: File) {
        script.renameTo(File(script.path.removeSuffix(".d")))
        this.loadScript(File(script.path.removeSuffix(".d")))
    }

    fun loadScripts() {
        this.plugin.logger.info("Loading scripts...")
        if (!File(this.plugin.dataFolder.path+"/scripts").exists()) {
            File(this.plugin.dataFolder.path+"/scripts").mkdirs()
        }

        File(this.plugin.dataFolder.path+"/scripts").walk().forEach { file ->
            if (file.extension == "lua") {
                if (file.name.startsWith(".")) {
                    return@forEach
                }
                this.loadScript(file)
            } else {
                if (file.name != "scripts") {
                    if (file.name.endsWith(".d"))
                        return@forEach
                    this.plugin.logger.warning("${file.name} is in the scripts folder but is not a lua file!")
                }
            }
        }
    }
}