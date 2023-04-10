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
package org.esa.s1tbx.fex.gpf.forest;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.math3.util.FastMath;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Mask;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.engine_utilities.datamodel.Unit;
import org.esa.snap.engine_utilities.eo.Constants;
import org.esa.snap.engine_utilities.gpf.FilterWindow;
import org.esa.snap.engine_utilities.gpf.OperatorUtils;
import org.esa.snap.engine_utilities.gpf.TileIndex;

import java.awt.*;
import java.util.Map;

/**
 * The forest area detection operator.
 * <p/>
 * The operator implements the algorithm given in [1]. It is assumed that the input source
 * product has already been calibrated, speckle filtered, multilooked and terrain corrected.
 * <p/>
 * [1] F. Ling, R. Leiterer, Y. Huang, J. Reiche and Z. Li, "Forest Change Mapping in
 * Northeast China Using SAR and InSAR Data", ISRSE 34, Sydney, Australia, 2011.
 */

@OperatorMetadata(alias = "Forest-Area-Detection",
        category = "Radar/SAR Applications",
        authors = "Jun Lu, Luis Veci",
        copyright = "Copyright (C) 2016 by Array Systems Computing Inc.",
        version = "1.0",
        description = "Detect forest area.", internal = true)
public class ForestAreaDetectionOp extends Operator {

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @TargetProduct
    private Product targetProduct = null;

    @Parameter(description = "The list of source bands.", alias = "sourceBands",
            rasterDataNodeType = Band.class, label = "Nominator Band")
    private String nominatorBandName = null;

    @Parameter(description = "The list of source bands.", alias = "sourceBands",
            rasterDataNodeType = Band.class, label = "Denominator Band")
    private String denominatorBandName = null;

    @Parameter(valueSet = {FilterWindow.SIZE_3x3, FilterWindow.SIZE_5x5, FilterWindow.SIZE_7x7, FilterWindow.SIZE_9x9},
            defaultValue = FilterWindow.SIZE_3x3, label = "Window Size")
    private String windowSizeStr = FilterWindow.SIZE_3x3;

    @Parameter(description = "The lower bound for ratio image", interval = "(0, *)", defaultValue = "3.76",
            label = "Ratio lower bound (dB)")
    private double T_Ratio_Low = 3.76;

    @Parameter(description = "The upper bound for ratio image", interval = "(0, *)", defaultValue = "6.55",
            label = "Ratio upper bound (dB)")
    private double T_Ratio_High = 6.55;

    //@Parameter(description = "The lower bound for HV image", interval = "(-30, *)", defaultValue = "-13.85",
    //            label="HV lower bound (dB)")
    //private double T_HV_Low = -13.85;

    private int sourceImageWidth = 0;
    private int sourceImageHeight = 0;
    private FilterWindow window;

    private String[] sourceBandNames = new String[2];

    public static final String FOREST_MASK_NAME = "forest_mask";
    public static final String RATIO_BAND_NAME = "ratio";

    /*
    private static double T_Ratio_Low = 3.76; // dB
    private static double T_Ratio_High = 6.55; // dB
    private static double T_HV_Low = -13.85 ; // dB
    private static double T_HV_High = -10.85 ; // dB
    */

