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
package cn.edu.tsinghua.iginx.engine.shared.function.manager;

import cn.edu.tsinghua.iginx.conf.Config;
import cn.edu.tsinghua.iginx.conf.ConfigDescriptor;
import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.validation.constraints.NotNull;
import pemja.core.PythonInterpreter;
import pemja.core.PythonInterpreterConfig;

/**
 * 管理线程上的interpreter，存到ThreadLocal中以便随时随地取用
 *
 * <p>不管理interpreter的生命周期，生命周期管理在AbstractTaskThreadPoolExecutor中
 */
public class ThreadInterpreterManager {
  private static final ThreadLocal<PythonInterpreter> interpreterThreadLocal = new ThreadLocal<>();
  private static final ThreadLocal<PythonInterpreterConfig> configThreadLocal = new ThreadLocal<>();

  private static final Config config = ConfigDescriptor.getInstance().getConfig();

  private static final String PythonCMD = config.getPythonCMD();

  private static final String PATH =
      String.join(File.separator, config.getDefaultUDFDir(), "python_scripts");

  @NotNull
  public static PythonInterpreter getInterpreter() {
    PythonInterpreter interpreter = interpreterThreadLocal.get();
    if (configThreadLocal.get() == null) {
      do {
        configThreadLocal.set(
            PythonInterpreterConfig.newBuilder()
                .setPythonExec(PythonCMD)
                .addPythonPaths(PATH)
                .build());
      } while (configThreadLocal.get().getPythonExec() == null);
    }

    if (interpreter == null) {
      boolean success = false;
      int count = 0;
      do {
        try {
          setInterpreter(new PythonInterpreter(configThreadLocal.get()));

        } catch (NullPointerException e) {
          count++;
          continue;
        }
        success = true;
      } while (!success && count < 100);
    }
    return interpreterThreadLocal.get();
  }

  public static boolean isInterpreterSet() {
    return interpreterThreadLocal.get() != null;
  }

  public static void setInterpreter(@NotNull PythonInterpreter interpreter) {
    interpreterThreadLocal.set(interpreter);
  }

  public static void setConfig(@NotNull PythonInterpreterConfig config) {
    configThreadLocal.set(config);
  }

  public static void executeWithInterpreter(Consumer<PythonInterpreter> action) {
    PythonInterpreter interpreter = getInterpreter();
    action.accept(interpreter);
  }

  public static <T> T executeWithInterpreterAndReturn(Function<PythonInterpreter, T> action) {
    PythonInterpreter interpreter = getInterpreter();
    return action.apply(interpreter);
  }
}
