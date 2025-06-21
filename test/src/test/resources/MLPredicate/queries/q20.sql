WITH tmpTableA AS(
    SELECT
        partkey,
        suppkey,
        0.5 * tmp AS val
    FROM
        (
            SELECT
                lineitem.l_partkey AS partkey,
                lineitem.l_suppkey AS suppkey,
                SUM( lineitem.l_quantity ) AS tmp
            FROM
                lineitem
            JOIN part ON
                part.p_partkey = lineitem.l_partkey
            JOIN partsupp ON
                partsupp.ps_partkey = part.p_partkey
                AND partsupp.ps_suppkey = lineitem.l_suppkey
            WHERE
                lineitem.l_shipdate >= 757353600000
                AND lineitem.l_shipdate < 788889600000
                AND lr_extend_price(
                    lineitem.l_quantity,
                    part.p_retailprice,
                    partsupp.ps_supplycost,
                    partsupp.ps_availqty,
                    lineitem.l_discount
                )< 30000
            GROUP BY
                lineitem.l_partkey,
                lineitem.l_suppkey
        )
) SELECT
    supplier.s_name,
    supplier.s_address
FROM
    supplier
JOIN nation ON
    supplier.s_nationkey = nation.n_nationkey
WHERE
    supplier.s_suppkey IN(
        SELECT
            partsupp.ps_suppkey
        FROM
            partsupp
        JOIN tmpTableA ON
            tmpTableA.suppkey = partsupp.ps_suppkey
            AND tmpTableA.partkey = partsupp.ps_partkey
        WHERE
            partsupp.ps_partkey IN(
                SELECT
                    p_partkey
                FROM
                    part
                WHERE
                    part.p_name LIKE 'forest.*'
            )
            AND partsupp.ps_availqty > tmpTableA.val
    )
    AND nation.n_name = 'CANADA'
ORDER BY
    supplier.s_name;