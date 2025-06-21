SELECT
    supp_nation,
    cust_nation,
    l_year,
    SUM( volume ) AS revenue
FROM
    (
        SELECT
            n1.n_name AS supp_nation,
            n2.n_name AS cust_nation,
            EXTRACT(
                lineitem.l_shipdate,
                "year"
            ) AS l_year,
            lineitem.l_extendedprice *(
                1 - lineitem.l_discount
            ) AS volume
        FROM
            supplier
        JOIN lineitem ON
            supplier.s_suppkey = lineitem.l_suppkey
        JOIN orders ON
            orders.o_orderkey = lineitem.l_orderkey
        JOIN customer ON
            customer.c_custkey = orders.o_custkey
        JOIN nation n1 ON
            supplier.s_nationkey = n1.n_nationkey
        JOIN nation n2 ON
            customer.c_nationkey = n2.n_nationkey
        JOIN part ON
            part.p_partkey = lineitem.l_partkey
        JOIN partsupp ON
            partsupp.ps_partkey = part.p_partkey
            AND partsupp.ps_suppkey = lineitem.l_suppkey
        WHERE
            lineitem.l_shipdate >= 788918400000
            AND lineitem.l_shipdate <= 851961600000
            AND(
                (
                    n1.n_name = 'FRANCE'
                    AND n2.n_name = 'GERMANY'
                )
                OR(
                    n1.n_name = 'GERMANY'
                    AND n2.n_name = 'FRANCE'
                )
            )
            AND lr_extend_price(
                lineitem.l_quantity,
                part.p_retailprice,
                partsupp.ps_supplycost,
                partsupp.ps_availqty,
                lineitem.l_discount
            )< 30000
    )
GROUP BY
    supp_nation,
    cust_nation,
    l_year
ORDER BY
    supp_nation,
    cust_nation,
    l_year;
