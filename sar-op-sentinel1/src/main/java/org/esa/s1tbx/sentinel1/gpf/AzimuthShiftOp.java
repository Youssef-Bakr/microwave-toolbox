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
package org.esa.s1tbx.sentinel1.gpf;

import com.bc.ceres.core.ProgressMonitor;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import org.apache.commons.math3.util.FastMath;
import org.esa.s1tbx.commons.Sentinel1Utils;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.dataop.downloadable.StatusProgressMonitor;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.SystemUtils;
import org.esa.snap.core.util.ThreadExecutor;
import org.esa.snap.core.util.ThreadRunnable;
import org.esa.snap.engine_utilities.datamodel.AbstractMetadata;
import org.esa.snap.engine_utilities.gpf.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Estimate global azimuth offset using Enhanced Spectral Diversity (ESD) approach.
 * Perform azimuth shift for all bursts in a sub-swath with the azimuth offset above
 * using a frequency domain method.
 */

@OperatorMetadata(alias = "Azimuth-Shift",
        category = "Radar/Coregistration/S-1 TOPS Coregistration",
        authors = "Jun Lu, Luis Veci",
        version = "1.0",
        copyright = "Copyright (C) 2014 by Array Systems Computing Inc.",
        description = "Estimate global azimuth offset for the whole image")
public class AzimuthShiftOp extends Operator {

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @TargetProduct(description = "The target product which will use the master's grid.")
    private Product targetProduct = null;

    @Parameter(description = "The coherence threshold for outlier removal", interval = "(0, 1]", defaultValue = "0.15",
            label = "Coherence Threshold for Outlier Removal")
    private double cohThreshold = 0.15;

    @Parameter(description = "The number of windows per overlap for ESD", interval = "[1, 20]", defaultValue = "10",
            label = "Number of Windows Per Overlap for ESD")
    private int numBlocksPerOverlap = 10;

    private boolean isAzimuthOffsetAvailable = false;
    private double azOffset = 0.0;
    private Sentinel1Utils.SubSwathInfo[] subSwath = null;
    private int subSwathIndex = 0;
    private String swathIndexStr = null;
    private String[] subSwathNames = null;
    private String[] polarizations = null;

    private static final int cohWin = 5; // window size for coherence calculation
    private static final String DerampDemodPhase = "derampDemodPhase";

    /**
     * Default constructor. The graph processing framework
     * requires that an operator has a default constructor.
     */
    public AzimuthShiftOp() {
    }

    /**
     * Initializes this operator and sets the one and only target product.
     * <p>The target product can be either defined by a field of type {@link Product} annotated with the
     * {@link TargetProduct TargetProduct} annotation or
     * by calling {@link #setTargetProduct} method.</p>
     * <p>The framework calls this method after it has created this operator.
     * Any client code that must be performed before computation of tile data
     * should be placed here.</p>
     *
     * @throws OperatorException If an error occurs during operator initialisation.
     * @see #getTargetProduct()
     */
    @Override
    public void initialize() throws OperatorException {

        try {
            final InputProductValidator validator = new InputProductValidator(sourceProduct);
            validator.checkIfSARProduct();
            validator.checkIfSentinel1Product();

            checkDerampDemodPhaseBand();

            final Sentinel1Utils su = new Sentinel1Utils(sourceProduct);
            su.computeDopplerRate();
            subSwath = su.getSubSwath();

            subSwathNames = su.getSubSwathNames();
            if (subSwathNames.length != 1) {
                throw new OperatorException("Split product is expected.");
            } else {
                subSwathIndex = 1;//Integer.parseInt(subSwathNames[0].substring(subSwathNames[0].length()-1));
                swathIndexStr = subSwathNames[0].substring(2);
            }

            polarizations = su.getPolarizations();

            createTargetProduct();

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        }
    }

    private void checkDerampDemodPhaseBand() {

        boolean hasDerampDemodPhaseBand = false;
        final Band[] sourceBands = sourceProduct.getBands();
        for (Band band:sourceBands) {
            if (band.getName().contains(DerampDemodPhase)) {
                hasDerampDemodPhaseBand = true;
                break;
            }
        }

        if (!hasDerampDemodPhaseBand) {
            throw new OperatorException("Cannot find derampDemodPhase band in source product. " +
                    "Please run Backgeocoding and select \"Output Deramp and Demod Phase\".");
        }
    }