    @Override
    public void initialize() throws OperatorException {

        try {
            sourceImageWidth = sourceProduct.getSceneRasterWidth();
            sourceImageHeight = sourceProduct.getSceneRasterHeight();

            window = new FilterWindow(windowSizeStr);

            createTargetProduct();

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Create target product.
     *
     * @throws Exception The exception.
     */
    private void createTargetProduct() throws Exception {

        targetProduct = new Product(sourceProduct.getName(),
                sourceProduct.getProductType(),
                sourceImageWidth,
                sourceImageHeight);

        ProductUtils.copyProductNodes(sourceProduct, targetProduct);

        addSelectedBands();
    }

    /**
     * Add the user selected bands to target product.
     *
     * @throws OperatorException The exceptions.
     */
    private void addSelectedBands() throws OperatorException {

        sourceBandNames[0] = nominatorBandName;
        sourceBandNames[1] = denominatorBandName;
        for (String bandName : sourceBandNames) {
            final String bandUnit = sourceProduct.getBand(bandName).getUnit();

            if (!bandUnit.equals(Unit.AMPLITUDE) && !bandUnit.equals(Unit.INTENSITY) &&
                    !bandUnit.equals(Unit.AMPLITUDE_DB) && !bandUnit.equals(Unit.INTENSITY_DB)) {
                throw new OperatorException("Please select amplitude or intensity band");
            }

            ProductUtils.copyBand(bandName, sourceProduct, bandName, targetProduct, true);
        }

        final Band targetRatioBand = new Band(RATIO_BAND_NAME,
                ProductData.TYPE_FLOAT32,
                sourceImageWidth,
                sourceImageHeight);

        targetRatioBand.setNoDataValue(0);
        targetRatioBand.setNoDataValueUsed(true);
        targetRatioBand.setUnit("ratio");
        targetProduct.addBand(targetRatioBand);

        final String expression = RATIO_BAND_NAME + " > " + String.valueOf(T_Ratio_Low) + " && " +
                RATIO_BAND_NAME + " < " + String.valueOf(T_Ratio_High);

        final Mask mask = new Mask(FOREST_MASK_NAME,
                targetProduct.getSceneRasterWidth(),
                targetProduct.getSceneRasterHeight(),
                Mask.BandMathsType.INSTANCE);

        mask.setDescription("Forest Area");
        mask.getImageConfig().setValue("color", Color.MAGENTA);
        mask.getImageConfig().setValue("transparency", 0.7);
        mask.getImageConfig().setValue("expression", expression);
        mask.setNoDataValue(0);
        mask.setNoDataValueUsed(true);
        targetProduct.getMaskGroup().add(mask);
        /*
        final Band targetBandMask = new Band(FOREST_MASK_NAME,
                                             ProductData.TYPE_INT8,
                                             sourceImageWidth,
                                             sourceImageHeight);

        targetBandMask.setNoDataValue(-1);
        targetBandMask.setNoDataValueUsed(true);
        targetBandMask.setUnit(Unit.AMPLITUDE);
        targetProduct.addBand(targetBandMask);
        */
    }

    /**
     * Called by the framework in order to compute the stack of tiles for the given target bands.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTiles     The current tiles to be computed for each target band.
     * @param targetRectangle The area in pixel coordinates to be computed (same for all rasters in <code>targetRasters</code>).
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException if an error occurs during computation of the target rasters.
     */
    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {

        try {
            final int tx0 = targetRectangle.x;
            final int ty0 = targetRectangle.y;
            final int tw = targetRectangle.width;
            final int th = targetRectangle.height;
            //System.out.println("tx0 = " + tx0 + ", ty0 = " + ty0 + ", tw = " + tw + ", th = " + th);

            final Rectangle sourceTileRectangle = window.getSourceTileRectangle(tx0, ty0, tw, th,
                                                                                sourceImageWidth, sourceImageHeight);
            final Band nominatorBand = sourceProduct.getBand(sourceBandNames[0]);
            final Band denominatorBand = sourceProduct.getBand(sourceBandNames[1]);
            final Tile nominatorTile = getSourceTile(nominatorBand, sourceTileRectangle);
            final Tile denominatorTile = getSourceTile(denominatorBand, sourceTileRectangle);
            final ProductData nominatorData = nominatorTile.getDataBuffer();
            final ProductData denominatorData = denominatorTile.getDataBuffer();
            final String nominatorBandUnit = nominatorBand.getUnit();
            final String denominatorBandUnit = denominatorBand.getUnit();
            final Double noDataValueN = nominatorBand.getNoDataValue();
            final Double noDataValueD = denominatorBand.getNoDataValue();

            final Band targetRatioBand = targetProduct.getBand(RATIO_BAND_NAME);
            final Tile targetRatioTile = targetTiles.get(targetRatioBand);
            final ProductData ratioData = targetRatioTile.getDataBuffer();
            //final Band targetMaskBand = targetProduct.getBand(FOREST_MASK_NAME);
            //final Tile targetMaskTile = targetTiles.get(targetMaskBand);
            //final ProductData maskData = targetMaskTile.getDataBuffer();

            final TileIndex trgIndex = new TileIndex(targetTiles.get(targetTiles.keySet().iterator().next()));
            final TileIndex srcIndex = new TileIndex(nominatorTile);    // src and trg tile are different size

            final int maxy = ty0 + th;
            final int maxx = tx0 + tw;
            final int windowSize = window.getWindowSize();

            double vDDB, vRatioDB;
            for (int ty = ty0; ty < maxy; ty++) {
                trgIndex.calculateStride(ty);
                srcIndex.calculateStride(ty);
                for (int tx = tx0; tx < maxx; tx++) {
                    final int trgIdx = trgIndex.getIndex(tx);
                    final int srcIdx = srcIndex.getIndex(tx);

                    final double vN = nominatorData.getElemDoubleAt(srcIdx);
                    final double vD = denominatorData.getElemDoubleAt(srcIdx);
                    if (noDataValueN.equals(vN) || noDataValueD.equals(vD)) {
                        //maskData.setElemIntAt(trgIdx, -1);
                        ratioData.setElemFloatAt(trgIdx, 0.0f);
                        continue;
                    }

                    final double vRatio = computeRatio(tx, ty, windowSize, nominatorTile, nominatorData, denominatorTile,
                            denominatorData, nominatorBandUnit, denominatorBandUnit, noDataValueN, noDataValueD);

                    if (noDataValueN.equals(vRatio) || noDataValueD.equals(vRatio)) {
                        //maskData.setElemIntAt(trgIdx, -1);
                        ratioData.setElemFloatAt(trgIdx, 0.0f);
                        continue;
                    }

                    vRatioDB = 10.0 * Math.log10(Math.max(vRatio, Constants.EPS));
                    vDDB = 10.0 * Math.log10(Math.max(vD, Constants.EPS));

                    //int maskBit = 0;
                    //if (vRatioDB > T_Ratio_Low && vRatioDB < T_Ratio_High && vDDB > T_HV_Low) {
                    //    maskBit = 1;
                    //}

                    //maskData.setElemIntAt(trgIdx, maskBit);
                    ratioData.setElemFloatAt(trgIdx, (float) vRatioDB);
                }
            }

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    /**
     * Compute pixel value for ratio band.
     *
     * @param tx              The x coordinate of the central pixel of the sliding window.
     * @param ty              The y coordinate of the central pixel of the sliding window.
     * @param nominatorTile   The source image tile for nominator band.
     * @param nominatorData   The source image data for nominator band.
     * @param denominatorTile The source image tile for denominator band.
     * @param denominatorData The source image data for denominator band.
     * @param bandUnitN       Unit for nominator band.
     * @param bandUnitD       Unit for denominator band.
     * @param noDataValueN    The place holder for no data for nominator band.
     * @param noDataValueD    The place holder for no data for denominator band.
     * @return The local coefficient of variance.
     */
    private double computeRatio(final int tx, final int ty, final int windowSize,
                                final Tile nominatorTile, final ProductData nominatorData,
                                final Tile denominatorTile, final ProductData denominatorData,
                                final String bandUnitN, final String bandUnitD,
                                final double noDataValueN, final double noDataValueD) {

        final double[] samplesN = new double[windowSize * windowSize];
        final double[] samplesD = new double[windowSize * windowSize];
        final int halfWindowSize = windowSize/2;

        final int numSamplesN = getSamples(tx, ty, bandUnitN, noDataValueN, halfWindowSize,
                                           nominatorTile, nominatorData, samplesN);
        if (numSamplesN == 0) {
            return noDataValueN;
        }

        final int numSamplesD = getSamples(tx, ty, bandUnitD, noDataValueD, halfWindowSize,
                                           denominatorTile, denominatorData, samplesD);
        if (numSamplesD == 0) {
            return noDataValueD;
        }

        final double meanN = getMeanValue(samplesN, numSamplesN);

        final double meanD = getMeanValue(samplesD, numSamplesD);

        if (meanD == 0.0) {
            return noDataValueD;
        }

        return meanN / meanD;
    }

    /**
     * Get source samples in the sliding window.
     *
     * @param tx          The x coordinate of the central pixel of the sliding window.
     * @param ty          The y coordinate of the central pixel of the sliding window.
     * @param bandUnit    Band unit.
     * @param noDataValue the place holder for no data
     * @param sourceTile  The source image tile.
     * @param srcData     The source image data.
     * @param samples     The sample array.
     * @return The number of samples.
     */
    private int getSamples(final int tx, final int ty, final String bandUnit, final double noDataValue,
                           final int halfWindowSize, final Tile sourceTile, final ProductData srcData, final double[] samples) {

        final int x0 = Math.max(tx - halfWindowSize, 0);
        final int y0 = Math.max(ty - halfWindowSize, 0);
        final int w = Math.min(tx + halfWindowSize, sourceImageWidth - 1) - x0 + 1;
        final int h = Math.min(ty + halfWindowSize, sourceImageHeight - 1) - y0 + 1;

        final TileIndex tileIndex = new TileIndex(sourceTile);

        int numSamples = 0;
        final int maxy = Math.min(y0 + h, sourceTile.getMaxY() - 1);
        final int maxx = Math.min(x0 + w, sourceTile.getMaxX() - 1);

        switch (bandUnit) {
            case Unit.AMPLITUDE:

                for (int y = y0; y < maxy; y++) {
                    tileIndex.calculateStride(y);
                    for (int x = x0; x < maxx; x++) {
                        final double v = srcData.getElemDoubleAt(tileIndex.getIndex(x));
                        if (v != noDataValue) {
                            samples[numSamples++] = v * v;
                        }
                    }
                }
                break;

            case Unit.INTENSITY:

                for (int y = y0; y < maxy; y++) {
                    tileIndex.calculateStride(y);
                    for (int x = x0; x < maxx; x++) {
                        final double v = srcData.getElemDoubleAt(tileIndex.getIndex(x));
                        if (v != noDataValue) {
                            samples[numSamples++] = v;
                        }
                    }
                }
                break;

            case Unit.AMPLITUDE_DB:

                for (int y = y0; y < maxy; y++) {
                    tileIndex.calculateStride(y);
                    for (int x = x0; x < maxx; x++) {
                        final double v = srcData.getElemDoubleAt(tileIndex.getIndex(x));
                        if (v != noDataValue) {
                            double vv = FastMath.pow(10.0, v / 10.0);
                            samples[numSamples++] = vv * vv;
                        }
                    }
                }
                break;

            case Unit.INTENSITY_DB:

                for (int y = y0; y < maxy; y++) {
                    tileIndex.calculateStride(y);
                    for (int x = x0; x < maxx; x++) {
                        final double v = srcData.getElemDoubleAt(tileIndex.getIndex(x));
                        if (v != noDataValue) {
                            samples[numSamples++] = FastMath.pow(10.0, v / 10.0);
                        }
                    }
                }
                break;

            default:
                throw new OperatorException("Unknown band unit:" + bandUnit);
        }

        return numSamples;
    }

    /**
     * Get the mean value of the samples.
     *
     * @param samples    The sample array.
     * @param numSamples The number of samples.
     * @return mean The mean value.
     */
    private static double getMeanValue(final double[] samples, final int numSamples) {

        double mean = 0.0;
        for (int i = 0; i < numSamples; i++) {
            mean += samples[i];
        }
        mean /= numSamples;

        return mean;
    }

    /**
     * Operator SPI.
     */
    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ForestAreaDetectionOp.class);
        }
    }
}
