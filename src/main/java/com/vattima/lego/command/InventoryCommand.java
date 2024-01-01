package com.vattima.lego.command;

import com.bricklink.api.rest.client.BricklinkRestClient;
import com.bricklink.api.rest.client.ParamsBuilder;
import com.bricklink.api.rest.model.v1.BricklinkResource;
import com.bricklink.api.rest.model.v1.Item;
import com.bricklink.api.rest.model.v1.Order;
import com.bricklink.web.support.BricklinkWebServiceImpl;
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
    private final BricklinkWebServiceImpl bricklinkWebService;
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
            //itemsToInclude.add("3e54c082348925603b06845060c9c80d");

            BricklinkInventoryDao bricklinkInventoryDao = parent.getBricklinkInventoryDao();
            PriceCalculatorService priceCalculatorService = parent.getPriceCalculatorService();
            DoubleAdder value = new DoubleAdder();
            AtomicInteger count = new AtomicInteger();
            bricklinkInventoryDao.getAll()
                                 .parallelStream()
                                 .filter(bi -> ((itemsToInclude.size() == 0) || (itemsToInclude.contains(bi.getUuid()) || itemsToInclude.contains(bi.getBlItemNo()))))
                                 .forEach(bi -> {
                                     count.incrementAndGet();
                                     if (Optional.ofNullable(bi.getOrderId())
                                                 .isPresent()) {
                                         log.debug("{} - Inventory Item is on order [{}] - skipping price calculation", logMessage(bi), bi.getOrderId());
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
                                             // Message will already be logged, no need to log a second message --> log.warn("{} - could not compute price", logMessage(bi));
                                         } else if (Optional.ofNullable(bi.getFixedPrice())
                                                            .orElse(false)) {
                                             log.warn("{} - has fixed price [{}] - not using computed price [{}]", logMessage(bi), bi.getUnitPrice(), price);
                                         } else {
                                             logPriceIfChangingMoreThan(bi, price, 5.0d);
                                             bricklinkInventoryDao.setPrice(bi.getBlInventoryId(), price);
                                         }
                                     }
                                 });
            log.info("Final cumulative value of [{}] items for sale = [{}]", count.get(), value.doubleValue());
        }

        private void logPriceIfChangingMoreThan(BricklinkInventory bi, double price, double delta) {
            if ((bi.getForSale()) & (Math.abs(bi.getUnitPrice() - price) > delta)) {
                log.info("%s - New Price: [%7.2f]".formatted(logMessage(bi), price));
            }
        }
    }

    private static String logMessage(BricklinkInventory bi) {
        return "[Box[%s] %s %s %s %s :: Price:[%7.2f] ]".formatted(bi.getBoxId(), bi.getNewOrUsed(), bi.getCompleteness(), bi.getBlItemNo(), bi.getItemName(), bi.getUnitPrice());
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
            BricklinkWebServiceImpl bricklinkWebService = parent.getBricklinkWebService();
            SaleItemDescriptionBuilder saleItemDescriptionBuilder = parent.getSaleItemDescriptionBuilder();
            InventoryService inventoryService = parent.getInventoryService();
            ImageScalingService imageScalingService = parent.getImageScalingService();
            BricklinkRestClient bricklinkRestClient = parent.getBricklinkRestClient();
            PhotosInterface photosInterface = parent.photosInterface;

            bricklinkWebService.authenticate();

            // Get all orders and update Bricklink Inventory
            BricklinkResource<List<Order>> filedOrders = bricklinkRestClient.getOrders(new ParamsBuilder().of("direction", "in")
                                                                                                          .of("filed", true)
                                                                                                          .get());
            BricklinkResource<List<Order>> notFiledOrders = bricklinkRestClient.getOrders(new ParamsBuilder().of("direction", "in")
                                                                                                             .of("filed", false)
                                                                                                             .get());
            Stream<Order> allOrdersStream = Stream.concat(filedOrders.getData()
                                                                     .stream(), notFiledOrders.getData()
                                                                                              .stream());
//            allOrdersStream.filter(o -> !o.getStatus()
//                                          .equalsIgnoreCase("CANCELLED"))
//                           .forEach(o -> {
//                inventoryService.updateInventoryItemsOnOrder(o.getOrder_id());
//            });

            try {
                List<String> itemsToInclude = new ArrayList<>();
                itemsToInclude.add("5d233bc5bc257d7672e3f74ae24169a1");
                itemsToInclude.add("a6b795d97c93202fd3647564fca6838f");
                itemsToInclude.add("d3ed8e8966c50d0e75eba3d417f4dc33");
                itemsToInclude.add("bc865fe2d69d73ae49b391b0c733c914");
                itemsToInclude.add("66ce3c467972532ec922c48aa57c84ee");
                itemsToInclude.add("cee8e1f1d37c5fe9ea8e17a8864607e8");
                itemsToInclude.add("99705fde39ce3314306c2a325ea5b344");
                itemsToInclude.add("816483c83a3f63dd338be305b89be608");
                itemsToInclude.add("59489570a2a4db151f64407c299185eb");
                itemsToInclude.add("f80b05815c1c3bc55db3edbf90415614");
                itemsToInclude.add("23e3659367c5afcb1b121bb95f9b1aeb");
                itemsToInclude.add("b88148fff1e7ea93fcfb15d5a4874790");


                bricklinkInventoryDao.getInventoryWork()
                                     .stream()
                                     .filter(bi -> ((itemsToInclude.size() == 0) || (itemsToInclude.contains(bi.getUuid()) || itemsToInclude.contains(bi.getBlItemNo()))))
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
                                                 Photo photo = photosInterface.getPhoto(photoMetaData.getPhotoId());
                                                 Path scaledImagePath = imageScalingService.scale(new URL(photo.getMedium800Url()));
                                                 bricklinkWebService.uploadInventoryImage(bi.getInventoryId(), scaledImagePath);
                                                 log.info("Uploaded primary photo for [{}-{}] with photo [{}]", bi.getBlItemNo(), bi.getUuid(), scaledImagePath);
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