    /**
     * Create target product.
     */
    private void createTargetProduct() {

        targetProduct = new Product(sourceProduct.getName(),
                sourceProduct.getProductType(),
                sourceProduct.getSceneRasterWidth(),
                sourceProduct.getSceneRasterHeight());

        ProductUtils.copyProductNodes(sourceProduct, targetProduct);

        final String[] bandNames = sourceProduct.getBandNames();
        for (String srcBandName : bandNames) {
            final Band band = sourceProduct.getBand(srcBandName);
            if (band instanceof VirtualBand) {
                continue;
            }

            Band targetBand;
            if (srcBandName.contains(StackUtils.MST) || srcBandName.contains("derampDemod")) {
                targetBand = ProductUtils.copyBand(srcBandName, sourceProduct, srcBandName, targetProduct, true);
            } else if (srcBandName.contains("azOffset") || srcBandName.contains("rgOffset")) {
                continue;
            } else {
                targetBand = new Band(srcBandName,
                        band.getDataType(),
                        band.getRasterWidth(),
                        band.getRasterHeight());

                targetBand.setUnit(band.getUnit());
                targetProduct.addBand(targetBand);
            }

            if(targetBand != null && srcBandName.startsWith("q_")) {
                final String suffix = srcBandName.substring(1);
                ReaderUtils.createVirtualIntensityBand(targetProduct, targetProduct.getBand("i" + suffix), targetBand, suffix);
            }
        }

        /*
        // test data generation
        final String[] bandNames = sourceProduct.getBandNames();
        String mstBandI = null, mstBandQ = null, slvBandI = null, slvBandQ = null, derampBand = null;
        for (String srcBandName : bandNames) {
            if (srcBandName.contains("i_") && srcBandName.contains(StackUtils.MST)) {
                mstBandI = srcBandName;
            } else if (srcBandName.contains("q_") && srcBandName.contains(StackUtils.MST)) {
                mstBandQ = srcBandName;
            } else if (srcBandName.contains("i_") && srcBandName.contains(StackUtils.SLV)) {
                slvBandI = srcBandName;
            } else if (srcBandName.contains("q_") && srcBandName.contains(StackUtils.SLV)) {
                slvBandQ = srcBandName;
            } else if (srcBandName.contains("derampDemod")) {
                derampBand = srcBandName;
            }
        }

        final Band tgtMstBandI = ProductUtils.copyBand(slvBandI, sourceProduct, mstBandI, targetProduct, true);
        final Band tgtMstBandQ = ProductUtils.copyBand(slvBandQ, sourceProduct, mstBandQ, targetProduct, true);
        ProductUtils.copyBand(derampBand, sourceProduct, derampBand, targetProduct, true);

        Band tgtSlvBandI = new Band(slvBandI,
                sourceProduct.getBand(slvBandI).getDataType(),
                sourceProduct.getBand(slvBandI).getRasterWidth(),
                sourceProduct.getBand(slvBandI).getRasterHeight());

        Band tgtSlvBandQ = new Band(slvBandQ,
                sourceProduct.getBand(slvBandQ).getDataType(),
                sourceProduct.getBand(slvBandQ).getRasterWidth(),
                sourceProduct.getBand(slvBandQ).getRasterHeight());

        tgtSlvBandI.setUnit(Unit.REAL);
        tgtSlvBandQ.setUnit(Unit.IMAGINARY);
        targetProduct.addBand(tgtSlvBandI);
        targetProduct.addBand(tgtSlvBandQ);

        final String slvSuffix = slvBandI.substring(1);
        ReaderUtils.createVirtualIntensityBand(targetProduct, tgtSlvBandI, tgtSlvBandQ, slvSuffix);

        final String mstSuffix = mstBandI.substring(1);
        ReaderUtils.createVirtualIntensityBand(targetProduct, tgtMstBandI, tgtMstBandQ, mstSuffix);
        //==============
        */

        targetProduct.setPreferredTileSize(512, subSwath[subSwathIndex - 1].linesPerBurst);
        updateTargetMetadata();
    }

    private void updateTargetMetadata() {

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        if (absTgt == null) {
            return;
        }

        MetadataElement ESDMeasurement = absTgt.getElement("ESD Measurement");
        if (ESDMeasurement == null) {
            absTgt.addElement(new MetadataElement("ESD Measurement"));
        }
        ESDMeasurement = absTgt.getElement("ESD Measurement");

        MetadataElement OverallRgAzShiftElem = ESDMeasurement.getElement("Overall_Range_Azimuth_Shift");
        if (OverallRgAzShiftElem == null) {
            ESDMeasurement.addElement(new MetadataElement("Overall_Range_Azimuth_Shift"));
        }
        OverallRgAzShiftElem = ESDMeasurement.getElement("Overall_Range_Azimuth_Shift");

        MetadataElement swathElem = OverallRgAzShiftElem.getElement(subSwathNames[0]);
        if (swathElem == null) {
            OverallRgAzShiftElem.addElement(new MetadataElement(subSwathNames[0]));
        }

        final MetadataElement AzShiftPerOverlapElem = new MetadataElement("Azimuth_Shift_Per_Overlap");
        AzShiftPerOverlapElem.addElement(new MetadataElement(subSwathNames[0]));
        ESDMeasurement.addElement(AzShiftPerOverlapElem);

        final MetadataElement AzShiftPerBlockElem = new MetadataElement("Azimuth_Shift_Per_Block");
        AzShiftPerBlockElem.addElement(new MetadataElement(subSwathNames[0]));
        ESDMeasurement.addElement(AzShiftPerBlockElem);
    }

