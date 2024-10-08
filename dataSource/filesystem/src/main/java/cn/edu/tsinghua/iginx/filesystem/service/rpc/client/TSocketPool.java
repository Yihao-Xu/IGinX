/*
 * IGinX - the polystore system with high performance
 * Copyright (C) Tsinghua University
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package cn.edu.tsinghua.iginx.filesystem.service.rpc.client;

import cn.edu.tsinghua.iginx.filesystem.service.rpc.client.pool.PooledTTransport;
import cn.edu.tsinghua.iginx.filesystem.service.rpc.client.pool.TTransportPool;
import cn.edu.tsinghua.iginx.filesystem.service.rpc.client.pool.TTransportPoolConfig;
import cn.edu.tsinghua.iginx.filesystem.service.rpc.client.transport.TSocketFactory;
import java.net.InetSocketAddress;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

public class TSocketPool extends TTransportPool {
  public TSocketPool(InetSocketAddress address, ClientConfig config) {
    super(new TSocketFactory(address, config), createGenericPoolConfig(config.getConnectPool()));
  }

  private static GenericObjectPoolConfig<PooledTTransport> createGenericPoolConfig(
      TTransportPoolConfig config) {
    GenericObjectPoolConfig<PooledTTransport> poolConfig = new GenericObjectPoolConfig<>();
    poolConfig.setMaxTotal(config.getMaxTotal());
    poolConfig.setMinEvictableIdleDuration(config.getMinEvictableIdleDuration());
    return poolConfig;
  }
}
