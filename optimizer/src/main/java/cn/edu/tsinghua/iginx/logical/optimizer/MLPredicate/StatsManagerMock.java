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

    minMap.put("us.d1.s1", 0d);
    maxMap.put("us.d1.s1", 15000d);

    minMap.put("us.d1.s2", 1d);
    maxMap.put("us.d1.s2", 15001d);

    minMap.put("us.d1.s4", 0.1d);
    maxMap.put("us.d1.s4", 15000.1d);
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