    /**
     * Called by the framework in order to compute a tile for the given target band.
     * <p>The default implementation throws a runtime exception with the message "not implemented".</p>
     *
     * @param targetTileMap   The target tiles associated with all target bands to be computed.
     * @param targetRectangle The rectangle of target tile.
     * @param pm              A progress monitor which should be used to determine computation cancelation requests.
     * @throws OperatorException
     *          If an error occurs during computation of the target raster.
     */
    @Override
     public void computeTileStack(Map<Band, Tile> targetTileMap, Rectangle targetRectangle, ProgressMonitor pm)
             throws OperatorException {

        final int x0 = targetRectangle.x;
        final int y0 = targetRectangle.y;
        final int w = targetRectangle.width;
        final int h = targetRectangle.height;
        final int xMax = x0 + w;
        final int yMax = y0 + h;
        //System.out.println("x0 = " + x0 + ", y0 = " + y0 + ", w = " + w + ", h = " + h);

        try {
            if (!isAzimuthOffsetAvailable) {
                estimateAzimuthOffset();
            }

            // test data generation
            //azOffset = 0.0002;

            Band slvBandI = null, slvBandQ = null;
            Band tgtBandI = null, tgtBandQ = null;
            Band derampDemodPhaseBand = null;
            final Band[] sourceBands = sourceProduct.getBands();
            for (Band band:sourceBands) {
                final String bandName = band.getName();
                if (bandName.contains("i_") && bandName.contains(StackUtils.SLV)) {
                    slvBandI = band;
                    tgtBandI = targetProduct.getBand(bandName);
                }

                if (bandName.contains("q_") && bandName.contains(StackUtils.SLV)) {
                    slvBandQ = band;
                    tgtBandQ = targetProduct.getBand(bandName);
                }

                if (bandName.contains(DerampDemodPhase)) {
                    derampDemodPhaseBand = band;
                }
            }

            // get deramp/demodulation phase
            final Tile derampDemodPhaseTile = getSourceTile(derampDemodPhaseBand, targetRectangle);
            final ProductData derampDemodPhaseData = derampDemodPhaseTile.getDataBuffer();
            final TileIndex index = new TileIndex(derampDemodPhaseTile);
            final double[][] derampDemodPhase = new double[h][w];
            for (int y = y0; y < yMax; y++) {
                index.calculateStride(y);
                final int yy = y - y0;
                for (int x = x0; x < xMax; x++) {
                    final int idx = index.getIndex(x);
                    derampDemodPhase[yy][x - x0] = derampDemodPhaseData.getElemDoubleAt(idx);
                }
            }

            // perform deramp and demodulation
            final Tile slvTileI = getSourceTile(slvBandI, targetRectangle);
            final Tile slvTileQ = getSourceTile(slvBandQ, targetRectangle);
            final double[][] derampDemodI = new double[h][w];
            final double[][] derampDemodQ = new double[h][w];
            BackGeocodingOp.performDerampDemod(
                    slvTileI, slvTileQ, targetRectangle, derampDemodPhase, derampDemodI, derampDemodQ);

            // compute shift phase
            final double[] phase = new double[2*h];
            computeShiftPhaseArray(azOffset, h, phase);

            // perform azimuth shift using FFT, and perform reramp and remodulation
            final Tile tgtTileI = targetTileMap.get(tgtBandI);
            final Tile tgtTileQ = targetTileMap.get(tgtBandQ);
            final ProductData tgtDataI = tgtTileI.getDataBuffer();
            final ProductData tgtDataQ = tgtTileQ.getDataBuffer();

            final double[] col1 = new double[2 * h];
            final double[] col2 = new double[2 * h];
            final DoubleFFT_1D col_fft = new DoubleFFT_1D(h);
            for (int c = 0; c < w; c++) {
                final int x = x0 + c;
                for (int r = 0; r < h; r++) {
                    col1[2 * r] = derampDemodI[r][c];
                    col1[2 * r + 1] = derampDemodQ[r][c];

                    col2[2 * r] = derampDemodPhase[r][c];
                    col2[2 * r + 1] = 0.0;
                }

                col_fft.complexForward(col1);
                col_fft.complexForward(col2);

                multiplySpectrumByShiftFactor(col1, phase);
                multiplySpectrumByShiftFactor(col2, phase);

                col_fft.complexInverse(col1, true);
                col_fft.complexInverse(col2, true);

                for (int r = 0; r < h; r++) {
                    final int y = y0 + r;
                    final double cosPhase = FastMath.cos(col2[2 * r]);
                    final double sinPhase = FastMath.sin(col2[2 * r]);
                    final int idx = tgtTileI.getDataBufferIndex(x, y);
                    tgtDataI.setElemDoubleAt(idx, (float)(col1[2 * r] * cosPhase + col1[2 * r + 1] * sinPhase));
                    tgtDataQ.setElemDoubleAt(idx, (float)(-col1[2 * r] * sinPhase + col1[2 * r + 1] * cosPhase));
                }
            }

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException(getId(), e);
        } finally {
            pm.done();
        }
    }

