package com.vattima.lego.command;

import com.bricklink.api.rest.client.BricklinkRestClient;
import com.bricklink.api.rest.client.ParamsBuilder;
import com.bricklink.api.rest.model.v1.BricklinkResource;
import com.bricklink.api.rest.model.v1.Item;
import com.bricklink.api.rest.model.v1.Order;
import com.bricklink.web.support.BricklinkSession;
import com.bricklink.web.support.BricklinkWebService;
import com.flickr4java.flickr.photos.Photo;
import com.flickr4java.flickr.photos.PhotosInterface;
import com.vattima.bricklink.inventory.service.InventoryService;
import com.vattima.bricklink.inventory.service.PriceCalculatorService;
import com.vattima.bricklink.inventory.service.SaleItemDescriptionBuilder;
import com.vattima.bricklink.inventory.support.SynchronizeResult;
import com.vattima.lego.imaging.model.AlbumManifest;
import com.vattima.lego.imaging.model.PhotoMetaData;
import com.vattima.lego.imaging.service.AlbumManager;
import com.vattima.lego.imaging.service.ImageScalingService;
import com.vattima.lego.inventory.pricing.PriceNotCalculableException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.bricklink.data.lego.dao.BricklinkInventoryDao;
import net.bricklink.data.lego.dao.BricklinkSaleItemDao;
import net.bricklink.data.lego.dto.BricklinkInventory;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.Stream;

@Slf4j
@Getter
@Setter
@RequiredArgsConstructor
@Component
@CommandLine.Command(name = "inventory", aliases = {"inventory"},
        subcommands = {InventoryCommand.SetNotForSaleCommand.class,
                InventoryCommand.SynchronizeCommand.class,
                InventoryCommand.PriceCalculatorCommand.class})
public class InventoryCommand implements Runnable {
    private final PriceCalculatorService priceCalculatorService;
    private final BricklinkInventoryDao bricklinkInventoryDao;
    private final BricklinkSaleItemDao bricklinkSaleItemDao;
    private final BricklinkRestClient bricklinkRestClient;
    private final InventoryService inventoryService;
    private final SaleItemDescriptionBuilder saleItemDescriptionBuilder;
    private final AlbumManager albumManager;
    private final BricklinkWebService bricklinkWebService;
    private final ImageScalingService imageScalingService = new ImageScalingService();
    private final PhotosInterface photosInterface;

    @Override
    public void run() {
        log.info("InventoryCommand");
    }

    @CommandLine.Command(name = "price-calculator", aliases = {"-pc"}, description = "Computes and updates Prices of all items")
    static class PriceCalculatorCommand implements Runnable {
        @CommandLine.ParentCommand
        InventoryCommand parent;

        @Override
        public void run() {
            List<String> itemsToInclude = new ArrayList<>();
//            itemsToInclude.add("0908b5bffadf0958be2f036eb10ac35a");

            BricklinkInventoryDao bricklinkInventoryDao = parent.getBricklinkInventoryDao();
            PriceCalculatorService priceCalculatorService = parent.getPriceCalculatorService();
            DoubleAdder value = new DoubleAdder();
            AtomicInteger count = new AtomicInteger();
            bricklinkInventoryDao.getAll()
                                 .parallelStream()
                                 .filter(bi -> ((itemsToInclude.size() == 0) || (itemsToInclude.contains(bi.getUuid()) || itemsToInclude.contains(bi.getBlItemNo()))))
                                 .forEach(bi -> {
                count.incrementAndGet();
                if (Optional.ofNullable(bi.getOrderId()).isPresent()) {
                    log.warn("{} - Inventory Item is on order [{}] - skipping price calculation", logMessage(bi), bi.getOrderId());
                } else {
                    double price = Double.NaN;
                    try {
                        price = priceCalculatorService.calculatePrice(bi);
                        //value.addAndGet(bi.getUnitPrice());
                        value.add(bi.getUnitPrice());
                    } catch (PriceNotCalculableException e) {
                        log.error("{} - Error [{}]", logMessage(bi), e.getMessage());
                    }
                    if (Double.isNaN(price)) {
                        log.warn("{} - could not compute price", logMessage(bi));
                    } else if (Optional.ofNullable(bi.getFixedPrice())
                                       .orElse(false)) {
                        log.warn("{} - has fixed price [{}] - not using computed price [{}]", logMessage(bi), bi.getUnitPrice(), price);
                    } else {
                        log.info("{} - ${}", logMessage(bi), price);
                        bricklinkInventoryDao.setPrice(bi.getBlInventoryId(), price);
                    }
                }
            });
            log.info("Final cumulative value of [{}] items for sale = [{}]", count.get(), value.doubleValue());
        }
    }

