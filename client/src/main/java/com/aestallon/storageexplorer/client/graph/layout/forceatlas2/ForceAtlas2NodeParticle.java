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

import java.util.Iterator;
import org.graphstream.ui.geom.Vector3;
import org.graphstream.ui.layout.springbox.BarnesHutLayout;
import org.graphstream.ui.layout.springbox.EdgeSpring;
import org.graphstream.ui.layout.springbox.Energies;
import org.graphstream.ui.layout.springbox.GraphCellData;
import org.graphstream.ui.layout.springbox.NodeParticle;
import org.graphstream.ui.layout.springbox.implementations.SpringBox;
import org.miv.pherd.Particle;
import org.miv.pherd.ParticleBox;
import org.miv.pherd.geom.Point3;
import org.miv.pherd.ntree.Cell;

final class ForceAtlas2NodeParticle extends NodeParticle {

  private Vector3 prevDisp;
  private double antiSwingCoeff = 1d;

  ForceAtlas2NodeParticle(BarnesHutLayout box, String id) {
    super(box, id);
  }

  ForceAtlas2NodeParticle(BarnesHutLayout box, String id, double x, double y, double z) {
    super(box, id, x, y, z);
  }

  @Override
  protected void repulsionN2(Vector3 delta) {
    ForceAtlas2 box = (ForceAtlas2) this.box;
    boolean is3D = box.is3D();
    ParticleBox nodes = box.getSpatialIndex();
    Energies energies = box.getEnergies();
    Iterator<Object> i = nodes.getParticleIdIterator();

    while (i.hasNext()) {
      ForceAtlas2NodeParticle node = (ForceAtlas2NodeParticle) nodes.getParticle(i.next());

      if (node != this) {
        delta.set(node.pos.x - pos.x, node.pos.y - pos.y, is3D ? node.pos.z - pos.z : 0);

        final double len = delta.normalize();
        final double factor = repulsionFactor(node.neighbours.size(), len, box);

        energies.accumulateEnergy(-factor);
        delta.scalarMult(factor);
        disp.add(delta);
      }
    }
  }

  private double repulsionFactor(final int neighbourCount,
                                 final double vectorLength,
                                 final ForceAtlas2 layout) {
    return -1d
           * antiSwingCoeff
           * layout.defaultRepulsion()
           * ((neighbours.size() + 1) * (neighbourCount + 1))
           / Math.abs(vectorLength);
  }

  @Override
  protected void repulsionNLogN(Vector3 delta) {
    //repulsionN2(delta);
    recurseRepulsion(box.getSpatialIndex().getNTree().getRootCell(), delta);
  }

  void recurseRepulsion(Cell cell, Vector3 delta) {
    ForceAtlas2 box = (ForceAtlas2) this.box;
    boolean is3D = box.is3D();
    Energies energies = box.getEnergies();

    if (intersection(cell)) {
      if (cell.isLeaf()) {
        Iterator<? extends Particle> i = cell.getParticles();

        while (i.hasNext()) {
          final ForceAtlas2NodeParticle node = (ForceAtlas2NodeParticle) i.next();
          if (node == this) {
            continue;
          }

          delta.set(node.pos.x - pos.x, node.pos.y - pos.y, is3D ? node.pos.z - pos.z : 0);

          double len = delta.normalize();
          double factor = repulsionFactor(node.neighbours.size(), len, box);

          energies.accumulateEnergy(factor);
          repE += factor;
          delta.scalarMult(-factor);
          disp.add(delta);
        }
      } else {
        int div = cell.getSpace().getDivisions();

        for (int i = 0; i < div; i++) {
          recurseRepulsion(cell.getSub(i), delta);
        }
      }
    } else {
      if (cell != this.cell) {
        GraphCellData bary = (GraphCellData) cell.getData();

        double dist = bary.distanceFrom(pos);
        double size = cell.getSpace().getSize();

        if ((!cell.isLeaf()) && ((size / dist) > box.getBarnesHutTheta())) {
          int div = cell.getSpace().getDivisions();

          for (int i = 0; i < div; i++) {
            recurseRepulsion(cell.getSub(i), delta);
          }
        } else {
          delta.set(bary.center.x - pos.x, bary.center.y - pos.y,
              is3D ? bary.center.z - pos.z : 0);

          double len = delta.normalize();
          double factor = repulsionFactor(
              (int) (bary.degree / bary.cell.getPopulation()),
              len,
              box);

          energies.accumulateEnergy(factor);
          delta.scalarMult(-factor);
          repE += factor;
          disp.add(delta);
        }
      }
    }
  }

  @Override
  protected void attraction(final Vector3 delta) {
    final ForceAtlas2 box = (ForceAtlas2) this.box;
    final boolean is3D = box.is3D();
    final Energies energies = box.getEnergies();

    for (final EdgeSpring edge : neighbours) {
      if (edge.ignored) {
        continue;
      }

      final NodeParticle other = edge.getOpposite(this);
      final Point3 opos = other.getPosition();

      delta.set(opos.x - pos.x, opos.y - pos.y, is3D ? opos.z - pos.z : 0);

      double len = delta.normalize();
      final double factor = box.modeLinLog
          ? antiSwingCoeff * Math.pow(len, box.defaultAttraction())
          : antiSwingCoeff * box.defaultAttraction() * len;
      delta.scalarMult(factor);

      disp.add(delta);
      attE += factor;
      energies.accumulateEnergy(factor);
    }
  }

  @Override
  protected void gravity(final Vector3 delta) {
    final SpringBox box = (SpringBox) this.box;
    final boolean is3D = box.is3D();
    delta.set(-pos.x, -pos.y, is3D ? -pos.z : 0);
    delta.normalize();
    delta.scalarMult(antiSwingCoeff * box.getGravityFactor());
    disp.add(delta);
    if (prevDisp != null) {
      final double cosTheta = prevDisp.dotProduct(disp) / (disp.length() * prevDisp.length());
      if (Double.compare(cosTheta, 0.5) > 0) {
        antiSwingCoeff = 1d;
      } else {
        antiSwingCoeff = antiSwingCoeff / 2;
      }
    }

    prevDisp = new Vector3(disp);
  }

  boolean intersection(Cell cell) {
    ForceAtlas2 box = (ForceAtlas2) this.box;

    double k = box.k();
    double vz = box.getViewZone();

    double x1 = cell.getSpace().getLoAnchor().x;
    double y1 = cell.getSpace().getLoAnchor().y;
    double z1 = cell.getSpace().getLoAnchor().z;

    double x2 = cell.getSpace().getHiAnchor().x;
    double y2 = cell.getSpace().getHiAnchor().y;
    double z2 = cell.getSpace().getHiAnchor().z;

    double X1 = pos.x - (k * vz);
    double Y1 = pos.y - (k * vz);
    double Z1 = pos.z - (k * vz);
    double X2 = pos.x + (k * vz);
    double Y2 = pos.y + (k * vz);
    double Z2 = pos.z + (k * vz);

    if (X2 < x1 || X1 > x2) {
      return false;
    }

    if (Y2 < y1 || Y1 > y2) {
      return false;
    }

    return (Z2 >= z1) && (Z1 <= z2);
  }

}
