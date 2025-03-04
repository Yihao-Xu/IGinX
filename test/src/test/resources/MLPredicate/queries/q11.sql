SELECT
    partsupp.ps_partkey,
    SUM( partsupp.ps_supplycost * partsupp.ps_availqty ) AS val
FROM
    partsupp
JOIN supplier ON
    partsupp.ps_suppkey = supplier.s_suppkey
JOIN nation ON
    supplier.s_nationkey = nation.n_nationkey
JOIN part ON
    partsupp.ps_partkey = part.p_partkey
WHERE
    nation.n_name = 'GERMANY'
    AND lr_extend_price(
        partsupp.ps_availqty,
        part.p_retailprice,
        partsupp.ps_supplycost,
        partsupp.ps_availqty,
        0
    )> 30000
GROUP BY
    partsupp.ps_partkey
HAVING
    SUM( partsupp.ps_supplycost * partsupp.ps_availqty )>(
        SELECT
            SUM( partsupp.ps_supplycost * partsupp.ps_availqty )* 0.0001000000
        FROM
            partsupp
        JOIN supplier ON
            partsupp.ps_suppkey = supplier.s_suppkey
        JOIN nation ON
            supplier.s_nationkey = nation.n_nationkey
        JOIN part ON
            partsupp.ps_partkey = part.p_partkey
        WHERE
            nation.n_name = 'GERMANY'
            AND lr_extend_price(
                partsupp.ps_availqty,
                part.p_retailprice,
                partsupp.ps_supplycost,
                partsupp.ps_availqty,
                0
            )> 30000
    )
ORDER BY
    val DESC;
