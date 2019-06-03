package com.vattima.lego.command;

import com.bricklink.api.rest.client.BricklinkRestClient;
import com.bricklink.api.rest.model.v1.BricklinkResource;
import com.bricklink.api.rest.model.v1.Item;
import com.bricklink.web.support.BricklinkSession;
import com.bricklink.web.support.BricklinkWebService;
import com.vattima.bricklink.inventory.service.InventoryService;
import com.vattima.bricklink.inventory.service.SaleItemDescriptionBuilder;
import com.vattima.lego.imaging.model.AlbumManifest;
import com.vattima.lego.imaging.service.AlbumManager;
import com.vattima.lego.imaging.service.ImageScalingService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.bricklink.data.lego.dao.BricklinkInventoryDao;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
@Component
@CommandLine.Command(name = "inventory", aliases = {"inventory"}, subcommands = {InventoryCommand.SetNotForSaleCommand.class, InventoryCommand.SynchronizeCommand.class})
public class InventoryCommand implements Runnable {
    private final BricklinkInventoryDao bricklinkInventoryDao;
    private final BricklinkRestClient bricklinkRestClient;
    private final InventoryService inventoryService;
    private final SaleItemDescriptionBuilder saleItemDescriptionBuilder;
    private final AlbumManager albumManager;
    private final BricklinkWebService bricklinkWebService;
    private final ImageScalingService imageScalingService = new ImageScalingService();

    @Override
    public void run() {
        log.info("InventoryCommand");
    }

    @CommandLine.Command(name = "set-not-for-sale", aliases = {"-snfs"}, description = "Updates all bricklink Inventories that are in Bricklink's Train Categories to be Not for Sale")
    static class SetNotForSaleCommand implements Runnable {
        @CommandLine.ParentCommand
        InventoryCommand parent;
        private List<Integer> trainCategories = Arrays.asList(122, 124);

        @Override
        public void run() {
            log.info("Set Not For Sale Command");
            BricklinkInventoryDao bricklinkInventoryDao = parent.getBricklinkInventoryDao();
            BricklinkRestClient bricklinkRestClient = parent.getBricklinkRestClient();
            bricklinkInventoryDao.getAllForSale()
                                 .stream()
                                 .filter(bi -> {
                                     BricklinkResource<Item> item = bricklinkRestClient.getCatalogItem("SET", bi.getBlItemNo());
                                     Integer categoryId = item.getData()
                                                              .getCategory_id();
                                     return this.isATrainCategory(categoryId);
                                 })
                                 .forEach(bi -> {
                                     bricklinkInventoryDao.setNotForSale(bi.getBlInventoryId());
                                     log.info("[{}}", bi);
                                 });
        }

        boolean isATrainCategory(final Integer categoryId) {
            return trainCategories.contains(categoryId);
        }
    }

    @CommandLine.Command(name = "synchronize", aliases = {"-sync"}, description = "Synchronizes inventory database with Bricklink Inventory")
    static class SynchronizeCommand implements Runnable {

        @CommandLine.ParentCommand
        InventoryCommand parent;

        @Override
        public void run() {
            log.info("Synchronize Command");
            AlbumManager albumManager = parent.getAlbumManager();
            BricklinkInventoryDao bricklinkInventoryDao = parent.getBricklinkInventoryDao();
            BricklinkWebService bricklinkWebService = parent.getBricklinkWebService();
            SaleItemDescriptionBuilder saleItemDescriptionBuilder = parent.getSaleItemDescriptionBuilder();
            InventoryService inventoryService = parent.getInventoryService();
            ImageScalingService imageScalingService = parent.getImageScalingService();
            BricklinkSession bricklinkSession = bricklinkWebService.authenticate();
            try {
                bricklinkInventoryDao.getInventoryWork(true)
                                     .parallelStream()
                                     .filter(bi -> {
                                         AlbumManifest albumManifest = albumManager.getAlbumManifest(bi.getUuid(), bi.getBlItemNo());
                                         return albumManifest.hasPrimaryPhoto();
                                     })
                                     .peek(bi -> {
                                         saleItemDescriptionBuilder.buildDescription(bi);
                                         bricklinkInventoryDao.update(bi);
                                         log.info("Description updated for [{}-{}] to [{}]", bi.getBlItemNo(), bi.getUuid(), bi.getDescription());
                                     })
                                     .forEach(bi -> {
                                         try {
                                             AlbumManifest albumManifest = albumManager.getAlbumManifest(bi.getUuid(), bi.getBlItemNo());
                                             Path scaledImagePath = imageScalingService.scale(albumManifest.getPrimaryPhoto()
                                                                                                           .getAbsolutePath());
                                             bricklinkWebService.uploadInventoryImage(bi.getInventoryId(), scaledImagePath);
                                             log.info("Uploaded primary photo for [{}-{}] with photo [{}]", bi.getBlItemNo(), bi.getUuid(), scaledImagePath);
                                         } catch (Exception e) {
                                             log.error(e.getMessage(), e);
                                         }

                                         log.info("Starting to synchronize [{}-{}]", bi.getBlItemNo(), bi.getUuid());
                                         inventoryService.synchronize(bi);
                                         log.info("Completed synchronization of [{}-{}]", bi.getBlItemNo(), bi.getUuid());
                                     });
            } finally {
                bricklinkWebService.logout();
            }

        }
    }
}
