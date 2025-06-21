SELECT
    SUM( revenue )
FROM
    (
        SELECT
            lineitem.l_extendedprice * lineitem.l_discount AS revenue
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
            AND lineitem.l_discount >= 0.05
            AND lineitem.l_discount <= 0.07
            AND lineitem.l_quantity < 24
            AND lr_extend_price(
                lineitem.l_quantity,
                part.p_retailprice,
                partsupp.ps_supplycost,
                partsupp.ps_availqty,
                lineitem.l_discount
            )< 30000
    );
