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
import org.esa.snap.engine_utilities.eo.Constants;
import org.esa.snap.engine_utilities.gpf.TileIndex;

import java.awt.*;
import java.util.Map;

/**
 * Perform Sinclair decomposition for given tile.
 */
public class Sinclair extends DecompositionBase implements Decomposition, QuadPolProcessor {

    public Sinclair(final PolBandUtils.PolSourceBand[] srcBandList, final PolBandUtils.MATRIX sourceProductType,
                    final int windowSize, final int srcImageWidth, final int srcImageHeight) {
        super(srcBandList, sourceProductType, windowSize, windowSize, srcImageWidth, srcImageHeight);
    }

    public String getSuffix() {
        return "_Sinclair";
    }

    /**
     * Return the list of band names for the target product
     *
     * @return list of band names
     */
    public String[] getTargetBandNames() {
        return new String[]{"Sinclair_r", "Sinclair_g", "Sinclair_b"};
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
     * Perform decomposition for given tile.
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed.
     * @param op              the polarimetric decomposition operator
     * @throws OperatorException If an error occurs during computation of the filtered value.
     */
    public void computeTile(final Map<Band, Tile> targetTiles, final Rectangle targetRectangle, final Operator op) {

        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        final int w = targetRectangle.width;
        final int h = targetRectangle.height;
        final int maxY = y0 + h;
        final int maxX = x0 + w;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        for (final PolBandUtils.PolSourceBand bandList : srcBandList) {

            final TargetInfo[] targetInfo = new TargetInfo[bandList.targetBands.length];
            int j = 0;
            for (Band targetBand : bandList.targetBands) {
                final String targetBandName = targetBand.getName();
                if (targetBandName.contains("Sinclair_r")) {
                    targetInfo[j] = new TargetInfo(targetTiles.get(targetBand), TargetBandColour.R);
                } else if (targetBandName.contains("Sinclair_g")) {
                    targetInfo[j] = new TargetInfo(targetTiles.get(targetBand), TargetBandColour.G);
                } else if (targetBandName.contains("Sinclair_b")) {
                    targetInfo[j] = new TargetInfo(targetTiles.get(targetBand), TargetBandColour.B);
                }
                ++j;
            }
            final TileIndex trgIndex = new TileIndex(targetInfo[0].tile);

            final double[][] Sr = new double[2][2]; // real part of scatter matrix
            final double[][] Si = new double[2][2]; // imaginary part of scatter matrix
            final double[][] Cr = new double[3][3]; // real part of covariance matrix
            final double[][] Ci = new double[3][3]; // imaginary part of covariance matrix
            final double[][] Tr = new double[3][3]; // real part of coherency matrix
            final double[][] Ti = new double[3][3]; // imaginary part of coherency matrix

            final Tile[] sourceTiles = new Tile[bandList.srcBands.length];
            final ProductData[] dataBuffers = new ProductData[bandList.srcBands.length];
            getQuadPolDataBuffer(op, bandList.srcBands, targetRectangle, sourceProductType, sourceTiles, dataBuffers);

            final TileIndex srcIndex = new TileIndex(sourceTiles[0]);
            final double nodatavalue = bandList.srcBands[0].getNoDataValue();

            double re = 0.0, im = 0.0, v = 0.0;
            for (int y = y0; y < maxY; ++y) {
                trgIndex.calculateStride(y);
                srcIndex.calculateStride(y);
                for (int x = x0; x < maxX; ++x) {
                    final int tgtIdx = trgIndex.getIndex(x);
                    final int srcIdx = srcIndex.getIndex(x);

                    if (sourceProductType == PolBandUtils.MATRIX.FULL) {
                        getComplexScatterMatrix(srcIdx, dataBuffers, Sr, Si);
                        boolean isNoData = isNoData(Sr, Si, nodatavalue);

                        for (TargetInfo target : targetInfo) {

                            if (isNoData) {
                                target.dataBuffer.setElemFloatAt(trgIndex.getIndex(x), (float) nodatavalue);
                            } else {
                                if (target.colour == TargetBandColour.R) {
                                    re = Sr[1][1];
                                    im = Si[1][1];
                                } else if (target.colour == TargetBandColour.G) {
                                    re = 0.5 * (Sr[0][1] + Sr[1][0]);
                                    im = 0.5 * (Si[0][1] + Si[1][0]);
                                } else if (target.colour == TargetBandColour.B) {
                                    re = Sr[0][0];
                                    im = Si[0][0];
                                }

                                v = re * re + im * im;
                                if (v < Constants.EPS) {
                                    v = Constants.EPS;
                                }
                                v = 10.0 * Math.log10(v);
                                target.dataBuffer.setElemFloatAt(tgtIdx, (float) v);
                            }
                        }

                    } else if (sourceProductType == PolBandUtils.MATRIX.C3) {

                        getCovarianceMatrixC3(srcIdx, dataBuffers, Cr, Ci);
                        boolean isNoData = isNoData(Cr, Ci, nodatavalue);

                        for (TargetInfo target : targetInfo) {

                            if (isNoData) {
                                target.dataBuffer.setElemFloatAt(trgIndex.getIndex(x), (float) nodatavalue);
                            } else {
                                if (target.colour == TargetBandColour.R) { // C33
                                    v = Cr[2][2];
                                } else if (target.colour == TargetBandColour.G) { // 0.5*C22
                                    v = 0.5 * Cr[1][1];
                                } else if (target.colour == TargetBandColour.B) { // C11
                                    v = Cr[0][0];
                                }

                                if (v < Constants.EPS) {
                                    v = Constants.EPS;
                                }
                                v = 10.0 * Math.log10(v);
                                target.dataBuffer.setElemFloatAt(tgtIdx, (float) v);
                            }
                        }

                    } else if (sourceProductType == PolBandUtils.MATRIX.T3) {

                        getCoherencyMatrixT3(srcIdx, dataBuffers, Tr, Ti);
                        boolean isNoData = isNoData(Tr, Ti, nodatavalue);

                        for (TargetInfo target : targetInfo) {

                            if (isNoData) {
                                target.dataBuffer.setElemFloatAt(trgIndex.getIndex(x), (float) nodatavalue);
                            } else {
                                if (target.colour == TargetBandColour.R) { // 0.5*(T11+T22) - T12_real
                                    v = 0.5 * (Tr[0][0] + Tr[1][1]) - Tr[0][1];
                                } else if (target.colour == TargetBandColour.G) { // 0.5*T33
                                    v = 0.5 * Tr[2][2];
                                } else if (target.colour == TargetBandColour.B) { // 0.5*(T11+T22) + T12_real
                                    v = 0.5 * (Tr[0][0] + Tr[1][1]) + Tr[0][1];
                                }

                                if (v < Constants.EPS) {
                                    v = Constants.EPS;
                                }
                                v = 10.0 * Math.log10(v);
                                target.dataBuffer.setElemFloatAt(tgtIdx, (float) v);
                            }
                        }
                    }

                }
            }
        }
    }

    public static RGB getSinclairDecomposition(final double[][] Cr, final double[][] Ci) {

        final double r = Cr[2][2];
        final double g = 0.5 * Cr[1][1];
        final double b = Cr[0][0];

        return new RGB(r, g, b);
    }

    public static class RGB {
        public final double r;
        public final double g;
        public final double b;

        public RGB(final double r, final double g, final double b) {
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }
}
