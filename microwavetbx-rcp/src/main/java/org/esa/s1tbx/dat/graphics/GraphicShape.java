/*
 * Copyright (C) 2016 by Array Systems Computing Inc. http://www.array.ca
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
package org.esa.s1tbx.dat.graphics;

import org.esa.s1tbx.dat.layers.ScreenPixelConverter;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

/**
 * Helper for creating shapes
*/
public class GraphicShape {

    public static void drawArrow(final Graphics2D g, final ScreenPixelConverter screenPixel,
                                 final int x, final int y, final int x2, final int y2, final int headlength) {
        final double[] ipts = new double[8];
        final double[] vpts = new double[8];

        final int headSize = Math.max(5, (int) ((x2 - x) * 0.1));
        createArrow(x, y, x2, y2, headSize, headlength, ipts);

        screenPixel.pixelToScreen(ipts, vpts);

        //arrowhead
        //g.draw(new Line2D.Double(vpts[4], vpts[5], vpts[2], vpts[3]));
        //g.draw(new Line2D.Double(vpts[6], vpts[7], vpts[2], vpts[3]));
        final Polygon head = new Polygon();
        head.addPoint((int) vpts[4], (int) vpts[5]);
        head.addPoint((int) vpts[2], (int) vpts[3]);
        head.addPoint((int) vpts[6], (int) vpts[7]);
        head.addPoint((int) vpts[4], (int) vpts[5]);
        g.fill(head);
        //body
        g.draw(new Line2D.Double(vpts[0], vpts[1], vpts[2], vpts[3]));
    }

    private static void createArrow(int x, int y, int xx, int yy, int i1, int length, double[] ipts) {
        ipts[0] = x;
        ipts[1] = y;
        ipts[2] = xx;
        ipts[3] = yy;
        final double d = xx - x;
        final double d1 = -(yy - y);
        double d2 = Math.sqrt(d * d + d1 * d1);
        final double d3;
        if (d2 > (3.0 * i1))
            d3 = i1;
        else
            d3 = d2 / 3.0;
        if (d2 < 1.0)
            d2 = 1.0;

        final double d4 = (d3 * d) / d2;
        final double d5 = -((d3 * d1) / d2);
        final double d6 = (double) xx - length * d4;
        final double d7 = (double) yy - length * d5;
        ipts[4] = (int) (d6 - d5);
        ipts[5] = (int) (d7 + d4);
        ipts[6] = (int) (d6 + d5);
        ipts[7] = (int) (d7 - d4);
    }

    public static Point.Double drawCircle(final Graphics2D g, final ScreenPixelConverter screenPixel,
                                   final double x, final double y, final int size, final Color color) {

        final double[] ipts = new double[6];
        final double[] vpts = new double[6];
        final double halfSize = size / 2.0;

        ipts[0] = x;
        ipts[1] = y;
        ipts[2] = x - halfSize;
        ipts[3] = y - halfSize;
        ipts[4] = ipts[2] + size;
        ipts[5] = ipts[3] + size;

        screenPixel.pixelToScreen(ipts, vpts);

        final double w = vpts[4] - vpts[2];
        final double h = vpts[5] - vpts[3];
        final Ellipse2D.Double circle = new Ellipse2D.Double(vpts[2], vpts[3], w, h);
        g.setColor(color);
        g.draw(circle);

        return new Point.Double(vpts[0], vpts[1]);
    }

    public static Point.Double drawRect(final Graphics2D g, final ScreenPixelConverter screenPixel,
                                        final double x, final double y, final int size, final Color color) {

        final double[] ipts = new double[6];
        final double[] vpts = new double[6];
        final double halfSize = size / 2.0;

        ipts[0] = x;
        ipts[1] = y;
        ipts[2] = x - halfSize;
        ipts[3] = y - halfSize;
        ipts[4] = x + halfSize;
        ipts[5] = y + halfSize;

        screenPixel.pixelToScreen(ipts, vpts);

        final double w = vpts[4] - vpts[2];
        final double h = vpts[5] - vpts[3];
        final Rectangle2D.Double rect = new Rectangle2D.Double(vpts[2], vpts[3], w, h);
        g.setColor(color);
        g.draw(rect);

        return new Point.Double(vpts[0], vpts[1]);
    }

    public static Point.Double drawX(final Graphics2D g, final ScreenPixelConverter screenPixel,
                                        final double x, final double y, final int size, final Color color) {

        final double[] ipts = new double[6];
        final double[] vpts = new double[6];
        final double halfSize = size / 2.0;

        ipts[0] = x;
        ipts[1] = y;
        ipts[2] = x - halfSize;
        ipts[3] = y - halfSize;
        ipts[4] = ipts[2] + size;
        ipts[5] = ipts[3] + size;

        screenPixel.pixelToScreen(ipts, vpts);

        g.setColor(color);
        g.drawLine((int)vpts[2], (int)vpts[3], (int)vpts[4], (int)vpts[5]);
        g.drawLine((int)vpts[4], (int)vpts[3], (int)vpts[2], (int)vpts[5]);

        return new Point.Double(vpts[0], vpts[1]);
    }

    public static Point.Double drawLine(final Graphics2D g, final ScreenPixelConverter screenPixel,
                                     final double x1, final double y1, final double x2, final double y2, final Color color) {

        final double[] ipts = new double[4];
        final double[] vpts = new double[4];

        ipts[0] = x1;
        ipts[1] = y1;
        ipts[2] = x2;
        ipts[3] = y2;

        screenPixel.pixelToScreen(ipts, vpts);

        g.setColor(color);
        g.drawLine((int)vpts[0], (int)vpts[1], (int)vpts[2], (int)vpts[3]);

        return new Point.Double(vpts[0], vpts[1]);
    }
}
