SELECT
    customer.c_name,
    customer.c_custkey,
    orders.o_orderkey,
    orders.o_orderdate,
    orders.o_totalprice,
    SUM( lineitem.l_quantity )
FROM
    customer
JOIN orders ON
    customer.c_custkey = orders.o_custkey
JOIN lineitem ON
    orders.o_orderkey = lineitem.l_orderkey
JOIN part ON
    part.p_partkey = lineitem.l_partkey
JOIN partsupp ON
    partsupp.ps_partkey = part.p_partkey
    AND partsupp.ps_suppkey = lineitem.l_suppkey
WHERE
    orders.o_orderkey IN(
        SELECT
            lineitem.l_orderkey
        FROM
            (
                SELECT
                    l_orderkey,
                    SUM( l_quantity )
                FROM
                    lineitem
                GROUP BY
                    l_orderkey
                HAVING
                    SUM( l_quantity )> 300
            )
    )
    AND lr_extend_price(
        lineitem.l_quantity,
        part.p_retailprice,
        partsupp.ps_supplycost,
        partsupp.ps_availqty,
        lineitem.l_discount
    )< 30000
GROUP BY
    customer.c_name,
    customer.c_custkey,
    orders.o_orderkey,
    orders.o_orderdate,
    orders.o_totalprice
ORDER BY
    orders.o_totalprice DESC LIMIT 100;
