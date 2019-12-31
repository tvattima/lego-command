package com.vattima.lego.command;

import com.bricklink.api.rest.client.BricklinkRestClient;
import com.bricklink.api.rest.client.ParamsBuilder;
import com.bricklink.api.rest.model.v1.BricklinkResource;
import com.bricklink.api.rest.model.v1.Order;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@CommandLine.Command(name = "fulfillment", aliases = {"ff"}, subcommands = {FulfillmentCommand.PullOrdersCommand.class})
@Getter
@Setter
@Component
@RequiredArgsConstructor
public class FulfillmentCommand implements Runnable {
    private final BricklinkRestClient bricklinkRestClient;

    @Override
    public void run() {
        log.info("FulfillmentCommand");
    }

    @CommandLine.Command(name = "pull-orders", aliases = {"-po"}, description = "Pulls Bricklink orders and creates or updates them into ShipStation")
    static class PullOrdersCommand implements Runnable {

        @CommandLine.ParentCommand
        FulfillmentCommand parent;

        @Override
        public void run() {
            BricklinkRestClient bricklinkRestClient = parent.getBricklinkRestClient();
            log.info("Pull-Orders Command");

            // Get all PENDING / UPDATED Bricklink orders
            BricklinkResource<List<Order>> ordersResource = bricklinkRestClient.getOrders(new ParamsBuilder().of("direction", "in").get(), Arrays.asList("Pending"));
            List<Order> orders = ordersResource.getData();
            ordersResource = bricklinkRestClient.getOrders(new ParamsBuilder().of("direction", "in").get(), Arrays.asList("Updated"));
            List<Order> updatedOrders = ordersResource.getData();
            orders.addAll(updatedOrders);
            log.info("orders [{}]", orders.size());
            orders.forEach(o -> {
                log.info("[{}]", o);
            });

//            // For each Bricklink Order
//            orders.stream().map(bo -> {
//
//            })
            //    Find ShipStation Order
            //    If found, update ShipStation Order
            //    If not found, create ShipStation Order
        }
    }
}
