/********************************/
/* Lightroom keywords generator */
/********************************/
select bi.box_id,
       bi.box_index,
       concat('bl:', bi.bl_item_number, ', ',
              'uuid:', bi.uuid, ', ',
              'bo:', if(bi.built_once = true, 'true', 'false'), ', ',
              'sealed:', if(bi.sealed = true, 'true', 'false'), ', ',
              'bc:', ifnull(lower(bc.condition_code), if(bi.sealed = true, 'm', 'e')), ', ',
              'ic:', ifnull(lower(ic.condition_code), if(bi.sealed = true, 'm', 'e'))) lr_keywords
from bricklink_inventory bi
         left join `condition` ic on ic.condition_id = bi.instructions_condition_id
         left join `condition` bc on bc.condition_id = bi.box_condition_id
where bi.box_id in (31)
order by bi.bl_item_number;