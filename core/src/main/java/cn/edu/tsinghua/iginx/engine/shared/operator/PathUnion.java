/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package cn.edu.tsinghua.iginx.engine.shared.operator;

import cn.edu.tsinghua.iginx.engine.shared.operator.type.OperatorType;
import cn.edu.tsinghua.iginx.engine.shared.source.Source;

public class PathUnion extends AbstractBinaryOperator {

  public PathUnion(Source sourceA, Source sourceB) {
    super(OperatorType.PathUnion, sourceA, sourceB);
  }

  @Override
  public Operator copy() {
    return new PathUnion(getSourceA().copy(), getSourceB().copy());
  }

  @Override
  public BinaryOperator copyWithSource(Source sourceA, Source sourceB) {
    return new PathUnion(sourceA, sourceB);
  }

  @Override
  public String getInfo() {
    return "";
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    return object != null && getClass() == object.getClass();
  }
}
