SELECT
    lineitem.l_shipmode,
    SUM( CASE WHEN orders.o_orderpriority = '1-URGENT' OR orders.o_orderpriority = '2-HIGH' THEN 1 ELSE 0 END ) AS high_line_count,
    SUM( CASE WHEN orders.o_orderpriority <> '1-URGENT' AND orders.o_orderpriority <> '2-HIGH' THEN 1 ELSE 0 END ) AS low_line_count
FROM
    orders
JOIN lineitem ON
    orders.o_orderkey = lineitem.l_orderkey
JOIN part ON
    part.p_partkey = lineitem.l_partkey
JOIN partsupp ON
    partsupp.ps_partkey = part.p_partkey
    AND partsupp.ps_suppkey = lineitem.l_suppkey
WHERE
    (
        lineitem.l_shipmode = 'MAIL'
        OR lineitem.l_shipmode = 'SHIP'
    )
    AND lineitem.l_commitdate < lineitem.l_receiptdate
    AND lineitem.l_shipdate < lineitem.l_commitdate
    AND lineitem.l_receiptdate >= 757353600000
    AND lineitem.l_receiptdate < 788889600000
    AND lr_extend_price(
        lineitem.l_quantity,
        part.p_retailprice,
        partsupp.ps_supplycost,
        partsupp.ps_availqty,
        lineitem.l_discount
    )< 30000
GROUP BY
    lineitem.l_shipmode
ORDER BY
    lineitem.l_shipmode;
