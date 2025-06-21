SELECT
    customer.c_custkey,
    customer.c_name,
    SUM( tmp ) AS revenue,
    customer.c_acctbal,
    nation.n_name,
    customer.c_address,
    customer.c_phone,
    customer.c_comment
FROM
    (
        SELECT
            customer.c_custkey,
            customer.c_name,
            lineitem.l_extendedprice *(
                1 - lineitem.l_discount
            ) AS tmp,
            customer.c_acctbal,
            nation.n_name,
            customer.c_address,
            customer.c_phone,
            customer.c_comment
        FROM
            customer
        JOIN orders ON
            customer.c_custkey = orders.o_custkey
        JOIN lineitem ON
            lineitem.l_orderkey = orders.o_orderkey
        JOIN nation ON
            customer.c_nationkey = nation.n_nationkey
        JOIN part ON
            part.p_partkey = lineitem.l_partkey
        JOIN partsupp ON
            partsupp.ps_partkey = part.p_partkey
            AND partsupp.ps_suppkey = lineitem.l_suppkey
        WHERE
            orders.o_orderdate >= 749404800000
            AND orders.o_orderdate < 757353600000
            AND lineitem.l_returnflag = 'R'
            AND lr_extend_price(
                lineitem.l_quantity,
                part.p_retailprice,
                partsupp.ps_supplycost,
                partsupp.ps_availqty,
                lineitem.l_discount
            )< 30000
    )
GROUP BY
    customer.c_custkey,
    customer.c_name,
    customer.c_acctbal,
    customer.c_phone,
    nation.n_name,
    customer.c_address,
    customer.c_comment
ORDER BY
    revenue DESC LIMIT 20;
