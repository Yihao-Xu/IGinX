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
package cn.edu.tsinghua.iginx.filesystem.struct.exception;

import java.io.IOException;

public class FileStructureException extends IOException {

  public FileStructureException() {}

  public FileStructureException(String message) {
    super(message);
  }

  public FileStructureException(String message, Throwable cause) {
    super(message, cause);
  }

  public FileStructureException(Throwable cause) {
    super(cause);
  }
}
