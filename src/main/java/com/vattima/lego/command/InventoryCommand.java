package com.vattima.lego.command;

import com.bricklink.api.rest.client.BricklinkRestClient;
import com.bricklink.api.rest.model.v1.BricklinkResource;
import com.bricklink.api.rest.model.v1.Item;
import com.vattima.bricklink.inventory.service.InventoryService;
import com.vattima.bricklink.inventory.service.SaleItemDescriptionBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.bricklink.data.lego.dao.BricklinkInventoryDao;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

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
            BricklinkInventoryDao bricklinkInventoryDao = parent.getBricklinkInventoryDao();
            SaleItemDescriptionBuilder saleItemDescriptionBuilder = parent.getSaleItemDescriptionBuilder();
            InventoryService inventoryService = parent.getInventoryService();
            bricklinkInventoryDao.getInventoryWork(true)
                                 .parallelStream()
                                 .filter(bi -> bi.getUuid().equals("234851bc94cf3b875dfd91db73a76524"))
                                 .peek(saleItemDescriptionBuilder::buildDescription)
                                 .limit(5)
                                 .peek(bricklinkInventoryDao::update)
                                 .forEach(inventoryService::synchronize);
        }
    }
}
