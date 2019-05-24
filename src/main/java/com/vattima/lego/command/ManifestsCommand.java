package com.vattima.lego.command;

import com.vattima.lego.imaging.config.LegoImagingProperties;
import com.vattima.lego.imaging.model.AlbumManifest;
import com.vattima.lego.imaging.model.PhotoMetaData;
import com.vattima.lego.imaging.service.AlbumManager;
import com.vattima.lego.imaging.service.PhotoServiceUploadManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static picocli.CommandLine.Command;

@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
@Component
@Command(name = "manifests", aliases = {"man"}, subcommands = {ManifestsCommand.FindCommand.class, ManifestsCommand.ManifestsUpdateCommand.class, ManifestsCommand.ManifestsUploadCommand.class})
public class ManifestsCommand implements Runnable {
    private final AlbumManager albumManager;
    private final LegoImagingProperties legoImagingProperties;
    private final PhotoServiceUploadManager photoServiceUploadManager;

    @CommandLine.Option(names = "--path", descriptionKey = "path")
    Path path;

    @Override
    public void run() {
        log.info("ManifestsCommand");
    }

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

        @CommandLine.ParentCommand
        ManifestsCommand parent;

        @Override
        public void run() {
            log.info("ManifestsUpdateCommand");
            try {
                Files.newDirectoryStream(parent.legoImagingProperties.getRootImagesPath(), "*.jpg")
                     .forEach(p -> {
                         PhotoMetaData photoMetaData = new PhotoMetaData(p.getParent(), p.getFileName());
                         parent.albumManager.addPhoto(photoMetaData);
                     });
            } catch (IOException e) {
                log.error("[{}]", e.getMessage(), e);
            }
        }
    }

    @Command(name = "upload", aliases = {"-up", "--upload"}, description = "Uploads all Album Manifiests found in the given path that have been updated with new or changed photos to the photo service. New Album Manifests will yield a new Album in the photo service.")
    static class ManifestsUploadCommand implements Runnable {
        @CommandLine.ParentCommand
        ManifestsCommand parent;

        @Override
        public void run() {
            log.info("ManifestsUploadCommand");
            parent.photoServiceUploadManager.updateAll();
        }
    }
}
