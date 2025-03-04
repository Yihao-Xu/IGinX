WITH revenue AS(
    SELECT
        lineitem.l_suppkey AS supplier_no,
        SUM( lineitem.l_extendedprice *( 1 - lineitem.l_discount )) AS total_revenue
    FROM
        lineitem
    JOIN part ON
        part.p_partkey = lineitem.l_partkey
    JOIN partsupp ON
        partsupp.ps_partkey = part.p_partkey
        AND partsupp.ps_suppkey = lineitem.l_suppkey
    WHERE
        lineitem.l_shipdate >= 820425600000
        AND lineitem.l_shipdate < 828288000000
        AND lr_extend_price(
            lineitem.l_quantity,
            part.p_retailprice,
            partsupp.ps_supplycost,
            partsupp.ps_availqty,
            lineitem.l_discount
        )> 30000
    GROUP BY
        lineitem.l_suppkey
) SELECT
    supplier.s_suppkey,
    supplier.s_name,
    supplier.s_address,
    supplier.s_phone,
    revenue.total_revenue
FROM
    supplier,
    revenue
WHERE
    supplier.s_suppkey = revenue.supplier_no
    AND revenue.total_revenue =(
        SELECT
            MAX( total_revenue )
        FROM
            revenue
    )
ORDER BY
    supplier.s_suppkey;
