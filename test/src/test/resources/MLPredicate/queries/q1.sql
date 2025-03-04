SELECT
    lineitem.l_returnflag AS l_returnflag,
    lineitem.l_linestatus AS l_linestatus,
    SUM( lineitem.l_quantity ) AS sum_qty,
    SUM( lineitem.l_extendedprice ) AS sum_base_price,
    SUM( tmp1 ) AS sum_disc_price,
    SUM( tmp2 ) AS sum_charge,
    AVG( lineitem.l_quantity ) AS avg_qty,
    AVG( lineitem.l_extendedprice ) AS avg_price,
    AVG( lineitem.l_discount ) AS avg_disc,
    COUNT( lineitem.l_returnflag ) AS count_order
FROM
    (
        SELECT
            lineitem.l_returnflag,
            lineitem.l_linestatus,
            lineitem.l_quantity,
            lineitem.l_extendedprice,
            lineitem.l_discount,
            lineitem.l_extendedprice *(
                1 - lineitem.l_discount
            ) AS tmp1,
            lineitem.l_extendedprice *(
                1 - lineitem.l_discount
            )*(
                1 + lineitem.l_tax
            ) AS tmp2
        FROM
            lineitem
        JOIN part ON
            part.p_partkey = lineitem.l_partkey
        JOIN partsupp ON
            partsupp.ps_partkey = part.p_partkey
            AND partsupp.ps_suppkey = lineitem.l_suppkey
        WHERE
            lineitem.l_shipdate <= 904694400000
            AND lr_extend_price(
                lineitem.l_quantity,
                part.p_retailprice,
                partsupp.ps_supplycost,
                partsupp.ps_availqty,
                lineitem.l_discount
            )> 30000
    )
GROUP BY
    lineitem.l_returnflag,
    lineitem.l_linestatus
ORDER BY
    lineitem.l_returnflag,
    lineitem.l_linestatus;
