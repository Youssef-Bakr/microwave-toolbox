/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
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
package org.csa.rstb.polarimetric.gpf.decompositions;

import org.csa.rstb.polarimetric.gpf.support.QuadPolProcessor;
import eu.esa.sar.commons.polsar.PolBandUtils;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.engine_utilities.datamodel.Unit;
import org.esa.snap.engine_utilities.gpf.TileIndex;

import java.awt.*;
import java.util.Map;

/**
 * Perform Yamaguchi decomposition for given tile.
 */
public class Yamaguchi extends DecompositionBase implements Decomposition, QuadPolProcessor {

    public Yamaguchi(final PolBandUtils.PolSourceBand[] srcBandList, final PolBandUtils.MATRIX sourceProductType,
                     final int windowSize, final int srcImageWidth, final int srcImageHeight) {
        super(srcBandList, sourceProductType, windowSize, windowSize, srcImageWidth, srcImageHeight);
    }

    public String getSuffix() {
        return "_Yamaguchi";
    }

    /**
     * Return the list of band names for the target product
     */
    public String[] getTargetBandNames() {
        return new String[]{"Yamaguchi_dbl_r", "Yamaguchi_vol_g", "Yamaguchi_surf_b", "Yamaguchi_hlx"};
    }

    /**
     * Sets the unit for the new target band
     *
     * @param targetBandName the band name
     * @param targetBand     the new target band
     */
    public void setBandUnit(final String targetBandName, final Band targetBand) {
        targetBand.setUnit(Unit.INTENSITY_DB);
    }

    /**
     * Compute min/max values of the Span image.
     *
     * @param op       the decomposition operator
     * @param bandList the src band list
     * @throws OperatorException when thread fails
     */
    private synchronized void setSpanMinMax(final Operator op, final PolBandUtils.PolSourceBand bandList)
            throws OperatorException {

        if (bandList.spanMinMaxSet) {
            return;
        }
        final DecompositionBase.MinMax span = computeSpanMinMax(op, sourceProductType, halfWindowSizeX, halfWindowSizeY, bandList);
        bandList.spanMin = span.min;
        bandList.spanMax = span.max;
        bandList.spanMinMaxSet = true;
    }

