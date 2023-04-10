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
import org.esa.s1tbx.dat.layers.ScreenPixelConverter;
import org.esa.snap.core.datamodel.GeoCoding;
import org.esa.snap.core.datamodel.GeoPos;
import org.esa.snap.core.datamodel.PixelPos;
import org.esa.snap.core.datamodel.RasterDataNode;
import org.esa.snap.engine_utilities.eo.Constants;
import org.esa.snap.ui.UIUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * map tools compass component
 */
public class CompassComponent implements MapToolsComponent {

    private static final ImageIcon roseIcon = UIUtils.loadImageIcon("/org/esa/s1tbx/dat/icons/compass_rose.png", CompassComponent.class);
    private final BufferedImage image;
    private final PixelPos tail, head;
    private final PixelPos point1;
    private double angle;
    private final int rasterWidth;
    private final int rasterHeight;
    private final static double marginPct = 0.05;
    private final int margin;

    public CompassComponent(final RasterDataNode raster) {
        image = new BufferedImage(roseIcon.getIconWidth(), roseIcon.getIconHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        final Graphics2D g = image.createGraphics();
        g.drawImage(roseIcon.getImage(), null, null);

        rasterWidth = raster.getRasterWidth();
        rasterHeight = raster.getRasterHeight();
        margin = (int) (Math.min(rasterWidth, rasterHeight) * marginPct);
        point1 = new PixelPos(margin, margin);

        final GeoCoding geoCoding = raster.getGeoCoding();
        if (geoCoding == null) {
            tail = head = point1;
            angle = Double.NaN;
            return;
        }

        final GeoPos point1Geo = geoCoding.getGeoPos(point1, null);
        final GeoPos point2Geo = geoCoding.getGeoPos(new PixelPos(rasterWidth / 2, rasterHeight / 2), null);
        final PixelPos point2 = geoCoding.getPixelPos(new GeoPos(point2Geo.getLat(), point1Geo.getLon()), null);

        final double op = point1.x - point2.x;
        final double hyp = FastMath.hypot(op, point1.y - point2.y);
        angle = FastMath.asin(op / hyp);

        if (point1Geo.getLat() < point2Geo.getLat()) {
            tail = point1;
            head = point2;
            angle += Constants.PI;
        } else {
            tail = point2;
            head = point1;
        }
    }

    public void render(final Graphics2D graphics, final ScreenPixelConverter screenPixel) {
        if (Double.isNaN(angle))
            return;

        final AffineTransform transformSave = graphics.getTransform();
        try {
            final AffineTransform transform = screenPixel.getImageTransform(transformSave);

            final double scale = (marginPct * 2 * rasterWidth) / (double) image.getWidth();
            transform.translate(point1.x, point1.y);
            transform.scale(scale, scale);
            transform.rotate(angle);

            graphics.drawRenderedImage(image, transform);
        } finally {
            graphics.setTransform(transformSave);
        }
    }
}
