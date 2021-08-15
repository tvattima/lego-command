/* Script to pull over a given box_id/box_index from inventory_index originally inventoried by Denise */
/* item must exist */
/* must know bl_item_id */
DELIMITER $$
DROP PROCEDURE IF EXISTS move_inventory_item $$

CREATE PROCEDURE move_inventory_item(
 IN old_box_id INT,
 IN old_box_index INT,
 IN new_box_id INT,
 IN item_number VARCHAR(10)
)
BEGIN
    START TRANSACTION;
    SET @found = 0;
    SET @current_index_id = 0;

    # make sure item_number does not already exist in new_box_id
    SELECT 1,
           ii.box_index
    INTO @found,
         @current_box_index_id
    FROM inventory_index ii
    WHERE ii.box_id = new_box_id
    AND   ii.item_number = item_number;
    if @found = 1 THEN
        SET @msg = CONCAT('Item [',item_number,'] already exists in box [',new_box_id,'] with index [',@current_box_index_id,']');
        SIGNAL SQLSTATE '99001' SET MESSAGE_TEXT = @msg;
    end if;

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
    WHERE ii.box_id = old_box_id
    AND   ii.box_index = old_box_index
    AND   ii.item_number = item_number;
    if @found != 1 THEN
        SIGNAL SQLSTATE '99001' SET MESSAGE_TEXT = 'Could not find old box index!';
    end if;

    if @active = 0 THEN
        SET @msg = CONCAT('Item [',item_number,'] is not active in box [',old_box_id,'] index [',old_box_index,'] - moved to box [',@moved_to_box_index,']');
        SIGNAL SQLSTATE '99001' SET MESSAGE_TEXT = @msg;
    end if;

    # update current inventory index that set was moved
    UPDATE inventory_index ii
    SET    ii.active = false,
           ii.moved_to_box_id = new_box_id
    WHERE ii.box_id = old_box_id
      AND ii.box_index = old_box_index
      AND ii.item_number = item_number;

    SELECT max(box_index) + 1 INTO @next_box_index
    FROM inventory_index ii
    WHERE ii.box_id = new_box_id;

    IF (@next_box_index < 1000) THEN
        SET @next_box_index = 1000;
    END IF;

    INSERT INTO inventory_index (box_id, box_index, item_number, box_name, box_number, sealed, quantity, description, active)
    SELECT new_box_id,
           @next_box_index,
           item_number,
           concat('Box ', new_box_id),
           new_box_id,
           @sealed,
           @quantity,
           @desc,
           1
    FROM DUAL
    WHERE NOT EXISTS (
        SELECT 1 FROM inventory_index ii
        WHERE ii.box_id = new_box_id
        AND   ii.box_index = @next_box_index
        AND   ii.item_number = item_number);

    # update bricklink inventory that set was moved
    UPDATE  bricklink_inventory bi
    SET     bi.box_id = new_box_id,
            bi.box_index = @next_box_index
    WHERE   bi.box_id = old_box_id
    AND     bi.box_index = old_box_index;

    commit;

END $$

DELIMITER ;

-- old_box_id
-- old_box_index
-- new_box_id
-- item_number from inventory_index table
CALL move_inventory_item(27, 16, 25, '1254');