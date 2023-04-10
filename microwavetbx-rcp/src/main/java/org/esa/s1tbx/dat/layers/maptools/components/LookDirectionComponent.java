/*
 * Copyright (C) 2014 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.s1tbx.dat.layers.maptools.components;

import org.esa.s1tbx.dat.graphics.GraphicShape;
import org.esa.s1tbx.dat.layers.ScreenPixelConverter;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * map tools look direction component
 */
public class LookDirectionComponent implements MapToolsComponent {
    private boolean valid = true;
    private final List<Point> tails = new ArrayList<>();
    private final List<Point> heads = new ArrayList<>();
    private final static BasicStroke thickStroke = new BasicStroke(4);
    private final static BasicStroke thinStroke = new BasicStroke(2);

    public LookDirectionComponent(final RasterDataNode raster) {
        try {
            final int arrowLength = (int) (raster.getRasterWidth() * 0.05);
            final Product product = raster.getProduct();
            final MetadataElement absRoot = AbstractMetadata.getAbstractedMetadata(product);
            final MetadataElement lookDirectionListElem = absRoot.getElement("Look_Direction_List");
            if (lookDirectionListElem != null) {
                final GeoCoding geoCoding = raster.getGeoCoding();

                final MetadataElement[] dirDataElems = lookDirectionListElem.getElements();
                for (MetadataElement dirElem : dirDataElems) {
                    final float tailLat = (float) dirElem.getAttributeDouble("tail_lat");
                    final float tailLon = (float) dirElem.getAttributeDouble("tail_lon");
                    final float headLat = (float) dirElem.getAttributeDouble("head_lat");
                    final float headLon = (float) dirElem.getAttributeDouble("head_lon");

                    final PixelPos tailPix = geoCoding.getPixelPos(new GeoPos(tailLat, tailLon), null);
                    final PixelPos headPix = geoCoding.getPixelPos(new GeoPos(headLat, headLon), null);

                    final double m = (headPix.getY() - tailPix.getY()) / (headPix.getX() - tailPix.getX());
                    int length = arrowLength;
                    if (tailPix.getX() > headPix.getX())
                        length = -length;
                    final int x = (int) tailPix.getX() + length;
                    final int y = (int) (m * x + (headPix.getY() - m * headPix.getX()));

                    tails.add(new Point((int) tailPix.getX(), (int) tailPix.getY()));
                    heads.add(new Point(x, y));
                }
            } else {
                final String pass = absRoot.getAttributeString(AbstractMetadata.PASS, null);
                String antennaPointing = absRoot.getAttributeString(AbstractMetadata.antenna_pointing, null);
                if (!antennaPointing.equalsIgnoreCase("right"))
                    antennaPointing = "left";

                int x = 0;
                int length = arrowLength;
                if ((pass.equalsIgnoreCase("DESCENDING") && antennaPointing.equalsIgnoreCase("right")) ||
                        (pass.equalsIgnoreCase("ASCENDING") && antennaPointing.equalsIgnoreCase("left"))) {
                    x = raster.getRasterWidth();
                    length = -length;
                }
                int y = 0;
                tails.add(new Point(x, y));
                heads.add(new Point(x + length, y));
                y = raster.getRasterHeight() / 2;
                tails.add(new Point(x, y));
                heads.add(new Point(x + length, y));
                y = raster.getRasterHeight();
                tails.add(new Point(x, y));
                heads.add(new Point(x + length, y));
            }
        } catch (Exception e) {
            valid = false;
        }
    }

    public void render(final Graphics2D g, final ScreenPixelConverter screenPixel) {
        if (!valid) return;

        for (int i = 0; i < tails.size(); ++i) {

            g.setColor(Color.BLACK);
            g.setStroke(thickStroke);
            GraphicShape.drawArrow(g, screenPixel,
                    (int) tails.get(i).getX(), (int) tails.get(i).getY(),
                    (int) heads.get(i).getX(), (int) heads.get(i).getY(), 4);
            g.setColor(Color.CYAN);
            g.setStroke(thinStroke);
            GraphicShape.drawArrow(g, screenPixel,
                    (int) tails.get(i).getX(), (int) tails.get(i).getY(),
                    (int) heads.get(i).getX(), (int) heads.get(i).getY(), 4);
        }
    }
}
