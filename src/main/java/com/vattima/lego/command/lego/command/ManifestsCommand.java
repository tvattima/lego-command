package com.vattima.lego.command.lego.command;

import com.vattima.lego.imaging.model.AlbumManifest;
import com.vattima.lego.imaging.service.AlbumManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.stream.Stream;

import static picocli.CommandLine.Command;

@Slf4j
@Command(name = "manifests", aliases = {"man"}, subcommands = {ManifestsCommand.FindCommand.class})
@Getter
@Setter
@Component
@RequiredArgsConstructor
public class ManifestsCommand implements Runnable {
    private final AlbumManager albumManager;

    @CommandLine.Option(names = "--path", descriptionKey = "path")
    Path path;

    @Override
    public void run() {
        log.info("ManifestsCommand");
    }

    @Command(name = "method", aliases = {"m"}, description = "Method command")
    public void method(@CommandLine.Option(names = {"-m", "--methodname"}) String methodName) {
        log.info("Command [method], methodName=[{}]", methodName);
    }

//    @Command(name = "method", aliases = {"m"}, description = "Method command")
//    static class MethodCommand implements Runnable {
//        @CommandLine.Option(names = {"mth"})
//        private String methodName;
//
//        @Override
//        public void run() {
//            log.info("Command [method], m=[{}]", methodName);
//        }
//    }


    @Command(name = "find", aliases = {"-f", "--file"}, description = "Finds all Album Manifests in the given path.")
    static class FindCommand implements Runnable {

        @CommandLine.ParentCommand
        ManifestsCommand parent;

        @Override
        public void run() {
            log.info("ManifestsFindCommand path=[{}]", parent.getPath());
            Stream<AlbumManifest> manifests = parent.getAlbumManager()
                                                    .findManifests(parent.getPath());
            manifests.forEach(m -> {
                System.out.println("Path [" + m.getAlbumManifestFile(parent.getPath()) + "]");
                System.out.println("Title [" + m.getBlItemNumber() + " - " + m.getUuid() + "]");
                System.out.println("Description [" + m.getDescription() + "]");
                System.out.println("URL [" + m.getUrl() + "]");
                System.out.println("Photos");
                m.getPhotos()
                 .forEach(pmd -> {
                     System.out.println("\tFilename [" + pmd.getFilename() + "], PhotoId [" + pmd.getPhotoId() + "], Is Primary [" + pmd.getPrimary() + "], Keywords [" + pmd.getKeywords() + "], MD5 [" + pmd.getMd5() + "]");
                 });
                System.out.println();
            });
        }
    }

    @Command(name = "update", aliases = {"-ud", "--update"}, description = "Updates all Album Manifests in the given path, adding or replacing photos and updating the database with photo keywords.")
    static class ManifestsUpdateCommand implements Runnable {

        @Override
        public void run() {
            log.info("ManifestsUpdateCommand");
        }
    }

    @Command(name = "upload", aliases = {"-up", "--update"}, description = "Uploads all Album Manifiests found in the given path that have been updated with new or changed photos to the photo service. New Album Manifests will yield a new Album in the photo service.")
    static class ManifestsUploadCommand implements Runnable {

        @Override
        public void run() {
            log.info("ManifestsUploadCommand");
        }
    }
}
