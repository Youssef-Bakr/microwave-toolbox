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

import org.apache.commons.math3.util.FastMath;
import org.esa.s1tbx.dat.layers.ArrowOverlay;
import org.esa.s1tbx.dat.layers.ScreenPixelConverter;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.engine_utilities.eo.Constants;
import org.esa.snap.engine_utilities.eo.GeoUtils;

import java.awt.*;

/**
 * map tools compass component
 */
public class NorthArrowComponent implements MapToolsComponent {

    private double angle;
    private ArrowOverlay arrow;

    public NorthArrowComponent(final RasterDataNode raster) {
        final int rasterWidth = raster.getRasterWidth();
        final int rasterHeight = raster.getRasterHeight();
        final int margin = (int) (0.05 * FastMath.hypot(rasterWidth, rasterHeight));
        PixelPos point1 = new PixelPos(margin, margin);

        final GeoCoding geoCoding = raster.getGeoCoding();
        if (geoCoding == null) {
            angle = Double.NaN;
            return;
        }

        final GeoPos point1Geo = geoCoding.getGeoPos(point1, null);
        final GeoPos centrePointGeo = geoCoding.getGeoPos(new PixelPos(rasterWidth / 2, rasterHeight / 2), null);
        PixelPos point2 = geoCoding.getPixelPos(new GeoPos(centrePointGeo.getLat(), point1Geo.getLon()), null);

        if (Double.isNaN(point2.getX()) || Double.isNaN(point2.getY())) {

            // point2 falls outside the raster

            // So set new point2 to be the centre point
            point2 = new PixelPos(rasterWidth / 2, rasterHeight / 2);

            // Set new point1 to be in the same longitude of centre point but latitude of the old point1
            final PixelPos oldPoint1 = point1;
            final GeoPos oldPoint1Geo = geoCoding.getGeoPos(oldPoint1, null);
            point1 = geoCoding.getPixelPos(new GeoPos(oldPoint1Geo.getLat(), centrePointGeo.getLon()), null);
        }

        final double op = point1.x - point2.x;
        final double hyp = FastMath.hypot(op, point1.y - point2.y);
        angle = FastMath.asin(op / hyp);

        if (point1Geo.getLat() < centrePointGeo.getLat()) {
            angle += Constants.PI;
        }

        // determine distance
        final GeoPos x5Geo = geoCoding.getGeoPos(new PixelPos((int) (margin * 1.5), margin), null);
        final GeoUtils.DistanceHeading dist = GeoUtils.vincenty_inverse(point1Geo, x5Geo);

        GeoUtils.LatLonHeading coord = GeoUtils.vincenty_direct(point1Geo, dist.distance, angle);
        final PixelPos point3 = geoCoding.getPixelPos(new GeoPos(coord.lat, coord.lon), null);

        final PixelPos dispTail = point1;
        final PixelPos dispHead = point3;

        arrow = new ArrowOverlay((int) dispTail.getX(), (int) dispTail.getY(), (int) dispHead.getX(), (int) dispHead.getY());
        arrow.setText("N");
    }

    public void render(final Graphics2D g, final ScreenPixelConverter screenPixel) {
        if (Double.isNaN(angle))
            return;

        arrow.drawArrow(g, screenPixel);
    }
}
