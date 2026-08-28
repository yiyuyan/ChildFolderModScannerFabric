package cn.ksmcbrigade.cfmsF;

import cn.ksmcbrigade.cfmsF.transformers.ArrayListTransformer;
import cn.ksmcbrigade.cfmsF.utils.AgentUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.discovery.ModCandidateImpl;
import net.fabricmc.loader.impl.discovery.ModDiscoverer;
import net.fabricmc.loader.impl.discovery.RuntimeModRemapper;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.metadata.DependencyOverrides;
import net.fabricmc.loader.impl.metadata.VersionOverrides;
import net.fabricmc.loader.impl.util.SystemProperties;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class LocatorAdapter implements LanguageAdapter{

    private static final String CONFIG_NAME="CFMSf.txt";

    @Override
    public native <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException;

    static {
        Log.info(LogCategory.DISCOVERY,"CFMSf Start!");

        FabricLoaderImpl loader = FabricLoaderImpl.INSTANCE;
        Path configDir = loader.getConfigDir();
        try {
            Log.info(LogCategory.DISCOVERY,"Unfreezing fabric loader...");

            setFreezeValue(loader,false);

            boolean remapRegularMods = loader.isDevelopmentEnvironment();
            VersionOverrides versionOverrides = new VersionOverrides();
            DependencyOverrides depOverrides = new DependencyOverrides(configDir);

            ModDiscoverer discoverer = new ModDiscoverer(versionOverrides,depOverrides);
            discoverer.addCandidateFinder(new Locator(getModsDirectory(loader), remapRegularMods,configDir.resolve(CONFIG_NAME)));

            List<ModCandidateImpl> modCandidates;
            Map<String, Set<ModCandidateImpl>> envDisabledMods = new HashMap<>();
            modCandidates = discoverer.discoverMods(loader, envDisabledMods);

            // dump version and dependency overrides info

            if (!versionOverrides.getAffectedModIds().isEmpty()) {
                Log.info(LogCategory.GENERAL, "Versions overridden for %s", String.join(", ", versionOverrides.getAffectedModIds()));
            }

            if (!depOverrides.getAffectedModIds().isEmpty()) {
                Log.info(LogCategory.GENERAL, "Dependencies overridden for %s", String.join(", ", depOverrides.getAffectedModIds()));
            }

            // resolve mods

           // modCandidates = ModResolver.resolve(modCandidates, loader.getEnvironmentType(), envDisabledMods);

            Method dumpListM = FabricLoaderImpl.class.getDeclaredMethod("dumpModList", List.class);
            dumpListM.setAccessible(true);
            dumpListM.invoke(loader,modCandidates);

            modCandidates = modCandidates.stream().filter(m->!loader.isModLoaded(m.getMetadata().getId())).collect(Collectors.toList());

            Path cacheDir = loader.getGameDir().resolve(".fabric");
            Path outputdir = cacheDir.resolve("processedMods");

            // runtime mod remapping

            if (remapRegularMods) {
                if (System.getProperty(SystemProperties.REMAP_CLASSPATH_FILE) == null) {
                    Log.warn(LogCategory.MOD_REMAP, "Runtime mod remapping disabled due to no fabric.remapClasspathFile being specified. You may need to update loom.");
                } else {
                    RuntimeModRemapper.remap(modCandidates, cacheDir.resolve("tmp"), outputdir);
                }
            }

            //modify ArrayList(force)

            Instrumentation instrumentation = AgentUtils.injectTmpAgent();
            ClassFileTransformer transformer = new ArrayListTransformer();
            instrumentation.addTransformer(transformer,true);
            instrumentation.retransformClasses(Class.forName("java.util.ArrayList$Itr"));
            instrumentation.removeTransformer(transformer);

            Method addModM = FabricLoaderImpl.class.getDeclaredMethod("addMod", ModCandidateImpl.class);
            addModM.setAccessible(true);

            for (ModCandidateImpl mod : modCandidates) {
                if (!mod.hasPath() && !mod.isBuiltin()) {
                    try {
                        mod.setPaths(Collections.singletonList(mod.copyToDir(outputdir, false)));
                    } catch (IOException e) {
                        throw new RuntimeException("Error extracting mod "+mod, e);
                    }
                }

                addModM.invoke(loader,mod);

                addIntoPath(loader,mod);
            }



            Log.info(LogCategory.DISCOVERY,"CFMSf Language Adapter End!");
        } catch (Throwable e) {
            Log.error(LogCategory.DISCOVERY,"Failed to load CFMSf.",e);
        } finally {
            try {
               setFreezeValue(loader,true);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                Log.error(LogCategory.LOG,"Failed to freeze fabric loader!",e);
            }
        }
    }

    private static void addIntoPath(FabricLoaderImpl loader, ModCandidateImpl mod) {
        ModContainerImpl container = getMod(loader,mod);
        if (!container.getMetadata().getId().equals(FabricLoaderImpl.MOD_ID) && !container.getMetadata().getType().equals("builtin")) {
            for (Path path : container.getCodeSourcePaths()) {
                FabricLauncherBase.getLauncher().addToClassPath(path);
            }
        }
    }

    public static ModContainerImpl getMod(FabricLoaderImpl loader,ModCandidateImpl candidate){
        return loader.getModsInternal().stream().filter(f->f.getMetadata().getId().equals(candidate.getMetadata().getId())).findFirst().orElseThrow();
    }

    private static Path getModsDirectory(FabricLoader loader) {
        String directory = System.getProperty(SystemProperties.MODS_FOLDER);
        return directory != null ? Paths.get(directory) : loader.getGameDir().resolve("mods");
    }

    public static void setFreezeValue(FabricLoader loader,boolean value) throws NoSuchFieldException, IllegalAccessException {
        Field freezeF = FabricLoaderImpl.class.getDeclaredField("frozen");
        freezeF.setAccessible(true);
        freezeF.set(loader,value);
    }
}