    /**
     * Estimate azimuth offset using ESD approach.
     */
    private synchronized void estimateAzimuthOffset() {

        if (isAzimuthOffsetAvailable) {
            return;
        }

        final int numOverlaps = subSwath[subSwathIndex - 1].numOfBursts - 1;
        final int numShifts = numOverlaps * numBlocksPerOverlap;

        //SystemUtils.LOG.info("estimateAzimuthOffset numOverlaps = " + numOverlaps);

        final StatusProgressMonitor status = new StatusProgressMonitor(StatusProgressMonitor.TYPE.SUBTASK);
        status.beginTask("Estimating azimuth offset... ", numShifts);

        final ThreadExecutor executor = new ThreadExecutor();
        try {
            final Band mBandI = getBand(StackUtils.MST, "i_", swathIndexStr, polarizations[0]);
            final Band mBandQ = getBand(StackUtils.MST, "q_", swathIndexStr, polarizations[0]);
            final Band sBandI = getBand(StackUtils.SLV, "i_", swathIndexStr, polarizations[0]);
            final Band sBandQ = getBand(StackUtils.SLV, "q_", swathIndexStr, polarizations[0]);

            final double spectralSeparation = computeSpectralSeparation();

            final List<AzimuthShiftData> azShiftArray = new ArrayList<>(numShifts);

            for (int i = 0; i < numOverlaps; i++) {

                final Rectangle overlapInBurstOneRectangle =  new Rectangle();
                final Rectangle overlapInBurstTwoRectangle = new Rectangle();

                getOverlappedRectangles(i, overlapInBurstOneRectangle, overlapInBurstTwoRectangle);

                final double[][] coherence = computeCoherence(
                        overlapInBurstOneRectangle, mBandI, mBandQ, sBandI, sBandQ, cohWin);

                final int w = overlapInBurstOneRectangle.width / numBlocksPerOverlap; // block width
                final int h = overlapInBurstOneRectangle.height;
                final int x0BurstOne = overlapInBurstOneRectangle.x;
                final int y0BurstOne = overlapInBurstOneRectangle.y;
                final int y0BurstTwo = overlapInBurstTwoRectangle.y;
                final int overlapIndex = i;

                for (int j = 0; j < numBlocksPerOverlap; j++) {
                    checkForCancellation();
                    final int x0 = x0BurstOne + j * w;
                    final int blockIndex = j;

                    final ThreadRunnable worker = new ThreadRunnable() {
                        @Override
                        public void process() {
                                final Rectangle blockInBurstOneRectangle = new Rectangle(x0, y0BurstOne, w, h);
                                final Rectangle blockInBurstTwoRectangle = new Rectangle(x0, y0BurstTwo, w, h);

                                final double[] blockCoherence = getBlockCoherence(blockIndex, w, h, coherence);

                                final double azShift = estimateAzOffsets(mBandI, mBandQ, sBandI, sBandQ, blockCoherence,
                                        blockInBurstTwoRectangle, blockInBurstOneRectangle, spectralSeparation);

                                synchronized(azShiftArray) {
                                    azShiftArray.add(new AzimuthShiftData(overlapIndex, blockIndex, azShift));
                                }
                        }
                    };
                    executor.execute(worker);
                    status.worked(1);
                }
            }

            status.done();
            executor.complete();

            // todo The following simple average should be replaced by weighted average using coherence as weight
            final double[] averagedAzShiftArray = new double[numOverlaps];
            double totalOffset = 0.0;
            for (int i = 0; i < numOverlaps; i++) {
                double sumAzOffset = 0.0;
                for (int j = 0; j < numShifts; j++) {
                    if (azShiftArray.get(j).overlapIndex == i) {
                        sumAzOffset += azShiftArray.get(j).shift;
                    }
                }
                averagedAzShiftArray[i] = sumAzOffset / numBlocksPerOverlap;
                //SystemUtils.LOG.info(
                //        "AzimuthShiftOp: overlap area = " + i + ", azimuth offset = " + averagedAzShiftArray[i]);
                totalOffset += sumAzOffset;
            }

            azOffset = -totalOffset / numShifts;
            SystemUtils.LOG.info("AzimuthShiftOp: Overall azimuth shift = " + azOffset);

            saveOverallAzimuthShift(azOffset);

            saveAzimuthShiftPerOverlap(averagedAzShiftArray);

            saveAzimuthShiftPerBlock(azShiftArray);

        } catch (Throwable e) {
            OperatorUtils.catchOperatorException("estimateAzimuthOffset", e);
        }

        isAzimuthOffsetAvailable = true;
    }

    private double computeSpectralSeparation () {

        final double tCycle =
                subSwath[subSwathIndex - 1].linesPerBurst * subSwath[subSwathIndex - 1].azimuthTimeInterval;

        double sumSpectralSeparation = 0.0;
        for (int b = 0; b < subSwath[subSwathIndex - 1].numOfBursts; b++) {
            for (int p = 0; p < subSwath[subSwathIndex - 1].samplesPerBurst; p++) {
                sumSpectralSeparation += subSwath[subSwathIndex - 1].dopplerRate[b][p] * tCycle;
            }
        }
        return sumSpectralSeparation / (subSwath[subSwathIndex - 1].numOfBursts *
                subSwath[subSwathIndex - 1].samplesPerBurst);
    }