    private static String logMessage(BricklinkInventory bi) {
        return String.format("[Box[%s] %s %s %s %s]", bi.getBoxId(), bi.getNewOrUsed(), bi.getCompleteness(), bi.getBlItemNo(), bi.getItemName());
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
            BricklinkRestClient bricklinkRestClient = parent.getBricklinkRestClient();
            PhotosInterface photosInterface = parent.photosInterface;

            BricklinkSession bricklinkSession = bricklinkWebService.authenticate();

            // Get all orders and update Bricklink Inventory
            BricklinkResource<List<Order>> filedOrders = bricklinkRestClient.getOrders(new ParamsBuilder().of("direction", "in").of("filed", true).get());
            BricklinkResource<List<Order>> notFiledOrders = bricklinkRestClient.getOrders(new ParamsBuilder().of("direction", "in").of("filed", false).get());
            Stream<Order> allOrdersStream = Stream.concat(filedOrders.getData().stream(), notFiledOrders.getData().stream());
            allOrdersStream.filter(o -> !o.getStatus()
                                          .equalsIgnoreCase("CANCELLED"))
                           .forEach(o -> {
                inventoryService.updateInventoryItemsOnOrder(o.getOrder_id());
            });

            try {
                List<String> itemsToInclude = new ArrayList<>();
                //itemsToInclude.add("120fdd3d0f580d9e72f5aaffd966d7bf");

                bricklinkInventoryDao.getInventoryWork(true)
                                     .parallelStream()
                                     .filter(bi -> ((itemsToInclude.size() == 0) || (itemsToInclude.contains(bi.getUuid()) || itemsToInclude.contains(bi.getBlItemNo()))))
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
                                         boolean canBeAvailableForSale = false;
                                         try {
                                             AlbumManifest albumManifest = albumManager.getAlbumManifest(bi.getUuid(), bi.getBlItemNo());
                                             if (albumManifest.hasPrimaryPhoto()) {
                                                 PhotoMetaData photoMetaData = albumManifest.getPrimaryPhoto();
                                                 if (photoMetaData.isChanged()) {
                                                     Photo photo = photosInterface.getPhoto(photoMetaData.getPhotoId());
                                                     Path scaledImagePath = imageScalingService.scale(new URL(photo.getMedium800Url()));
                                                     bricklinkWebService.uploadInventoryImage(bi.getInventoryId(), scaledImagePath);
                                                     log.info("Uploaded primary photo for [{}-{}] with photo [{}]", bi.getBlItemNo(), bi.getUuid(), scaledImagePath);
                                                 } else {
                                                     log.info("Primary photo for [{}-{}] not changed - no scaling or upload needed", bi.getBlItemNo(), bi.getUuid());
                                                 }
                                             } else {
                                                 log.warn("No primary photo specified for [{}-{}]", bi.getBlItemNo(), bi.getUuid());
                                             }
                                             canBeAvailableForSale = bi.canBeAvailableForSale() && albumManifest.hasPrimaryPhoto();
                                         } catch (Exception e) {
                                             log.error(e.getMessage(), e);
                                         }

                                         log.info("Starting to synchronize [{}-{}]", bi.getBlItemNo(), bi.getUuid());
                                         if (canBeAvailableForSale) {
                                             log.info("[{}-{}] is available for sale", bi.getBlItemNo(), bi.getUuid());
                                         } else {
                                             log.warn("[{}-{}] is not available for sale", bi.getBlItemNo(), bi.getUuid());
                                         }
                                         bi.setIsStockRoom(!canBeAvailableForSale);
                                         bricklinkInventoryDao.update(bi);
                                         Optional<SynchronizeResult> result = Optional.ofNullable(inventoryService.synchronize(bi));
                                         result.ifPresent(r -> {
                                             if (!r.isSuccess()) {
                                                 log.error("Error synchronizing [{}-{}] - code [{}], message [{}], description [{}]", bi.getBlItemNo(), bi.getUuid(), r.getCode(), r.getMessage(), r.getDescription());
                                             }
                                         });
                                         Optional.ofNullable(bi.getExtendedDescription())
                                                 .ifPresent(ed -> {
                                                     bricklinkWebService.updateExtendedDescription(bi.getInventoryId(), ed);
                                                 });
                                         bricklinkWebService.updateInventoryCondition(bi.getInventoryId(), bi.getNewOrUsed(), bi.getCompleteness());
                                         log.info("Completed synchronization of [{}-{}]", bi.getBlItemNo(), bi.getUuid());
                                     });
            } finally {
                bricklinkWebService.logout();
            }

        }
    }
}
