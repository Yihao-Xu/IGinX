SELECT
    100.00 * SUM( CASE WHEN part.p_type LIKE 'PROMO.*' THEN lineitem.l_extendedprice *( 1 - lineitem.l_discount ) ELSE 0.0 END )/ SUM( lineitem.l_extendedprice *( 1 - lineitem.l_discount )) AS promo_revenue
FROM
    lineitem
JOIN part ON
    lineitem.l_partkey = part.p_partkey
JOIN partsupp ON
    partsupp.ps_partkey = part.p_partkey
    AND partsupp.ps_suppkey = lineitem.l_suppkey
WHERE
    lineitem.l_shipdate >= 809884800000
    AND lineitem.l_shipdate < 812476800000
    AND lr_extend_price(
        lineitem.l_quantity,
        part.p_retailprice,
        partsupp.ps_supplycost,
        partsupp.ps_availqty,
        lineitem.l_discount
    )> 30000;
