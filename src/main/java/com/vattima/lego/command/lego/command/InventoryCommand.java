package com.vattima.lego.command.lego.command;

import com.bricklink.api.rest.client.BricklinkRestClient;
import com.vattima.bricklink.inventory.service.InventoryService;
import com.vattima.bricklink.inventory.service.SaleItemDescriptionBuilder;
import com.vattima.lego.imaging.model.AlbumManifest;
import com.vattima.lego.imaging.service.AlbumManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.bricklink.data.lego.dao.BricklinkInventoryDao;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static picocli.CommandLine.Command;

@Slf4j
@Command(name = "inventory", aliases = {"inv"}, subcommands = {InventoryCommand.SynchronizeCommand.class})
@Getter
@Setter
@Component
@RequiredArgsConstructor
public class InventoryCommand implements Runnable {
    private final AlbumManager albumManager;
    private final InventoryService inventoryService;
    private final BricklinkInventoryDao bricklinkInventoryDao;
    private final SaleItemDescriptionBuilder saleItemDescriptionBuilder;

    @CommandLine.Option(names = "--path", descriptionKey = "path")
    Path path;

    @Override
    public void run() {
        log.info("InventoryCommand");
    }


    @Command(name = "sync", aliases = {"--sync"}, description = "Synchronizes Bricklink Inventory with Bricklink")
    static class SynchronizeCommand implements Runnable {

        @CommandLine.ParentCommand
        InventoryCommand parent;

        @Override
        public void run() {
            log.info("SynchronizeCommand");
            BricklinkInventoryDao bricklinkInventoryDao = parent.getBricklinkInventoryDao();
            SaleItemDescriptionBuilder saleItemDescriptionBuilder = parent.getSaleItemDescriptionBuilder();
            InventoryService inventoryService = parent.getInventoryService();
            bricklinkInventoryDao.getInventoryWork(true).parallelStream().filter(bi -> ((Optional.ofNullable(bi.getBoxConditionId()).map(c -> (c > 0)).orElse(false)) && (Optional.ofNullable(bi.getInstructionsConditionId()).map(c -> (c > 0)).orElse(false)))).limit(5).forEach(bi -> {
                saleItemDescriptionBuilder.buildDescription(bi);
                bricklinkInventoryDao.update(bi);
                inventoryService.synchronize(bi);
            });

        }
    }
}