    private void getOverlappedRectangles(final int overlapIndex,
                                         final Rectangle overlapInBurstOneRectangle,
                                         final Rectangle overlapInBurstTwoRectangle) {

        final int firstValidPixelOfBurstOne = getBurstFirstValidPixel(overlapIndex);
        final int lastValidPixelOfBurstOne = getBurstLastValidPixel(overlapIndex);
        final int firstValidPixelOfBurstTwo = getBurstFirstValidPixel(overlapIndex + 1);
        final int lastValidPixelOfBurstTwo = getBurstLastValidPixel(overlapIndex + 1);
        final int firstValidPixel = Math.max(firstValidPixelOfBurstOne, firstValidPixelOfBurstTwo);
        final int lastValidPixel = Math.min(lastValidPixelOfBurstOne, lastValidPixelOfBurstTwo);
        final int x0 = firstValidPixel;
        final int w = lastValidPixel - firstValidPixel + 1;

        final int numOfInvalidLinesInBurstOne = subSwath[subSwathIndex - 1].linesPerBurst -
                subSwath[subSwathIndex - 1].lastValidLine[overlapIndex] - 1;

        final int numOfInvalidLinesInBurstTwo = subSwath[subSwathIndex - 1].firstValidLine[overlapIndex + 1];

        final int numOverlappedLines = computeBurstOverlapSize(overlapIndex);

        final int h = numOverlappedLines - numOfInvalidLinesInBurstOne - numOfInvalidLinesInBurstTwo;

        final int y0BurstOne =
                subSwath[subSwathIndex - 1].linesPerBurst * (overlapIndex + 1) - numOfInvalidLinesInBurstOne - h;

        final int y0BurstTwo =
                subSwath[subSwathIndex - 1].linesPerBurst * (overlapIndex + 1) + numOfInvalidLinesInBurstTwo;

        overlapInBurstOneRectangle.setBounds(x0, y0BurstOne, w, h);
        overlapInBurstTwoRectangle.setBounds(x0, y0BurstTwo, w, h);
    }

    private int getBurstFirstValidPixel(final int burstIndex) {

        for (int lineIdx = 0; lineIdx < subSwath[subSwathIndex - 1].firstValidSample[burstIndex].length; lineIdx++) {
            if (subSwath[subSwathIndex - 1].firstValidSample[burstIndex][lineIdx] != -1) {
                return subSwath[subSwathIndex - 1].firstValidSample[burstIndex][lineIdx];
            }
        }
        return -1;
    }

    private int getBurstLastValidPixel(final int burstIndex) {

        for (int lineIdx = 0; lineIdx < subSwath[subSwathIndex - 1].lastValidSample[burstIndex].length; lineIdx++) {
            if (subSwath[subSwathIndex - 1].lastValidSample[burstIndex][lineIdx] != -1) {
                return subSwath[subSwathIndex - 1].lastValidSample[burstIndex][lineIdx];
            }
        }
        return -1;
    }

    private double[] getBlockCoherence(
            final int blockIndex, final int blockWidth, final int blockHeight, final double[][] coherence) {

        final double[] blockCoherence = new double[blockWidth*blockHeight];

        for (int i = 0; i < blockCoherence.length; i++) {
            final int r = i / blockWidth;
            final int c = blockIndex*blockWidth + i - r*blockWidth;
            blockCoherence[i] = coherence[r][c];
        }
        return blockCoherence;
    }

    private void saveOverallAzimuthShift(final double azimuthShift) {

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        if (absTgt == null) {
            return;
        }

        final MetadataElement ESDMeasurement = absTgt.getElement("ESD Measurement");
        final MetadataElement OverallRgAzShiftElem = ESDMeasurement.getElement("Overall_Range_Azimuth_Shift");
        final MetadataElement swathElem = OverallRgAzShiftElem.getElement(subSwathNames[0]);

        final MetadataAttribute azimuthShiftAttr = new MetadataAttribute("azimuthShift", ProductData.TYPE_FLOAT32);
        azimuthShiftAttr.setUnit("pixel");
        swathElem.addAttribute(azimuthShiftAttr);
        swathElem.setAttributeDouble("azimuthShift", azimuthShift);
    }

    private void saveAzimuthShiftPerOverlap(final double[] averagedAzShiftArray) {

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        if (absTgt == null) {
            return;
        }

        final MetadataElement ESDMeasurement = absTgt.getElement("ESD Measurement");
        final MetadataElement AzShiftPerOverlapElem = ESDMeasurement.getElement("Azimuth_Shift_Per_Overlap");
        final MetadataElement swathElem = AzShiftPerOverlapElem.getElement(subSwathNames[0]);

        swathElem.addAttribute(new MetadataAttribute("count", ProductData.TYPE_INT16));
        swathElem.setAttributeInt("count", averagedAzShiftArray.length);

        for (int i = 0; i < averagedAzShiftArray.length; i++) {
            final MetadataElement overlapListElem = new MetadataElement("AzimuthShiftList." + i);
            final MetadataAttribute azimuthShiftAttr = new MetadataAttribute("azimuthShift", ProductData.TYPE_FLOAT32);
            azimuthShiftAttr.setUnit("pixel");
            overlapListElem.addAttribute(azimuthShiftAttr);
            overlapListElem.setAttributeDouble("azimuthShift", averagedAzShiftArray[i]);
            overlapListElem.addAttribute(new MetadataAttribute("overlapIndex", ProductData.TYPE_INT16));
            overlapListElem.setAttributeInt("overlapIndex", i);
            swathElem.addElement(overlapListElem);
        }
    }

