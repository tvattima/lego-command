package com.vattima.lego.command;

import com.bricklink.api.rest.client.BricklinkRestClient;
import com.vattima.bricklink.inventory.service.InventoryService;
import com.vattima.bricklink.inventory.service.SaleItemDescriptionBuilder;
import com.vattima.lego.imaging.config.LegoImagingProperties;
import com.vattima.lego.imaging.service.AlbumManager;
import com.vattima.lego.imaging.service.PhotoServiceUploadManager;
import com.vattima.lego.inventory.pricing.BricklinkPriceCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bricklink.data.lego.dao.BricklinkInventoryDao;
import org.fusesource.jansi.AnsiConsole;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;

@Slf4j
@EnableConfigurationProperties
@SpringBootApplication(scanBasePackages = {"net.bricklink", "com.bricklink", "com.vattima"})
public class LegoCommandsApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegoCommandsApplication.class, args);
    }

    @Component
    @RequiredArgsConstructor
    public static class CommandLineRunner implements ApplicationRunner {
        private final ManifestsCommand manifestsCommand;
        private final BricklinkCommand bricklinkCommand;
        private final InventoryCommand inventoryCommand;
        private final FulfillmentCommand fulfillmentCommand;

        @Override
        public void run(ApplicationArguments args) throws Exception {
            AnsiConsole.systemInstall();
            CommandLine commandLine = new CommandLine(new MainCommand());
            commandLine.addSubcommand("manifests", manifestsCommand);
            commandLine.addSubcommand("bricklink", bricklinkCommand);
            commandLine.addSubcommand("inventory", inventoryCommand);
            commandLine.addSubcommand("fulfillment", fulfillmentCommand);
            List<Object> result = commandLine.parseWithHandler(new CommandLine.RunAll(), args.getSourceArgs());
            log.info("parsed [{}]", result);

			AnsiConsole.systemUninstall();
		}
	}
}
