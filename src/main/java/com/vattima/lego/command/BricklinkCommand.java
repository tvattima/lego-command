package com.vattima.lego.command;

import com.bricklink.web.api.BricklinkWebService;
import com.vattima.lego.imaging.model.AlbumManifest;
import com.vattima.lego.imaging.service.AlbumManager;
import com.vattima.lego.imaging.service.ImageScalingService;
import com.vattima.lego.inventory.pricing.BricklinkPriceCrawler;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.bricklink.data.lego.dao.BricklinkInventoryDao;
import net.bricklink.data.lego.dto.BricklinkInventory;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Optional;

@Slf4j
@CommandLine.Command(name = "bricklink", aliases = {"bl"}, subcommands = {BricklinkCommand.CrawlCommand.class, BricklinkCommand.UpdatePhotosCommand.class})
@Getter
@Setter
@Component
@RequiredArgsConstructor
public class BricklinkCommand implements Runnable {
    private final BricklinkPriceCrawler bricklinkPriceCrawler;
    private final BricklinkInventoryDao bricklinkInventoryDao;
    private final AlbumManager albumManager;
    private final BricklinkWebService bricklinkWebService;
    private ImageScalingService imageScalingService = new ImageScalingService();

    @Override
    public void run() {
        log.info("BricklinkCommand");
    }

    @CommandLine.Command(name = "crawl", aliases = {"-c"}, description = "Crawls Bricklink and updates Sale Item table with current items for sale and their prices")
    static class CrawlCommand implements Runnable {

        @CommandLine.ParentCommand
        BricklinkCommand parent;

        @Override
        public void run() {
            log.info("CrawlCommand");
            parent.bricklinkPriceCrawler.crawlPrices();
        }
    }

    @CommandLine.Command(name = "update-photos", aliases = {"-up"}, description = "Updates Bricklink Inventory items with a custom primary photo scaled down below 2MB")
    static class UpdatePhotosCommand implements Runnable {

        @CommandLine.ParentCommand
        BricklinkCommand parent;

        @Override
        public void run() {
            BricklinkInventoryDao bricklinkInventoryDao = parent.getBricklinkInventoryDao();
            AlbumManager albumManager = parent.getAlbumManager();
            ImageScalingService imageScalingService = parent.getImageScalingService();
            log.info("UpdatePhotosCommand");

            bricklinkInventoryDao.getInventoryWork()
                                 .parallelStream()
                                 .map(bi -> {
                                     AlbumManifest albumManifest = albumManager.getAlbumManifest(bi.getUuid(), bi.getBlItemNo());
                                     return new PhotoUploadHolder(bi, albumManifest);
                                 })
                                 .filter(puh -> Optional.ofNullable(puh.getBricklinkInventory()
                                                                       .getInventoryId())
                                                        .isPresent())
                                 .filter(PhotoUploadHolder::hasPrimaryPhoto)
//                                 .filter(puh -> puh.getAlbumManifest().getPrimaryPhoto().isChanged())
                                 .forEach(puh -> {
                                     uploadPrimaryPhoto(puh, imageScalingService);
                                     log.info("[{}]", puh);
                                 });
        }

        public void uploadPrimaryPhoto(final PhotoUploadHolder photoUploadHolder, final ImageScalingService imageScalingService) {
            BricklinkWebService bricklinkWebService = parent.getBricklinkWebService();
            try {
                AlbumManifest albumManifest = photoUploadHolder.getAlbumManifest();
                Path photoPath = albumManifest.getPrimaryPhoto()
                                              .getAbsolutePath();
                log.info("Scaling photo [{}]", photoPath);
                Path scaledPhotoPath = imageScalingService.scale(photoPath);
                log.info("Invoking bricklinkWebService.uploadInventoryImage({}, {})", photoUploadHolder.getBricklinkInventory()
                                                                                                       .getInventoryId(), scaledPhotoPath);
                bricklinkWebService.uploadInventoryImage(photoUploadHolder.getBricklinkInventory()
                                                                          .getInventoryId(), scaledPhotoPath);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    @Data
    @RequiredArgsConstructor
    private static class PhotoUploadHolder {
        final BricklinkInventory bricklinkInventory;
        final AlbumManifest albumManifest;

        public boolean hasPrimaryPhoto() {
            return albumManifest.hasPrimaryPhoto();
        }
    }
}
