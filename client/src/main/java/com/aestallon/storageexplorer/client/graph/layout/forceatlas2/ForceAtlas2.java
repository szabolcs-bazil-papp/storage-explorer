/*
 * Copyright (C) 2025 Szabolcs Bazil Papp
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */

package com.aestallon.storageexplorer.client.graph.layout.forceatlas2;

import org.graphstream.ui.layout.springbox.NodeParticle;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;

public class ForceAtlas2 extends SpringBox {
  
  final boolean modeLinLog;
  public ForceAtlas2(final boolean modeLinLog) {
    super();
    this.modeLinLog = modeLinLog;
    this.K1 = 0.03;
    this.K2 = 0.03;
  }

  @Override
  public String getLayoutAlgorithmName() {
    return "ForceAtlas2";
  }

  @Override
  public NodeParticle newNodeParticle(String id) {
    return new ForceAtlas2NodeParticle(this, id);
  }

  /**
   * Returns the optimal distance between nodes
   * 
   * @return a {@code double} representing the optimal distance between nodes
   */
  double k() {
    return k;
  }
  
  double defaultAttraction() {
    return K1;
  }
  
  double defaultRepulsion() {
    return K2;
  }
  
}
