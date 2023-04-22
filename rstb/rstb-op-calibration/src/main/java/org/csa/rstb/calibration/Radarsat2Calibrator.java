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
package org.csa.rstb.calibration;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.math3.util.FastMath;
import eu.esa.sar.calibration.gpf.support.BaseCalibrator;
import eu.esa.sar.calibration.gpf.support.Calibrator;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.datamodel.Unit;
import org.esa.snap.engine_utilities.eo.Constants;
import org.esa.snap.engine_utilities.gpf.OperatorUtils;
import org.esa.snap.engine_utilities.gpf.TileIndex;

import java.awt.*;
import java.io.File;

/**
 * Calibration for Radarsat2 data products.
 */

public class Radarsat2Calibrator extends BaseCalibrator implements Calibrator {

    private static final String[] SUPPORTED_MISSIONS = new String[] {"RS2"};

    private static final String lutsigma = "lutSigma";
    private static final String lutgamma = "lutGamma";
    private static final String lutbeta = "lutBeta";
    private static final String USE_INCIDENCE_ANGLE_FROM_DEM = "Use projected local incidence angle from DEM";

    private TiePointGrid incidenceAngle = null;
    private double offset = 0.0;
    private double[] gains = null;

    private int subsetOffsetX = 0;
    private int subsetOffsetY = 0;

    private boolean inputSigma0 = false;
    private boolean isSLC = false;

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public Radarsat2Calibrator() {
    }

    @Override
    public String[] getSupportedMissions() {
        return SUPPORTED_MISSIONS;
    }

    /**
     * Set external auxiliary file.
     */
    public void setExternalAuxFile(File file) throws OperatorException {
        if (file != null) {
            throw new OperatorException("No external auxiliary file should be selected for Radarsat2 product");
        }
    }

    /**
     * Set auxiliary file flag.
     */
    @Override
    public void setAuxFileFlag(String file) {
    }

    /**

     */
    public void initialize(final Operator op, final Product srcProduct, final Product tgtProduct,
                           final boolean mustPerformRetroCalibration, final boolean mustUpdateMetadata)
            throws OperatorException {
        try {
            calibrationOp = op;
            sourceProduct = srcProduct;
            targetProduct = tgtProduct;

            absRoot = AbstractMetadata.getAbstractedMetadata(sourceProduct);

            getMission();

            if (absRoot.getAttribute(AbstractMetadata.abs_calibration_flag).getData().getElemBoolean()) {
                if (outputImageInComplex) {
                    throw new OperatorException("Absolute radiometric calibration has already been applied to the product");
                }
                inputSigma0 = true;
            }

            isSLC = sourceProduct.getProductType().toLowerCase().contains("slc");

            getSubsetOffset();

            if (!inputSigma0) {
                getLUT();
            }

            getTiePointGridData(sourceProduct);

            if (mustUpdateMetadata) {
                updateTargetProductMetadata();
            }

        } catch (Exception e) {
            throw new OperatorException(e);
        }
    }

    /**
     * Get product mission from abstract metadata.
     */
    private void getMission() {
        final String mission = absRoot.getAttributeString(AbstractMetadata.MISSION);
        if (!mission.equals("RS2")) {
            throw new OperatorException(mission + " is not a valid mission for Radarsat2 Calibration");
        }
    }

    /**
     * Get subset x and y offsets from abstract metadata.
     */
    private void getSubsetOffset() {
        subsetOffsetX = absRoot.getAttributeInt(AbstractMetadata.subset_offset_x);
        subsetOffsetY = absRoot.getAttributeInt(AbstractMetadata.subset_offset_y);
    }

