/*
 * IGinX - the polystore system with high performance
 * Copyright (C) Tsinghua University
 * TSIGinX@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package cn.edu.tsinghua.iginx.logical.optimizer.MLPredicate;

import java.util.HashMap;
import java.util.Map;

public class StatsManagerMock {
  private final Map<String, Double> minMap;
  private final Map<String, Double> maxMap;

  private static StatsManagerMock instance;

  private StatsManagerMock() {
    minMap = new HashMap<>();
    maxMap = new HashMap<>();
    minMap.put("region.r_regionkey", 0d);
    maxMap.put("region.r_regionkey", 4d);
    minMap.put("supplier.s_suppkey", 1d);
    maxMap.put("supplier.s_suppkey", 1000d);
    minMap.put("supplier.s_nationkey", 0d);
    maxMap.put("supplier.s_nationkey", 24d);
    minMap.put("supplier.s_acctbal", -966.2d);
    maxMap.put("supplier.s_acctbal", 9993.46d);
    minMap.put("partsupp.ps_partkey", 1d);
    maxMap.put("partsupp.ps_partkey", 20000d);
    minMap.put("partsupp.ps_suppkey", 1d);
    maxMap.put("partsupp.ps_suppkey", 1000d);
    minMap.put("partsupp.ps_availqty", 1d);
    maxMap.put("partsupp.ps_availqty", 9999d);
    minMap.put("partsupp.ps_supplycost", 1.01d);
    maxMap.put("partsupp.ps_supplycost", 999.99d);
    minMap.put("orders.o_orderkey", 1d);
    maxMap.put("orders.o_orderkey", 600000d);
    minMap.put("orders.o_custkey", 1d);
    maxMap.put("orders.o_custkey", 14999d);
    minMap.put("orders.o_totalprice", 833.4d);
    maxMap.put("orders.o_totalprice", 479129.21d);
    minMap.put("orders.o_shippriority", 0d);
    maxMap.put("orders.o_shippriority", 0d);
    minMap.put("part.p_partkey", 1d);
    maxMap.put("part.p_partkey", 20000d);
    minMap.put("part.p_size", 1d);
    maxMap.put("part.p_size", 50d);
    minMap.put("part.p_retailprice", 901.0d);
    maxMap.put("part.p_retailprice", 1918.99d);
    minMap.put("nation.n_nationkey", 0d);
    maxMap.put("nation.n_nationkey", 24d);
    minMap.put("nation.n_regionkey", 0d);
    maxMap.put("nation.n_regionkey", 4d);
    minMap.put("customer.c_custkey", 1d);
    maxMap.put("customer.c_custkey", 15000d);
    minMap.put("customer.c_nationkey", 0d);
    maxMap.put("customer.c_nationkey", 24d);
    minMap.put("customer.c_acctbal", -999.95d);
    maxMap.put("customer.c_acctbal", 9999.72d);
    minMap.put("lineitem.l_orderkey", 1d);
    maxMap.put("lineitem.l_orderkey", 600000d);
    minMap.put("lineitem.l_partkey", 1d);
    maxMap.put("lineitem.l_partkey", 20000d);
    minMap.put("lineitem.l_suppkey", 1d);
    maxMap.put("lineitem.l_suppkey", 1000d);
    minMap.put("lineitem.l_linenumber", 1d);
    maxMap.put("lineitem.l_linenumber", 7d);
    minMap.put("lineitem.l_quantity", 1d);
    maxMap.put("lineitem.l_quantity", 50d);
    minMap.put("lineitem.l_extendedprice", 901.0d);
    maxMap.put("lineitem.l_extendedprice", 95949.5d);
    minMap.put("lineitem.l_discount", 0.0d);
    maxMap.put("lineitem.l_discount", 0.1d);
    minMap.put("lineitem.l_tax", 0.0d);
    maxMap.put("lineitem.l_tax", 0.08d);
  }

  public static StatsManagerMock getInstance() {
    if (instance == null) {
      instance = new StatsManagerMock();
    }
    return instance;
  }

  public double getMin(String path) {
    return minMap.get(path);
  }

  public double getMax(String path) {
    return maxMap.get(path);
  }
}