    /**
     * Perform decomposition for given tile.
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed.
     * @param op              the polarimetric decomposition operator
     * @throws OperatorException If an error occurs during computation of the filtered value.
     */
    public void computeTile(final Map<Band, Tile> targetTiles, final Rectangle targetRectangle,
                            final Operator op) throws OperatorException {

        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        final int w = targetRectangle.width;
        final int h = targetRectangle.height;
        final int maxY = y0 + h;
        final int maxX = x0 + w;
        //System.out.println("freeman x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        for (final PolBandUtils.PolSourceBand bandList : srcBandList) {

            final TargetInfo[] targetInfo = new TargetInfo[bandList.targetBands.length];
            int j = 0;
            for (Band targetBand : bandList.targetBands) {
                final String targetBandName = targetBand.getName();
                if (targetBandName.contains("Yamaguchi_dbl_r")) {
                    targetInfo[j] = new TargetInfo(targetTiles.get(targetBand), TargetBandColour.R);
                } else if (targetBandName.contains("Yamaguchi_vol_g")) {
                    targetInfo[j] = new TargetInfo(targetTiles.get(targetBand), TargetBandColour.G);
                } else if (targetBandName.contains("Yamaguchi_surf_b")) {
                    targetInfo[j] = new TargetInfo(targetTiles.get(targetBand), TargetBandColour.B);
                } else if (targetBandName.contains("Yamaguchi_hlx")) {
                    targetInfo[j] = new TargetInfo(targetTiles.get(targetBand), null);
                }
                ++j;
            }
            final TileIndex trgIndex = new TileIndex(targetInfo[0].tile);

            final double[][] Cr = new double[3][3];
            final double[][] Ci = new double[3][3];

            if (!bandList.spanMinMaxSet) {
                setSpanMinMax(op, bandList);
            }

            final Tile[] sourceTiles = new Tile[bandList.srcBands.length];
            final ProductData[] dataBuffers = new ProductData[bandList.srcBands.length];
            final Rectangle sourceRectangle = getSourceRectangle(x0, y0, w, h);
            getQuadPolDataBuffer(op, bandList.srcBands, sourceRectangle, sourceProductType, sourceTiles, dataBuffers);

            double pd, pv, ps, pc;
            for (int y = y0; y < maxY; ++y) {
                trgIndex.calculateStride(y);
                for (int x = x0; x < maxX; ++x) {

                    getMeanCovarianceMatrix(x, y, halfWindowSizeX, halfWindowSizeY,
                            sourceProductType, sourceTiles, dataBuffers, Cr, Ci);

                    final YDD data = getYamaguchiDecomposition(Cr, Ci);

                    ps = scaleDb(data.ps, bandList.spanMin, bandList.spanMax);
                    pd = scaleDb(data.pd, bandList.spanMin, bandList.spanMax);
                    pv = scaleDb(data.pv, bandList.spanMin, bandList.spanMax);
                    pc = scaleDb(data.pc, bandList.spanMin, bandList.spanMax);

                    // save Pd as red, Pv as green and Ps as blue
                    for (TargetInfo target : targetInfo) {

                        if (target.colour == TargetBandColour.R) {
                            target.dataBuffer.setElemFloatAt(trgIndex.getIndex(x), (float) pd);
                        } else if (target.colour == TargetBandColour.G) {
                            target.dataBuffer.setElemFloatAt(trgIndex.getIndex(x), (float) pv);
                        } else if (target.colour == TargetBandColour.B) {
                            target.dataBuffer.setElemFloatAt(trgIndex.getIndex(x), (float) ps);
                        } else {
                            target.dataBuffer.setElemFloatAt(trgIndex.getIndex(x), (float) pc);
                        }
                    }
                }
            }
        }
    }

    public YDD getYamaguchiDecomposition(final double[][] Cr, final double[][] Ci) {

        double ratio, d, cR, cI, c0, s, pd, pv, ps, pc, span, k1, k2, k3;

        final double[][] Tr = new double[3][3];
        final double[][] Ti = new double[3][3];

        c3ToT3(Cr, Ci, Tr, Ti);

        span = Tr[0][0] + Tr[1][1] + Tr[2][2];
        pc = 2 * Math.abs(Ti[1][2]);
        ratio = 10 * Math.log10(Cr[2][2] / Cr[0][0]);

        if (ratio <= -2) {
            k1 = 1.0 / 6.0;
            k2 = 7.0 / 30.0;
            k3 = 4.0 / 15.0;
        } else if (ratio > 2) {
            k1 = -1.0 / 6.0;
            k2 = 7.0 / 30.0;
            k3 = 4.0 / 15.0;
        } else { // -2 < ratio <= 2
            k1 = 0.0;
            k2 = 1.0 / 4.0;
            k3 = 1.0 / 4.0;
        }

        pv = (Tr[2][2] - 0.5 * pc) / k3;

        if (pv <= 0) { // Freeman-Durden 3 component decomposition
            pc = 0;
            final FreemanDurden.FDD data = FreemanDurden.getFreemanDurdenDecomposition(Cr, Ci);
            ps = data.ps;
            pd = data.pd;
            pv = data.pv;

        } else { // Yamaguchi 4 component decomposition

            s = Tr[0][0] - 0.5 * pv;
            d = Tr[1][1] - k2 * pv - 0.5 * pc;
            cR = Tr[0][1] - k1 * pv;
            cI = Ti[0][1];

            if (pv + pc < span) {

                c0 = Cr[0][2] - 0.5 * Cr[1][1] + 0.5 * pc;
                if (c0 < 0) {
                    ps = s - (cR * cR + cI * cI) / d;
                    pd = d + (cR * cR + cI * cI) / d;
                } else {
                    ps = s + (cR * cR + cI * cI) / s;
                    pd = d - (cR * cR + cI * cI) / s;
                }

                if (ps > 0 && pd < 0) {
                    pd = 0;
                    ps = span - pv - pc;
                } else if (ps < 0 && pd > 0) {
                    ps = 0;
                    pd = span - pv - pc;
                } else if (ps < 0 && pd < 0) {
                    ps = 0;
                    pd = 0;
                    pv = span - pc;
                }

            } else {
                ps = 0.0;
                pd = 0.0;
                pv = span - pc;
            }
        }

        return new YDD(pv, pd, ps, pc);
    }

    public static class YDD {
        public final double pv;
        public final double pd;
        public final double ps;
        public final double pc;

        public YDD(final double pv, final double pd, final double ps, final double pc) {
            this.pd = pd;
            this.ps = ps;
            this.pv = pv;
            this.pc = pc;
        }
    }
}