    /**
     * Get antenna pattern gain array from metadata.
     */
    private void getLUT() {
        final MetadataElement origProdRoot = AbstractMetadata.getOriginalProductMetadata(sourceProduct);
        final MetadataElement lutSigmaElem = origProdRoot.getElement(lutsigma);

        if (lutSigmaElem != null) {
            offset = lutSigmaElem.getAttributeDouble("offset", 0);

            final MetadataAttribute gainsAttrib = lutSigmaElem.getAttribute("gains");
            if (gainsAttrib != null) {
                gains = (double[]) gainsAttrib.getData().getElems();
            }
        } else {
            throw new OperatorException(lutsigma + " not found. Please ensure the look up table " + lutsigma + ".xml is in the same folder as the original product");
        }

        if (gains.length < sourceProduct.getSceneRasterWidth()) {
            throw new OperatorException("Calibration LUT is smaller than source product width");
        }
    }

    /**
     * Get incidence angle and slant range time tie point grids.
     *
     * @param sourceProduct the source
     */
    private void getTiePointGridData(Product sourceProduct) {
        incidenceAngle = OperatorUtils.getIncidenceAngle(sourceProduct);
    }

    /**
     * Update the metadata in the target product.
     */
    private void updateTargetProductMetadata() {

        final MetadataElement abs = AbstractMetadata.getAbstractedMetadata(targetProduct);

        abs.getAttribute(AbstractMetadata.abs_calibration_flag).getData().setElemBoolean(true);

        final MetadataElement origProdRoot = AbstractMetadata.getOriginalProductMetadata(targetProduct);
        origProdRoot.removeElement(origProdRoot.getElement(lutsigma));
        origProdRoot.removeElement(origProdRoot.getElement(lutgamma));
        origProdRoot.removeElement(origProdRoot.getElement(lutbeta));
    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetBand The target band.
     * @param targetTile The current tile associated with the target band to be computed.
     * @param pm         A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException If an error occurs during computation of the target raster.
     */
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetTileRectangle = targetTile.getRectangle();
        final int x0 = targetTileRectangle.x;
        final int y0 = targetTileRectangle.y;
        final int w = targetTileRectangle.width;
        final int h = targetTileRectangle.height;

        Tile sourceRaster1 = null;
        ProductData srcData1 = null;
        ProductData srcData2 = null;
        Band sourceBand1 = null;

        final String[] srcBandNames = targetBandNameToSourceBandName.get(targetBand.getName());
        if (srcBandNames.length == 1) {
            sourceBand1 = sourceProduct.getBand(srcBandNames[0]);
            sourceRaster1 = calibrationOp.getSourceTile(sourceBand1, targetTileRectangle);
            srcData1 = sourceRaster1.getDataBuffer();
        } else {
            sourceBand1 = sourceProduct.getBand(srcBandNames[0]);
            final Band sourceBand2 = sourceProduct.getBand(srcBandNames[1]);
            sourceRaster1 = calibrationOp.getSourceTile(sourceBand1, targetTileRectangle);
            final Tile sourceRaster2 = calibrationOp.getSourceTile(sourceBand2, targetTileRectangle);
            srcData1 = sourceRaster1.getDataBuffer();
            srcData2 = sourceRaster2.getDataBuffer();
        }

        final Unit.UnitType tgtBandUnit = Unit.getUnitType(targetBand);
        final Unit.UnitType srcBandUnit = Unit.getUnitType(sourceBand1);

        final ProductData trgData = targetTile.getDataBuffer();
        final TileIndex srcIndex = new TileIndex(sourceRaster1);
        final TileIndex tgtIndex = new TileIndex(targetTile);

        final int maxY = y0 + h;
        final int maxX = x0 + w;

        double sigma = 0.0, dn, i, q, phaseTerm = 0.0;
        int srcIdx, tgtIdx;
        final Double noDataValue = targetBand.getNoDataValue();

        for (int y = y0; y < maxY; ++y) {
            srcIndex.calculateStride(y);
            tgtIndex.calculateStride(y);

            for (int x = x0; x < maxX; ++x) {
                srcIdx = srcIndex.getIndex(x);
                tgtIdx = tgtIndex.getIndex(x);

                dn = srcData1.getElemDoubleAt(srcIdx);
                if (srcBandUnit == Unit.UnitType.AMPLITUDE) {
                    dn *= dn;
                } else if (srcBandUnit == Unit.UnitType.INTENSITY) {

                } else if (srcBandUnit == Unit.UnitType.REAL) {
                    i = dn;
                    q = srcData2.getElemDoubleAt(srcIdx);
                    dn = i * i + q * q;
                    if (dn > 0.0) {
                        if (tgtBandUnit == Unit.UnitType.REAL) {
                            phaseTerm = i / Math.sqrt(dn);
                        } else if (tgtBandUnit == Unit.UnitType.IMAGINARY) {
                            phaseTerm = q / Math.sqrt(dn);
                        }
                    } else {
                        phaseTerm = 0.0;
                    }
                } else if (srcBandUnit == Unit.UnitType.INTENSITY_DB) {
                    dn = FastMath.pow(10, dn / 10.0); // convert dB to linear scale
                } else {
                    throw new OperatorException("RadarSat2 Calibration: unhandled unit");
                }

                if (inputSigma0) {
                    sigma = dn;
                } else {
                    if (isSLC) {
                        if (gains != null) {
                            sigma = dn / (gains[x + subsetOffsetX] * gains[x + subsetOffsetX]);
                            if (outputImageInComplex) {
                                sigma = Math.sqrt(sigma)*phaseTerm;
                            }
                        }
                    } else {
                        sigma = dn + offset;
                        if (gains != null) {
                            sigma /= gains[x + subsetOffsetX];
                        }
                    }
                }

                if (outputImageScaleInDb) { // convert calibration result to dB
                    if (sigma < underFlowFloat) {
                        sigma = -underFlowFloat;
                    } else {
                        sigma = 10.0 * Math.log10(sigma);
                    }
                }

                trgData.setElemDoubleAt(tgtIdx, sigma);
            }
        }
    }

