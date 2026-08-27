package cn.ksmcbrigade.cfmsF;

import net.fabricmc.loader.impl.discovery.DirectoryModCandidateFinder;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class Locator extends DirectoryModCandidateFinder {

    public final Path scanPath;
    public final boolean needRemap;

    public final Path configFilePath;
    public Set<String> blackList = new HashSet<>();

    public Locator(Path path, boolean requiresRemap,Path config) throws IOException {
        super(path, requiresRemap);

        this.scanPath = path;
        this.needRemap = requiresRemap;
        this.configFilePath = config;

        if(!Files.exists(configFilePath)){
            Files.writeString(configFilePath,"disable\n.connector");
        }
        blackList.addAll(Files.readAllLines(configFilePath));
    }

    @Override
    public void findCandidates(ModCandidateConsumer out) {
        if (!Files.exists(scanPath)) {
            try {
                Files.createDirectory(scanPath);
            } catch (IOException e) {
                throw new RuntimeException("Could not create directory " + scanPath, e);
            }
        }

        if (!Files.isDirectory(scanPath)) {
            throw new RuntimeException(scanPath + " is not a directory!");
        }

        try {
            Files.walkFileTree(this.scanPath, EnumSet.of(FileVisitOption.FOLLOW_LINKS),Integer.MAX_VALUE, new SimpleFileVisitor() {

                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (blackList.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult preVisitDirectory(@NotNull Object dir0, @NotNull BasicFileAttributes attrs) throws IOException {
                    if(dir0 instanceof Path){
                        Path dir = (Path) dir0;
                        return preVisitDirectory(dir,attrs);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Object file0, @NotNull BasicFileAttributes attrs) throws IOException {
                    if(file0 instanceof Path){
                        Path file = (Path) file0;
                       return visitFile(file,attrs);
                    }
                    return FileVisitResult.CONTINUE;
                }

                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                    if (isValidFile1(file)) {
                        out.accept(file, needRemap);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Exception while searching for mods in '" + scanPath + "'!", e);
        }
    }

    boolean isValidFile1(Path path){
        if(!isValidFile0(path)) return false;
        for (int i = 0; i < path.getNameCount(); i++) {
            if (blackList.contains(path.getName(i).toString())) {
                return false;
            }
        }
        return true;
    }

    boolean isValidFile0(Path path) {
        /*
         * We only propose a file as a possible mod in the following scenarios:
         * General: Must be a jar file
         *
         * Some OSes Generate metadata so consider the following because of OSes:
         * UNIX: Exclude if file is hidden; this occurs when starting a file name with `.`
         * MacOS: Exclude hidden + startsWith "." since Mac OS names their metadata files in the form of `.mod.jar`
         */

        if (!Files.isRegularFile(path)) return false;

        try {
            if (Files.isHidden(path)) return false;
        } catch (IOException e) {
            Log.warn(LogCategory.DISCOVERY, "Error checking if file %s is hidden", path, e);
            return false;
        }

        String fileName = path.getFileName().toString();

        return fileName.endsWith(".jar") && !fileName.startsWith(".");
    }
}
