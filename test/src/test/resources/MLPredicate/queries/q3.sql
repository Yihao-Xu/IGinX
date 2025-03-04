SELECT
    l_orderkey,
    SUM( tmp ) AS revenue,
    o_orderdate,
    o_shippriority
FROM
    (
        SELECT
            lineitem.l_extendedprice *(
                1 - lineitem.l_discount
            ) AS tmp,
            lineitem.l_orderkey AS l_orderkey,
            orders.o_orderdate AS o_orderdate,
            orders.o_shippriority AS o_shippriority
        FROM
            customer
        JOIN orders ON
            customer.c_custkey = orders.o_custkey
        JOIN lineitem ON
            lineitem.l_orderkey = orders.o_orderkey
        JOIN part ON
            part.p_partkey = lineitem.l_partkey
        JOIN partsupp ON
            partsupp.ps_partkey = part.p_partkey
            AND partsupp.ps_suppkey = lineitem.l_suppkey
        WHERE
            customer.c_mktsegment = 'BUILDING'
            AND orders.o_orderdate < 795196800000
            AND lineitem.l_shipdate > 795225600000
            AND lr_extend_price(
                lineitem.l_quantity,
                part.p_retailprice,
                partsupp.ps_supplycost,
                partsupp.ps_availqty,
                lineitem.l_discount
            )> 30000
    ) AS subquery
GROUP BY
    l_orderkey,
    o_orderdate,
    o_shippriority
ORDER BY
    revenue DESC LIMIT 10;
