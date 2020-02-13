package com.vattima.lego.command;

import com.bricklink.fulfillment.api.bricklink.BricklinkOrderService;
import com.bricklink.fulfillment.api.shipstation.OrdersAPI;
import com.bricklink.fulfillment.api.shipstation.ShipStationOrderService;
import com.bricklink.fulfillment.mapper.BricklinkToShipstationMapper;
import com.bricklink.fulfillment.shipstation.model.ShipStationOrder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;

@Slf4j
@CommandLine.Command(name = "fulfillment", aliases = {"ff"}, subcommands = {FulfillmentCommand.PullOrdersCommand.class})
@Getter
@Setter
@Component
@RequiredArgsConstructor
public class FulfillmentCommand implements Runnable {
    private final OrdersAPI ordersAPI;
    private final BricklinkOrderService bricklinkOrderService;
    private final ShipStationOrderService shipStationOrderService;
    private final BricklinkToShipstationMapper mapper;

    @Override
    public void run() {
        log.info("FulfillmentCommand");
    }

    @CommandLine.Command(name = "pull-order", aliases = {"-po"}, description = "Pulls a Bricklink order and creates or updates it in ShipStation")
    static class PullOrdersCommand implements Runnable {

        @CommandLine.ParentCommand
        FulfillmentCommand parent;

        @Override
        public void run() {
            OrdersAPI ordersAPI = parent.ordersAPI;
            BricklinkOrderService bricklinkOrderService = parent.bricklinkOrderService;
            ShipStationOrderService shipStationOrderService = parent.shipStationOrderService;
            BricklinkToShipstationMapper mapper = parent.mapper;

            log.info("Pull-Order Command");

            // Get all PENDING / UPDATED Bricklink orders
            List<com.bricklink.api.rest.model.v1.Order> orders = bricklinkOrderService.getOrdersForFulfillment();

            log.info("orders [{}]", orders.size());
            orders.forEach(o -> {
                com.bricklink.api.rest.model.v1.Order order = bricklinkOrderService.getOrder(o.getOrder_id());
                ShipStationOrder shipStationOrder = shipStationOrderService.getOrder(o.getOrder_id());
                mapper.mapBricklinkOrderToShipStationOrder(order, shipStationOrder);
                bricklinkOrderService.getOrderItems(o.getOrder_id())
                            .forEach(blOrderItem -> {
                                mapper.addOrderItem(blOrderItem, shipStationOrder);
                            });
                if (bricklinkOrderService.isInternational(order)) {
                    shipStationOrder.createInternationalOptions();
                }
                ShipStationOrder shipStationOrderFinal = ordersAPI.createOrUpdateOrder(shipStationOrder);
                log.info("Created/Updated Shipstation Order [{}]", shipStationOrderFinal);
            });
        }
    }
}
