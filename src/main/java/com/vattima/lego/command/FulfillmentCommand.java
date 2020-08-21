package com.vattima.lego.command;

import com.bricklink.api.rest.model.v1.Shipping;
import com.bricklink.fulfillment.api.bricklink.BricklinkOrderService;
import com.bricklink.fulfillment.api.shipstation.OrdersAPI;
import com.bricklink.fulfillment.api.shipstation.ShipStationOrderService;
import com.bricklink.fulfillment.api.shipstation.ShipmentsAPI;
import com.bricklink.fulfillment.mapper.BricklinkToShipstationMapper;
import com.bricklink.fulfillment.shipstation.model.ShipStationOrder;
import com.bricklink.fulfillment.shipstation.model.Tracking;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;
import java.util.Optional;

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
    private final ShipmentsAPI shipmentsAPI;
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
            ShipmentsAPI shipmentsAPI = parent.shipmentsAPI;
            BricklinkToShipstationMapper mapper = parent.mapper;

            log.info("Pull-Order Command");

            // Get all PENDING / UPDATED Bricklink orders
            List<com.bricklink.api.rest.model.v1.Order> orders = bricklinkOrderService.getOrdersForFulfillment();

            log.info("orders [{}]", orders.size());
            orders.forEach(o -> {
                com.bricklink.api.rest.model.v1.Order order = bricklinkOrderService.getOrder(o.getOrder_id());
                ShipStationOrder shipStationOrder = shipStationOrderService.getOrder(o.getOrder_id());
                if (shipStationOrder.isShipped()) {
                    log.info("Order [{}] has shipped. Updating Bricklink Order with tracking information", o.getOrder_id());
                    Tracking tracking = shipStationOrderService.getOrderTracking(shipStationOrder);
                    Shipping shipping = Optional.ofNullable(o.getShipping()).orElse(new Shipping());
                    shipping.setTracking_link(tracking.getTrackingURL().toExternalForm());
                    shipping.setTracking_no(tracking.getTrackingNumber());
                    shipping.setDate_shipped(tracking.getDateShipped());
                    o.setShipping(shipping);
                    bricklinkOrderService.markShipped(o);
                    return;
                }
                mapper.mapBricklinkOrderToShipStationOrder(order, shipStationOrder);
                bricklinkOrderService.getOrderItems(o.getOrder_id())
                            .forEach(blOrderItem -> {
                                mapper.addOrderItem(blOrderItem, shipStationOrder);
                            });
                if (bricklinkOrderService.isInternational(order)) {
                    shipStationOrder.createInternationalOptions();
                }
                shipStationOrder.createInsuranceOptions();
                ShipStationOrder shipStationOrderFinal = ordersAPI.createOrUpdateOrder(shipStationOrder);
                log.info("Created/Updated Shipstation Order [{}]", shipStationOrderFinal);
            });
        }
    }
}
