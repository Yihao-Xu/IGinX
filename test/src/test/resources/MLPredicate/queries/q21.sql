SELECT
    supplier.s_name,
    COUNT( supplier.s_name ) AS numwait
FROM
    lineitem AS l1
JOIN supplier ON
    supplier.s_suppkey = l1.l_suppkey
JOIN orders ON
    orders.o_orderkey = l1.l_orderkey
JOIN nation ON
    supplier.s_nationkey = nation.n_nationkey
JOIN part ON
    part.p_partkey = l1.l_partkey
JOIN partsupp ON
    partsupp.ps_partkey = part.p_partkey
    AND partsupp.ps_suppkey = l1.l_suppkey
WHERE
    orders.o_orderstatus = 'F'
    AND l1.l_receiptdate > l1.l_commitdate
    AND EXISTS(
        SELECT
            l_orderkey
        FROM
            lineitem AS l2
        WHERE
            l2.l_orderkey = l1.l_orderkey
            AND l2.l_suppkey <> l1.l_suppkey
    )
    AND NOT EXISTS(
        SELECT
            l_orderkey
        FROM
            lineitem AS l3
        WHERE
            l3.l_orderkey = l1.l_orderkey
            AND l3.l_suppkey <> l1.l_suppkey
            AND l3.l_receiptdate > l3.l_commitdate
    )
    AND nation.n_name = 'SAUDI ARABIA'
    AND lr_extend_price(
        l1.l_quantity,
        part.p_retailprice,
        partsupp.ps_supplycost,
        partsupp.ps_availqty,
        l1.l_discount
    )< 30000
GROUP BY
    supplier.s_name
ORDER BY
    numwait DESC,
    supplier.s_name LIMIT 100;
