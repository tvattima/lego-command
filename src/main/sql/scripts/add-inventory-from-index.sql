-- box_id,box_index,item_number,box_name,box_number,sealed,quantity,description
-- 25,1002,40593,Box 25,Box 25,true,1,Fun Creativity 12-in-1 --- 229400
-- 25,1003,40642,Box 25,Box 25,true,1,Gingerbread Ornaments --- 229751
-- 25,1004,60381,Box 25,Box 25,true,1,Advent Calendar 2023 --- 227986


/* Script to pull over a given box_id/box_index from inventory_index originally inventoried by Denise */
/* item must exist */
/* must know bl_item_id */
DELIMITER $$
DROP PROCEDURE IF EXISTS add_inventory_from_index $$

CREATE PROCEDURE add_inventory_from_index()
BEGIN
    START TRANSACTION;
    SET @item_number = '60381', @box_id = 25, @box_index = 1004, @bl_item_id = 227986;
    SET @item_id = null, @item_name = 'Advent Calendar 2023, City', @number_of_pieces = 0, @issue_year = 2023, @issue_location = null, @theme_id = 46, @item_type_code = 'S', @notes = null;
    SET @new_or_used = 'N', @completeness = 'C', @sealed = false, @built_once = 1, @box_condition_id = 1, @instructions_condition_id = 1, @item_type = 'SET';
    SET @forSale = false;
    SET @extra_description = @notes;
    SET @bl_item_number = concat(@item_number, '-1');
    SET @uuid = md5(concat(@box_id, @box_index, @bl_item_number));

    # get current inventory_index information
    SELECT 1,
           ii.box_name,
           ii.box_number,
           ii.sealed,
           ii.quantity,
           ii.description,
           ii.active,
           ii.moved_to_box_id
    INTO @found,
        @box_name,
        @box_number,
        @sealed,
        @quantity,
        @desc,
        @active,
        @moved_to_box_index
    FROM inventory_index ii
    WHERE ii.box_id = @box_id
      AND   ii.box_index = @box_index;
    if @found != 1 THEN
        SIGNAL SQLSTATE '99001' SET MESSAGE_TEXT = 'Could not find box_id/box_index';
    end if;

    IF isnull(@item_id) THEN
        insert into item (item_number, item_name, number_of_pieces, issue_year, issue_location, theme_id,
                          item_type_code, notes)
        select @item_number,
               @item_name,
               @number_of_pieces,
               @issue_year,
               @issue_location,
               @theme_id,
               @item_type_code,
               @notes
        FROM DUAL
        where not exists(select 1 from item item1 where item1.item_number = @item_number);
    ELSE
        BEGIN
            select @item_number = i.item_number,
                   @item_name = i.item_name,
                   @number_of_pieces = i.number_of_pieces,
                   @issue_year = i.issue_year,
                   @issue_location = i.issue_location,
                   @theme_id = i.theme_id,
                   @item_type_code = i.item_type_code,
                   @notes = i.notes
            FROM item i
            where i.item_id = @item_id;
        END;
    END IF;

    select i.item_id
    into @item_id
    from item i
    where i.item_number = @item_number
      and item_name = @item_name;

    insert into bricklink_item (item_id, bl_item_number, bl_item_id)
    select @item_id, @bl_item_number, @bl_item_id
    from item i
    where item_number = @item_number
      and not exists(select 1 from bricklink_item bli where bli.item_id = @item_id);

    insert into bricklink_inventory (uuid, box_id, box_index, bl_item_number, inventory_id, item_type, color_id,
                                     color_name,
                                     quantity, new_or_used, completeness, unit_price, bind_id, description, remarks,
                                     bulk,
                                     is_retain, is_stock_room, stock_room_id, my_cost, sale_rate, tier_quantity1,
                                     tier_quantity2, tier_quantity3, tier_price1, tier_price2, tier_price3, my_weight,
                                     sealed, order_id, fixed_price, for_sale, built_once, box_condition_id,
                                     instructions_condition_id, internal_comments, extended_description,
                                     extra_description)
    select @uuid                      uuid,
           @box_id                    box_id,
           @box_index                 box_index,
           @bl_item_number,
           null                       inventory_id,
           @item_type                 item_type,
           0                          color_id,
           null                       color_name,
           1                          quantity,
           @new_or_used               new_or_used,
           @completeness              completeness,
           0.00                       unit_price,
           null                       bind_id,
           'Contact me for photos!'   description,
           @uuid                      remarks,
           1                          bulk,
           1                          is_retain,
           1                          is_stock_room,
           'A'                        stock_room_id,
           0.00                       my_cost,
           null                       sale_rate,
           null                       tier_quantity1,
           null                       tier_quantity2,
           null                       tier_quantity3,
           null                       tier_price1,
           null                       tier_price2,
           null                       tier_price3,
           null                       my_weight,
           if(@sealed,1,0)            sealed,
           null                       order_id,
           0                          fixed_price,
           if(@forSale,1,0)           for_sale,
           @built_once                built_once,
           @box_condition_id          box_condition_id,
           @instructions_condition_id instructions_condition_id,
           null                       internal_comments,
           null                       extended_description,
           @extra_description         extra_description
    from inventory_index ii
    where ii.box_id = @box_id
      and ii.box_index = @box_index
      and not exists(select 1 from bricklink_inventory bi where bi.box_id = @box_id and bi.box_index = @box_index);

    select *
    from bricklink_inventory bi
    where bi.box_id = @box_id
      and bi.box_index = @box_index;

    commit;

END $$

DELIMITER ;

CALL add_inventory_from_index();