SELECT
    o_orderpriority,
    COUNT( o_orderkey ) AS order_count
FROM
    orders
WHERE
    o_orderdate >= 741456000000
    AND o_orderdate < 749404800000
    AND EXISTS(
        SELECT
            l_orderkey
        FROM
            lineitem
        JOIN part ON
            part.p_partkey = lineitem.l_partkey
        JOIN partsupp ON
            partsupp.ps_partkey = part.p_partkey
            AND partsupp.ps_suppkey = lineitem.l_suppkey
        WHERE
            lineitem.l_orderkey = orders.o_orderkey
            AND lineitem.l_commitdate < lineitem.l_receiptdate
            AND lr_extend_price(
                lineitem.l_quantity,
                part.p_retailprice,
                partsupp.ps_supplycost,
                partsupp.ps_availqty,
                lineitem.l_discount
            )< 30000
    )
GROUP BY
    o_orderpriority
ORDER BY
    o_orderpriority;