    private void saveAzimuthShiftPerBlock(final List<AzimuthShiftData> azShiftArray) {

        final MetadataElement absTgt = AbstractMetadata.getAbstractedMetadata(targetProduct);
        if (absTgt == null) {
            return;
        }

        final MetadataElement ESDMeasurement = absTgt.getElement("ESD Measurement");
        final MetadataElement AzShiftPerBlockElem = ESDMeasurement.getElement("Azimuth_Shift_Per_Block");
        final MetadataElement swathElem = AzShiftPerBlockElem.getElement(subSwathNames[0]);

        swathElem.addAttribute(new MetadataAttribute("count", ProductData.TYPE_INT16));
        swathElem.setAttributeInt("count", azShiftArray.size());

        for (int i = 0; i < azShiftArray.size(); i++) {
            final MetadataElement overlapListElem = new MetadataElement("AzimuthShiftList." + i);
            final MetadataAttribute azimuthShiftAttr = new MetadataAttribute("azimuthShift", ProductData.TYPE_FLOAT32);
            azimuthShiftAttr.setUnit("pixel");
            overlapListElem.addAttribute(azimuthShiftAttr);
            overlapListElem.setAttributeDouble("azimuthShift", azShiftArray.get(i).shift);
            overlapListElem.addAttribute(new MetadataAttribute("overlapIndex", ProductData.TYPE_INT16));
            overlapListElem.setAttributeInt("overlapIndex", azShiftArray.get(i).overlapIndex);
            overlapListElem.addAttribute(new MetadataAttribute("blockIndex", ProductData.TYPE_INT16));
            overlapListElem.setAttributeInt("blockIndex", azShiftArray.get(i).blockIndex);
            swathElem.addElement(overlapListElem);
        }
    }

    /**
     * Compute burst overlap size for all bursts in given sub-swath.
     * @return The burst overlap size array.
     */
    private int computeBurstOverlapSize(final int overlapIndex) {

        final double endTime = subSwath[subSwathIndex - 1].burstLastLineTime[overlapIndex];
        final double startTime = subSwath[subSwathIndex - 1].burstFirstLineTime[overlapIndex + 1];
        return (int)((endTime - startTime) / subSwath[subSwathIndex - 1].azimuthTimeInterval);
    }