    public double applyCalibration(
            final double v, final double rangeIndex, final double azimuthIndex, final double slantRange,
            final double satelliteHeight, final double sceneToEarthCentre, final double localIncidenceAngle,
            final String bandName, final String bandPolar, final Unit.UnitType bandUnit, int[] subSwathIndex) {

        double sigma = 0.0;
        if (bandUnit == Unit.UnitType.AMPLITUDE) {
            sigma = v * v;
        } else if (bandUnit == Unit.UnitType.INTENSITY || bandUnit == Unit.UnitType.REAL || bandUnit == Unit.UnitType.IMAGINARY) {
            sigma = v;
        } else if (bandUnit == Unit.UnitType.INTENSITY_DB) {
            sigma = FastMath.pow(10, v / 10.0); // convert dB to linear scale
        } else {
            throw new OperatorException("Unknown band unit");
        }

        if (isSLC) {
            if (gains != null) {
                sigma /= (gains[(int) rangeIndex] * gains[(int) rangeIndex]);
            }
        } else {
            sigma += offset;
            if (gains != null) {
                sigma /= gains[(int) rangeIndex];
            }
        }

        if (incidenceAngleSelection.contains(USE_INCIDENCE_ANGLE_FROM_DEM)) {
            return sigma * FastMath.sin(localIncidenceAngle * Constants.DTOR);
        } else { // USE_INCIDENCE_ANGLE_FROM_ELLIPSOID
            return sigma;
        }
    }

    public double applyRetroCalibration(int x, int y, double v, String bandPolar, final Unit.UnitType bandUnit, int[] subSwathIndex) {
        if (incidenceAngleSelection.contains(USE_INCIDENCE_ANGLE_FROM_DEM)) {
            return v / FastMath.sin(incidenceAngle.getPixelDouble(x, y) * Constants.DTOR);
        } else { // USE_INCIDENCE_ANGLE_FROM_ELLIPSOID
            return v;
        }
    }

    public void removeFactorsForCurrentTile(final Band targetBand, final Tile targetTile,
                                            final String srcBandName) throws OperatorException {

        final Band sourceBand = sourceProduct.getBand(targetBand.getName());
        final Tile sourceTile = calibrationOp.getSourceTile(sourceBand, targetTile.getRectangle());
        targetTile.setRawSamples(sourceTile.getRawSamples());
    }
}
