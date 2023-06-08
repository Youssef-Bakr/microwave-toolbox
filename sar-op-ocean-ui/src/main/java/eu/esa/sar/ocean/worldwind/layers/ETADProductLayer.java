/*
 * Copyright (C) 2023 by SkyWatch Space Applications Inc. http://www.skywatch.com
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package eu.esa.sar.ocean.worldwind.layers;

import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.render.Polyline;
import org.esa.snap.core.datamodel.MetadataElement;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.worldwind.layers.BaseLayer;
import org.esa.snap.worldwind.layers.WWLayer;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ETAD visualization
 */
public class ETADProductLayer extends BaseLayer implements WWLayer {

    private final ConcurrentHashMap<String, Polyline[]> outlineTable = new ConcurrentHashMap<>();

    public ETADProductLayer() {
        this.setName("S-1 ETAD");
    }

    private static String getUniqueName(final Product product) {
        return product.getProductRefString() + product.getName();
    }

    @Override
    public void updateInfoAnnotation(final SelectEvent event) {
    }

    @Override
    public void setSelectedProduct(final Product product) {
        super.setSelectedProduct(product);

        if (selectedProduct != null) {
            final String selName = getUniqueName(selectedProduct);
            for (String name : outlineTable.keySet()) {
                final Polyline[] lineList = outlineTable.get(name);
                final boolean highlight = name.equals(selName);
                for (Polyline line : lineList) {
                    line.setHighlighted(highlight);
                    line.setHighlightColor(Color.GREEN);
                }
            }
        }
    }

    public void addProduct(final Product product, WorldWindowGLCanvas wwd) {

        if(product.getProductType().equals("ETAD")) {
            final String name = getUniqueName(product);
            if (this.outlineTable.get(name) != null)
                return;

            addOutline(product);
        }
    }

    private void addOutline(final Product product) {

        final MetadataElement origMeta = AbstractMetadata.getOriginalProductMetadata(product);
        final MetadataElement annotation = origMeta.getElement("annotation");
        final MetadataElement etadProduct = annotation.getElement("etadProduct");
        final MetadataElement etadBurstList = etadProduct.getElement("etadBurstList");

        final List<Polyline> polyLineList = new ArrayList<>();
        final MetadataElement[] burstElems = etadBurstList.getElements();
        for(MetadataElement burstElem : burstElems) {
            final MetadataElement burstCoverage = burstElem.getElement("burstCoverage");
            final MetadataElement spatialCoverage = burstCoverage.getElement("spatialCoverage");

            final List<Position> positions = new ArrayList<>(4);
            final MetadataElement[] coordinates = spatialCoverage.getElements();

            positions.add(getPosition(coordinates[1]));
            positions.add(getPosition(coordinates[0]));
            positions.add(getPosition(coordinates[2]));
            positions.add(getPosition(coordinates[3]));
            positions.add(getPosition(coordinates[1]));

            Polyline polyLine = new Polyline();
            polyLine.setFollowTerrain(true);
            polyLine.setPositions(positions);
            polyLineList.add(polyLine);

            addRenderable(polyLine);
        }

        outlineTable.put(getUniqueName(product), polyLineList.toArray(new Polyline[0]));
    }

    private Position getPosition(final MetadataElement coordinate) {
        final MetadataElement latitude = coordinate.getElement("latitude");
        final MetadataElement longitude = coordinate.getElement("longitude");
        double lat = latitude.getAttributeDouble("latitude");
        double lon = longitude.getAttributeDouble("longitude");

        return new Position(Angle.fromDegreesLatitude(lat), Angle.fromDegreesLongitude(lon), 0.0);
    }

    public void removeProduct(final Product product) {
        removeOutline(getUniqueName(product));
    }

    private void removeOutline(String imagePath) {
        final Polyline[] lineList = this.outlineTable.get(imagePath);
        if (lineList != null) {
            for (Polyline line : lineList) {
                this.removeRenderable(line);
            }
            this.outlineTable.remove(imagePath);
        }
    }

    @Override
    public JPanel getControlPanel(WorldWindowGLCanvas wwd) {
        return null;
    }
}