    private double estimateAzOffsets(final Band mBandI, final Band mBandQ, final Band sBandI, final Band sBandQ,
                                     final double[] blockCoherence, final Rectangle backwardRectangle,
                                     final Rectangle forwardRectangle, final double spectralSeparation) {

        final int mDataType = mBandI.getDataType();
        final int sDataType = sBandI.getDataType();

        final Tile mTileIBack = getSourceTile(mBandI, backwardRectangle);
        final Tile mTileQBack = getSourceTile(mBandQ, backwardRectangle);
        final Tile sTileIBack = getSourceTile(sBandI, backwardRectangle);
        final Tile sTileQBack = getSourceTile(sBandQ, backwardRectangle);

        double[] mIBackArray, mQBackArray;
        if (mDataType == ProductData.TYPE_INT16) {
            final short[] mIBackArrayShort = (short[]) mTileIBack.getDataBuffer().getElems();
            final short[] mQBackArrayShort = (short[]) mTileQBack.getDataBuffer().getElems();
            mIBackArray = new double[mIBackArrayShort.length];
            mQBackArray = new double[mQBackArrayShort.length];
            for (int i = 0; i < mIBackArrayShort.length; i++) {
                mIBackArray[i] = (double)mIBackArrayShort[i];
                mQBackArray[i] = (double)mQBackArrayShort[i];
            }
        } else {
            mIBackArray = (double[]) mTileIBack.getDataBuffer().getElems();
            mQBackArray = (double[]) mTileQBack.getDataBuffer().getElems();
        }

        // test data processing
        /*if (mDataType == ProductData.TYPE_FLOAT32) {
            final float[] mIBackArrayFloat = (float[])mTileIBack.getDataBuffer().getElems();
            final float[] mQBackArrayFloat = (float[])mTileQBack.getDataBuffer().getElems();
            mIBackArray = new double[mIBackArrayFloat.length];
            mQBackArray = new double[mQBackArrayFloat.length];
            for (int i = 0; i < mIBackArrayFloat.length; i++) {
                mIBackArray[i] = (double)mIBackArrayFloat[i];
                mQBackArray[i] = (double)mQBackArrayFloat[i];
            }
        } else {
            mIBackArray = (double[]) mTileIBack.getDataBuffer().getElems();
            mQBackArray = (double[]) mTileQBack.getDataBuffer().getElems();
        }*/


        double[] sIBackArray, sQBackArray;
        if (sDataType == ProductData.TYPE_FLOAT32) {
            final float[] sIBackArrayFloat = (float[])sTileIBack.getDataBuffer().getElems();
            final float[] sQBackArrayFloat = (float[])sTileQBack.getDataBuffer().getElems();
            sIBackArray = new double[sIBackArrayFloat.length];
            sQBackArray = new double[sQBackArrayFloat.length];
            for (int i = 0; i < sIBackArrayFloat.length; i++) {
                sIBackArray[i] = (double)sIBackArrayFloat[i];
                sQBackArray[i] = (double)sQBackArrayFloat[i];
            }
        } else {
            sIBackArray = (double[]) sTileIBack.getDataBuffer().getElems();
            sQBackArray = (double[]) sTileQBack.getDataBuffer().getElems();
        }

        final Tile mTileIFor = getSourceTile(mBandI, forwardRectangle);
        final Tile mTileQFor = getSourceTile(mBandQ, forwardRectangle);
        final Tile sTileIFor = getSourceTile(sBandI, forwardRectangle);
        final Tile sTileQFor = getSourceTile(sBandQ, forwardRectangle);

        double[] mIForArray, mQForArray;
        if (mDataType == ProductData.TYPE_INT16) {
            final short[] mIForArrayShort = (short[]) mTileIFor.getDataBuffer().getElems();
            final short[] mQForArrayShort = (short[]) mTileQFor.getDataBuffer().getElems();
            mIForArray = new double[mIForArrayShort.length];
            mQForArray = new double[mQForArrayShort.length];
            for (int i = 0; i < mIForArrayShort.length; i++) {
                mIForArray[i] = (double)mIForArrayShort[i];
                mQForArray[i] = (double)mQForArrayShort[i];
            }
        } else {
            mIForArray = (double[]) mTileIFor.getDataBuffer().getElems();
            mQForArray = (double[]) mTileQFor.getDataBuffer().getElems();
        }

        // test data processing
        /*if (mDataType == ProductData.TYPE_FLOAT32) {
            final float[] mIForArrayFloat = (float[])mTileIFor.getDataBuffer().getElems();
            final float[] mQForArrayFloat = (float[])mTileQFor.getDataBuffer().getElems();
            mIForArray = new double[mIForArrayFloat.length];
            mQForArray = new double[mQForArrayFloat.length];
            for (int i = 0; i < mIForArrayFloat.length; i++) {
                mIForArray[i] = (double)mIForArrayFloat[i];
                mQForArray[i] = (double)mQForArrayFloat[i];
            }
        } else {
            mIForArray = (double[]) mTileIFor.getDataBuffer().getElems();
            mQForArray = (double[]) mTileQFor.getDataBuffer().getElems();
        }*/

        double[] sIForArray, sQForArray;
        if (sDataType == ProductData.TYPE_FLOAT32) {
            final float[] sIForArrayFloat = (float[])sTileIFor.getDataBuffer().getElems();
            final float[] sQForArrayFloat = (float[])sTileQFor.getDataBuffer().getElems();
            sIForArray = new double[sIForArrayFloat.length];
            sQForArray = new double[sQForArrayFloat.length];
            for (int i = 0; i < sIForArrayFloat.length; i++) {
                sIForArray[i] = (double)sIForArrayFloat[i];
                sQForArray[i] = (double)sQForArrayFloat[i];
            }
        } else {
            sIForArray = (double[]) sTileIFor.getDataBuffer().getElems();
            sQForArray = (double[]) sTileQFor.getDataBuffer().getElems();
        }

        final int arrayLength = mIBackArray.length;
        final double[] backIntReal = new double[arrayLength];
        final double[] backIntImag = new double[arrayLength];
        complexArrayMultiplication(mIBackArray, mQBackArray, sIBackArray, sQBackArray, backIntReal, backIntImag);

        final double[] forIntReal = new double[arrayLength];
        final double[] forIntImag = new double[arrayLength];
        complexArrayMultiplication(mIForArray, mQForArray, sIForArray, sQForArray, forIntReal, forIntImag);

        final double[] diffIntReal = new double[arrayLength];
        final double[] diffIntImag = new double[arrayLength];
        complexArrayMultiplication(forIntReal, forIntImag, backIntReal, backIntImag, diffIntReal, diffIntImag);

        double sumReal = 0.0, sumImag = 0.0;
        for (int i = 0; i < arrayLength; i++) {
            if (blockCoherence[i] > cohThreshold) {
                final double theta = Math.atan2(diffIntImag[i], diffIntReal[i]);
                sumReal += FastMath.cos(theta);
                sumImag += FastMath.sin(theta);
            }
        }

        final double phase = Math.atan2(sumImag, sumReal);
        return phase / (2 * Math.PI * spectralSeparation * subSwath[subSwathIndex - 1].azimuthTimeInterval);
    }

    private static void complexArrayMultiplication(final double[] realArray1, final double[] imagArray1,
                                            final double[] realArray2, final double[] imagArray2,
                                            final double[] realOutput, final double[] imagOutput) {

        final int arrayLength = realArray1.length;
        if (imagArray1.length != arrayLength || realArray2.length != arrayLength || imagArray2.length != arrayLength ||
                realOutput.length != arrayLength || imagOutput.length != arrayLength) {
            throw new OperatorException("Arrays of the same length are expected.");
        }

        for (int i = 0; i < arrayLength; i++) {
            realOutput[i] = realArray1[i] * realArray2[i] + imagArray1[i] * imagArray2[i];
            imagOutput[i] = imagArray1[i] * realArray2[i] - realArray1[i] * imagArray2[i];
        }
    }

    private Band getBand(final String suffix, final String prefix, final String swathIndexStr, final String polarization) {

        final String[] bandNames = sourceProduct.getBandNames();
        for (String bandName : bandNames) {
            if (bandName.contains(suffix) && bandName.contains(prefix) &&
                    bandName.contains(swathIndexStr) && bandName.contains(polarization)) {
                return sourceProduct.getBand(bandName);
            }
        }
        return null;
    }

    private static void computeShiftPhaseArray(final double shift, final int signalLength, final double[] phaseArray) {

        int k2;
        double phaseK;
        final double phase = -2.0 * Math.PI * shift / signalLength;
        final int halfSignalLength = (int) (signalLength * 0.5 + 0.5);

        for (int k = 0; k < signalLength; ++k) {
            if (k < halfSignalLength) {
                phaseK = phase * k;
            } else {
                phaseK = phase * (k - signalLength);
            }
            k2 = k * 2;
            phaseArray[k2] = FastMath.cos(phaseK);
            phaseArray[k2 + 1] = FastMath.sin(phaseK);
        }
    }

    private static void multiplySpectrumByShiftFactor(final double[] array, final double[] phaseArray) {

        int k2;
        double c, s;
        double real, imag;
        final int signalLength = array.length / 2;
        for (int k = 0; k < signalLength; ++k) {
            k2 = k * 2;
            c = phaseArray[k2];
            s = phaseArray[k2 + 1];
            real = array[k2];
            imag = array[k2 + 1];
            array[k2] = real * c - imag * s;
            array[k2 + 1] = real * s + imag * c;
        }
    }

    private double[][] computeCoherence(final Rectangle rectangle, final Band mBandI, final Band mBandQ,
                                        final Band sBandI, final Band sBandQ, final int cohWin) {

        final int x0 = rectangle.x;
        final int y0 = rectangle.y;
        final int w = rectangle.width;
        final int h = rectangle.height;
        final int xMax = x0 + w;
        final int yMax = y0 + h;
        final int halfWindowSize = cohWin / 2;
        final double[][] coherence = new double[h][w];

        final Tile mstTileI = getSourceTile(mBandI, rectangle);
        final Tile mstTileQ = getSourceTile(mBandQ, rectangle);
        final ProductData mstDataBufferI = mstTileI.getDataBuffer();
        final ProductData mstDataBufferQ = mstTileQ.getDataBuffer();

        final Tile slvTileI = getSourceTile(sBandI, rectangle);
        final Tile slvTileQ = getSourceTile(sBandQ, rectangle);
        final ProductData slvDataBufferI = slvTileI.getDataBuffer();
        final ProductData slvDataBufferQ = slvTileQ.getDataBuffer();

        final TileIndex srcIndex = new TileIndex(mstTileI);

        final double[][] cohReal = new double[h][w];
        final double[][] cohImag = new double[h][w];
        final double[][] mstPower = new double[h][w];
        final double[][] slvPower = new double[h][w];
        for (int y = y0; y < yMax; ++y) {
            srcIndex.calculateStride(y);
            final int yy = y - y0;
            for (int x = x0; x < xMax; ++x) {
                final int srcIdx = srcIndex.getIndex(x);
                final int xx = x - x0;

                final float mI = mstDataBufferI.getElemFloatAt(srcIdx);
                final float mQ = mstDataBufferQ.getElemFloatAt(srcIdx);
                final float sI = slvDataBufferI.getElemFloatAt(srcIdx);
                final float sQ = slvDataBufferQ.getElemFloatAt(srcIdx);

                cohReal[yy][xx] = mI * sI + mQ * sQ;
                cohImag[yy][xx] = mQ * sI - mI * sQ;
                mstPower[yy][xx] = mI * mI + mQ * mQ;
                slvPower[yy][xx] = sI * sI + sQ * sQ;
            }
        }

        for (int y = y0; y < yMax; ++y) {
            final int yy = y - y0;
            for (int x = x0; x < xMax; ++x) {
                final int xx = x - x0;

                final int rowSt = Math.max(yy - halfWindowSize, 0);
                final int rowEd = Math.min(yy + halfWindowSize, h - 1);
                final int colSt = Math.max(xx - halfWindowSize, 0);
                final int colEd = Math.min(xx + halfWindowSize, w - 1);

                float cohRealSum = 0.0f, cohImagSum = 0.0f, mstPowerSum = 0.0f, slvPowerSum = 0.0f;
                int count = 0;
                for (int r = rowSt; r <= rowEd; r++) {
                    for (int c = colSt; c <= colEd; c++) {
                        cohRealSum += cohReal[r][c];
                        cohImagSum += cohImag[r][c];
                        mstPowerSum += mstPower[r][c];
                        slvPowerSum += slvPower[r][c];
                        count++;
                    }
                }

                if (count > 0 && mstPowerSum != 0.0 && slvPowerSum != 0.0) {
                    final double cohRealMean = cohRealSum / count;
                    final double cohImagMean = cohImagSum / count;
                    final double mstPowerMean = mstPowerSum / count;
                    final double slvPowerMean = slvPowerSum / count;
                    coherence[yy][xx] = Math.sqrt((cohRealMean * cohRealMean + cohImagMean * cohImagMean) /
                            (mstPowerMean * slvPowerMean));
                }
            }
        }
        return coherence;
    }

    private static class AzimuthShiftData {
        int overlapIndex;
        int blockIndex;
        double shift;

        public AzimuthShiftData(final int overlapIndex, final int blockIndex, final double shift) {
            this.overlapIndex = overlapIndex;
            this.blockIndex = blockIndex;
            this.shift = shift;
        }
    }

    /**
     * The SPI is used to register this operator in the graph processing framework
     * via the SPI configuration file
     * {@code META-INF/services/org.esa.snap.core.gpf.OperatorSpi}.
     * This class may also serve as a factory for new operator instances.
     *
     * @see OperatorSpi#createOperator()
     * @see OperatorSpi#createOperator(java.util.Map, java.util.Map)
     */
    public static class Spi extends OperatorSpi {
        public Spi() {
            super(AzimuthShiftOp.class);
        }
    }

}
